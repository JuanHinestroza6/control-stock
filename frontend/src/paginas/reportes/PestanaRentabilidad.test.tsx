import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { cloneElement, type ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as api from '../../api/reportes'
import { renderizarApp } from '../../pruebas/render'
import type { FilaRentabilidad, RentabilidadResponse, TotalesRentabilidad } from '../../tipos/reportes'
import { PestanaRentabilidad } from './PestanaRentabilidad'

vi.mock('../../api/reportes')
vi.mock('../../api/categorias')
// jsdom no mide el contenedor: ResponsiveContainer recibe un tamaño fijo.
vi.mock('recharts', async (importar) => {
  const real = await importar<typeof import('recharts')>()
  return {
    ...real,
    ResponsiveContainer: ({ children }: { children: ReactElement<{ width?: number; height?: number }> }) => cloneElement(children, { width: 600, height: 288 }),
  }
})

function fila(extra: Partial<FilaRentabilidad> = {}): FilaRentabilidad {
  return {
    id: 1, codigo: null, nombre: 'Camisetas', categoria: null, ventasLista: 1_200_000, descuentos: 100_000, facturado: 1_100_000,
    devoluciones: 0, ingresoNeto: 1_100_000, costoVentas: 600_000, margenBruto: 500_000, margenPorcentaje: 45.5, mermaNoApta: 20_000,
    resultadoDespuesMermas: 480_000, unidadesVendidas: 30, unidadesDevueltas: 0, unidadesNetas: 30, participacionMargen: 62.5, ...extra,
  }
}

const totales: TotalesRentabilidad = {
  ventasLista: 2_000_000, descuentos: 150_000, facturado: 1_850_000, devoluciones: 0, ingresoNeto: 1_850_000, costoVentas: 1_000_000,
  margenBruto: 850_000, margenPorcentaje: 45.9, mermaNoApta: 20_000, resultadoDespuesMermas: 830_000, unidadesVendidas: 55,
  unidadesDevueltas: 0, unidadesNetas: 55,
}

function respuesta(extra: Partial<RentabilidadResponse> = {}): RentabilidadResponse {
  return {
    desde: '2026-10-01', hasta: '2026-10-21', categoriaId: null, categoria: null, agrupacion: 'CATEGORIA', orden: 'MARGEN',
    sinVentas: false, mensaje: null, filas: [fila(), fila({ id: 2, nombre: 'Jeans', margenBruto: -50_000, margenPorcentaje: -16.7, participacionMargen: 37.5, ingresoNeto: 300_000 })],
    totales, notas: ['Los costos usan el costo de la venta.'], generadoEn: '2026-10-21T10:00:00', ...extra,
  }
}

function montar(datos: RentabilidadResponse = respuesta()) {
  vi.mocked(api.obtenerRentabilidad).mockResolvedValue(datos)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([
    { id: 2, nombre: 'Camisetas' },
    { id: 3, nombre: 'Jeans' },
  ] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  return renderizarApp(<PestanaRentabilidad />, { rol: 'CONTABILIDAD' })
}

const ultimosFiltros = () => vi.mocked(api.obtenerRentabilidad).mock.calls.at(-1)![0]

beforeEach(() => {
  vi.resetAllMocks()
  URL.createObjectURL = vi.fn(() => 'blob:prueba')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

describe('PestanaRentabilidad', () => {
  it('muestra tabla con totales, gráfico, notas y fecha de generación', async () => {
    montar()
    const tabla = await screen.findByRole('table', { name: /rentabilidad por agrupación/i })
    expect(within(tabla).getAllByRole('row')).toHaveLength(4) // encabezado + 2 filas + total
    expect(within(tabla).getByText('Total')).toBeInTheDocument()
    expect(within(tabla).getByText('45,5 %')).toBeInTheDocument()
    expect(within(tabla).getByText('62,5 %')).toBeInTheDocument()
    expect(screen.getByText(/costo de la venta/)).toBeInTheDocument()
    expect(screen.getByText(/^Generado/)).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: /gráfico de barras/i }, { timeout: 8000 })).toBeInTheDocument()
    // Los filtros efectivos del servidor llenan las fechas.
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-10-01')
    expect(screen.getByLabelText('Hasta')).toHaveValue('2026-10-21')
  })

  it('el margen negativo se ve como negativo', async () => {
    montar()
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText(/-\$\s?50\.000/)).toHaveClass('text-critico')
  })

  it('sin ventas muestra el mensaje del servidor y no la tabla', async () => {
    montar(respuesta({ sinVentas: true, mensaje: 'No hay ventas en el periodo', filas: [], notas: [] }))
    expect(await screen.findByText('No hay ventas en el periodo')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /exportar rentabilidad a pdf/i })).toBeEnabled()
  })

  it('muestra el error con reintento', async () => {
    vi.mocked(api.obtenerRentabilidad).mockRejectedValueOnce(new Error('falló'))
    vi.mocked(api.obtenerRentabilidad).mockResolvedValue(respuesta())
    vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
    renderizarApp(<PestanaRentabilidad />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('los filtros se convierten en parámetros de la consulta', async () => {
    montar()
    await screen.findByRole('table')
    expect(ultimosFiltros()).toEqual({ agrupacion: 'CATEGORIA', orden: 'MARGEN' })
    await userEvent.selectOptions(screen.getByLabelText('Agrupar por'), 'PRODUCTO')
    await waitFor(() => expect(ultimosFiltros().agrupacion).toBe('PRODUCTO'))
    await userEvent.selectOptions(screen.getByLabelText('Ordenar por'), 'UNIDADES')
    await waitFor(() => expect(ultimosFiltros().orden).toBe('UNIDADES'))
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await waitFor(() => expect(ultimosFiltros().categoriaId).toBe(3))
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-09-01' } })
    await waitFor(() => expect(ultimosFiltros().desde).toBe('2026-09-01'))
    expect(ultimosFiltros().hasta).toBe('2026-10-21')
  })

  it('no consulta con un rango inválido y muestra el error', async () => {
    montar()
    await screen.findByRole('table')
    const llamadas = vi.mocked(api.obtenerRentabilidad).mock.calls.length
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-10-30' } })
    expect(await screen.findByText(/no puede ser posterior/i)).toBeInTheDocument()
    expect(vi.mocked(api.obtenerRentabilidad).mock.calls.length).toBe(llamadas)
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2023-01-01' } })
    expect(await screen.findByText(/731 días/)).toBeInTheDocument()
  })

  it('exporta con los mismos filtros', async () => {
    montar()
    vi.mocked(api.exportarRentabilidad).mockResolvedValue({ nombre: 'rentabilidad.pdf', blob: new Blob(['x']) })
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Agrupar por'), 'VARIANTE')
    await waitFor(() => expect(ultimosFiltros().agrupacion).toBe('VARIANTE'))
    await userEvent.click(screen.getByRole('button', { name: /exportar rentabilidad a pdf/i }))
    await waitFor(() => expect(api.exportarRentabilidad).toHaveBeenCalledWith(expect.objectContaining({ agrupacion: 'VARIANTE' }), 'PDF'))
    expect(URL.createObjectURL).toHaveBeenCalled()
  })

  it('si falla la exportación muestra el error y ofrece el otro formato', async () => {
    montar()
    const problema = new AxiosError('x', '500', undefined, undefined, {
      status: 500, data: { detail: 'Formato no disponible' }, statusText: '', headers: {}, config: {} as never,
    } as AxiosResponse)
    vi.mocked(api.exportarRentabilidad).mockRejectedValueOnce(problema)
    vi.mocked(api.exportarRentabilidad).mockResolvedValueOnce({ nombre: 'rentabilidad.xlsx', blob: new Blob(['x']) })
    await screen.findByRole('table')
    await userEvent.click(screen.getByRole('button', { name: /exportar rentabilidad a pdf/i }))
    const alerta = await screen.findByText(/no se pudo exportar a pdf/i)
    expect(alerta).toHaveTextContent('Formato no disponible')
    await userEvent.click(screen.getByRole('button', { name: 'Descargar en Excel' }))
    await waitFor(() => expect(api.exportarRentabilidad).toHaveBeenLastCalledWith(expect.anything(), 'XLSX'))
    await waitFor(() => expect(screen.queryByText(/no se pudo exportar/i)).not.toBeInTheDocument())
  })
})
