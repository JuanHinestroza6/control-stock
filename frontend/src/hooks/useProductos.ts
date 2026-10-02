import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { actualizarProducto, crearProducto, listarProductos, obtenerProducto } from '../api/productos'
import type {
  ActualizarProductoRequest,
  CrearProductoRequest,
  FiltrosProductos,
  PaginaProductos,
  ProductoDetalleResponse,
} from '../tipos/catalogo'

export function useProductos(filtros: FiltrosProductos) {
  return useQuery<PaginaProductos>({
    queryKey: ['productos', filtros],
    queryFn: () => listarProductos(filtros),
    placeholderData: keepPreviousData,
  })
}

/** `id` indefinido deshabilita la consulta (pantalla de creación). */
export function useProducto(id: number | undefined) {
  return useQuery<ProductoDetalleResponse>({
    queryKey: ['producto', id],
    queryFn: () => obtenerProducto(id as number),
    enabled: id !== undefined,
  })
}

export function useCrearProducto() {
  const qc = useQueryClient()
  return useMutation<ProductoDetalleResponse, unknown, CrearProductoRequest>({
    mutationFn: (datos) => crearProducto(datos),
    onSuccess: () => Promise.all([qc.invalidateQueries({ queryKey: ['productos'] }), qc.invalidateQueries({ queryKey: ['categorias'] })]),
  })
}

export function useActualizarProducto(id: number) {
  const qc = useQueryClient()
  return useMutation<ProductoDetalleResponse, unknown, ActualizarProductoRequest>({
    mutationFn: (datos) => actualizarProducto(id, datos),
    onSuccess: (detalle) => {
      qc.setQueryData(['producto', id], detalle)
      return Promise.all([qc.invalidateQueries({ queryKey: ['productos'] }), qc.invalidateQueries({ queryKey: ['categorias'] })])
    },
  })
}
