import type { CategoriaRequest, CategoriaResponse } from '../tipos/catalogo'
import { cliente } from './cliente'

export async function listarCategorias(): Promise<CategoriaResponse[]> {
  const { data } = await cliente.get<CategoriaResponse[]>('/categorias')
  return data
}

export async function crearCategoria(datos: CategoriaRequest): Promise<CategoriaResponse> {
  const { data } = await cliente.post<CategoriaResponse>('/categorias', datos)
  return data
}

export async function actualizarCategoria(id: number, datos: CategoriaRequest): Promise<CategoriaResponse> {
  const { data } = await cliente.put<CategoriaResponse>(`/categorias/${id}`, datos)
  return data
}

export async function eliminarCategoria(id: number): Promise<void> {
  await cliente.delete(`/categorias/${id}`)
}
