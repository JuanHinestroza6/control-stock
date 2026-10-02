import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as api from '../../api/productos'
import { renderizarApp } from '../../pruebas/render'
import type { PaginaProductos, ProductoResumenResponse } from '../../tipos/catalogo'
import { ProductosPage } from './ProductosPage'

vi.mock('../../api/productos')
vi.mock('../../api/categorias')

function producto(id: number, extra: Partial<ProductoResumenResponse> = {}): ProductoResumenResponse {
  return {
    id,
    codigo: `LIV00${id}`,
    nombre: `Producto ${id}`,
    categoria: { id: 1, nombre: 'Camisetas' },
    precioVenta: 59900,
    activo: true,
    totalVariantes: 6,
    stockDisponibleTotal: 12,
    ...extra,
  }
}

function pagina(content: ProductoResumenResponse[], number = 0, totalPages = 1): PaginaProductos {
  return { content, page: { size: 20, number, totalElements: content.length, totalPages } }
}

const ultimosFiltros = () => vi.mocked(api.listarProductos).mock.calls.at(-1)?.[0]

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([{ id: 1, nombre: 'Camisetas', totalProductos: 2 }])
  vi.mocked(api.listarProductos).mockResolvedValue(pagina([producto(1), producto(2, { activo: false })]))
})

describe('ProductosPage', () => {
  it('muestra carga y luego productos con totales, enlace al detalle y a categorías', async () => {
    renderizarApp(<ProductosPage />, { rol: 'ADMIN' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Producto 1' })).toHaveAttribute('href', '/productos/1')
    expect(screen.getByRole('link', { name: 'Categorías' })).toHaveAttribute('href', '/productos/categorias')
    expect(screen.getByText('Inactivo')).toBeInTheDocument()
    expect(ultimosFiltros()).toMatchObject({ page: 0, size: 20, sort: 'nombre,asc' })
  })

  it('muestra el error y reintenta', async () => {
    vi.mocked(api.listarProductos).mockRejectedValueOnce(
      new AxiosError('x', 'E', undefined, undefined, { status: 500, statusText: '', headers: {}, config: {} as never, data: { detail: 'Fallo del servidor' } }),
    )
    renderizarApp(<ProductosPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Fallo del servidor')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('link', { name: 'Producto 1' })).toBeInTheDocument()
  })

  it('vacío sin filtros ofrece crear solo a quien puede editar', async () => {
    vi.mocked(api.listarProductos).mockResolvedValue(pagina([]))
    const { unmount } = renderizarApp(<ProductosPage />, { rol: 'INVENTARIO' })
    expect(await screen.findByRole('link', { name: 'Crear el primer producto' })).toHaveAttribute('href', '/productos/nuevo')
    unmount()
    renderizarApp(<ProductosPage />, { rol: 'VENDEDOR' })
    expect(await screen.findByText('Aún no hay productos.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Crear el primer producto|Nuevo producto/ })).not.toBeInTheDocument()
  })

  it('oculta "Nuevo producto" a VENDEDOR y CONTABILIDAD y lo muestra a ADMIN', async () => {
    const { unmount } = renderizarApp(<ProductosPage />, { rol: 'CONTABILIDAD' })
    await screen.findByText('Producto 1')
    expect(screen.queryByRole('link', { name: 'Nuevo producto' })).not.toBeInTheDocument()
    unmount()
    renderizarApp(<ProductosPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('link', { name: 'Nuevo producto' })).toBeInTheDocument()
  })

  it('aplica filtros (con búsqueda diferida) y vuelve a la página 0', async () => {
    renderizarApp(<ProductosPage />, { rol: 'ADMIN' })
    await screen.findByText('Producto 1')
    await userEvent.selectOptions(await screen.findByLabelText('Categoría'), 'Camisetas')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'Inactivos')
    await userEvent.type(screen.getByLabelText('Buscar'), 'oversize')
    await waitFor(() => expect(ultimosFiltros()).toMatchObject({ q: 'oversize', categoriaId: 1, activo: false, page: 0 }))
  })

  it('con filtros sin resultados permite limpiarlos', async () => {
    vi.mocked(api.listarProductos).mockImplementation((f) => Promise.resolve(f.categoriaId ? pagina([]) : pagina([producto(1)])))
    renderizarApp(<ProductosPage />, { rol: 'ADMIN' })
    await screen.findByText('Producto 1')
    await userEvent.selectOptions(await screen.findByLabelText('Categoría'), 'Camisetas')
    expect(await screen.findByText('Ningún producto coincide con los filtros.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(await screen.findByText('Producto 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Categoría')).toHaveValue('')
  })

  it('pagina pidiendo la página siguiente', async () => {
    vi.mocked(api.listarProductos).mockImplementation((f) => Promise.resolve(pagina([producto(1)], f.page ?? 0, 3)))
    renderizarApp(<ProductosPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(ultimosFiltros()).toMatchObject({ page: 1 }))
    expect(await screen.findByText('Página 2 de 3')).toBeInTheDocument()
  })
})
