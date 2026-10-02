import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/categorias'
import { renderizarApp } from '../../pruebas/render'
import type { CategoriaResponse } from '../../tipos/catalogo'
import { CategoriasPage } from './CategoriasPage'

vi.mock('../../api/categorias')

const CATEGORIAS: CategoriaResponse[] = [
  { id: 1, nombre: 'Camisetas', totalProductos: 3 },
  { id: 2, nombre: 'Gorras', totalProductos: 0 },
]

function errorHttp(status: number, detail: string, errores?: { campo: string; mensaje: string }[]) {
  return new AxiosError('fallo', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: '',
    headers: {},
    config: {} as never,
    data: { detail, errores },
  })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.listarCategorias).mockResolvedValue(CATEGORIAS)
})

describe('CategoriasPage', () => {
  it('muestra el estado de carga y luego las categorías con sus totales', async () => {
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
    expect(await screen.findByText('Camisetas')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '3' })).toBeInTheDocument()
  })

  it('muestra el error del backend y reintenta', async () => {
    vi.mocked(api.listarCategorias).mockRejectedValueOnce(errorHttp(500, 'Servidor caído'))
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Servidor caído')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Camisetas')).toBeInTheDocument()
  })

  it('lista vacía ofrece crear según el rol', async () => {
    vi.mocked(api.listarCategorias).mockResolvedValue([])
    renderizarApp(<CategoriasPage />, { rol: 'INVENTARIO' })
    expect(await screen.findByText('Aún no hay categorías.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nueva categoría' })).toBeInTheDocument()
  })

  it('VENDEDOR no ve acciones de edición', async () => {
    renderizarApp(<CategoriasPage />, { rol: 'VENDEDOR' })
    await screen.findByText('Camisetas')
    expect(screen.queryByRole('button', { name: 'Nueva categoría' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Editar|Eliminar/ })).not.toBeInTheDocument()
  })

  it('no permite eliminar una categoría con productos', async () => {
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    await screen.findByText('Camisetas')
    expect(screen.getByRole('button', { name: 'Eliminar Camisetas' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Eliminar Gorras' })).toBeEnabled()
  })

  it('valida el nombre en cliente sin llamar al backend', async () => {
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Nueva categoría' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Ingresa el nombre')).toBeInTheDocument()
    expect(api.crearCategoria).not.toHaveBeenCalled()
  })

  it('crea una categoría, cierra el formulario y recarga la lista', async () => {
    vi.mocked(api.crearCategoria).mockResolvedValue({ id: 9, nombre: 'Medias', totalProductos: 0 })
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Nueva categoría' }))
    await userEvent.type(screen.getByLabelText('Nombre'), '  Medias ')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(api.crearCategoria).toHaveBeenCalledWith({ nombre: 'Medias' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.listarCategorias).toHaveBeenCalledTimes(2)
  })

  it('muestra el 409 de nombre duplicado en el campo y mantiene el formulario abierto', async () => {
    vi.mocked(api.crearCategoria).mockRejectedValue(errorHttp(409, 'Ya existe', [{ campo: 'nombre', mensaje: 'Ya existe una categoría con ese nombre' }]))
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Nueva categoría' }))
    await userEvent.type(screen.getByLabelText('Nombre'), 'Gorras')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Ya existe una categoría con ese nombre')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('edita enviando id y nombre nuevo', async () => {
    vi.mocked(api.actualizarCategoria).mockResolvedValue({ id: 2, nombre: 'Gorros', totalProductos: 0 })
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Editar Gorras' }))
    const campo = screen.getByLabelText('Nombre')
    expect(campo).toHaveValue('Gorras')
    await userEvent.clear(campo)
    await userEvent.type(campo, 'Gorros')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(api.actualizarCategoria).toHaveBeenCalledWith(2, { nombre: 'Gorros' }))
  })

  it('elimina tras confirmar y muestra el error del backend si falla', async () => {
    vi.mocked(api.eliminarCategoria).mockRejectedValueOnce(errorHttp(409, 'La categoría tiene productos asociados'))
    renderizarApp(<CategoriasPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar Gorras' }))
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar', description: '' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La categoría tiene productos asociados')

    vi.mocked(api.eliminarCategoria).mockResolvedValueOnce()
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar', description: '' }))
    await waitFor(() => expect(api.eliminarCategoria).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
})
