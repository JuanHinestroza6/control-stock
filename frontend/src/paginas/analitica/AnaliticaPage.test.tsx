import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/analitica'
import * as apiCategorias from '../../api/categorias'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { EjecucionAnaliticaResponse } from '../../tipos/analitica'
import { AnaliticaPage } from './AnaliticaPage'

vi.mock('../../api/analitica')
vi.mock('../../api/categorias')

function ejecucion(extra: Partial<EjecucionAnaliticaResponse> = {}): EjecucionAnaliticaResponse {
  return {
    id: 1, origen: 'NOCTURNA', usuario: null, iniciadaEn: '2026-10-13T07:00:00Z', terminadaEn: '2026-10-13T07:00:05Z',
    estado: 'EXITOSA', fechaCorte: '2026-10-12', duracionMs: 5200, variantesAnalizadas: 299, claseA: 60, claseB: 90, claseC: 149,
    conPuntoReorden: 250, alertasEvaluadas: 299, alertasFallidas: 0, variantesActivas: 298, enAlertaStock: 41,
    porcentajeAlertaStock: 13.8, sinRotacionActivas: 7, mensaje: null, ...extra,
  }
}

function montar(rol: Rol, ultima: EjecucionAnaliticaResponse | null = ejecucion(), ruta = '/analitica') {
  vi.mocked(api.obtenerUltimaEjecucion).mockResolvedValue(ultima)
  return renderizarApp(<AnaliticaPage />, { rol, ruta })
}

function error409() {
  const respuesta = { status: 409, data: { detail: 'otro texto' } } as AxiosResponse
  return new AxiosError('conflicto', 'ERR_BAD_REQUEST', undefined, undefined, respuesta)
}

async function abrirYConfirmar() {
  await userEvent.click(await screen.findByRole('button', { name: 'Recalcular' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Recalcular' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.obtenerMasVendidos).mockResolvedValue({
    desde: '2026-09-15', hasta: '2026-10-14', criterio: 'UNIDADES', agrupacion: 'PRODUCTO', categoriaId: null,
    totalUnidades: 0, totalIngreso: 0, items: [],
  })
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
})

describe('AnaliticaPage - pestañas por rol', () => {
  it.each<Rol>(['ADMIN', 'INVENTARIO', 'CONTABILIDAD'])('%s ve las tres pestañas', async (rol) => {
    montar(rol)
    const tabs = screen.getAllByRole('tab').map((t) => t.textContent)
    expect(tabs).toEqual(['Más vendidos', 'Clasificación ABC', 'Punto de reorden'])
    expect(await screen.findByText('Última ejecución')).toBeInTheDocument()
  })

  it('VENDEDOR solo ve Más vendidos, sin panel ni Recalcular', () => {
    montar('VENDEDOR')
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Más vendidos'])
    expect(screen.queryByText('Última ejecución')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /recalcular/i })).not.toBeInTheDocument()
    expect(api.obtenerUltimaEjecucion).not.toHaveBeenCalled()
  })

  it('VENDEDOR no accede a una pestaña restringida por la URL', () => {
    montar('VENDEDOR', null, '/analitica?pestana=abc')
    expect(screen.getByRole('tab', { name: 'Más vendidos', selected: true })).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: /ABC/ })).not.toBeInTheDocument()
  })

  it('la pestaña activa sale de la URL y cambia al hacer clic', async () => {
    montar('ADMIN', null, '/analitica?pestana=reorden')
    expect(screen.getByRole('tab', { name: 'Punto de reorden', selected: true })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Clasificación ABC' }))
    expect(screen.getByRole('tab', { name: 'Clasificación ABC', selected: true })).toBeInTheDocument()
    expect(screen.getByRole('tabpanel')).toHaveAttribute('aria-labelledby', 'tab-abc')
  })
})

describe('PanelEjecucion', () => {
  it('INVENTARIO ve la ejecución pero no Recalcular', async () => {
    montar('INVENTARIO')
    expect(await screen.findByText('Exitosa')).toBeInTheDocument()
    expect(screen.getByText('60 / 90 / 149')).toBeInTheDocument()
    expect(screen.getByText(/41 de 298/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /recalcular/i })).not.toBeInTheDocument()
  })

  it('CONTABILIDAD ve la ejecución pero no Recalcular', async () => {
    montar('CONTABILIDAD')
    expect(await screen.findByText('Exitosa')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /recalcular/i })).not.toBeInTheDocument()
  })

  it('estado vacío si nunca corrió', async () => {
    montar('ADMIN', null)
    expect(await screen.findByText('Aún no se ha calculado la analítica.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Recalcular' })).toBeEnabled()
  })

  it('ejecución FALLIDA muestra el mensaje', async () => {
    montar('CONTABILIDAD', ejecucion({ estado: 'FALLIDA', mensaje: 'Falló el cálculo ABC' }))
    expect(await screen.findByText('Fallida')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Falló el cálculo ABC')
  })

  it('muestra error con reintento', async () => {
    vi.mocked(api.obtenerUltimaEjecucion).mockRejectedValueOnce(new Error('x')).mockResolvedValue(ejecucion())
    renderizarApp(<AnaliticaPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: /reintentar/i }))
    expect(await screen.findByText('Exitosa')).toBeInTheDocument()
  })

  it('ADMIN recalcula con confirmación y el botón queda deshabilitado durante la petición', async () => {
    let terminar: (e: EjecucionAnaliticaResponse) => void = () => {}
    vi.mocked(api.recalcularAnalitica).mockReturnValue(new Promise((r) => (terminar = r)))
    montar('ADMIN')
    await userEvent.click(await screen.findByRole('button', { name: 'Recalcular' }))
    expect(api.recalcularAnalitica).not.toHaveBeenCalled()
    const dialogo = screen.getByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Recalcular' }))
    await waitFor(() => expect(api.recalcularAnalitica).toHaveBeenCalledTimes(1))
    expect(within(dialogo).getByRole('button', { name: 'Recalcular' })).toBeDisabled()
    terminar(ejecucion({ id: 2, origen: 'MANUAL' }))
    expect(await screen.findByText('Recálculo terminado.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('409 muestra el mensaje amable', async () => {
    vi.mocked(api.recalcularAnalitica).mockRejectedValue(error409())
    montar('ADMIN')
    await abrirYConfirmar()
    expect(await screen.findByText('Ya hay un recálculo en curso.')).toBeInTheDocument()
  })

  it('error genérico muestra un mensaje', async () => {
    vi.mocked(api.recalcularAnalitica).mockRejectedValue(new Error('boom'))
    montar('ADMIN')
    await abrirYConfirmar()
    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent(/error inesperado/i)
  })

  it('con una ejecución EN_CURSO el botón está deshabilitado', async () => {
    montar('ADMIN', ejecucion({ estado: 'EN_CURSO', duracionMs: null, terminadaEn: null }))
    expect(await screen.findByText('En curso')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /recalculando/i })).toBeDisabled()
  })
})
