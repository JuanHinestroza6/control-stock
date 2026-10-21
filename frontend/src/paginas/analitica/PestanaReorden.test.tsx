import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/analitica'
import * as apiCategorias from '../../api/categorias'
import { renderizarApp } from '../../pruebas/render'
import type { EjecucionAnaliticaResponse, PaginaReorden, ReordenVarianteResponse, ResumenAbcResponse } from '../../tipos/analitica'
import type { Rol } from '../../tipos/auth'
import { PestanaReorden } from './PestanaReorden'

vi.mock('../../api/analitica')
vi.mock('../../api/categorias')

const resumen = (ejecucion: EjecucionAnaliticaResponse | null = { id: 1, estado: 'EXITOSA' } as EjecucionAnaliticaResponse): ResumenAbcResponse => ({
  ejecucion, ventanaDesde: '2025-10-14', ventanaHasta: '2026-10-13', totalIngreso: 0, clases: [],
})

function fila(extra: Partial<ReordenVarianteResponse> = {}): ReordenVarianteResponse {
  return {
    varianteId: 1, sku: 'LIV001-M-NEG', productoNombre: 'Camiseta Oversize', categoria: { id: 2, nombre: 'Camisetas' }, talla: 'M', color: 'NEGRO',
    claseAbc: 'A', diasHistoria: 90, demandaMedia: 1.5, desviacion: 0.8, leadTimeDias: 15, nivelServicio: 0.95, z: 1.65, stockSeguridad: 5.1,
    puntoReorden: 28, metodo: 'NORMAL', stockMinimo: 5, umbralEfectivo: 28, fuenteUmbral: 'ROP', stockDisponible: 10, estadoStock: 'BAJO',
    fuenteDemanda: 'HISTORICA', demandaLeadTime: null, tasaPronosticada: null, enCamino: 0, posicion: 10, ...extra,
  }
}

const pagina = (content: ReordenVarianteResponse[], page: Partial<PaginaReorden['page']> = {}): PaginaReorden => ({
  content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1, ...page },
})

