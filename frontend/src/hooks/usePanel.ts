import { useQuery } from '@tanstack/react-query'
import { obtenerPanel } from '../api/panel'
import type { PanelResponse } from '../tipos/panel'

export const INTERVALO_PANEL_MS = 60_000

/** Panel de inicio; se refresca cada 60 s mientras la pestaña está visible. */
export function usePanel() {
  return useQuery<PanelResponse>({
    queryKey: ['panel'],
    queryFn: obtenerPanel,
    refetchInterval: INTERVALO_PANEL_MS,
  })
}
