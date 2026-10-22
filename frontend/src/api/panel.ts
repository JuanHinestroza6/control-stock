import type { PanelResponse } from '../tipos/panel'
import { cliente } from './cliente'

export async function obtenerPanel(): Promise<PanelResponse> {
  const { data } = await cliente.get<PanelResponse>('/panel')
  return data
}
