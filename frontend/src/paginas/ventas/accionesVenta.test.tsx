import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/ventas'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { DevolucionResponse, VentaDetalleResponse } from '../../tipos/ventas'
import { VentaDetallePage } from './VentaDetallePage'

vi.mock('../../api/ventas')

function venta(extra: Partial<VentaDetalleResponse> = {}): VentaDetalleResponse {
  return {
    id: 5, numero: 'V-000005', fecha: '2026-10-02T10:30:00', vendedor: 'Ana Pérez', estado: 'REGISTRADA', total: 100000, descuentoTotal: 0,
    anuladaEn: null, anuladaPor: null, motivoAnulacion: null,
    lineas: [
      {
        detalleId: 50, varianteId: 11, sku: 'LIV001-S-NEG', productoNombre: 'Camiseta Oversize', talla: 'S', color: 'NEGRO', cantidad: 3,
        precioLista: 50000, precioUnitario: 50000, costoUnitario: null, subtotal: 150000, cantidadDevuelta: 1, devoluciones: [],
      },
      {
        detalleId: 51, varianteId: 12, sku: 'LIV001-M-NEG', productoNombre: 'Camiseta Oversize', talla: 'M', color: 'NEGRO', cantidad: 1,
        precioLista: 50000, precioUnitario: 50000, costoUnitario: null, subtotal: 50000, cantidadDevuelta: 1, devoluciones: [],
      },
    ],
    ...extra,
  }
}

function pantalla(rol: Rol) {
  return renderizarApp(
    <Routes>
      <Route path="/ventas/:id" element={<VentaDetallePage />} />
    </Routes>,
    { rol, ruta: '/ventas/5' },
  )
}

function error(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.obtenerVenta).mockResolvedValue(venta())
})

describe('acciones del detalle de venta', () => {
  it('Devolver para ADMIN y VENDEDOR solo en líneas con unidades pendientes; Anular solo ADMIN', async () => {
    const a = pantalla('VENDEDOR')
    await screen.findByRole('heading', { name: 'Venta V-000005' })
    expect(screen.getByRole('button', { name: 'Devolver LIV001-S-NEG' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Devolver LIV001-M-NEG' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Anular venta' })).not.toBeInTheDocument()
    a.unmount()

    const b = pantalla('ADMIN')
    expect(await screen.findByRole('button', { name: 'Anular venta' })).toBeInTheDocument()
    b.unmount()

    pantalla('CONTABILIDAD')
    await screen.findByRole('heading', { name: 'Venta V-000005' })
    expect(screen.queryByRole('button', { name: /Devolver|Anular venta/ })).not.toBeInTheDocument()
  })

  it('una venta anulada no ofrece acciones', async () => {
    vi.mocked(api.obtenerVenta).mockResolvedValue(venta({ estado: 'ANULADA', anuladaEn: '2026-10-03T09:00:00', anuladaPor: 'Admin', motivoAnulacion: 'error' }))
    pantalla('ADMIN')
    await screen.findByRole('heading', { name: 'Venta V-000005' })
    expect(screen.queryByRole('button', { name: /Devolver|Anular venta/ })).not.toBeInTheDocument()
  })

  it('devolución: valida cantidad pendiente y motivo, y envía con aptaParaVenta', async () => {
    vi.mocked(api.registrarDevolucion).mockResolvedValue({} as DevolucionResponse)
    pantalla('VENDEDOR')
    await userEvent.click(await screen.findByRole('button', { name: 'Devolver LIV001-S-NEG' }))
    const cantidad = screen.getByLabelText('Cantidad a devolver')
    await userEvent.clear(cantidad)
    await userEvent.type(cantidad, '3')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar devolución' }))
    expect(screen.getByText('Ingresa un entero entre 1 y 2')).toBeInTheDocument()
    expect(screen.getByText(/motivo es obligatorio/)).toBeInTheDocument()
    expect(api.registrarDevolucion).not.toHaveBeenCalled()

    await userEvent.clear(cantidad)
    await userEvent.type(cantidad, '2')
    await userEvent.type(screen.getByLabelText('Motivo'), 'talla errada')
    await userEvent.click(screen.getByLabelText(/Apta para venta/))
    await userEvent.click(screen.getByRole('button', { name: 'Registrar devolución' }))
    await waitFor(() =>
      expect(api.registrarDevolucion).toHaveBeenCalledWith(5, { detalleVentaId: 50, cantidad: 2, motivo: 'talla errada', aptaParaVenta: false }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('devolución: el 422 se ve en el modal y no se pierde lo escrito', async () => {
    vi.mocked(api.registrarDevolucion).mockRejectedValue(error(422, 'Solo quedan 1 unidades por devolver de LIV001-S-NEG.'))
    pantalla('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Devolver LIV001-S-NEG' }))
    await userEvent.type(screen.getByLabelText('Motivo'), 'talla errada')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar devolución' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Solo quedan 1 unidades')
    expect(screen.getByLabelText('Motivo')).toHaveValue('talla errada')
  })

  it('anular: exige motivo, envía y cierra', async () => {
    const anulada = venta({ estado: 'ANULADA', anuladaEn: '2026-10-03T09:00:00', anuladaPor: 'Admin', motivoAnulacion: 'error de digitación' })
    vi.mocked(api.anularVenta).mockImplementation(() => {
      vi.mocked(api.obtenerVenta).mockResolvedValue(anulada)
      return Promise.resolve(anulada)
    })
    pantalla('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Anular venta' }))
    const dialogo = screen.getByRole('dialog', { name: 'Anular venta V-000005' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Anular venta' }))
    expect(screen.getByText(/motivo es obligatorio/)).toBeInTheDocument()
    expect(api.anularVenta).not.toHaveBeenCalled()
    await userEvent.type(screen.getByLabelText('Motivo de la anulación'), '  error de digitación ')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Anular venta' }))
    await waitFor(() => expect(api.anularVenta).toHaveBeenCalledWith(5, { motivo: 'error de digitación' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText('Motivo: error de digitación')).toBeInTheDocument()
  })

  it('anular: el 409 y el 422 se muestran en el modal', async () => {
    vi.mocked(api.anularVenta)
      .mockRejectedValueOnce(error(409, 'La venta V-000005 ya está anulada.'))
      .mockRejectedValueOnce(error(422, 'La venta V-000005 tiene devoluciones; corrige el stock con ajustes.'))
    pantalla('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Anular venta' }))
    await userEvent.type(screen.getByLabelText('Motivo de la anulación'), 'error de digitación')
    const dialogo = screen.getByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Anular venta' }))
    expect(await screen.findByText('La venta V-000005 ya está anulada.')).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Anular venta' }))
    expect(await screen.findByText(/tiene devoluciones/)).toBeInTheDocument()
  })
})
