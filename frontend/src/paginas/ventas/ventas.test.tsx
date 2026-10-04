import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/ventas'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { PaginaVentas, VentaDetalleResponse, VentaResumenResponse } from '../../tipos/ventas'
import { VentaDetallePage } from './VentaDetallePage'
import { VentasPage } from './VentasPage'

vi.mock('../../api/ventas')

function resumen(id: number, extra: Partial<VentaResumenResponse> = {}): VentaResumenResponse {
  return { id, numero: `V-00000${id}`, fecha: '2026-10-02T10:30:00', vendedor: 'Ana Pérez', unidades: 3, total: 150000, estado: 'REGISTRADA', ...extra }
}

function pagina(content: VentaResumenResponse[], number = 0, totalPages = 1): PaginaVentas {
  return { content, page: { size: 20, number, totalElements: content.length, totalPages } }
}

const ultimos = () => vi.mocked(api.listarVentas).mock.calls.at(-1)?.[0]

function detalle(extra: Partial<VentaDetalleResponse> = {}): VentaDetalleResponse {
  return {
    id: 5, numero: 'V-000005', fecha: '2026-10-02T10:30:00', vendedor: 'Ana Pérez', estado: 'REGISTRADA', total: 95000, descuentoTotal: 5000,
    anuladaEn: null, anuladaPor: null, motivoAnulacion: null, reservas: [],
    lineas: [
      {
        detalleId: 50, varianteId: 11, sku: 'LIV001-S-NEG', productoNombre: 'Camiseta Oversize', talla: 'S', color: 'NEGRO', cantidad: 2,
        precioLista: 50000, precioUnitario: 45000, costoUnitario: 25000, subtotal: 90000, cantidadDevuelta: 1,
        devoluciones: [{ id: 1, detalleVentaId: 50, cantidad: 1, motivo: 'talla errada', aptaParaVenta: true, fecha: '2026-10-03T09:00:00', usuarioNombre: 'Luis Gómez' }],
      },
      {
        detalleId: 51, varianteId: 12, sku: 'LIV001-M-NEG', productoNombre: 'Camiseta Oversize', talla: 'M', color: 'NEGRO', cantidad: 1,
        precioLista: 5000, precioUnitario: 5000, costoUnitario: 2000, subtotal: 5000, cantidadDevuelta: 0, devoluciones: [],
      },
    ],
    ...extra,
  }
}

function verDetalle(rol: Rol, ruta = '/ventas/5') {
  return renderizarApp(
    <Routes>
      <Route path="/ventas/:id" element={<VentaDetallePage />} />
    </Routes>,
    { rol, ruta },
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.listarVentas).mockResolvedValue(pagina([resumen(1), resumen(2, { estado: 'ANULADA' })]))
  vi.mocked(api.obtenerVenta).mockResolvedValue(detalle())
})

