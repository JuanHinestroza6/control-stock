import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { actualizarCategoria, crearCategoria, eliminarCategoria, listarCategorias } from '../api/categorias'
import type { CategoriaRequest, CategoriaResponse } from '../tipos/catalogo'

export const CLAVE_CATEGORIAS = ['categorias'] as const

export function useCategorias() {
  return useQuery<CategoriaResponse[]>({ queryKey: CLAVE_CATEGORIAS, queryFn: listarCategorias })
}

export function useCrearCategoria() {
  const qc = useQueryClient()
  return useMutation<CategoriaResponse, unknown, CategoriaRequest>({
    mutationFn: (datos) => crearCategoria(datos),
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVE_CATEGORIAS }),
  })
}

export function useActualizarCategoria() {
  const qc = useQueryClient()
  return useMutation<CategoriaResponse, unknown, { id: number; datos: CategoriaRequest }>({
    mutationFn: ({ id, datos }) => actualizarCategoria(id, datos),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['productos'] })
      void qc.invalidateQueries({ queryKey: ['producto'] })
      return qc.invalidateQueries({ queryKey: CLAVE_CATEGORIAS })
    },
  })
}

export function useEliminarCategoria() {
  const qc = useQueryClient()
  return useMutation<void, unknown, number>({
    mutationFn: (id) => eliminarCategoria(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVE_CATEGORIAS }),
  })
}
