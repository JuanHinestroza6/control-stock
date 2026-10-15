import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/productos'
import * as apiVariantes from '../../api/variantes'
import App from '../../App'
import { renderizarApp } from '../../pruebas/render'
import type { ProductoDetalleResponse, VarianteResponse } from '../../tipos/catalogo'

vi.mock('../../api/productos')
vi.mock('../../api/variantes')
vi.mock('../../api/categorias')

const PRODUCTO: ProductoDetalleResponse = {
  id: 5,
  codigo: 'LIV005',
  nombre: 'Hoodie Basic',
  categoria: { id: 2, nombre: 'Hoodies' },
  precioCompra: 50000,
  precioVenta: 120000,
  leadTimeDias: 20,
  loteMinimo: 6,
  activo: true,
  totalVariantes: 2,
  creadoEn: '2026-10-01T10:00:00',
  actualizadoEn: '2026-10-01T10:00:00',
}

function variante(id: number, extra: Partial<VarianteResponse> = {}): VarianteResponse {
  return {
    id,
    productoId: 5,
    sku: `LIV005-M-NEG${id}`,
    talla: 'M',
    color: 'NEGRO',
    stockFisico: 0,
    stockReservado: 0,
    stockDisponible: 0,
    stockMinimo: 3,
    puntoReorden: null,
    claseAbc: null,
    estadoStock: 'AGOTADO',
    activo: true,
    ...extra,
  }
}

function errorHttp(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.obtenerProducto).mockResolvedValue(PRODUCTO)
  vi.mocked(apiVariantes.listarVariantes).mockResolvedValue([variante(1), variante(2, { activo: false })])
  vi.mocked(apiVariantes.obtenerMatriz).mockResolvedValue({
    productoId: 5,
    tallas: ['M'],
    colores: ['NEGRO'],
    celdas: [
      { talla: 'M', color: 'NEGRO', varianteId: 1, sku: 'MATRIZ-M-NEGRO', stockFisico: 0, stockReservado: 0, stockDisponible: 0, estadoStock: 'AGOTADO', activo: true },
    ],
  })
})

