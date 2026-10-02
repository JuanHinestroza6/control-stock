import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as api from '../../api/productos'
import App from '../../App'
import { renderizarApp } from '../../pruebas/render'
import type { ProductoDetalleResponse } from '../../tipos/catalogo'

vi.mock('../../api/productos')
vi.mock('../../api/categorias')

const DETALLE: ProductoDetalleResponse = {
  id: 5,
  codigo: 'LIV005',
  nombre: 'Hoodie Basic',
  categoria: { id: 2, nombre: 'Hoodies' },
  precioCompra: 50000,
  precioVenta: 120000,
  leadTimeDias: 20,
  loteMinimo: 6,
  activo: true,
  totalVariantes: 0,
  creadoEn: '2026-10-01T10:00:00',
  actualizadoEn: '2026-10-01T10:00:00',
}

function errorHttp(status: number, detail: string, errores?: { campo: string; mensaje: string }[]) {
  return new AxiosError('x', 'E', undefined, undefined, {
    status,
    statusText: '',
    headers: {},
    config: {} as never,
    data: { detail, errores },
  })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([
    { id: 1, nombre: 'Camisetas', totalProductos: 0 },
    { id: 2, nombre: 'Hoodies', totalProductos: 1 },
  ])
  vi.mocked(api.obtenerProducto).mockResolvedValue(DETALLE)
  vi.mocked(api.listarProductos).mockResolvedValue({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })
})

async function llenarCrear(compra: string, venta: string) {
  await userEvent.type(await screen.findByLabelText('Código'), 'liv010')
  await userEvent.type(screen.getByLabelText('Nombre'), 'Jogger Cargo')
  await userEvent.selectOptions(screen.getByLabelText('Categoría'), 'Camisetas')
  await userEvent.type(screen.getByLabelText('Precio de compra (COP)'), compra)
  await userEvent.type(screen.getByLabelText('Precio de venta (COP)'), venta)
}

describe('crear producto', () => {
  it('VENDEDOR y CONTABILIDAD reciben acceso denegado', () => {
    renderizarApp(<App />, { ruta: '/productos/nuevo', rol: 'VENDEDOR' })
    expect(screen.getByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
  })

  it('no consulta un producto al crear', async () => {
    renderizarApp(<App />, { ruta: '/productos/nuevo', rol: 'ADMIN' })
    await screen.findByLabelText('Código')
    expect(api.obtenerProducto).not.toHaveBeenCalled()
  })

  it('muestra errores de validación en cliente y no llama al backend', async () => {
    renderizarApp(<App />, { ruta: '/productos/nuevo', rol: 'INVENTARIO' })
    await userEvent.click(await screen.findByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Ingresa el nombre')).toBeInTheDocument()
    expect(screen.getByText('Selecciona una categoría')).toBeInTheDocument()
    expect(api.crearProducto).not.toHaveBeenCalled()
  })

  it('avisa si venta < costo, sin bloquear el envío', async () => {
    vi.mocked(api.crearProducto).mockResolvedValue({ ...DETALLE, id: 9 })
    renderizarApp(<App />, { ruta: '/productos/nuevo', rol: 'ADMIN' })
    await llenarCrear('50000', '40000')
    expect(screen.getByRole('status')).toHaveTextContent('menor que el precio de compra')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() =>
      expect(api.crearProducto).toHaveBeenCalledWith({
        codigo: 'liv010',
        nombre: 'Jogger Cargo',
        categoriaId: 1,
        precioCompra: 50000,
        precioVenta: 40000,
        leadTimeDias: null,
        loteMinimo: null,
      }),
    )
  })

  it('no muestra aviso si la venta es mayor que el costo', async () => {
    renderizarApp(<App />, { ruta: '/productos/nuevo', rol: 'ADMIN' })
    await llenarCrear('30000', '59900')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('asigna el 409 de código duplicado al campo', async () => {
    vi.mocked(api.crearProducto).mockRejectedValue(errorHttp(409, 'Duplicado', [{ campo: 'codigo', mensaje: 'Ya existe un producto con ese código' }]))
    renderizarApp(<App />, { ruta: '/productos/nuevo', rol: 'ADMIN' })
    await llenarCrear('30000', '59900')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Ya existe un producto con ese código')).toBeInTheDocument()
  })

  it('muestra en un banner un error sin campo (404 de categoría)', async () => {
    vi.mocked(api.crearProducto).mockRejectedValue(errorHttp(404, 'La categoría no existe'))
    renderizarApp(<App />, { ruta: '/productos/nuevo', rol: 'ADMIN' })
    await llenarCrear('30000', '59900')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La categoría no existe')
  })
})

describe('editar producto', () => {
  it('carga los datos, deja el código de solo lectura y envía sin código', async () => {
    vi.mocked(api.actualizarProducto).mockResolvedValue(DETALLE)
    renderizarApp(<App />, { ruta: '/productos/5/editar', rol: 'INVENTARIO' })
    const codigo = await screen.findByLabelText('Código')
    expect(codigo).toHaveValue('LIV005')
    expect(codigo).toHaveAttribute('readonly')
    expect(screen.getByLabelText('Nombre')).toHaveValue('Hoodie Basic')
    expect(screen.getByLabelText('Categoría')).toHaveValue('2')
    const nombre = screen.getByLabelText('Nombre')
    await userEvent.clear(nombre)
    await userEvent.type(nombre, 'Hoodie Premium')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() =>
      expect(api.actualizarProducto).toHaveBeenCalledWith(5, {
        nombre: 'Hoodie Premium',
        categoriaId: 2,
        precioCompra: 50000,
        precioVenta: 120000,
        leadTimeDias: 20,
        loteMinimo: 6,
      }),
    )
  })

  it('error al cargar el producto permite reintentar', async () => {
    vi.mocked(api.obtenerProducto).mockRejectedValueOnce(errorHttp(404, 'Producto no encontrado'))
    renderizarApp(<App />, { ruta: '/productos/5/editar', rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Producto no encontrado')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByLabelText('Código')).toHaveValue('LIV005')
  })

  it('VENDEDOR no accede a la edición', () => {
    renderizarApp(<App />, { ruta: '/productos/5/editar', rol: 'VENDEDOR' })
    expect(screen.getByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
    expect(api.obtenerProducto).not.toHaveBeenCalled()
  })
})
