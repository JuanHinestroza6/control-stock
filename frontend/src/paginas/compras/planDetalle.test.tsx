import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/compras'
import * as apiInventario from '../../api/inventario'
import { SesionProvider } from '../../hooks/SesionProvider'
import { guardarSesion } from '../../lib/auth'
import { crearSesion, renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { EstadoPlan, LineaPlanResponse, PlanCompraDetalleResponse } from '../../tipos/compras'
import type { InventarioItemResponse } from '../../tipos/inventario'
import { PlanDetallePage } from './PlanDetallePage'

vi.mock('../../api/compras')
vi.mock('../../api/inventario')

function linea(id: number, extra: Partial<LineaPlanResponse> = {}): LineaPlanResponse {
  return {
    id, varianteId: 10 + id, sku: `LIV00${id}-M-NEG`, productoId: id, productoCodigo: `LIV00${id}`, productoNombre: 'Camiseta Oversize',
    talla: 'M', color: 'NEGRO', claseAbc: 'A', cantidadSugerida: 20, cantidadAprobada: 24, costoEstimado: 25000, subtotal: 600000,
    justificacion: 'Posición 3 por debajo del umbral 8', cantidadRecibida: null, costoRecibido: null, disponibleActual: 3,
    enCaminoActual: 0, varianteActiva: true, ...extra,
  }
}
function plan(estado: EstadoPlan = 'BORRADOR', extra: Partial<PlanCompraDetalleResponse> = {}): PlanCompraDetalleResponse {
  return {
    id: 7, numero: 'PC-000007', estado, origen: 'SUGERIDO', creadoEn: '2026-10-10T10:00:00', creadoPor: 'Ana Pérez',
    aprobadoEn: null, aprobadoPor: null, recibidoEn: null, unidades: 24, costoEstimadoTotal: 600000, llegadaEsperada: null, atrasado: false,
    version: 3, observacion: null, motivoCancelacion: null, canceladoEn: null, canceladoPor: null, omitidas: null,
    lineas: [linea(1)], ...extra,
  }
}
function item(): InventarioItemResponse {
  return {
    varianteId: 99, sku: 'LIV009-S-AZU', productoId: 9, productoCodigo: 'LIV009', productoNombre: 'Hoodie Basic',
    categoria: { id: 1, nombre: 'Hoodies' }, talla: 'S', color: 'AZUL', stockFisico: 0, stockReservado: 0, stockDisponible: 0,
    stockMinimo: 3, puntoReorden: null, claseAbc: null, estadoStock: 'AGOTADO', precioVenta: 89900, activo: true,
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

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiInventario.listarInventario).mockResolvedValue({ content: [item()], page: { size: 8, number: 0, totalElements: 1, totalPages: 1 } })
})

