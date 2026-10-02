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
const CLAVES_STOCK = ['inventario', 'movimientos', 'matriz', 'variantes', 'productos', 'producto']

export function invalidarStock(qc: QueryClient, extra: string[] = []) {
  return Promise.all([...CLAVES_STOCK, ...extra].map((k) => qc.invalidateQueries({ queryKey: [k] })))
}

export function useInventario(filtros: FiltrosInventario, habilitado = true) {
  return useQuery<PaginaInventario>({
    queryKey: ['inventario', filtros],
    queryFn: () => listarInventario(filtros),
    placeholderData: keepPreviousData,
    enabled: habilitado,
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
    mutationFn: (datos) => registrarEntrada(datos),
    onSuccess: () => invalidarStock(qc),
  })
}

export function useRegistrarAjuste() {
  const qc = useQueryClient()
  return useMutation<MovimientoResponse, unknown, AjusteRequest>({
    mutationFn: (datos) => registrarAjuste(datos),
    onSuccess: () => invalidarStock(qc),
  })
}
