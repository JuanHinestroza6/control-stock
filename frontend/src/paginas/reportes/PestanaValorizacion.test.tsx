import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as api from '../../api/reportes'
import { renderizarApp } from '../../pruebas/render'
import type { FilaValorizacion, ValorizacionResponse } from '../../tipos/reportes'
import { PestanaValorizacion } from './PestanaValorizacion'

vi.mock('../../api/reportes')
vi.mock('../../api/categorias')

function fila(extra: Partial<FilaValorizacion> = {}): FilaValorizacion {
  return {
    id: 1, codigo: null, nombre: 'Camisetas', categoria: null, unidadesFisicas: 100, unidadesReservadas: 5, valorCosto: 3_000_000,
    valorVenta: 6_000_000, margenPotencial: 3_000_000, margenPotencialPorcentaje: 50, unidadesEnCamino: 20, valorEnCamino: 600_000, ...extra,
  }
}

function respuesta(extra: Partial<ValorizacionResponse> = {}): ValorizacionResponse {
  return {
    categoriaId: null, categoria: null, agrupacion: 'CATEGORIA', incluirInactivas: true, sinExistencias: false, mensaje: null,
    filas: [fila(), fila({ id: 2, nombre: 'Jeans', unidadesFisicas: 40, valorCosto: 2_000_000, margenPotencialPorcentaje: null })],
    totales: fila({ id: null, nombre: 'Total', unidadesFisicas: 140, valorCosto: 5_000_000, margenPotencialPorcentaje: 48.3 }),
    notas: ['Valorizado al costo actual.'], generadoEn: '2026-10-21T10:00:00', ...extra,
  }
}

function montar(datos: ValorizacionResponse = respuesta()) {
  vi.mocked(api.obtenerValorizacion).mockResolvedValue(datos)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([{ id: 3, nombre: 'Jeans' }] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  return renderizarApp(<PestanaValorizacion />, { rol: 'CONTABILIDAD' })
}

const ultimosFiltros = () => vi.mocked(api.obtenerValorizacion).mock.calls.at(-1)![0]

beforeEach(() => {
  vi.resetAllMocks()
  URL.createObjectURL = vi.fn(() => 'blob:prueba')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

describe('PestanaValorizacion', () => {
  it('muestra la tabla con totales, lo en camino como informativo y las notas', async () => {
    montar()
    const tabla = await screen.findByRole('table', { name: /valorización del inventario/i })
    expect(within(tabla).getAllByRole('row')).toHaveLength(4)
    expect(within(tabla).getByText('Total')).toBeInTheDocument()
    expect(within(tabla).getByText('48,3 %')).toBeInTheDocument()
    expect(within(tabla).getByText('—')).toBeInTheDocument()
    expect(screen.getByText(/lo que está en camino es informativo/i)).toBeInTheDocument()
    expect(screen.getByText('Valorizado al costo actual.')).toBeInTheDocument()
  })

  it('incluye inactivas por defecto y los filtros arman la consulta', async () => {
    montar()
    await screen.findByRole('table')
    expect(ultimosFiltros()).toEqual({ agrupacion: 'CATEGORIA', incluirInactivas: true })
    const casilla = screen.getByRole('checkbox', { name: /incluir inactivas/i })
    expect(casilla).toBeChecked()
    await userEvent.click(casilla)
    await waitFor(() => expect(ultimosFiltros().incluirInactivas).toBe(false))
    await userEvent.selectOptions(screen.getByLabelText('Agrupar por'), 'VARIANTE')
    await waitFor(() => expect(ultimosFiltros().agrupacion).toBe('VARIANTE'))
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await waitFor(() => expect(ultimosFiltros().categoriaId).toBe(3))
  })

  it('sin existencias muestra el mensaje del servidor', async () => {
    montar(respuesta({ sinExistencias: true, mensaje: 'No hay existencias para valorizar', filas: [] }))
    expect(await screen.findByText('No hay existencias para valorizar')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('exporta con los filtros vigentes', async () => {
    montar()
    vi.mocked(api.exportarValorizacion).mockResolvedValue({ nombre: 'valorizacion.pdf', blob: new Blob(['x']) })
    await screen.findByRole('table')
    await userEvent.click(screen.getByRole('button', { name: /exportar valorización a pdf/i }))
    await waitFor(() => expect(api.exportarValorizacion).toHaveBeenCalledWith({ agrupacion: 'CATEGORIA', incluirInactivas: true }, 'PDF'))
  })
})