describe('PlanDetallePage — borrador', () => {
  it('muestra la cabecera, la justificación y la cantidad sugerida', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    montar('INVENTARIO')
    expect(await screen.findByRole('heading', { name: 'Plan PC-000007' })).toBeInTheDocument()
    expect(screen.getByText('Borrador')).toBeInTheDocument()
    expect(screen.getByText('Sugerido')).toBeInTheDocument()
    expect(screen.getByText(/Sugerido: 20 u\./)).toBeInTheDocument()
    expect(screen.getByText(/Posición 3 por debajo/)).toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad de LIV001-M-NEG')).toHaveValue('24')
  })

  it('edita, agrega y quita líneas y guarda con la versión', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    vi.mocked(api.actualizarPlan).mockResolvedValue(plan('BORRADOR', { version: 4 }))
    montar('INVENTARIO')
    const cantidad = await screen.findByLabelText('Cantidad de LIV001-M-NEG')
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled()
    await userEvent.clear(cantidad)
    await userEvent.type(cantidad, '30')
    await userEvent.type(screen.getByLabelText(/Agregar variante/), 'LIV009')
    await userEvent.click(await screen.findByRole('button', { name: /LIV009-S-AZU/ }))
    await userEvent.type(screen.getByLabelText('Cantidad de LIV009-S-AZU'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(api.actualizarPlan).toHaveBeenCalled())
    expect(vi.mocked(api.actualizarPlan).mock.calls[0]).toEqual([
      7,
      { version: 3, lineas: [{ varianteId: 11, cantidad: 30, costoEstimado: 25000 }, { varianteId: 99, cantidad: 5 }] },
    ])
    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Quitar LIV009-S-AZU' }))
    expect(screen.queryByLabelText('Cantidad de LIV009-S-AZU')).not.toBeInTheDocument()
  })

  it('valida la cantidad en el cliente', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    montar('ADMIN')
    const cantidad = await screen.findByLabelText('Cantidad de LIV001-M-NEG')
    await userEvent.clear(cantidad)
    await userEvent.type(cantidad, '0')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByText(/La cantidad debe ser un entero/)).toBeInTheDocument()
    expect(api.actualizarPlan).not.toHaveBeenCalled()
  })

  it('ante un 409 conserva lo escrito, ofrece cargar la versión vigente y guarda con la nueva versión', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValueOnce(plan('BORRADOR')).mockResolvedValue(plan('BORRADOR', { version: 5 }))
    vi.mocked(api.actualizarPlan).mockRejectedValueOnce(error(409, 'El plan fue modificado por otro usuario.')).mockResolvedValue(plan('BORRADOR', { version: 6 }))
    montar('INVENTARIO')
    const cantidad = await screen.findByLabelText('Cantidad de LIV001-M-NEG')
    await userEvent.clear(cantidad)
    await userEvent.type(cantidad, '40')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByText(/El plan cambió mientras lo editabas/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cargar versión vigente' }))
    await waitFor(() => expect(api.obtenerPlan).toHaveBeenCalledTimes(2))
    expect(screen.getByLabelText('Cantidad de LIV001-M-NEG')).toHaveValue('40')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(api.actualizarPlan).toHaveBeenCalledTimes(2))
    expect(vi.mocked(api.actualizarPlan).mock.calls[1][1]).toMatchObject({ version: 5 })
  })

  it('descartar los cambios tras el 409 vuelve a lo del servidor', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR', { version: 5 }))
    vi.mocked(api.actualizarPlan).mockRejectedValue(error(409, 'Versión vieja.'))
    montar('ADMIN')
    const cantidad = await screen.findByLabelText('Cantidad de LIV001-M-NEG')
    await userEvent.clear(cantidad)
    await userEvent.type(cantidad, '40')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Descartar mis cambios' }))
    await waitFor(() => expect(screen.getByLabelText('Cantidad de LIV001-M-NEG')).toHaveValue('24'))
  })
})

