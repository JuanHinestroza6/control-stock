import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/analitica'
import * as apiCategorias from '../../api/categorias'
import { hoyIso, restarDias, validarRango } from '../../lib/rangoFechas'
import { renderizarApp } from '../../pruebas/render'
import type { MasVendidoItem, MasVendidosResponse } from '../../tipos/analitica'
import type { Rol } from '../../tipos/auth'
import { PestanaMasVendidos } from './PestanaMasVendidos'

vi.mock('../../api/analitica')
vi.mock('../../api/categorias')

function item(extra: Partial<MasVendidoItem> = {}): MasVendidoItem {
  return {
    posicion: 1, productoId: 1, productoCodigo: 'LIV001', productoNombre: 'Camiseta Oversize', categoria: { id: 2, nombre: 'Camisetas' },
    unidades: 120, ingreso: 6_000_000, participacion: 42.5, activo: true, ...extra,
  }
}

function respuesta(items: MasVendidoItem[], extra: Partial<MasVendidosResponse> = {}): MasVendidosResponse {
  return {
    desde: '2026-09-15', hasta: '2026-10-14', criterio: 'UNIDADES', agrupacion: 'PRODUCTO', categoriaId: null,
    totalUnidades: 300, totalIngreso: 15_000_000, items, ...extra,
  }
}

function montar(rol: Rol = 'ADMIN', datos: MasVendidosResponse = respuesta([item()])) {
  vi.mocked(api.obtenerMasVendidos).mockResolvedValue(datos)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([
    { id: 2, nombre: 'Camisetas' },
    { id: 3, nombre: 'Jeans' },
  ] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  return renderizarApp(<PestanaMasVendidos />, { rol })
}

const ultimaLlamada = () => vi.mocked(api.obtenerMasVendidos).mock.calls.at(-1)![0]

beforeEach(() => vi.resetAllMocks())

describe('PestanaMasVendidos', () => {
  it('consulta con los valores por defecto', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    const hoy = hoyIso()
    expect(ultimaLlamada()).toEqual({ desde: restarDias(hoy, 29), hasta: hoy, criterio: 'UNIDADES', agrupacion: 'PRODUCTO', limite: 10 })
  })

  it('cada filtro arma la query correcta', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    const hoy = hoyIso()
    await userEvent.selectOptions(screen.getByLabelText('Periodo'), '90')
    await waitFor(() => expect(ultimaLlamada().desde).toBe(restarDias(hoy, 89)))
    await userEvent.selectOptions(screen.getByLabelText('Periodo'), '365')
    await waitFor(() => expect(ultimaLlamada().desde).toBe(restarDias(hoy, 364)))
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await waitFor(() => expect(ultimaLlamada().categoriaId).toBe(3))
    await userEvent.selectOptions(screen.getByLabelText('Criterio'), 'INGRESO')
    await waitFor(() => expect(ultimaLlamada().criterio).toBe('INGRESO'))
    await userEvent.selectOptions(screen.getByLabelText('Agrupar por'), 'VARIANTE')
    await waitFor(() => expect(ultimaLlamada().agrupacion).toBe('VARIANTE'))
    await userEvent.selectOptions(screen.getByLabelText('Cantidad'), '50')
    await waitFor(() => expect(ultimaLlamada().limite).toBe(50))
  })

  it('rango personalizado: consulta con las fechas válidas', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    await userEvent.selectOptions(screen.getByLabelText('Periodo'), 'personalizado')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-01-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-02-01')
    await waitFor(() => expect(ultimaLlamada()).toMatchObject({ desde: '2026-01-01', hasta: '2026-02-01' }))
  })

  it('rango personalizado inválido no consulta y muestra el error', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    await userEvent.selectOptions(screen.getByLabelText('Periodo'), 'personalizado')
    const llamadas = vi.mocked(api.obtenerMasVendidos).mock.calls.length
    await userEvent.type(screen.getByLabelText('Desde'), '2026-03-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-02-01')
    expect(await screen.findByText(/desde no puede ser posterior/i)).toBeInTheDocument()
    expect(vi.mocked(api.obtenerMasVendidos).mock.calls.length).toBe(llamadas)
  })

  it('valida el máximo de 731 días', () => {
    expect(validarRango('2024-01-01', '2025-12-31')).toBeNull()
    expect(validarRango('2024-01-01', '2026-01-01')).toMatch(/731/)
    expect(validarRango('', '2026-01-01')).not.toBeNull()
  })

  it('tabla por producto con totales, barra e inactivo', async () => {
    montar('ADMIN', respuesta([item(), item({ posicion: 2, productoId: 2, productoCodigo: 'LIV002', productoNombre: 'Hoodie', participacion: 10, activo: false })]))
    expect(await screen.findByText('Camiseta Oversize')).toBeInTheDocument()
    expect(screen.getByText('LIV001')).toBeInTheDocument()
    expect(screen.getByText('Inactivo')).toBeInTheDocument()
    expect(screen.getByTestId('total-unidades')).toHaveTextContent('300')
    const barras = screen.getAllByTestId('barra-participacion')
    expect(barras[0]).toHaveStyle({ width: '42.5%' })
    expect(barras[1]).toHaveStyle({ width: '10%' })
    expect(screen.queryByText(/margen|costo/i)).not.toBeInTheDocument()
  })

  it('tabla por variante muestra sku, talla y color', async () => {
    montar('ADMIN', respuesta([item({ varianteId: 9, sku: 'LIV001-M-NEG', talla: 'M', color: 'NEGRO' })], { agrupacion: 'VARIANTE' }))
    await userEvent.selectOptions(screen.getByLabelText('Agrupar por'), 'VARIANTE')
    expect(await screen.findByText(/LIV001-M-NEG · M · NEGRO/)).toBeInTheDocument()
  })

  it('estado vacío', async () => {
    montar('ADMIN', respuesta([], { totalUnidades: 0, totalIngreso: 0 }))
    expect(await screen.findByText(/No hay ventas en el periodo/)).toBeInTheDocument()
  })

  it('estado de error con reintento', async () => {
    montar()
    vi.mocked(api.obtenerMasVendidos).mockRejectedValue(new Error('x'))
    await userEvent.selectOptions(screen.getByLabelText('Criterio'), 'INGRESO')
    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  it('el VENDEDOR la ve', async () => {
    montar('VENDEDOR')
    expect(await screen.findByText('Camiseta Oversize')).toBeInTheDocument()
  })
})
