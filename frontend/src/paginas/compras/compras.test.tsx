import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as api from '../../api/compras'
import * as apiInventario from '../../api/inventario'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { PlanCompraDetalleResponse, PlanCompraResumenResponse } from '../../tipos/compras'
import type { InventarioItemResponse } from '../../tipos/inventario'
import App from '../../App'
import { ComprasPage } from './ComprasPage'
import { PlanDetallePage } from './PlanDetallePage'
import { PlanNuevoPage } from './PlanNuevoPage'
import { construirLineasPlan } from './textosPlan'

vi.mock('../../api/compras')
vi.mock('../../api/categorias')
vi.mock('../../api/inventario')

function resumen(id: number, extra: Partial<PlanCompraResumenResponse> = {}): PlanCompraResumenResponse {
  return {
    id, numero: `PC-00000${id}`, estado: 'BORRADOR', origen: 'SUGERIDO', creadoEn: '2026-10-10T10:00:00', creadoPor: 'Ana Pérez',
    aprobadoEn: null, aprobadoPor: null, recibidoEn: null, lineas: 3, unidades: 40, costoEstimadoTotal: 1200000, llegadaEsperada: null,
    atrasado: false, ...extra,
  }
}
function pag(content: PlanCompraResumenResponse[]) {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } }
}
function detalle(id: number, extra: Partial<PlanCompraDetalleResponse> = {}): PlanCompraDetalleResponse {
  const { lineas: _l, ...base } = resumen(id)
  return { ...base, version: 0, observacion: null, motivoCancelacion: null, canceladoEn: null, canceladoPor: null, omitidas: null, lineas: [], ...extra }
}
function item(): InventarioItemResponse {
  return {
    varianteId: 11, sku: 'LIV001-S-NEG', productoId: 1, productoCodigo: 'LIV001', productoNombre: 'Camiseta Oversize',
    categoria: { id: 1, nombre: 'Camisetas' }, talla: 'S', color: 'NEGRO', stockFisico: 0, stockReservado: 0, stockDisponible: 0,
    stockMinimo: 3, puntoReorden: null, claseAbc: null, estadoStock: 'AGOTADO', precioVenta: 59900, activo: true,
  }
}
function error(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}

function montar(rol: Rol) {
  return renderizarApp(
    <Routes>
      <Route path="/compras" element={<ComprasPage />} />
      <Route path="/compras/nueva" element={<PlanNuevoPage />} />
      <Route path="/compras/:id" element={<PlanDetallePage />} />
    </Routes>,
    { rol, ruta: '/compras' },
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([{ id: 2, nombre: 'Hoodies', totalProductos: 4 }])
  vi.mocked(apiInventario.listarInventario).mockResolvedValue({ content: [item()], page: { size: 8, number: 0, totalElements: 1, totalPages: 1 } })
})

