import type { VarianteResponse } from '../tipos/catalogo'
import { cliente } from './cliente'

export async function listarVariantes(productoId: number, incluirInactivas: boolean): Promise<VarianteResponse[]> {
  const { data } = await cliente.get<VarianteResponse[]>(`/productos/${productoId}/variantes`, {
    params: { incluirInactivas },
  })
  return data
}

export async function actualizarVariante(id: number, stockMinimo: number): Promise<VarianteResponse> {
  const { data } = await cliente.put<VarianteResponse>(`/variantes/${id}`, { stockMinimo })
  return data
}

export async function cambiarEstadoVariante(id: number, activo: boolean): Promise<VarianteResponse> {
  const { data } = await cliente.patch<VarianteResponse>(`/variantes/${id}/estado`, { activo })
  return data
}
