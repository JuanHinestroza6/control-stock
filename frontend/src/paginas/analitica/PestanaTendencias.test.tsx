import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { cloneElement, type ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/analitica'
import * as apiCategorias from '../../api/categorias'
import * as apiProductos from '../../api/productos'
import { mesRelativo } from '../../lib/meses'
import { renderizarApp } from '../../pruebas/render'
import type { PuntoTendencia, TendenciaResponse } from '../../tipos/analitica'
import { PestanaTendencias } from './PestanaTendencias'

vi.mock('../../api/analitica')
vi.mock('../../api/categorias')
vi.mock('../../api/productos')
// jsdom no mide el contenedor: ResponsiveContainer recibe un tamaño fijo.
vi.mock('recharts', async (importar) => {
  const real = await importar<typeof import('recharts')>()
  return {
    ...real,
    ResponsiveContainer: ({ children }: { children: ReactElement<{ width?: number; height?: number }> }) => cloneElement(children, { width: 600, height: 288 }),
  }
})

function punto(extra: Partial<PuntoTendencia> = {}): PuntoTendencia {
  return {
    periodo: '2026-08', unidades: 120, ingreso: 6_000_000, unidadesAnioAnterior: 100, ingresoAnioAnterior: 5_000_000,
    variacionUnidades: 20, variacionIngreso: 20, ...extra,
  }
}

function respuesta(extra: Partial<TendenciaResponse> = {}): TendenciaResponse {
  return {
    desde: '2026-08', hasta: '2026-09', categoriaId: null, productoId: null, totalUnidades: 230, totalIngreso: 11_000_000,
    puntos: [punto(), punto({ periodo: '2026-09', unidades: 110, ingreso: 5_000_000, variacionUnidades: null, variacionIngreso: -10 })],
    ...extra,
  }
}

function montar(datos: TendenciaResponse = respuesta(), falloInicial = false) {
  if (falloInicial) vi.mocked(api.obtenerTendencias).mockRejectedValueOnce(new Error('falló'))
  vi.mocked(api.obtenerTendencias).mockResolvedValue(datos)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([
    { id: 2, nombre: 'Camisetas' },
    { id: 3, nombre: 'Jeans' },
  ] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  vi.mocked(apiProductos.listarProductos).mockResolvedValue({
    content: [{ id: 7, codigo: 'LIV001', nombre: 'Camiseta Oversize' }],
    page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
  } as Awaited<ReturnType<typeof apiProductos.listarProductos>>)
  return renderizarApp(<PestanaTendencias />, { rol: 'ADMIN' })
}

const ultimaLlamada = () => vi.mocked(api.obtenerTendencias).mock.calls.at(-1)![0]

beforeEach(() => vi.resetAllMocks())

describe('PestanaTendencias', () => {
  it('consulta los últimos 24 meses completos y muestra gráfico, totales y tabla', async () => {
    montar()
    const tabla = await screen.findByRole('table', { name: /demanda mensual/i })
    expect(ultimaLlamada()).toEqual({ desde: mesRelativo(24), hasta: mesRelativo(1) })
    expect(await screen.findByRole('img', { name: /gráfico de líneas/i }, { timeout: 8000 })).toBeInTheDocument()
    expect(screen.getByTestId('total-unidades')).toHaveTextContent('230')
    expect(within(tabla).getAllByRole('row')).toHaveLength(3)
    expect(within(tabla).getByText('ago 2026')).toBeInTheDocument()
    expect(within(tabla).getByText('+20,0 %')).toBeInTheDocument()
    // Variación sin año anterior comparable.
    expect(within(tabla).getByText('—')).toBeInTheDocument()
  })

  it('los filtros arman la query y cambiar la categoría limpia el producto', async () => {
    montar()
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Periodo'), '12')
    await waitFor(() => expect(ultimaLlamada().desde).toBe(mesRelativo(12)))
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await waitFor(() => expect(ultimaLlamada().categoriaId).toBe(3))
    await screen.findByRole('option', { name: 'LIV001 · Camiseta Oversize' })
    await userEvent.selectOptions(screen.getByLabelText('Producto'), '7')
    await waitFor(() => expect(ultimaLlamada().productoId).toBe(7))
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '2')
    await waitFor(() => expect(ultimaLlamada()).toMatchObject({ categoriaId: 2 }))
    expect(ultimaLlamada().productoId).toBeUndefined()
  })

  it('la métrica Ingreso cambia los encabezados y formatea moneda', async () => {
    montar()
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByRole('columnheader', { name: 'Unidades' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Métrica'), 'INGRESO')
    expect(within(tabla).getByRole('columnheader', { name: 'Ingreso' })).toBeInTheDocument()
    expect(within(tabla).getAllByText(/\$\s?6\.000\.000/).length).toBeGreaterThan(0)
  })

  it.each(['12', '24', '36'])('el periodo %s meses manda desde y hasta correctos', async (meses) => {
    montar()
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Periodo'), meses)
    await waitFor(() => expect(ultimaLlamada()).toEqual({ desde: mesRelativo(Number(meses)), hasta: mesRelativo(1) }))
  })

  it('la tabla accesible refleja todos los datos de la métrica, con variación nula como guion', async () => {
    montar()
    const tabla = await screen.findByRole('table', { name: /demanda mensual/i })
    const filas = within(tabla).getAllByRole('row')
    const celdas = (f: HTMLElement) => within(f).getAllByRole('cell').map((c) => c.textContent)
    expect(celdas(filas[1])).toEqual(['ago 2026', '120', '100', '+20,0 %'])
    expect(celdas(filas[2])).toEqual(['sep 2026', '110', '100', '—'])
    // Con Ingreso, la variación de septiembre sí existe y es negativa.
    await userEvent.selectOptions(screen.getByLabelText('Métrica'), 'INGRESO')
    const filasIng = within(tabla).getAllByRole('row')
    expect(celdas(filasIng[2])[3]).toMatch(/-10,0\s?%/)
    expect(within(tabla).queryByText('—')).not.toBeInTheDocument()
  })

  it('muestra estado vacío sin ventas', async () => {
    montar(respuesta({ puntos: [], totalUnidades: 0, totalIngreso: 0 }))
    expect(await screen.findByText('No hay ventas en el periodo y los filtros elegidos.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('muestra error con reintento', async () => {
    montar(respuesta(), true)
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('muestra cargando mientras llega la respuesta', () => {
    vi.mocked(api.obtenerTendencias).mockReturnValue(new Promise(() => {}))
    vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
    vi.mocked(apiProductos.listarProductos).mockResolvedValue({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } })
    renderizarApp(<PestanaTendencias />, { rol: 'ADMIN' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
  })
})
