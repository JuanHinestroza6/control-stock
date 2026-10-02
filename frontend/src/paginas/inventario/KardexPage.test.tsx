import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/movimientos'
import { renderizarApp } from '../../pruebas/render'
import type { MovimientoResponse, PaginaMovimientos } from '../../tipos/inventario'
import { KardexPage } from './KardexPage'

vi.mock('../../api/movimientos')

function mov(id: number, extra: Partial<MovimientoResponse> = {}): MovimientoResponse {
  return {
    id,
    fecha: '2026-10-02T10:30:00',
    tipo: 'ENTRADA',
    cantidad: 5,
    fisicoAntes: 2,
    fisicoDespues: 7,
    reservadoAntes: 0,
    reservadoDespues: 0,
    costoUnitario: 25000,
    varianteId: 7,
    sku: 'LIV001-M-NEG',
    productoNombre: 'Camiseta Oversize',
    usuarioNombre: 'Ana Pérez',
    referenciaTipo: null,
    referenciaId: null,
    observacion: 'Pedido proveedor',
    ...extra,
  }
}

function pagina(content: MovimientoResponse[], number = 0, totalPages = 1): PaginaMovimientos {
  return { content, page: { size: 20, number, totalElements: content.length, totalPages } }
}

const ultimos = () => vi.mocked(api.listarMovimientos).mock.calls.at(-1)?.[0]

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.listarMovimientos).mockResolvedValue(
    pagina([mov(2, { tipo: 'VENTA', cantidad: 1, fisicoAntes: 7, fisicoDespues: 6, costoUnitario: null, usuarioNombre: null, referenciaTipo: 'VENTA', referenciaId: 9 }), mov(1)]),
  )
})

describe('KardexPage', () => {
  it('muestra carga y movimientos con saldos antes/después, costo y usuario del sistema', async () => {
    renderizarApp(<KardexPage />, { rol: 'CONTABILIDAD' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText('7 → 6')).toBeInTheDocument()
    expect(within(tabla).getByText('2 → 7')).toBeInTheDocument()
    expect(within(tabla).getByText('Sistema')).toBeInTheDocument()
    expect(within(tabla).getByText('VENTA #9')).toBeInTheDocument()
    expect(within(tabla).getByText('Venta')).toBeInTheDocument()
    expect(ultimos()).toMatchObject({ page: 0, size: 20 })
  })

  it('toma varianteId de la URL y permite verlas todas', async () => {
    renderizarApp(<KardexPage />, { rol: 'ADMIN', ruta: '/inventario/kardex?varianteId=7' })
    await screen.findByRole('table')
    expect(ultimos()).toMatchObject({ varianteId: 7 })
    await userEvent.click(screen.getByRole('button', { name: 'Ver todas las variantes' }))
    await waitFor(() => expect(ultimos()?.varianteId).toBeUndefined())
  })

  it('filtra por tipo y fechas y vuelve a la página 0', async () => {
    renderizarApp(<KardexPage />, { rol: 'ADMIN' })
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Tipo de movimiento'), 'Entrada')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-10-02')
    await waitFor(() => expect(ultimos()).toMatchObject({ tipo: 'ENTRADA', desde: '2026-10-01', hasta: '2026-10-02', page: 0 }))
  })

  it('no consulta con un rango de fechas invertido y avisa', async () => {
    renderizarApp(<KardexPage />, { rol: 'ADMIN' })
    await screen.findByRole('table')
    const llamadas = vi.mocked(api.listarMovimientos).mock.calls.length
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-10-01')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-05')
    expect(screen.getByText('La fecha inicial no puede ser posterior a la final.')).toBeInTheDocument()
    expect(ultimos()).not.toMatchObject({ desde: '2026-10-05' })
    expect(vi.mocked(api.listarMovimientos).mock.calls.length).toBeGreaterThanOrEqual(llamadas)
  })

  it('muestra el error y reintenta', async () => {
    vi.mocked(api.listarMovimientos).mockRejectedValueOnce(
      new AxiosError('x', 'E', undefined, undefined, { status: 500, statusText: '', headers: {}, config: {} as never, data: { detail: 'Fallo del servidor' } }),
    )
    renderizarApp(<KardexPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Fallo del servidor')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('vacío sin filtros y con filtros', async () => {
    vi.mocked(api.listarMovimientos).mockResolvedValue(pagina([]))
    renderizarApp(<KardexPage />, { rol: 'ADMIN' })
    expect(await screen.findByText('Aún no hay movimientos en el kardex.')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Tipo de movimiento'), 'Venta')
    expect(await screen.findByText('Ningún movimiento coincide con los filtros.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(await screen.findByText('Aún no hay movimientos en el kardex.')).toBeInTheDocument()
  })

  it('pagina pidiendo la página siguiente', async () => {
    vi.mocked(api.listarMovimientos).mockImplementation((f) => Promise.resolve(pagina([mov(1)], f.page ?? 0, 3)))
    renderizarApp(<KardexPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(ultimos()).toMatchObject({ page: 1 }))
  })
})