describe('ComprasPage', () => {
  it('lista los planes con número, atrasado y botones para quien gestiona', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([resumen(1), resumen(2, { estado: 'APROBADO', atrasado: true, llegadaEsperada: '2026-10-01T00:00:00' })]))
    montar('INVENTARIO')
    expect(await screen.findByText('PC-000001')).toBeInTheDocument()
    expect(screen.getByText('Atrasado')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generar sugerencia' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Nuevo plan' })).toBeInTheDocument()
  })

  it('CONTABILIDAD solo ve: sin botones', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([resumen(1)]))
    montar('CONTABILIDAD')
    await screen.findByText('PC-000001')
    expect(screen.queryByRole('button', { name: 'Generar sugerencia' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Nuevo plan' })).not.toBeInTheDocument()
  })

  it('filtra por estado', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([resumen(1)]))
    montar('ADMIN')
    await screen.findByText('PC-000001')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'APROBADO')
    await waitFor(() => expect(vi.mocked(api.listarPlanes).mock.calls.at(-1)?.[0]).toMatchObject({ estado: 'APROBADO' }))
  })

  it('muestra vacío y error con reintento', async () => {
    vi.mocked(api.listarPlanes).mockRejectedValueOnce(new Error('x')).mockResolvedValue(pag([]))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: /reintentar/i }))
    expect(await screen.findByText('Aún no hay planes de compra.')).toBeInTheDocument()
  })

  it('genera la sugerencia y navega al detalle avisando las omitidas', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([resumen(1)]))
    vi.mocked(api.sugerirPlan).mockResolvedValue(detalle(7, { omitidas: ['LIV009-M-NEG'] }))
    vi.mocked(api.obtenerPlan).mockResolvedValue(detalle(7))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Generar sugerencia' }))
    await userEvent.selectOptions(await screen.findByLabelText('Categoría (opcional)'), '2')
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Generar sugerencia' }))
    expect(await screen.findByRole('heading', { name: 'Plan PC-000007' })).toBeInTheDocument()
    expect(vi.mocked(api.sugerirPlan).mock.calls[0][0]).toEqual({ categoriaId: 2 })
    expect(screen.getByRole('status')).toHaveTextContent('LIV009-M-NEG')
  })

  it('muestra el 422 sin candidatas de forma amable', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([resumen(1)]))
    vi.mocked(api.sugerirPlan).mockRejectedValue(error(422, 'Ninguna variante cumple.'))
    montar('INVENTARIO')
    await userEvent.click(await screen.findByRole('button', { name: 'Generar sugerencia' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Generar sugerencia' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No hay variantes que necesiten reposición por ahora. Ninguna variante cumple.')
  })
})

describe('PlanNuevoPage', () => {
  async function agregarVariante() {
    await userEvent.type(screen.getByLabelText(/Agregar variante/), 'LIV001')
    await userEvent.click(await screen.findByRole('button', { name: /LIV001-S-NEG/ }))
  }

  it('crea un plan manual y bloquea la variante repetida', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([]))
    vi.mocked(api.crearPlan).mockResolvedValue(detalle(9, { origen: 'MANUAL' }))
    vi.mocked(api.obtenerPlan).mockResolvedValue(detalle(9))
    renderizarApp(
      <Routes>
        <Route path="/compras/nueva" element={<PlanNuevoPage />} />
        <Route path="/compras/:id" element={<PlanDetallePage />} />
      </Routes>,
      { rol: 'ADMIN', ruta: '/compras/nueva' },
    )
    await agregarVariante()
    // ya agregada: el buscador no la vuelve a ofrecer
    await userEvent.type(screen.getByLabelText(/Agregar variante/), 'LIV001')
    expect(await screen.findByText('Sin resultados para la búsqueda.')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(1)

    await userEvent.type(screen.getByLabelText('Cantidad de LIV001-S-NEG'), '12')
    await userEvent.type(screen.getByLabelText('Costo estimado de LIV001-S-NEG'), '25000')
    await userEvent.click(screen.getByRole('button', { name: 'Crear borrador' }))
    expect(await screen.findByRole('heading', { name: 'Plan PC-000009' })).toBeInTheDocument()
    expect(vi.mocked(api.crearPlan).mock.calls[0][0]).toEqual({ lineas: [{ varianteId: 11, cantidad: 12, costoEstimado: 25000 }] })
  })

  it('valida cantidad en el cliente y no llama al backend', async () => {
    renderizarApp(<PlanNuevoPage />, { rol: 'INVENTARIO', ruta: '/compras/nueva' })
    await userEvent.click(screen.getByRole('button', { name: 'Crear borrador' }))
    expect(await screen.findByText('Agrega al menos una variante.')).toBeInTheDocument()
    await agregarVariante()
    await userEvent.click(screen.getByRole('button', { name: 'Crear borrador' }))
    expect(await screen.findByText(/La cantidad debe ser un entero/)).toBeInTheDocument()
    expect(api.crearPlan).not.toHaveBeenCalled()
  })
})