describe('VentasPage', () => {
  it('muestra carga, filas con enlace al detalle y estados', async () => {
    renderizarApp(<VentasPage />, { rol: 'CONTABILIDAD' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'V-000001' })).toHaveAttribute('href', '/ventas/1')
    expect(within(screen.getByRole('table')).getByText('Anulada')).toBeInTheDocument()
    expect(ultimos()).toMatchObject({ page: 0, size: 20, sort: 'fecha,desc' })
  })

  it('"Nueva venta" solo para ADMIN y VENDEDOR', async () => {
    const a = renderizarApp(<VentasPage />, { rol: 'VENDEDOR' })
    expect(await screen.findByRole('link', { name: 'Nueva venta' })).toHaveAttribute('href', '/ventas/nueva')
    a.unmount()
    renderizarApp(<VentasPage />, { rol: 'CONTABILIDAD' })
    await screen.findByRole('table')
    expect(screen.queryByRole('link', { name: 'Nueva venta' })).not.toBeInTheDocument()
  })

  it('filtra por estado y fechas; un rango invertido avisa y no consulta con él', async () => {
    renderizarApp(<VentasPage />, { rol: 'ADMIN' })
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'Anuladas')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-10-02')
    await waitFor(() => expect(ultimos()).toMatchObject({ estado: 'ANULADA', desde: '2026-10-01', hasta: '2026-10-02', page: 0 }))
    await userEvent.clear(screen.getByLabelText('Hasta'))
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-09-01')
    expect(screen.getByText('La fecha inicial no puede ser posterior a la final.')).toBeInTheDocument()
  })

  it('error con reintento, vacío sin filtros y vacío con filtros', async () => {
    vi.mocked(api.listarVentas).mockRejectedValueOnce(
      new AxiosError('x', 'E', undefined, undefined, { status: 500, statusText: '', headers: {}, config: {} as never, data: { detail: 'Fallo del servidor' } }),
    )
    const a = renderizarApp(<VentasPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Fallo del servidor')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await screen.findByRole('table')
    a.unmount()

    vi.mocked(api.listarVentas).mockResolvedValue(pagina([]))
    renderizarApp(<VentasPage />, { rol: 'VENDEDOR' })
    expect(await screen.findByRole('link', { name: 'Registrar la primera venta' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'Anuladas')
    expect(await screen.findByText('Ninguna venta coincide con los filtros.')).toBeInTheDocument()
  })

  it('pagina pidiendo la página siguiente', async () => {
    vi.mocked(api.listarVentas).mockImplementation((f) => Promise.resolve(pagina([resumen(1)], f.page ?? 0, 3)))
    renderizarApp(<VentasPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(ultimos()).toMatchObject({ page: 1 }))
  })
})

describe('VentaDetallePage', () => {
  it('muestra totales, descuento (lista vs unitario), costo y devoluciones', async () => {
    verDetalle('ADMIN')
    expect(await screen.findByRole('heading', { name: 'Venta V-000005' })).toBeInTheDocument()
    expect(screen.getByText('Descuento 10%')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Costo unitario' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Devoluciones de LIV001-S-NEG' })).toHaveTextContent('talla errada')
    expect(api.obtenerVenta).toHaveBeenCalledWith(5)
  })

  it('sin costos (VENDEDOR) oculta la columna de costo', async () => {
    vi.mocked(api.obtenerVenta).mockResolvedValue(
      detalle({ lineas: detalle().lineas.map((l) => ({ ...l, costoUnitario: null })) }),
    )
    verDetalle('VENDEDOR')
    await screen.findByRole('heading', { name: 'Venta V-000005' })
    expect(screen.queryByRole('columnheader', { name: 'Costo unitario' })).not.toBeInTheDocument()
  })

  it('una venta anulada muestra quién, cuándo y el motivo', async () => {
    vi.mocked(api.obtenerVenta).mockResolvedValue(
      detalle({ estado: 'ANULADA', anuladaEn: '2026-10-04T08:00:00', anuladaPor: 'Admin', motivoAnulacion: 'error de digitación' }),
    )
    verDetalle('CONTABILIDAD')
    expect(await screen.findByText('Motivo: error de digitación')).toBeInTheDocument()
    expect(screen.getByText(/por Admin/)).toBeInTheDocument()
  })

  it('404 del backend muestra el error con reintento', async () => {
    vi.mocked(api.obtenerVenta).mockRejectedValue(
      new AxiosError('x', 'E', undefined, undefined, { status: 404, statusText: '', headers: {}, config: {} as never, data: { detail: 'Venta no encontrada' } }),
    )
    verDetalle('ADMIN', '/ventas/999')
    expect(await screen.findByRole('alert')).toHaveTextContent('Venta no encontrada')
  })

  it('id no numérico no consulta', async () => {
    verDetalle('ADMIN', '/ventas/abc')
    expect(await screen.findByRole('alert')).toHaveTextContent('no existe')
    expect(api.obtenerVenta).not.toHaveBeenCalled()
  })
})
