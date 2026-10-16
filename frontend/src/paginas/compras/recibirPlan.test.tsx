import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/compras'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { EstadoPlan, LineaPlanResponse, PlanCompraDetalleResponse } from '../../tipos/compras'
import { PlanDetallePage } from './PlanDetallePage'

vi.mock('../../api/compras')

function linea(id: number, productoId: number, extra: Partial<LineaPlanResponse> = {}): LineaPlanResponse {
  return {
    id, varianteId: 10 + id, sku: `LIV00${productoId}-T${id}`, productoId, productoCodigo: `LIV00${productoId}`, productoNombre: 'Camiseta',
    talla: 'M', color: 'NEGRO', claseAbc: 'A', cantidadSugerida: 20, cantidadAprobada: 10, costoEstimado: 25000, subtotal: 250000,
    justificacion: null, cantidadRecibida: null, costoRecibido: null, disponibleActual: 3, enCaminoActual: 10, varianteActiva: true, ...extra,
  }
}
function plan(estado: EstadoPlan, lineas: LineaPlanResponse[]): PlanCompraDetalleResponse {
  return {
    id: 7, numero: 'PC-000007', estado, origen: 'SUGERIDO', creadoEn: '2026-10-10T10:00:00', creadoPor: 'Ana', aprobadoEn: '2026-10-11T10:00:00',
    aprobadoPor: 'Admin', recibidoEn: null, unidades: 10, costoEstimadoTotal: 250000, llegadaEsperada: '2026-10-26T10:00:00', atrasado: false,
    version: 3, observacion: null, motivoCancelacion: null, canceladoEn: null, canceladoPor: null, omitidas: null, lineas,
  }
}
function error(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}
function montar(rol: Rol) {
  return renderizarApp(
    <Routes>
      <Route path="/compras/:id" element={<PlanDetallePage />} />
    </Routes>,
    { rol, ruta: '/compras/7' },
  )
}
const dosProductos = () => [linea(1, 1), linea(2, 1, { costoEstimado: 25000 }), linea(3, 2, { costoEstimado: 40000 })]

beforeEach(() => {
  vi.resetAllMocks()
})