describe('Acceso por rol y navegación (App)', () => {
  it.each(['/compras', '/compras/nueva'])('VENDEDOR no accede a %s: acceso denegado sin consultar planes', async (ruta) => {
    renderizarApp(<App />, { rol: 'VENDEDOR', ruta })
    expect(await screen.findByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
    expect(api.listarPlanes).not.toHaveBeenCalled()
  })

  it('CONTABILIDAD no accede a /compras/nueva', async () => {
    renderizarApp(<App />, { rol: 'CONTABILIDAD', ruta: '/compras/nueva' })
    expect(await screen.findByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Crear borrador' })).not.toBeInTheDocument()
  })

  it('CONTABILIDAD ve el listado en /compras sin botones de gestión', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([resumen(1)]))
    renderizarApp(<App />, { rol: 'CONTABILIDAD', ruta: '/compras' })
    expect(await screen.findByText('PC-000001')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Nuevo plan' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Generar sugerencia' })).not.toBeInTheDocument()
  })

  it('tras sugerir, el detalle muestra las omitidas del state y sin state no muestra aviso', async () => {
    vi.mocked(api.listarPlanes).mockResolvedValue(pag([resumen(1)]))
    vi.mocked(api.sugerirPlan).mockResolvedValue(detalle(7, { omitidas: ['LIV009-M-NEG', 'LIV010-L-AZU'] }))
    vi.mocked(api.obtenerPlan).mockResolvedValue(detalle(7))
    renderizarApp(<App />, { rol: 'INVENTARIO', ruta: '/compras' })
    await userEvent.click(await screen.findByRole('button', { name: 'Generar sugerencia' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Generar sugerencia' }))
    expect(await screen.findByRole('heading', { name: 'Plan PC-000007' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Se omitieron 2 variante(s)')
    expect(screen.getByRole('status')).toHaveTextContent('LIV009-M-NEG, LIV010-L-AZU')
    expect(vi.mocked(api.sugerirPlan).mock.calls[0][0]).toEqual({})
  })

  it('el detalle abierto directamente (sin state) no muestra aviso de omitidas', async () => {
    vi.mocked(api.obtenerPlan).mockResolvedValue(detalle(7))
    renderizarApp(<App />, { rol: 'ADMIN', ruta: '/compras/7' })
    expect(await screen.findByRole('heading', { name: 'Plan PC-000007' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('pagina el listado y vuelve a la página 0 al cambiar el filtro', async () => {
    const dos = { content: [resumen(1)], page: { size: 20, number: 0, totalElements: 21, totalPages: 2 } }
    vi.mocked(api.listarPlanes).mockResolvedValue(dos)
    renderizarApp(<ComprasPage />, { rol: 'ADMIN', ruta: '/compras' })
    await screen.findByText('PC-000001')
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(vi.mocked(api.listarPlanes).mock.calls.at(-1)?.[0]).toMatchObject({ page: 1 }))
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'RECIBIDO')
    await waitFor(() => expect(vi.mocked(api.listarPlanes).mock.calls.at(-1)?.[0]).toMatchObject({ page: 0, estado: 'RECIBIDO' }))
  })
})

describe('construirLineasPlan', () => {
  const base = { varianteId: 1, sku: 'A', descripcion: '', cantidad: '5', costo: '' }
  it('omite el costo vacío y acepta coma decimal', () => {
    expect(construirLineasPlan([base]).lineas).toEqual([{ varianteId: 1, cantidad: 5 }])
    expect(construirLineasPlan([{ ...base, costo: '12,5' }]).lineas).toEqual([{ varianteId: 1, cantidad: 5, costoEstimado: 12.5 }])
  })
  it('rechaza cantidad fuera de rango y costo negativo', () => {
    expect(construirLineasPlan([{ ...base, cantidad: '0' }]).errores[1]).toMatch(/cantidad/)
    expect(construirLineasPlan([{ ...base, cantidad: '100001' }]).lineas).toBeUndefined()
    expect(construirLineasPlan([{ ...base, costo: '-1' }]).errores[1]).toMatch(/costo/)
  })
})