describe('PlanDetallePage — aprobar y cancelar', () => {
  it('ADMIN aprueba mostrando el total estimado y con la versión del plan', async () => {
    const aprobado = plan('APROBADO', { version: 4, aprobadoEn: '2026-10-11T09:00:00', aprobadoPor: 'Luis', llegadaEsperada: '2026-10-26T09:00:00' })
    vi.mocked(api.obtenerPlan).mockResolvedValueOnce(plan('BORRADOR')).mockResolvedValue(aprobado)
    vi.mocked(api.aprobarPlan).mockResolvedValue(aprobado)
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar plan' }))
    const dialogo = screen.getByRole('dialog')
    expect(dialogo).toHaveTextContent(/1 línea\(s\)/)
    expect(dialogo).toHaveTextContent(/600\.000/)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar' }))
    await waitFor(() => expect(api.aprobarPlan).toHaveBeenCalledWith(7, { version: 3 }))
    expect(await screen.findByText('Llegada esperada')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprobar plan' })).not.toBeInTheDocument()
    expect(screen.getByText('Aprobado', { selector: 'span' })).toBeInTheDocument()
  })

  it('muestra el 409/422 de aprobar dentro del diálogo', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    vi.mocked(api.aprobarPlan).mockRejectedValue(error(422, 'La variante LIV001-M-NEG está inactiva.'))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar plan' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Aprobar' }))
    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('inactiva')
  })

  it('INVENTARIO no ve Aprobar; en un borrador solo descarta', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    montar('INVENTARIO')
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.queryByRole('button', { name: 'Aprobar plan' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descartar borrador' })).toBeInTheDocument()
  })

  it('INVENTARIO no cancela un APROBADO, ADMIN sí', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO'))
    const { unmount } = montar('INVENTARIO')
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.queryByRole('button', { name: 'Cancelar plan' })).not.toBeInTheDocument()
    unmount()
    montar('ADMIN')
    expect(await screen.findByRole('button', { name: 'Cancelar plan' })).toBeInTheDocument()
  })

  it('cancela con motivo validado (5 a 200) y muestra el estado cancelado', async () => {
    const cancelado = plan('CANCELADO', { motivoCancelacion: 'Proveedor sin stock', canceladoEn: '2026-10-12T08:00:00', canceladoPor: 'Luis' })
    vi.mocked(api.obtenerPlan).mockResolvedValueOnce(plan('APROBADO')).mockResolvedValue(cancelado)
    vi.mocked(api.cancelarPlan).mockResolvedValue(cancelado)
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar plan' }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.type(within(dialogo).getByLabelText('Motivo de la cancelación'), 'no')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar plan' }))
    expect(await within(dialogo).findByText(/El motivo es obligatorio/)).toBeInTheDocument()
    expect(api.cancelarPlan).not.toHaveBeenCalled()
    await userEvent.clear(within(dialogo).getByLabelText('Motivo de la cancelación'))
    await userEvent.type(within(dialogo).getByLabelText('Motivo de la cancelación'), 'Proveedor sin stock')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar plan' }))
    await waitFor(() => expect(api.cancelarPlan).toHaveBeenCalledWith(7, { motivo: 'Proveedor sin stock' }))
    expect(await screen.findByText(/Motivo de la cancelación:/)).toBeInTheDocument()
    expect(screen.getByText(/Proveedor sin stock/)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('PlanDetallePage — solo lectura', () => {
  it('CONTABILIDAD ve el borrador con costos y sin acciones ni campos', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    montar('CONTABILIDAD')
    expect(await screen.findByRole('heading', { name: 'Plan PC-000007' })).toBeInTheDocument()
    expect(screen.getAllByText(/600\.000/).length).toBeGreaterThan(0)
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Aprobar|Cancelar|Descartar|Guardar|Recibir/ })).not.toBeInTheDocument()
  })

  it('RECIBIDO muestra cantidades y costos recibidos', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(
      plan('RECIBIDO', { recibidoEn: '2026-10-20T10:00:00', lineas: [linea(1, { cantidadRecibida: 22, costoRecibido: 26000 })] }),
    )
    montar('ADMIN')
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.getByRole('columnheader', { name: 'Recibida' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '22' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: /26\.000/ })).toBeInTheDocument()
  })

  it('APROBADO atrasado muestra el badge y ofrece Recibir a INVENTARIO', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', { atrasado: true, llegadaEsperada: '2026-10-01T00:00:00' }))
    montar('INVENTARIO')
    expect(await screen.findByText('Atrasado')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Recibir' })).toBeInTheDocument()
  })

  it('muestra error con reintento si el plan no carga', async () => {
    vi.mocked(api.obtenerPlan).mockRejectedValueOnce(new Error('x')).mockResolvedValue(plan('BORRADOR'))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: /reintentar/i }))
    expect(await screen.findByRole('heading', { name: 'Plan PC-000007' })).toBeInTheDocument()
  })
})

describe('PlanDetallePage — matriz de botones por rol y estado', () => {
  const ESTADOS: EstadoPlan[] = ['BORRADOR', 'APROBADO', 'RECIBIDO', 'CANCELADO']
  const ROLES: Rol[] = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD']
  const nada: [boolean, boolean, boolean] = [false, false, false]
  // [aprobar, descartar borrador, cancelar plan]; Recibir se verifica aparte
  const esperado: Record<Rol, Record<EstadoPlan, [boolean, boolean, boolean]>> = {
    ADMIN: { BORRADOR: [true, true, false], APROBADO: [false, false, true], RECIBIDO: nada, CANCELADO: nada },
    INVENTARIO: { BORRADOR: [false, true, false], APROBADO: nada, RECIBIDO: nada, CANCELADO: nada },
    VENDEDOR: { BORRADOR: nada, APROBADO: nada, RECIBIDO: nada, CANCELADO: nada },
    CONTABILIDAD: { BORRADOR: nada, APROBADO: nada, RECIBIDO: nada, CANCELADO: nada },
  }
  const casos = ROLES.flatMap((rol) => ESTADOS.map((estado) => [rol, estado] as const))

  it.each(casos)('%s con plan %s muestra solo los botones permitidos', async (rol, estado) => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan(estado))
    montar(rol)
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    const [aprobar, descartar, cancelar] = esperado[rol][estado]
    expect(!!screen.queryByRole('button', { name: 'Aprobar plan' })).toBe(aprobar)
    expect(!!screen.queryByRole('button', { name: 'Descartar borrador' })).toBe(descartar)
    expect(!!screen.queryByRole('button', { name: 'Cancelar plan' })).toBe(cancelar)
    const editable = estado === 'BORRADOR' && (rol === 'ADMIN' || rol === 'INVENTARIO')
    expect(!!screen.queryByRole('button', { name: 'Guardar cambios' })).toBe(editable)
    const recibir = estado === 'APROBADO' && (rol === 'ADMIN' || rol === 'INVENTARIO')
    expect(!!screen.queryByRole('button', { name: 'Recibir' })).toBe(recibir)
  })
})