describe('Recibir plan', () => {
  it.each<Rol>(['ADMIN', 'INVENTARIO'])('%s ve Recibir en un plan aprobado', async (rol) => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    montar(rol)
    expect(await screen.findByRole('button', { name: 'Recibir' })).toBeInTheDocument()
  })

  it('CONTABILIDAD no ve Recibir y no aparece en borrador ni recibido', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    const { unmount } = montar('CONTABILIDAD')
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.queryByRole('button', { name: 'Recibir' })).not.toBeInTheDocument()
    unmount()

    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('RECIBIDO', [linea(1, 1)]))
    montar('ADMIN')
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.queryByRole('button', { name: 'Recibir' })).not.toBeInTheDocument()
  })

  it('envía cantidades por línea y un costo por producto, con el resumen', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', dosProductos()))
    vi.mocked(api.recibirPlan).mockResolvedValue(plan('RECIBIDO', dosProductos()))
    montar('INVENTARIO')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    expect(within(dialogo).getByLabelText('Costo unitario de LIV001')).toHaveValue('25000')
    expect(within(dialogo).getByLabelText('Costo unitario de LIV002')).toHaveValue('40000')
    expect(within(dialogo).getByLabelText('Resumen de la recepción')).toHaveTextContent('30 unidad(es)')

    const c2 = within(dialogo).getByLabelText('Cantidad recibida de LIV001-T2')
    await userEvent.clear(c2)
    await userEvent.type(c2, '0')
    const costo = within(dialogo).getByLabelText('Costo unitario de LIV001')
    await userEvent.clear(costo)
    await userEvent.type(costo, '26000')
    const resumen = within(dialogo).getByLabelText('Resumen de la recepción')
    expect(resumen).toHaveTextContent('20 unidad(es)')
    expect(resumen).toHaveTextContent('1 línea(s) en 0 dejarán de estar en camino')

    await userEvent.type(within(dialogo).getByLabelText(/Observación/), 'Llegó parcial')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar recepción' }))
    expect(api.recibirPlan).toHaveBeenCalledWith(7, {
      observacion: 'Llegó parcial',
      lineas: [
        { detalleId: 1, cantidadRecibida: 10, costoUnitario: 26000 },
        { detalleId: 2, cantidadRecibida: 0, costoUnitario: 26000 },
        { detalleId: 3, cantidadRecibida: 10, costoUnitario: 40000 },
      ],
    })
  })

  it('con todas las líneas en 0 pide usar Cancelar y no llama al servidor', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    const cant = within(dialogo).getByLabelText('Cantidad recibida de LIV001-T1')
    await userEvent.clear(cant)
    await userEvent.type(cant, '0')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar recepción' }))
    expect(within(dialogo).getByText(/Use Cancelar plan/)).toBeInTheDocument()
    expect(api.recibirPlan).not.toHaveBeenCalled()
  })

  it('valida el rango de la cantidad y el costo', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    const cant = within(dialogo).getByLabelText('Cantidad recibida de LIV001-T1')
    await userEvent.clear(cant)
    await userEvent.type(cant, '100001')
    const costo = within(dialogo).getByLabelText('Costo unitario de LIV001')
    await userEvent.clear(costo)
    await userEvent.type(costo, 'abc')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar recepción' }))
    expect(within(dialogo).getByText('Entero entre 0 y 100.000')).toBeInTheDocument()
    expect(within(dialogo).getByText('Costo válido (0 o más)')).toBeInTheDocument()
    expect(api.recibirPlan).not.toHaveBeenCalled()
  })

  it('avisa de las variantes inactivas pero permite recibir', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1, { varianteActiva: false })]))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    expect(within(dialogo).getByRole('status')).toHaveTextContent('se recibe igual')
    expect(within(dialogo).getByRole('button', { name: 'Confirmar recepción' })).toBeEnabled()
  })

  it.each([
    [409, 'El plan PC-000007 ya está RECIBIDO.'],
    [422, 'Las variantes del mismo producto deben llevar el mismo costo.'],
  ])('muestra el error %i del servidor y conserva el formulario', async (status, detalle) => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    vi.mocked(api.recibirPlan).mockRejectedValue(error(status, detalle))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    const cant = within(dialogo).getByLabelText('Cantidad recibida de LIV001-T1')
    await userEvent.clear(cant)
    await userEvent.type(cant, '7')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar recepción' }))
    expect(await within(dialogo).findByText(detalle)).toBeInTheDocument()
    expect(within(dialogo).getByLabelText('Cantidad recibida de LIV001-T1')).toHaveValue('7')
    expect(within(dialogo).getByRole('button', { name: 'Volver a confirmar' })).toBeInTheDocument()
  })

  it.each<[string, string, number]>([
    ['coma', '25000,5', 25000.5],
    ['punto', '25000.5', 25000.5],
  ])('acepta el costo con %s decimal', async (_nombre, texto, esperado) => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    vi.mocked(api.recibirPlan).mockResolvedValue(plan('RECIBIDO', [linea(1, 1)]))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    const costo = within(dialogo).getByLabelText('Costo unitario de LIV001')
    await userEvent.clear(costo)
    await userEvent.type(costo, texto)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar recepción' }))
    expect(api.recibirPlan).toHaveBeenCalledWith(7, {
      observacion: undefined,
      lineas: [{ detalleId: 1, cantidadRecibida: 10, costoUnitario: esperado }],
    })
  })

  it.each(['25000.555', '25000,123', '-5'])('rechaza el costo %s', async (texto) => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    const costo = within(dialogo).getByLabelText('Costo unitario de LIV001')
    await userEvent.clear(costo)
    await userEvent.type(costo, texto)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar recepción' }))
    expect(within(dialogo).getByText('Costo válido (0 o más)')).toBeInTheDocument()
    expect(api.recibirPlan).not.toHaveBeenCalled()
  })

  it('un producto con tres líneas envía el mismo costo en todas', async () => {
    const lineas = [linea(1, 1), linea(2, 1), linea(3, 1)]
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', lineas))
    vi.mocked(api.recibirPlan).mockResolvedValue(plan('RECIBIDO', lineas))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    const dialogo = screen.getByRole('dialog')
    expect(within(dialogo.querySelector('form') as HTMLElement).getAllByLabelText(/Costo unitario/)).toHaveLength(1)
    const costo = within(dialogo).getByLabelText('Costo unitario de LIV001')
    await userEvent.clear(costo)
    await userEvent.type(costo, '31000')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar recepción' }))
    expect(api.recibirPlan).toHaveBeenCalledWith(7, {
      observacion: undefined,
      lineas: [
        { detalleId: 1, cantidadRecibida: 10, costoUnitario: 31000 },
        { detalleId: 2, cantidadRecibida: 10, costoUnitario: 31000 },
        { detalleId: 3, cantidadRecibida: 10, costoUnitario: 31000 },
      ],
    })
  })

  it.each<[Rol, EstadoPlan]>([
    ['VENDEDOR', 'APROBADO'],
    ['CONTABILIDAD', 'APROBADO'],
    ['ADMIN', 'BORRADOR'],
    ['ADMIN', 'RECIBIDO'],
    ['ADMIN', 'CANCELADO'],
    ['INVENTARIO', 'BORRADOR'],
  ])('%s con plan %s no ve Recibir', async (rol, estado) => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan(estado, [linea(1, 1)]))
    montar(rol)
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.queryByRole('button', { name: 'Recibir' })).not.toBeInTheDocument()
  })

  it('Volver cierra el modal sin enviar nada', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Volver' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.recibirPlan).not.toHaveBeenCalled()
  })

  it('tras recibir cierra el modal y el detalle muestra Recibido', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', [linea(1, 1)]))
    vi.mocked(api.recibirPlan).mockImplementation(async () => {
      vi.mocked(api.obtenerPlan).mockResolvedValue(plan('RECIBIDO', [linea(1, 1, { cantidadRecibida: 10, costoRecibido: 25000 })]))
      return plan('RECIBIDO', [linea(1, 1, { cantidadRecibida: 10, costoRecibido: 25000 })])
    })
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recibir' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar recepción' }))
    expect(await screen.findByText('Recibido')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Recibir' })).not.toBeInTheDocument()
  })
})
