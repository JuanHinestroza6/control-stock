import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/reportes'
import * as apiCategorias from '../../api/categorias'
import { renderizarApp } from '../../pruebas/render'
import type { FilaLibro, LibroVentasResponse } from '../../tipos/reportes'
import { PestanaLibroVentas } from './PestanaLibroVentas'

vi.mock('../../api/reportes')
vi.mock('../../api/categorias')

function fila(extra: Partial<FilaLibro> = {}): FilaLibro {
  return {
    periodo: '2026-10-20', numeroVentas: 3, ventasLista: 500_000, descuentos: 50_000, facturado: 450_000, devoluciones: 0,
    ingresoNeto: 450_000, costoVentas: 200_000, margenBruto: 250_000, mermaNoApta: 0, unidadesVendidas: 8, unidadesDevueltas: 0, ...extra,
  }
}

function respuesta(extra: Partial<LibroVentasResponse> = {}): LibroVentasResponse {
  return {
    desde: '2026-10-01', hasta: '2026-10-21', agrupacion: 'DIA', sinVentas: false, mensaje: null,
    filas: [fila(), fila({ periodo: '2026-10-21', numeroVentas: 1, devoluciones: 90_000, ingresoNeto: -40_000, margenBruto: -90_000 })],
    totales: fila({ periodo: null, numeroVentas: 4, ingresoNeto: 410_000 }),
    anuladas: [
      { ventaId: 12, numero: 'V-000012', fecha: '2026-10-05T10:00:00', total: 120_000, anuladaEn: '2026-10-06T09:00:00', anuladaPor: 'Ana Pérez', motivoAnulacion: 'Error de digitación' },
    ],
    totalAnuladas: 1, montoAnuladas: 120_000, lineasDetalle: 1234, notas: ['Nota del libro.'], generadoEn: '2026-10-21T10:00:00', ...extra,
  }
}

function montar(datos: LibroVentasResponse = respuesta()) {
  vi.mocked(api.obtenerLibroVentas).mockResolvedValue(datos)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
  return renderizarApp(<PestanaLibroVentas />, { rol: 'CONTABILIDAD' })
}

const ultimosFiltros = () => vi.mocked(api.obtenerLibroVentas).mock.calls.at(-1)![0]

beforeEach(() => {
  vi.resetAllMocks()
  URL.createObjectURL = vi.fn(() => 'blob:prueba')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

describe('PestanaLibroVentas', () => {
  it('muestra periodos, totales, anuladas, aviso del detalle y notas', async () => {
    montar()
    const tabla = await screen.findByRole('table', { name: /libro de ventas por periodo/i })
    expect(within(tabla).getAllByRole('row')).toHaveLength(4)
    expect(within(tabla).getByText('20 oct 2026')).toBeInTheDocument()
    expect(within(tabla).getByText('Total')).toBeInTheDocument()
    // Neto negativo visible como tal.
    expect(within(tabla).getByText(/-\$\s?40\.000/)).toHaveClass('text-critico')
    expect(screen.getByText(/1\.234 líneas de venta solo se incluye al exportar a Excel/)).toBeInTheDocument()
    expect(screen.getByText(/devoluciones en la fecha en que se registraron/)).toBeInTheDocument()
    expect(screen.getByTestId('total-anuladas')).toHaveTextContent('1')
    expect(screen.getByTestId('monto-anuladas')).toHaveTextContent('120.000')
    const anuladas = screen.getByRole('table', { name: /ventas anuladas/i })
    expect(within(anuladas).getByText('V-000012')).toBeInTheDocument()
    expect(within(anuladas).getByText('Error de digitación')).toBeInTheDocument()
    expect(screen.getByText('Nota del libro.')).toBeInTheDocument()
  })

  it('sin ventas muestra el mensaje y sigue mostrando las anuladas en cero', async () => {
    montar(respuesta({ sinVentas: true, mensaje: 'No hay ventas en el periodo', filas: [], anuladas: [], totalAnuladas: 0, montoAnuladas: 0 }))
    expect(await screen.findByText('No hay ventas en el periodo')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByText(/no hay ventas anuladas/i)).toBeInTheDocument()
  })

  it('agrupación y rango se convierten en parámetros', async () => {
    montar()
    await screen.findByRole('table', { name: /libro de ventas/i })
    expect(ultimosFiltros()).toEqual({ agrupacion: 'DIA' })
    await userEvent.selectOptions(screen.getByLabelText('Agrupar por'), 'MES')
    await waitFor(() => expect(ultimosFiltros().agrupacion).toBe('MES'))
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-10-15' } })
    await waitFor(() => expect(ultimosFiltros().hasta).toBe('2026-10-15'))
    expect(ultimosFiltros().desde).toBe('2026-10-01')
  })

  it('exporta con los filtros vigentes', async () => {
    montar()
    vi.mocked(api.exportarLibroVentas).mockResolvedValue({ nombre: 'libro.xlsx', blob: new Blob(['x']) })
    await screen.findByRole('table', { name: /libro de ventas/i })
    await userEvent.click(screen.getByRole('button', { name: /exportar libro de ventas a excel/i }))
    await waitFor(() => expect(api.exportarLibroVentas).toHaveBeenCalledWith({ agrupacion: 'DIA' }, 'XLSX'))
  })
})
