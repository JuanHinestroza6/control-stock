import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { listarProductos } from '../api/productos'
import type { FiltrosProductos, PaginaProductos } from '../tipos/catalogo'

export function useProductos(filtros: FiltrosProductos) {
  return useQuery<PaginaProductos>({
    queryKey: ['productos', filtros],
    queryFn: () => listarProductos(filtros),
    placeholderData: keepPreviousData,
  })
}
