import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/analitica'
import * as apiCategorias from '../../api/categorias'
import { BadgeClaseAbc } from '../../componentes/dominio/BadgeClaseAbc'
import { renderizarApp } from '../../pruebas/render'
import type { AbcVarianteResponse, EjecucionAnaliticaResponse, PaginaAbc, ResumenAbcResponse } from '../../tipos/analitica'
import type { Rol } from '../../tipos/auth'
import { PestanaAbc } from './PestanaAbc'

vi.mock('../../api/analitica')
vi.mock('../../api/categorias')

const ejecucion = { id: 1, estado: 'EXITOSA' } as EjecucionAnaliticaResponse

function resumen(extra: Partial<ResumenAbcResponse> = {}): ResumenAbcResponse {
  return {
    ejecucion,
    ventanaDesde: '2025-10-14',
    ventanaHasta: '2026-10-13',
    totalIngreso: 1_000_000,
    clases: [
      { clase: 'A', variantes: 10, ingreso: 800_000, participacion: 80 },
      { clase: 'B', variantes: 20, ingreso: 150_000, participacion: 15 },
      { clase: 'C', variantes: 70, ingreso: 50_000, participacion: 5 },
    ],
    ...extra,
  }
}

function fila(extra: Partial<AbcVarianteResponse> = {}): AbcVarianteResponse {
  return {
    varianteId: 1, sku: 'LIV001-M-NEG', productoId: 1, productoNombre: 'Camiseta Oversize', categoria: { id: 2, nombre: 'Camisetas' },
    talla: 'M', color: 'NEGRO', posicion: 1, unidadesNetas: 40, ingresoNeto: 500_000, participacion: 50, acumulado: 50, claseAbc: 'A', ...extra,
  }
}

function pagina(content: AbcVarianteResponse[], page: Partial<PaginaAbc['page']> = {}): PaginaAbc {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1, ...page } }
}

function montar(rol: Rol = 'ADMIN', res: ResumenAbcResponse = resumen(), lista: PaginaAbc = pagina([fila()])) {
  vi.mocked(api.obtenerResumenAbc).mockResolvedValue(res)
  vi.mocked(api.listarAbc).mockResolvedValue(lista)
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([
    { id: 2, nombre: 'Camisetas' },
    { id: 3, nombre: 'Jeans' },
  ] as Awaited<ReturnType<typeof apiCategorias.listarCategorias>>)
  return renderizarApp(<PestanaAbc />, { rol })
}

const ultimaLlamada = () => vi.mocked(api.listarAbc).mock.calls.at(-1)![0]

beforeEach(() => vi.resetAllMocks())

describe('PestanaAbc', () => {
  it('muestra las tarjetas por clase y la ventana analizada', async () => {
    montar()
    const tarjetaA = await screen.findByTestId('tarjeta-clase-A')
    expect(tarjetaA).toHaveTextContent('10 variantes')
    expect(tarjetaA).toHaveTextContent('80,0 % del ingreso')
    expect(tarjetaA).toHaveTextContent('800.000')
    expect(screen.getByTestId('tarjeta-clase-B')).toHaveTextContent('20 variantes')
    expect(screen.getByTestId('tarjeta-clase-C')).toHaveTextContent('5,0 % del ingreso')
    expect(screen.getByTestId('ventana-abc')).toHaveTextContent(/2025.*a.*2026/)
  })

  it('sin ejecución invita al ADMIN a recalcular y no consulta la lista', async () => {
    montar('ADMIN', resumen({ ejecucion: null, clases: [], totalIngreso: 0 }))
    expect(await screen.findByText(/Usa "Recalcular"/)).toBeInTheDocument()
    expect(api.listarAbc).not.toHaveBeenCalled()
  })

  it('sin ejecución, un rol sin permiso de recalcular ve el aviso sin la invitación', async () => {
    montar('CONTABILIDAD', resumen({ ejecucion: null, clases: [], totalIngreso: 0 }))
    expect(await screen.findByText(/Pide a un administrador/)).toBeInTheDocument()
  })

  it('error del resumen muestra reintentar', async () => {
    vi.mocked(api.obtenerResumenAbc).mockRejectedValue(new Error('x'))
    vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
    renderizarApp(<PestanaAbc />, { rol: 'ADMIN' })
    expect(await screen.findByRole('button', { name: /reintentar/i })).toBeInTheDocument()
  })

  it('consulta con orden por posición y tamaño 20 por defecto, y pinta las columnas', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    expect(ultimaLlamada()).toEqual({ page: 0, size: 20, sort: 'posicion,asc' })
    const tabla = screen.getByRole('table')
    expect(within(tabla).getByText('LIV001-M-NEG', { selector: 'td' })).toBeInTheDocument()
    expect(within(tabla).getByText('NEGRO')).toBeInTheDocument()
    expect(within(tabla).getAllByText('50,00 %')).toHaveLength(2)
  })

  it('filtros y orden arman la query y vuelven a la página 0', async () => {
    montar()
    await screen.findByText('Camiseta Oversize')
    await userEvent.selectOptions(screen.getByLabelText('Clase'), 'B')
    await waitFor(() => expect(ultimaLlamada().clase).toBe('B'))
    await screen.findByRole('option', { name: 'Jeans' })
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '3')
    await waitFor(() => expect(ultimaLlamada().categoriaId).toBe(3))
    await userEvent.selectOptions(screen.getByLabelText('Ordenar por'), 'ingresoNeto,desc')
    await waitFor(() => expect(ultimaLlamada().sort).toBe('ingresoNeto,desc'))
    await userEvent.type(screen.getByLabelText('Buscar'), ' LIV001 ')
    await waitFor(() => expect(ultimaLlamada().q).toBe('LIV001'))
    expect(ultimaLlamada()).toEqual({ q: 'LIV001', clase: 'B', categoriaId: 3, page: 0, size: 20, sort: 'ingresoNeto,desc' })
  })

  it('paginación pide la página siguiente', async () => {
    montar('ADMIN', resumen(), pagina([fila()], { totalElements: 45, totalPages: 3 }))
    await screen.findByText('Camiseta Oversize')
    expect(screen.getByText('1–20 de 45')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(ultimaLlamada().page).toBe(1))
  })

  it('lista vacía con filtros muestra el estado vacío', async () => {
    montar('INVENTARIO', resumen(), pagina([]))
    await userEvent.type(await screen.findByLabelText('Buscar'), 'zzz')
    expect(await screen.findByText('Ninguna variante coincide con los filtros.')).toBeInTheDocument()
  })
})

describe('BadgeClaseAbc', () => {
  it.each([
    ['A', 'bg-ok/15'],
    ['B', 'bg-acento/10'],
    ['C', 'bg-oxford/5'],
  ] as const)('clase %s usa su tono', (clase, estilo) => {
    render(<BadgeClaseAbc clase={clase} />)
    expect(screen.getByLabelText(`Clase ${clase}`)).toHaveClass(estilo)
  })

  it('sin clase muestra un guion accesible', () => {
    render(<BadgeClaseAbc clase={null} />)
    expect(screen.getByLabelText('Sin clasificar')).toHaveTextContent('—')
  })
})
