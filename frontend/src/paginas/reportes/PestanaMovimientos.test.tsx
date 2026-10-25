import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as api from '../../api/reportes'
import { renderizarApp } from '../../pruebas/render'
import type { FilaMovimientos, MovimientosResponse } from '../../tipos/reportes'
import { PestanaMovimientos } from './PestanaMovimientos'

vi.mock('../../api/reportes')
vi.mock('../../api/categorias')

function fila(extra: Partial<FilaMovimientos> = {}): FilaMovimientos {
  return {
    id: 1, codigo: null, nombre: 'Camisetas', categoria: null, saldoInicial: 457, entradas: 226, valorEntradas: 8_948_400,
    ventas: 270, anulaciones: 2, devoluciones: 9, ajustesPositivos: 1, ajustesNegativos: 0, saldoFinal: 425, ...extra,
  }
}

function respuesta(extra: Partial<MovimientosResponse> = {}): MovimientosResponse {
  return {
    desde: '2026-09-01', hasta: '2026-09-30', categoriaId: null, categoria: null, agrupacion: 'CATEGORIA', sinMovimientos: false,
    mensaje: null,
    filas: [fila(), fila({ id: 2, nombre: 'Jeans', saldoInicial: 223, entradas: 154, ventas: 131, saldoFinal: 255 })],
    totales: fila({ id: null, nombre: 'Total', saldoInicial: 680, entradas: 380, ventas: 401, saldoFinal: 1_679 }),
    movimientosDetalle: 892, notas: ['Las reservas y liberaciones no cambian el físico y no se incluyen.'],
    generadoEn: '2026-10-21T10:00:00', ...extra,
  }
}

function montar(datos: MovimientosResponse = respuesta()) {
  vi.mocked(api.obtenerMovimientos).mockResolvedValue(datos)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([{ id: 3, nombre: 'Jeans' }] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  return renderizarApp(<PestanaMovimientos />, { rol: 'CONTABILIDAD' })
}

const ultimosFiltros = () => vi.mocked(api.obtenerMovimientos).mock.calls.at(-1)![0]

beforeEach(() => {
  vi.resetAllMocks()
  URL.createObjectURL = vi.fn(() => 'blob:prueba')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

describe('PestanaMovimientos', () => {
  it('muestra saldos y flujos con totales, el aviso del detalle y las notas', async () => {
    montar()
    const tabla = await screen.findByRole('table', { name: /movimientos del periodo/i })
    expect(within(tabla).getAllByRole('row')).toHaveLength(4)
    expect(within(tabla).getByText('Total')).toBeInTheDocument()
    expect(within(tabla).getByText('1.679')).toBeInTheDocument()
    expect(screen.getByText(/detalle de 892 movimientos solo se incluye al exportar a excel/i)).toBeInTheDocument()
    expect(screen.getByText(/reservas y liberaciones/)).toBeInTheDocument()
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-09-01')
  })

  it('los filtros arman la consulta', async () => {
    montar()
    await screen.findByRole('table')
    expect(ultimosFiltros()).toEqual({ agrupacion: 'CATEGORIA' })
    await userEvent.selectOptions(screen.getByLabelText('Agrupar por'), 'VARIANTE')
    await waitFor(() => expect(ultimosFiltros().agrupacion).toBe('VARIANTE'))
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await waitFor(() => expect(ultimosFiltros().categoriaId).toBe(3))
  })

  it('sin movimientos muestra el mensaje y conserva los saldos', async () => {
    montar(respuesta({
      sinMovimientos: true, mensaje: 'No hay movimientos en el periodo', movimientosDetalle: 0,
      filas: [fila({ entradas: 0, ventas: 0, saldoInicial: 7, saldoFinal: 7 })], totales: fila({ id: null, nombre: 'Total' }),
    }))
    expect(await screen.findByText('No hay movimientos en el periodo')).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.queryByText(/solo se incluye al exportar/i)).not.toBeInTheDocument()
  })

  it('sobre el tope del Excel pide acotar el rango', async () => {
    montar(respuesta({ movimientosDetalle: 60_000 }))
    expect(await screen.findByText(/admite hasta 50\.000: acote el rango/i)).toBeInTheDocument()
    expect(screen.queryByText(/solo se incluye al exportar/i)).not.toBeInTheDocument()
  })

  it('exporta con los filtros vigentes', async () => {
    montar()
    vi.mocked(api.exportarMovimientos).mockResolvedValue({ nombre: 'movimientos.xlsx', blob: new Blob(['x']) })
    await screen.findByRole('table')
    await userEvent.click(screen.getByRole('button', { name: /exportar movimientos a excel/i }))
    await waitFor(() => expect(api.exportarMovimientos).toHaveBeenCalledWith({ agrupacion: 'CATEGORIA' }, 'XLSX'))
  })
})
