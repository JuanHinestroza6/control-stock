import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { Rol } from '../../tipos/auth'
import { modulosPermitidos } from './modulos'
import { Sidebar } from './Sidebar'

function etiquetasVisibles(rol: Rol): string[] {
  render(
    <MemoryRouter>
      <Sidebar rol={rol} abierto={false} onCerrar={() => {}} />
    </MemoryRouter>,
  )
  return screen.getAllByRole('link').map((a) => a.textContent ?? '')
}

describe('Sidebar', () => {
  it('ADMIN ve los 10 módulos', () => {
    expect(etiquetasVisibles('ADMIN')).toEqual([
      'Inicio',
      'Productos',
      'Inventario',
      'Ventas',
      'Reservas',
      'Alertas',
      'Analítica',
      'Compras',
      'Reportes',
      'Usuarios',
    ])
  })

  it('VENDEDOR no ve Usuarios, Compras, Reportes ni Inventario', () => {
    const etiquetas = etiquetasVisibles('VENDEDOR')
    expect(etiquetas).toEqual(['Inicio', 'Productos', 'Ventas', 'Reservas', 'Alertas', 'Analítica'])
  })

  it('CONTABILIDAD ve Reportes y Compras pero no Reservas ni Usuarios', () => {
    const etiquetas = etiquetasVisibles('CONTABILIDAD')
    expect(etiquetas).toContain('Reportes')
    expect(etiquetas).toContain('Compras')
    expect(etiquetas).not.toContain('Reservas')
    expect(etiquetas).not.toContain('Usuarios')
  })

  it('INVENTARIO no ve Ventas, Reportes ni Usuarios', () => {
    const etiquetas = modulosPermitidos('INVENTARIO').map((m) => m.etiqueta)
    expect(etiquetas).not.toContain('Ventas')
    expect(etiquetas).not.toContain('Reportes')
    expect(etiquetas).not.toContain('Usuarios')
    expect(etiquetas).toContain('Inventario')
  })

  it('el botón de cerrar y la tecla Escape cierran el drawer abierto', async () => {
    const onCerrar = vi.fn()
    render(
      <MemoryRouter>
        <Sidebar rol="ADMIN" abierto onCerrar={onCerrar} />
      </MemoryRouter>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar menú' }))
    await userEvent.keyboard('{Escape}')
    expect(onCerrar).toHaveBeenCalledTimes(2)
  })
})
