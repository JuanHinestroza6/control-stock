import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { CeldaMatriz, MatrizResponse } from '../../tipos/catalogo'
import { BadgeStock } from './BadgeStock'
import { MatrizTallaColor } from './MatrizTallaColor'

describe('BadgeStock', () => {
  it.each([
    ['OK', 'Stock óptimo', 'bg-ok/15'],
    ['BAJO', 'Stock bajo', 'bg-alerta/15'],
    ['AGOTADO', 'Agotado', 'bg-critico/15'],
  ] as const)('%s se muestra como "%s" con su color', (estado, texto, clase) => {
    render(<BadgeStock estado={estado} />)
    expect(screen.getByText(texto)).toHaveClass(clase)
  })
})

function celda(talla: CeldaMatriz['talla'], color: string, extra: Partial<CeldaMatriz> = {}): CeldaMatriz {
  return {
    talla,
    color,
    varianteId: 1,
    sku: `SKU-${talla}-${color}`,
    stockFisico: 5,
    stockReservado: 1,
    stockDisponible: 4,
    estadoStock: 'OK',
    activo: true,
    ...extra,
  }
}

const MATRIZ: MatrizResponse = {
  productoId: 1,
  tallas: ['S', 'M'],
  colores: ['BLANCO', 'NEGRO'],
  celdas: [
    celda('S', 'BLANCO'),
    celda('M', 'BLANCO', { estadoStock: 'AGOTADO', stockDisponible: 0 }),
    celda('S', 'NEGRO', { activo: false }),
    // M / NEGRO no existe
  ],
}

describe('MatrizTallaColor', () => {
  it('muestra tallas como columnas y colores como filas', () => {
    render(<MatrizTallaColor matriz={MATRIZ} />)
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Color', 'S', 'M'])
    expect(screen.getByRole('rowheader', { name: 'NEGRO' })).toBeInTheDocument()
  })

  it('celda activa: disponible, estado y SKU', () => {
    render(<MatrizTallaColor matriz={MATRIZ} />)
    const fila = screen.getByRole('row', { name: /BLANCO/ })
    expect(within(fila).getByText('SKU-S-BLANCO')).toBeInTheDocument()
    expect(within(fila).getByText('Stock óptimo')).toBeInTheDocument()
    expect(within(fila).getByText('Agotado')).toBeInTheDocument()
  })

  it('celda inactiva se marca como Inactiva y sin badge de stock', () => {
    render(<MatrizTallaColor matriz={MATRIZ} />)
    const fila = screen.getByRole('row', { name: /NEGRO/ })
    expect(within(fila).getByText('Inactiva')).toBeInTheDocument()
    expect(within(fila).queryByText('Stock óptimo')).not.toBeInTheDocument()
  })

  it('combinación no creada se muestra como "Sin variante"', () => {
    render(<MatrizTallaColor matriz={MATRIZ} />)
    expect(screen.getAllByLabelText('Sin variante')).toHaveLength(1)
  })
})