function montar(rol: Rol = 'ADMIN', res = resumen(), lista = pagina([fila()])) {
  vi.mocked(api.obtenerResumenAbc).mockResolvedValue(res)
  vi.mocked(api.listarReorden).mockResolvedValue(lista)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([
    { id: 2, nombre: 'Camisetas' },
    { id: 3, nombre: 'Jeans' },
  ] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  return renderizarApp(<PestanaReorden />, { rol })
}

const ultimaLlamada = () => vi.mocked(api.listarReorden).mock.calls.at(-1)![0]

beforeEach(() => vi.resetAllMocks())

describe('PestanaReorden', () => {
  it('consulta con orden por SKU y tamaño 20, y pinta nivel de servicio y estado', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    expect(ultimaLlamada()).toEqual({ page: 0, size: 20, sort: 'sku,asc' })
    const tabla = screen.getByRole('table')
    expect(within(tabla).getByText('95 %')).toBeInTheDocument()
    expect(within(tabla).getByText('Stock bajo')).toBeInTheDocument()
  })

  it('filtros (clase, categoría, método, enAlerta, búsqueda) y orden arman la query', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    await userEvent.selectOptions(screen.getByLabelText('Clase'), 'B')
    await waitFor(() => expect(ultimaLlamada().clase).toBe('B'))
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await userEvent.selectOptions(screen.getByLabelText('Método'), 'POISSON')
    await userEvent.click(screen.getByLabelText('Solo en alerta'))
    await userEvent.selectOptions(screen.getByLabelText('Ordenar por'), 'demandaMedia,desc')
    await userEvent.type(screen.getByLabelText('Buscar'), ' LIV001 ')
    await waitFor(() => expect(ultimaLlamada().q).toBe('LIV001'))
    expect(ultimaLlamada()).toEqual({
      q: 'LIV001', clase: 'B', categoriaId: 3, metodo: 'POISSON', enAlerta: true, page: 0, size: 20, sort: 'demandaMedia,desc',
    })
  })

  it('desmarcar "solo en alerta" omite el parámetro', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    const check = screen.getByLabelText('Solo en alerta')
    await userEvent.click(check)
    await waitFor(() => expect(ultimaLlamada().enAlerta).toBe(true))
    await userEvent.click(check)
    await waitFor(() => expect(ultimaLlamada().enAlerta).toBeUndefined())
  })

  it.each([
    ['NORMAL', 'Normal'],
    ['POISSON', 'Poisson (demanda baja)'],
    ['SIN_DEMANDA', 'Sin demanda'],
    ['SIN_HISTORIA', 'Sin historia'],
  ] as const)('badge del método %s', async (metodo, texto) => {
    montar('ADMIN', resumen(), pagina([fila({ metodo })]))
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText(texto)).toBeInTheDocument()
  })

  it('muestra el umbral efectivo con su fuente', async () => {
    montar('ADMIN', resumen(), pagina([
      fila({ varianteId: 1, umbralEfectivo: 28, fuenteUmbral: 'ROP' }),
      fila({ varianteId: 2, sku: 'LIV002-S-AZU', puntoReorden: null, metodo: 'SIN_HISTORIA', umbralEfectivo: 5, fuenteUmbral: 'STOCK_MINIMO' }),
    ]))
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText('Punto de reorden')).toBeInTheDocument()
    expect(within(tabla).getByText('Stock mínimo', { selector: 'span' })).toBeInTheDocument()
    expect(within(tabla).getByText('—')).toBeInTheDocument()
  })

  it('SIN_HISTORIA sin stock de seguridad ni ROP muestra guiones', async () => {
    montar('ADMIN', resumen(), pagina([fila({ metodo: 'SIN_HISTORIA', stockSeguridad: null, puntoReorden: null, umbralEfectivo: 5, fuenteUmbral: 'STOCK_MINIMO' })]))
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getAllByText('—')).toHaveLength(2)
    expect(within(tabla).queryByText('0,0')).not.toBeInTheDocument()
  })

  it('muestra en camino, posición y fuente de la demanda', async () => {
    montar('ADMIN', resumen(), pagina([fila({ enCamino: 40, posicion: 50, fuenteDemanda: 'PRONOSTICO' })]))
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText('Pronóstico')).toBeInTheDocument()
    expect(within(tabla).getByText('40')).toBeInTheDocument()
    expect(within(tabla).getByText('50')).toBeInTheDocument()
  })

  it('"Solo en alerta" descarta las filas cuya posición supera el umbral y conserva agotadas y bajas', async () => {
    const lista = pagina([
      fila({ varianteId: 1, sku: 'BAJA-1', stockDisponible: 10, enCamino: 0, posicion: 10, umbralEfectivo: 28 }),
      fila({ varianteId: 2, sku: 'CUBIERTA-2', stockDisponible: 10, enCamino: 40, posicion: 50, umbralEfectivo: 28 }),
      fila({ varianteId: 3, sku: 'AGOTADA-3', stockDisponible: 0, enCamino: 40, posicion: 40, umbralEfectivo: 28, estadoStock: 'AGOTADO' }),
    ])
    montar('ADMIN', resumen(), lista)
    await screen.findByText('BAJA-1')
    expect(screen.getByText('CUBIERTA-2')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Solo en alerta'))
    await waitFor(() => expect(screen.queryByText('CUBIERTA-2')).not.toBeInTheDocument())
    expect(screen.getByText('BAJA-1')).toBeInTheDocument()
    expect(screen.getByText('AGOTADA-3')).toBeInTheDocument()
  })

  it('"Solo en alerta" con una página sin filas en alerta y más páginas avisa en vez de mostrar vacío', async () => {
    montar('ADMIN', resumen(), pagina([fila({ stockDisponible: 10, enCamino: 40, posicion: 50, umbralEfectivo: 28 })], { totalPages: 3 }))
    await screen.findByText('Camiseta Oversize')
    await userEvent.click(screen.getByLabelText('Solo en alerta'))
    expect(await screen.findByRole('status')).toHaveTextContent(/página siguiente/)
  })

  it('la ayuda con la fórmula se muestra al desplegar', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    await userEvent.click(screen.getByText('¿Cómo se calcula el punto de reorden?'))
    expect(screen.getByText(/ROP = ⌈d̄·L \+ z·σ·√L⌉/)).toBeVisible()
    expect(screen.getByText(/A 95 %, B 90 %, C 80 %/)).toBeVisible()
    expect(screen.getByText(/se recalcula cada noche/)).toBeVisible()
    expect(screen.getByText(/Fuente de la demanda:/)).toBeVisible()
    expect(screen.getByText(/posición = disponible \+ unidades en camino/)).toBeVisible()
  })

  it('sin ejecución muestra el aviso y no consulta la lista', async () => {
    montar('ADMIN', resumen(null))
    expect(await screen.findByText(/Usa "Recalcular"/)).toBeInTheDocument()
    expect(api.listarReorden).not.toHaveBeenCalled()
  })

  it('sin ejecución, un rol sin permiso de recalcular ve el aviso sin invitación', async () => {
    montar('CONTABILIDAD', resumen(null))
    expect(await screen.findByText(/Pide a un administrador/)).toBeInTheDocument()
  })

  it('lista vacía con filtros muestra el estado vacío', async () => {
    montar('INVENTARIO', resumen(), pagina([]))
    await userEvent.type(await screen.findByLabelText('Buscar'), 'zzz')
    expect(await screen.findByText('Ninguna variante coincide con los filtros.')).toBeInTheDocument()
  })

  it('paginación pide la página siguiente', async () => {
    montar('ADMIN', resumen(), pagina([fila()], { totalElements: 45, totalPages: 3 }))
    await screen.findByText('Camiseta Oversize')
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(ultimaLlamada().page).toBe(1))
  })
})
