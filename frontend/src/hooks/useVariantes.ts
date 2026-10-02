import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { cambiarEstadoProducto } from '../api/productos'
import { actualizarVariante, cambiarEstadoVariante, listarVariantes, obtenerMatriz } from '../api/variantes'
import type { MatrizResponse, ProductoDetalleResponse, VarianteResponse } from '../tipos/catalogo'

export function useVariantes(productoId: number, incluirInactivas: boolean) {
  return useQuery<VarianteResponse[]>({
    queryKey: ['variantes', productoId, incluirInactivas],
    queryFn: () => listarVariantes(productoId, incluirInactivas),
  })
}

/** Un cambio de variantes afecta a la tabla, la matriz y los totales del listado de productos. */
export function invalidarCatalogo(qc: QueryClient, productoId: number) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ['variantes', productoId] }),
    qc.invalidateQueries({ queryKey: ['matriz', productoId] }),
    qc.invalidateQueries({ queryKey: ['producto', productoId] }),
    qc.invalidateQueries({ queryKey: ['productos'] }),
  ])
}

export function useCambiarEstadoProducto(productoId: number) {
  const qc = useQueryClient()
  return useMutation<ProductoDetalleResponse, unknown, boolean>({
    mutationFn: (activo) => cambiarEstadoProducto(productoId, activo),
    onSuccess: () => invalidarCatalogo(qc, productoId),
  })
}

export function useActualizarVariante(productoId: number) {
  const qc = useQueryClient()
  return useMutation<VarianteResponse, unknown, { id: number; stockMinimo: number }>({
    mutationFn: ({ id, stockMinimo }) => actualizarVariante(id, stockMinimo),
    onSuccess: () => invalidarCatalogo(qc, productoId),
  })
}

export function useCambiarEstadoVariante(productoId: number) {
  const qc = useQueryClient()
  return useMutation<VarianteResponse, unknown, { id: number; activo: boolean }>({
    mutationFn: ({ id, activo }) => cambiarEstadoVariante(id, activo),
    onSuccess: () => invalidarCatalogo(qc, productoId),
  })
}

export function useMatriz(productoId: number, incluirInactivas: boolean) {
  return useQuery<MatrizResponse>({
    queryKey: ['matriz', productoId, incluirInactivas],
    queryFn: () => obtenerMatriz(productoId, incluirInactivas),
  })
}