describe('ProductoDetallePage', () => {
  it('muestra datos, costo y variantes con su estado', async () => {
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    expect(await screen.findByRole('heading', { level: 1, name: 'Hoodie Basic' })).toBeInTheDocument()
    expect(screen.getByText('Precio de compra')).toBeInTheDocument()
    expect(await screen.findByText('LIV005-M-NEG1')).toBeInTheDocument()
    expect(screen.getAllByText('Agotado').length).toBeGreaterThan(0)
    expect(screen.getByText('Inactiva')).toBeInTheDocument()
  })

  it('para VENDEDOR no hay costo (null) ni acciones de edición', async () => {
    vi.mocked(api.obtenerProducto).mockResolvedValue({ ...PRODUCTO, precioCompra: null })
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'VENDEDOR' })
    await screen.findByText('LIV005-M-NEG1')
    expect(screen.queryByText('Precio de compra')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Editar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Desactivar|Activar|Mínimo/ })).not.toBeInTheDocument()
  })

  it('error al cargar el producto permite reintentar', async () => {
    vi.mocked(api.obtenerProducto).mockRejectedValueOnce(errorHttp(404, 'Producto no encontrado'))
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Producto no encontrado')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Hoodie Basic' })).toBeInTheDocument()
  })

  it('sin variantes muestra el estado vacío', async () => {
    vi.mocked(apiVariantes.listarVariantes).mockResolvedValue([])
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    expect(await screen.findByText('Este producto aún no tiene variantes.')).toBeInTheDocument()
  })

  it('muestra la matriz y la pide con incluirInactivas segun el checkbox', async () => {
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    expect(await screen.findByRole('table', { name: 'Stock disponible por talla y color' })).toBeInTheDocument()
    expect(apiVariantes.obtenerMatriz).toHaveBeenLastCalledWith(5, false)
    await userEvent.click(screen.getByLabelText('Mostrar inactivas'))
    await waitFor(() => expect(apiVariantes.obtenerMatriz).toHaveBeenLastCalledWith(5, true))
  })

  it('matriz sin celdas muestra el estado vacio', async () => {
    vi.mocked(apiVariantes.obtenerMatriz).mockResolvedValue({ productoId: 5, tallas: [], colores: [], celdas: [] })
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    expect(await screen.findByText('Aún no hay variantes para mostrar la matriz.')).toBeInTheDocument()
  })

  it('"Mostrar inactivas" cambia el parámetro de la consulta', async () => {
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    await screen.findByText('LIV005-M-NEG1')
    expect(apiVariantes.listarVariantes).toHaveBeenLastCalledWith(5, false)
    await userEvent.click(screen.getByLabelText('Mostrar inactivas'))
    await waitFor(() => expect(apiVariantes.listarVariantes).toHaveBeenLastCalledWith(5, true))
  })

  it('desactivar producto: el texto avisa de la cascada y el 409 se ve en el diálogo', async () => {
    vi.mocked(api.cambiarEstadoProducto).mockRejectedValueOnce(
      errorHttp(409, 'No se puede desactivar el producto: la variante LIV005-M-NEG1 tiene 2 unidades en apartados activos.'),
    )
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'INVENTARIO' })
    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar producto' }))
    const dialogo = screen.getByRole('dialog', { name: 'Desactivar producto' })
    expect(dialogo).toHaveTextContent('todas sus variantes (2)')
    expect(dialogo).toHaveTextContent('no se reactivan')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar' }))
    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('apartados activos')
    expect(api.cambiarEstadoProducto).toHaveBeenCalledWith(5, false)
  })

  it('desactiva el producto y cierra el diálogo', async () => {
    vi.mocked(api.cambiarEstadoProducto).mockResolvedValue({ ...PRODUCTO, activo: false })
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar producto' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desactivar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('producto inactivo: ofrece activar y bloquea activar variantes', async () => {
    vi.mocked(api.obtenerProducto).mockResolvedValue({ ...PRODUCTO, activo: false })
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Mostrar inactivas' }))
    expect(await screen.findByRole('button', { name: 'Activar producto' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Activar LIV005-M-NEG2' })).toBeDisabled()
  })

  it('desactivar una variante muestra el 409 de reservas en el diálogo', async () => {
    vi.mocked(apiVariantes.cambiarEstadoVariante).mockRejectedValue(
      errorHttp(409, 'La variante LIV005-M-NEG1 tiene 2 unidades en apartados activos.'),
    )
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar LIV005-M-NEG1' }))
    const dialogo = screen.getByRole('dialog', { name: 'Desactivar variante' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar' }))
    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('apartados activos')
    expect(apiVariantes.cambiarEstadoVariante).toHaveBeenCalledWith(1, false)
  })

  it('edita el stock mínimo con validación en cliente', async () => {
    vi.mocked(apiVariantes.actualizarVariante).mockResolvedValue(variante(1, { stockMinimo: 8 }))
    renderizarApp(<App />, { ruta: '/productos/5', rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Editar stock mínimo de LIV005-M-NEG1' }))
    const campo = screen.getByRole('textbox', { name: 'Stock mínimo' })
    expect(campo).toHaveValue('3')
    expect(screen.getByText(/el stock mínimo solo se usa si no hay ROP/)).toBeInTheDocument()
    await userEvent.clear(campo)
    await userEvent.type(campo, '-2')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Ingresa un entero entre 0 y 100000')).toBeInTheDocument()
    expect(apiVariantes.actualizarVariante).not.toHaveBeenCalled()
    await userEvent.clear(campo)
    await userEvent.type(campo, '8')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(apiVariantes.actualizarVariante).toHaveBeenCalledWith(1, 8))
  })
})
