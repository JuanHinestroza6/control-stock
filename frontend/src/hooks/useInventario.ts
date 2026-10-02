import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { listarInventario, registrarAjuste, registrarEntrada } from '../api/inventario'
import { listarMovimientos } from '../api/movimientos'
import type {
  AjusteRequest,
  EntradaRequest,
  FiltrosInventario,
  FiltrosMovimientos,
  MovimientoResponse,
  PaginaInventario,
  PaginaMovimientos,
} from '../tipos/inventario'

/** Claves que toda operación de stock deja obsoletas. */
const CLAVES_STOCK = ['inventario', 'movimientos', 'matriz', 'variantes', 'productos']

export function invalidarStock(qc: QueryClient, extra: string[] = []) {
  return Promise.all([...CLAVES_STOCK, ...extra].map((k) => qc.invalidateQueries({ queryKey: [k] })))
}

export function useInventario(filtros: FiltrosInventario) {
  return useQuery<PaginaInventario>({
    queryKey: ['inventario', filtros],
    queryFn: () => listarInventario(filtros),
    placeholderData: keepPreviousData,
  })
}

/** `habilitado = false` deja la consulta en reposo (p. ej. rol sin acceso al kardex). */
export function useMovimientos(filtros: FiltrosMovimientos, habilitado = true) {
  return useQuery<PaginaMovimientos>({
    queryKey: ['movimientos', filtros],
    queryFn: () => listarMovimientos(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
  })
}

export function useRegistrarEntrada() {
  const qc = useQueryClient()
  return useMutation<MovimientoResponse[], unknown, EntradaRequest>({
    mutationFn: registrarEntrada,
    onSuccess: () => invalidarStock(qc),
  })
}

export function useRegistrarAjuste() {
  const qc = useQueryClient()
  return useMutation<MovimientoResponse, unknown, AjusteRequest>({
    mutationFn: registrarAjuste,
    onSuccess: () => invalidarStock(qc),
  })
}
