import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as api from '../../api/inventario'
import { renderizarApp } from '../../pruebas/render'
import type { InventarioItemResponse, PaginaInventario } from '../../tipos/inventario'
import { InventarioPage } from './InventarioPage'

vi.mock('../../api/inventario')
vi.mock('../../api/categorias')

function item(id: number, extra: Partial<InventarioItemResponse> = {}): InventarioItemResponse {
  return {
    varianteId: id,
    sku: `LIV001-M-NEG${id}`,
    productoId: 1,
    productoCodigo: 'LIV001',
    productoNombre: 'Camiseta Oversize',
    categoria: { id: 1, nombre: 'Camisetas' },
    talla: 'M',
    color: 'NEGRO',
    stockFisico: 10,
    stockReservado: 2,
    stockDisponible: 8,
    stockMinimo: 3,
    puntoReorden: null,
    estadoStock: 'OK',
    precioVenta: 59900,
    activo: true,
    ...extra,
  }
}

function pagina(content: InventarioItemResponse[], number = 0, totalPages = 1): PaginaInventario {
  return { content, page: { size: 20, number, totalElements: content.length, totalPages } }
}

const ultimosFiltros = () => vi.mocked(api.listarInventario).mock.calls.at(-1)?.[0]

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([{ id: 1, nombre: 'Camisetas', totalProductos: 2 }])
  vi.mocked(api.listarInventario).mockResolvedValue(
    pagina([item(1), item(2, { estadoStock: 'AGOTADO', stockDisponible: 0, activo: false })]),
  )
})

describe('InventarioPage', () => {
  it('muestra carga, luego filas con estado y consulta por defecto sin inactivas', async () => {
    renderizarApp(<InventarioPage />, { rol: 'ADMIN' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
    expect(await screen.findByText('LIV001-M-NEG1')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('Stock óptimo')).toBeInTheDocument()
    expect(screen.getByText(/Inactiva/)).toBeInTheDocument()
    expect(ultimosFiltros()).toMatchObject({ page: 0, size: 20, sort: 'producto.nombre,asc', incluirInactivas: false })
  })

  it('muestra el error y reintenta', async () => {
    vi.mocked(api.listarInventario).mockRejectedValueOnce(
      new AxiosError('x', 'E', undefined, undefined, {
        status: 500,
        statusText: '',
        headers: {},
        config: {} as never,
        data: { detail: 'Fallo del servidor' },
      }),
    )
    renderizarApp(<InventarioPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Fallo del servidor')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('LIV001-M-NEG1')).toBeInTheDocument()
  })

  it('vacío sin filtros ofrece ir a productos', async () => {
    vi.mocked(api.listarInventario).mockResolvedValue(pagina([]))
    renderizarApp(<InventarioPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('link', { name: 'Ir a productos' })).toHaveAttribute('href', '/productos')
  })

  it('aplica filtros y vuelve a la página 0; limpiar los restablece', async () => {
    vi.mocked(api.listarInventario).mockImplementation((f) =>
      Promise.resolve(f.estadoStock ? pagina([]) : pagina([item(1)])),
    )
    renderizarApp(<InventarioPage />, { rol: 'ADMIN' })
    await screen.findByText('LIV001-M-NEG1')
    await userEvent.selectOptions(await screen.findByLabelText('Categoría'), 'Camisetas')
    await userEvent.selectOptions(screen.getByLabelText('Estado de stock'), 'Stock bajo')
    await userEvent.click(screen.getByLabelText('Incluir inactivas'))
    await userEvent.type(screen.getByLabelText('Buscar'), 'liv')
    await waitFor(() =>
      expect(ultimosFiltros()).toMatchObject({ q: 'liv', categoriaId: 1, estadoStock: 'BAJO', incluirInactivas: true, page: 0 }),
    )
    expect(await screen.findByText('Ninguna variante coincide con los filtros.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(await screen.findByText('LIV001-M-NEG1')).toBeInTheDocument()
    expect(screen.getByLabelText('Estado de stock')).toHaveValue('')
  })

  it('pagina pidiendo la página siguiente', async () => {
    vi.mocked(api.listarInventario).mockImplementation((f) => Promise.resolve(pagina([item(1)], f.page ?? 0, 3)))
    renderizarApp(<InventarioPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(ultimosFiltros()).toMatchObject({ page: 1 }))
  })

  it('el botón Ajuste abre el modal solo para quien mueve stock y no en variantes inactivas', async () => {
    const inv = renderizarApp(<InventarioPage />, { rol: 'INVENTARIO' })
    await screen.findByText('LIV001-M-NEG1')
    expect(screen.queryByRole('button', { name: 'Ajuste de LIV001-M-NEG2' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Ajuste de LIV001-M-NEG1' }))
    expect(screen.getByRole('dialog', { name: 'Ajuste de LIV001-M-NEG1' })).toBeInTheDocument()
    inv.unmount()

    renderizarApp(<InventarioPage />, { rol: 'CONTABILIDAD' })
    await screen.findByText('LIV001-M-NEG1')
    expect(screen.queryByRole('button', { name: /Ajuste de/ })).not.toBeInTheDocument()
  })

  it('muestra las acciones según el rol', async () => {
    const admin = renderizarApp(<InventarioPage />, { rol: 'ADMIN' })
    await screen.findByText('LIV001-M-NEG1')
    expect(screen.getByRole('link', { name: 'Nueva entrada' })).toHaveAttribute('href', '/inventario/entradas/nueva')
    expect(screen.getByRole('link', { name: 'Entrada de LIV001-M-NEG1' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Entrada de LIV001-M-NEG2' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kardex de LIV001-M-NEG1' })).toHaveAttribute(
      'href',
      '/inventario/kardex?varianteId=1',
    )
    admin.unmount()

    const conta = renderizarApp(<InventarioPage />, { rol: 'CONTABILIDAD' })
    await screen.findByText('LIV001-M-NEG1')
    expect(screen.queryByRole('link', { name: 'Nueva entrada' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kardex de LIV001-M-NEG1' })).toBeInTheDocument()
    conta.unmount()

    renderizarApp(<InventarioPage />, { rol: 'VENDEDOR' })
    await screen.findByText('LIV001-M-NEG1')
    expect(screen.queryByRole('link', { name: /Entrada|Kardex/ })).not.toBeInTheDocument()
  })
})