describe('PlanDetallePage — casos adicionales', () => {
  it('bloquea un motivo de más de 200 caracteres y no llama a la API', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO'))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar plan' }))
    const dialogo = screen.getByRole('dialog')
    fireEvent.change(within(dialogo).getByLabelText('Motivo de la cancelación'), { target: { value: 'x'.repeat(201) } })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar plan' }))
    expect(await within(dialogo).findByText(/El motivo es obligatorio/)).toBeInTheDocument()
    expect(api.cancelarPlan).not.toHaveBeenCalled()
  })

  it('acepta el motivo de exactamente 200 caracteres', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO'))
    vi.mocked(api.cancelarPlan).mockResolvedValue(plan('CANCELADO', { motivoCancelacion: 'x' }))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar plan' }))
    const dialogo = screen.getByRole('dialog')
    fireEvent.change(within(dialogo).getByLabelText('Motivo de la cancelación'), { target: { value: 'x'.repeat(200) } })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar plan' }))
    await waitFor(() => expect(api.cancelarPlan).toHaveBeenCalledWith(7, { motivo: 'x'.repeat(200) }))
  })

  it('un motivo de 4 caracteres (con espacios alrededor) se rechaza', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    montar('INVENTARIO')
    await userEvent.click(await screen.findByRole('button', { name: 'Descartar borrador' }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.type(within(dialogo).getByLabelText('Motivo de la cancelación'), '  abcd  ')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar plan' }))
    expect(await within(dialogo).findByText(/El motivo es obligatorio/)).toBeInTheDocument()
    expect(api.cancelarPlan).not.toHaveBeenCalled()
  })

  it('muestra el aviso de variantes omitidas recibido por la navegación', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    localStorage.clear()
    guardarSesion(crearSesion('INVENTARIO'))
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={[{ pathname: '/compras/7', state: { omitidas: ['LIV002-S-ROJ', 'LIV003-L-AZU'] } }]}>
          <SesionProvider>
            <Routes>
              <Route path="/compras/:id" element={<PlanDetallePage />} />
            </Routes>
          </SesionProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    const aviso = await screen.findByRole('status')
    expect(aviso).toHaveTextContent('Se omitieron 2 variante(s)')
    expect(aviso).toHaveTextContent('LIV002-S-ROJ, LIV003-L-AZU')
  })

  it('sin estado de navegación no muestra aviso de omitidas', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    montar('INVENTARIO')
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.queryByText(/Se omitieron/)).not.toBeInTheDocument()
  })

  it('el badge Atrasado no aparece si el plan no está atrasado', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', { atrasado: false, llegadaEsperada: '2099-01-01T00:00:00' }))
    montar('ADMIN')
    expect(await screen.findByText('Llegada esperada')).toBeInTheDocument()
    expect(screen.queryByText('Atrasado')).not.toBeInTheDocument()
  })

  it('un plan APROBADO atrasado muestra la llegada esperada y el badge', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('APROBADO', { atrasado: true, llegadaEsperada: '2026-10-01T00:00:00' }))
    montar('CONTABILIDAD')
    expect(await screen.findByText('Atrasado')).toBeInTheDocument()
    expect(screen.getByText('Llegada esperada')).toBeInTheDocument()
  })

  it('el 409 al aprobar se muestra en el diálogo y el plan sigue como borrador', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    vi.mocked(api.aprobarPlan).mockRejectedValue(error(409, 'El plan PC-000007 ya está APROBADO.'))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar plan' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Aprobar' }))
    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('ya está APROBADO')
    expect(api.aprobarPlan).toHaveBeenCalledWith(7, { version: 3 })
    expect(screen.getByText('Borrador')).toBeInTheDocument()
  })

  it('Aprobar queda deshabilitado con cambios sin guardar', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR'))
    montar('ADMIN')
    const cantidad = await screen.findByLabelText('Cantidad de LIV001-M-NEG')
    expect(screen.getByRole('button', { name: 'Aprobar plan' })).toBeEnabled()
    await userEvent.type(cantidad, '5')
    expect(screen.getByRole('button', { name: 'Aprobar plan' })).toBeDisabled()
  })

  it('Aprobar queda deshabilitado en un borrador sin líneas', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(plan('BORRADOR', { lineas: [] }))
    montar('ADMIN')
    await screen.findByRole('heading', { name: 'Plan PC-000007' })
    expect(screen.getByRole('button', { name: 'Aprobar plan' })).toBeDisabled()
  })
})
