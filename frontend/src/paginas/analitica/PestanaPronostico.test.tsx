import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { cloneElement, type ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/analitica'
import * as apiCategorias from '../../api/categorias'
import { tonoWape } from '../../lib/pronostico'
import { renderizarApp } from '../../pruebas/render'
import type { PronosticoDetalleResponse, PronosticoProductoResponse } from '../../tipos/analitica'
import { PestanaPronostico } from './PestanaPronostico'

vi.mock('../../api/analitica')
vi.mock('../../api/categorias')
// jsdom no mide el contenedor: ResponsiveContainer recibe un tamaño fijo.
vi.mock('recharts', async (importar) => {
  const real = await importar<typeof import('recharts')>()
  return {
    ...real,
    ResponsiveContainer: ({ children }: { children: ReactElement<{ width?: number; height?: number }> }) => cloneElement(children, { width: 600, height: 320 }),
  }
})

const PROXIMOS = [
  { periodo: '2026-10', cantidad: 40 },
  { periodo: '2026-11', cantidad: 55.5 },
  { periodo: '2026-12', cantidad: 70 },
]

function producto(extra: Partial<PronosticoProductoResponse> = {}): PronosticoProductoResponse {
  return {
    productoId: 7, codigo: 'LIV001', nombre: 'Camiseta Oversize', categoria: 'Camisetas', metodo: 'HOLT_WINTERS', errorWape: 0.153,
    wapeSes: 0.25, wapeHw: 0.153, motivoSinHw: null, mesesHistoria: 35, unidadesUltimos12m: 500, unidadesCorregidas12m: 560,
    proximos: PROXIMOS, ...extra,
  }
}

const pagina = (content: PronosticoProductoResponse[]) => ({
  content,
  page: { size: 20, number: 0, totalElements: content.length, totalPages: content.length ? 1 : 0 },
})

function detalle(extra: Partial<PronosticoDetalleResponse> = {}): PronosticoDetalleResponse {
  return {
    ...producto(),
    parametros: { alfa: 0.3, beta: 0.1, gamma: 0.2 },
    serie: [
      { periodo: '2026-08', observada: 30, disponibilidad: 0.9, corregida: 33, backtestSes: 28, backtestHw: 31 },
      { periodo: '2026-09', observada: 35, disponibilidad: 1, corregida: 35, backtestSes: null, backtestHw: null },
    ],
    pronostico: PROXIMOS,
    variantes: [
      { varianteId: 1, sku: 'LIV001-M-NEG', talla: 'M', color: 'NEGRO', participacion: 0.6, pronostico: PROXIMOS.map((p) => ({ ...p, cantidad: p.cantidad * 0.6 })) },
      { varianteId: 2, sku: 'LIV001-L-NEG', talla: 'L', color: 'NEGRO', participacion: 0.4, pronostico: PROXIMOS.map((p) => ({ ...p, cantidad: p.cantidad * 0.4 })) },
    ],
    calculadoEn: '2026-10-16T07:00:00Z',
    ...extra,
  }
}

function montar(filas: PronosticoProductoResponse[] = [producto()], det: PronosticoDetalleResponse = detalle()) {
  vi.mocked(api.listarPronosticos).mockResolvedValue(pagina(filas))
  vi.mocked(api.obtenerPronostico).mockResolvedValue(det)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([
    { id: 2, nombre: 'Camisetas' },
    { id: 3, nombre: 'Jeans' },
  ] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  return renderizarApp(<PestanaPronostico />, { rol: 'ADMIN' })
}

const ultimaLlamada = () => vi.mocked(api.listarPronosticos).mock.calls.at(-1)![0]

beforeEach(() => vi.resetAllMocks())

describe('tonoWape', () => {
  it.each([
    [null, 'neutro'],
    [0, 'ok'],
    [0.2, 'ok'],
    [0.21, 'alerta'],
    [0.4, 'alerta'],
    [0.41, 'critico'],
  ])('WAPE %s es %s', (valor, tono) => {
    expect(tonoWape(valor)).toBe(tono)
  })
})

describe('PestanaPronostico - listado', () => {
  it('muestra método, WAPE, próximos 3 meses y la nota del riesgo del backtest', async () => {
    montar([
      producto(),
      producto({ productoId: 8, codigo: 'LIV002', nombre: 'Jean Recto', metodo: 'SES', errorWape: 0.5, motivoSinHw: 'POCA_HISTORIA' }),
      producto({ productoId: 9, codigo: 'LIV003', nombre: 'Gorra', metodo: null, errorWape: null, proximos: [], motivoSinHw: null }),
    ])
    const tabla = await screen.findByRole('table', { name: /pronóstico de demanda/i })
    expect(screen.getByRole('note')).toHaveTextContent(/noviembre y diciembre/)
    const filas = within(tabla).getAllByRole('row')
    expect(filas).toHaveLength(4)
    expect(within(filas[1]).getByText('Holt-Winters')).toBeInTheDocument()
    expect(within(filas[1]).getByText(/15,3\s?%/)).toBeInTheDocument()
    expect(within(filas[1]).getByText('55,5')).toBeInTheDocument()
    expect(within(filas[1]).getByText('dic 2026')).toBeInTheDocument()
    expect(within(filas[2]).getByText(/50,0\s?%/)).toBeInTheDocument()
    expect(within(filas[2]).getByText('Sin Holt-Winters: menos de 30 meses de historia')).toBeInTheDocument()
    expect(within(filas[3]).getByText('Sin historia')).toBeInTheDocument()
    expect(within(filas[3]).getByText(/Sin historia suficiente/)).toBeInTheDocument()
  })

  it('los filtros arman la consulta y reinician la página', async () => {
    montar()
    await screen.findByRole('table')
    expect(ultimaLlamada()).toMatchObject({ page: 0, size: 20, sort: 'codigo,asc' })
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await waitFor(() => expect(ultimaLlamada().categoriaId).toBe(3))
    await userEvent.selectOptions(screen.getByLabelText('Método'), 'HOLT_WINTERS')
    await waitFor(() => expect(ultimaLlamada().metodo).toBe('HOLT_WINTERS'))
    await userEvent.selectOptions(screen.getByLabelText('Ordenar por'), 'errorWape,desc')
    await waitFor(() => expect(ultimaLlamada().sort).toBe('errorWape,desc'))
    await userEvent.type(screen.getByLabelText('Buscar'), 'camis')
    await waitFor(() => expect(ultimaLlamada().q).toBe('camis'))
  })

  it('muestra vacío sin pronósticos y vacío con filtros', async () => {
    montar([])
    expect(await screen.findByText(/Aún no hay pronósticos/)).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Método'), 'SES')
    expect(await screen.findByText('Ningún producto coincide con los filtros.')).toBeInTheDocument()
  })

  it('muestra error con reintento', async () => {
    vi.mocked(api.listarPronosticos).mockRejectedValueOnce(new Error('falló')).mockResolvedValue(pagina([producto()]))
    vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
    renderizarApp(<PestanaPronostico />, { rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('muestra cargando mientras llega la respuesta', () => {
    vi.mocked(api.listarPronosticos).mockReturnValue(new Promise(() => {}))
    vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
    renderizarApp(<PestanaPronostico />, { rol: 'ADMIN' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
  })
})

describe('PestanaPronostico - detalle', () => {
  async function abrir() {
    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalle de Camiseta Oversize' }))
  }

  it('abre el detalle con gráfico, resumen y desagregación por variante, y vuelve', async () => {
    montar()
    await abrir()
    expect(api.obtenerPronostico).toHaveBeenCalledWith(7)
    expect(await screen.findByRole('img', { name: /gráfico de líneas/i }, { timeout: 8000 })).toBeInTheDocument()
    expect(screen.getByTestId('meses-historia')).toHaveTextContent('35')
    expect(screen.getByTestId('unidades-12m')).toHaveTextContent('500 / 560')
    expect(screen.getByTestId('wape')).toHaveTextContent(/15,3\s?%/)
    expect(screen.getByRole('note')).toHaveTextContent(/noviembre y diciembre/)
    const tabla = screen.getByRole('table', { name: /por variante/i })
    const filas = within(tabla).getAllByRole('row')
    expect(filas).toHaveLength(3)
    const celdas = within(filas[1]).getAllByRole('cell').map((c) => c.textContent)
    expect(celdas).toEqual(['LIV001-M-NEG', 'M', 'NEGRO', '60,0 %', '24', '33,3', '42'])
    await userEvent.click(screen.getByRole('button', { name: 'Volver al listado' }))
    expect(await screen.findByRole('table', { name: /pronóstico de demanda/i })).toBeInTheDocument()
  })

  it('producto sin cálculo muestra estado vacío', async () => {
    montar([producto()], detalle({ metodo: null, errorWape: null, serie: [], pronostico: [], variantes: [], parametros: null }))
    await abrir()
    expect(await screen.findByText(/aún no tiene historia suficiente/)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('sin variantes activas muestra vacío en la desagregación', async () => {
    montar([producto()], detalle({ variantes: [] }))
    await abrir()
    expect(await screen.findByText(/no tiene variantes activas/)).toBeInTheDocument()
  })

  it('error del detalle con reintento', async () => {
    montar()
    vi.mocked(api.obtenerPronostico).mockRejectedValueOnce(new Error('falló')).mockResolvedValue(detalle())
    await abrir()
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByTestId('meses-historia')).toBeInTheDocument()
  })

  it('el detalle muestra el motivo sin Holt-Winters', async () => {
    montar([producto()], detalle({ metodo: 'SES', motivoSinHw: 'MES_EN_CERO', wapeHw: null }))
    await abrir()
    expect(await screen.findByText(/Sin Holt-Winters: tiene meses sin demanda/)).toBeInTheDocument()
  })
})
