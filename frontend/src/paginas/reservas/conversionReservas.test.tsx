import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/reservas'
import { construirConversion } from '../../lib/reservas'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { ReservaResponse } from '../../tipos/reservas'
import type { VentaDetalleResponse } from '../../tipos/ventas'
import { ReservasPage } from './ReservasPage'

vi.mock('../../api/reservas')

function reserva(id: number, extra: Partial<ReservaResponse> = {}): ReservaResponse {
  return {
    id, numero: `R-00000${id}`, varianteId: 11, sku: 'LIV001-S-NEG', productoNombre: 'Camiseta Oversize', talla: 'S', color: 'NEGRO',
    cantidad: 2, precioVenta: 50000, clienteNombre: 'Carla Ruiz', clienteContacto: '300 123', estado: 'ACTIVA',
    creadaEn: '2099-10-02T10:00:00', venceEn: '2099-10-04T10:00:00', creadaPor: 'Ana', cerradaEn: null, cerradaPor: null,
    motivoCancelacion: null, ventaId: null, ventaNumero: null, ...extra,
  }
}

function montar(rol: Rol, lista: ReservaResponse[]) {
  vi.mocked(api.listarReservas).mockResolvedValue({ content: lista, page: { size: 20, number: 0, totalElements: lista.length, totalPages: 1 } })
  return renderizarApp(
    <Routes>
      <Route path="/reservas" element={<ReservasPage />} />
      <Route path="/ventas/:id" element={<p>Detalle de venta</p>} />
    </Routes>,
    { rol, ruta: '/reservas' },
  )
}

beforeEach(() => vi.resetAllMocks())

describe('selección y acciones en ReservasPage', () => {
  it('solo permite seleccionar reservas del mismo cliente', async () => {
    montar('VENDEDOR', [reserva(1), reserva(2, { clienteContacto: '300123' }), reserva(3, { clienteContacto: '999', clienteNombre: 'Otro' })])
    await userEvent.click(await screen.findByLabelText('Seleccionar R-000001'))
    expect(screen.getByLabelText('Seleccionar R-000002')).toBeEnabled()
    expect(screen.getByLabelText('Seleccionar R-000003')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Convertir en venta' })).toBeInTheDocument()
  })

  it('INVENTARIO y CONTABILIDAD no ven selección; INVENTARIO sí ve cancelar y extender', async () => {
    const { unmount } = montar('INVENTARIO', [reserva(1)])
    await screen.findByText('R-000001')
    expect(screen.queryByLabelText('Seleccionar R-000001')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Extender R-000001' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancelar R-000001' })).toBeInTheDocument()
    unmount()
    montar('CONTABILIDAD', [reserva(1)])
    await screen.findByText('R-000001')
    expect(screen.queryByRole('button', { name: 'Extender R-000001' })).not.toBeInTheDocument()
  })

  it('no ofrece acciones en reservas cerradas', async () => {
    montar('ADMIN', [reserva(1, { estado: 'CANCELADA', cerradaEn: '2026-10-03T09:00:00', motivoCancelacion: 'x' })])
    await screen.findByText('R-000001')
    expect(screen.queryByLabelText('Seleccionar R-000001')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Extender R-000001' })).not.toBeInTheDocument()
  })

  it('convierte con precio editado, agrupa por variante y navega a la venta', async () => {
    vi.mocked(api.convertirReservas).mockResolvedValue({ id: 9 } as VentaDetalleResponse)
    montar('VENDEDOR', [reserva(1), reserva(2)])
    await userEvent.click(await screen.findByLabelText('Seleccionar R-000001'))
    await userEvent.click(screen.getByLabelText('Seleccionar R-000002'))
    await userEvent.click(screen.getByRole('button', { name: 'Convertir en venta' }))
    expect(screen.getByText(/Total:/)).toHaveTextContent('200.000')
    await userEvent.type(screen.getByLabelText('Precio LIV001-S-NEG'), '45000')
    expect(screen.getByText(/Total:/)).toHaveTextContent('180.000')
    expect(screen.getByText(/Descuento:/)).toHaveTextContent('20.000')
    await userEvent.click(within_dialog())
    await waitFor(() =>
      expect(api.convertirReservas).toHaveBeenCalledWith({ reservaIds: [1, 2], precios: [{ varianteId: 11, precioUnitario: 45000 }] }),
    )
    expect(await screen.findByText('Detalle de venta')).toBeInTheDocument()
  })

  it('rechaza precio mayor a la lista sin llamar a la API', async () => {
    montar('ADMIN', [reserva(1)])
    await userEvent.click(await screen.findByLabelText('Seleccionar R-000001'))
    await userEvent.click(screen.getByRole('button', { name: 'Convertir en venta' }))
    await userEvent.type(screen.getByLabelText('Precio LIV001-S-NEG'), '60000')
    await userEvent.click(within_dialog())
    expect(screen.getByText('No puede superar el precio de lista')).toBeInTheDocument()
    expect(api.convertirReservas).not.toHaveBeenCalled()
  })
})

function within_dialog() {
  const dialogo = screen.getByRole('dialog')
  return Array.from(dialogo.querySelectorAll('button')).find((b) => b.getAttribute('type') === 'submit') as HTMLElement
}

describe('construirConversion', () => {
  it('no envía precios iguales a la lista y rechaza clientes distintos', () => {
    expect(construirConversion([reserva(1)], { 11: '50000' }).solicitud).toEqual({ reservaIds: [1] })
    expect(construirConversion([reserva(1), reserva(2, { clienteContacto: 'x' })], {}).general).toBe('Las reservas deben ser del mismo cliente.')
    expect(construirConversion([reserva(1)], { 11: '0' }).errores[11]).toBe('Precio inválido')
  })
})
