import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { atenderAlerta, listarAlertas, obtenerResumenAlertas } from '../api/alertas'
import type { AlertaResponse, FiltrosAlertas, PaginaAlertas, ResumenAlertasResponse } from '../tipos/alertas'

export function useAlertas(filtros: FiltrosAlertas) {
  return useQuery<PaginaAlertas>({
    queryKey: ['alertas', filtros],
    queryFn: () => listarAlertas(filtros),
    placeholderData: keepPreviousData,
  })
}

/** Contador de la campana: se refresca cada 60 s. `habilitado = false` para roles sin acceso. */
export function useResumenAlertas(habilitado = true) {
  return useQuery<ResumenAlertasResponse>({
    queryKey: ['alertas', 'resumen'],
    queryFn: obtenerResumenAlertas,
    refetchInterval: 60_000,
    enabled: habilitado,
  })
}

export function useAtenderAlerta() {
  const qc = useQueryClient()
  return useMutation<AlertaResponse, unknown, number>({
    mutationFn: (id) => atenderAlerta(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alertas'] }),
  })
}
