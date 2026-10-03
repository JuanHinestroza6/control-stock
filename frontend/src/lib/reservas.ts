import type { TonoBadge } from '../componentes/ui/Badge'

const HORA_MS = 3_600_000
const UMBRAL_POR_VENCER_H = 12

function duracion(ms: number): string {
  const horas = Math.floor(ms / HORA_MS)
  const minutos = Math.floor((ms % HORA_MS) / 60_000)
  if (horas >= 24) return `${Math.floor(horas / 24)} d ${horas % 24} h`
  if (horas > 0) return `${horas} h ${minutos} min`
  return `${Math.max(minutos, 1)} min`
}

/** Texto y tono del vencimiento de una reserva activa: `critico` si ya pasó, `alerta` si falta menos de 12 h. */
export function vencimientoRelativo(venceEn: string, ahora: Date = new Date()): { texto: string; tono: TonoBadge } {
  const diff = new Date(venceEn).getTime() - ahora.getTime()
  if (Number.isNaN(diff)) return { texto: '', tono: 'neutro' }
  if (diff < 0) return { texto: `Venció hace ${duracion(-diff)}`, tono: 'critico' }
  return { texto: `Vence en ${duracion(diff)}`, tono: diff < UMBRAL_POR_VENCER_H * HORA_MS ? 'alerta' : 'neutro' }
}
