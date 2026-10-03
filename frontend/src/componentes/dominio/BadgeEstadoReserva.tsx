import type { EstadoReserva } from '../../tipos/reservas'
import { Badge, type TonoBadge } from '../ui/Badge'

const CONFIG: Record<EstadoReserva, { tono: TonoBadge; texto: string }> = {
  ACTIVA: { tono: 'acento', texto: 'Activa' },
  CONVERTIDA: { tono: 'ok', texto: 'Convertida' },
  CANCELADA: { tono: 'neutro', texto: 'Cancelada' },
  VENCIDA: { tono: 'critico', texto: 'Vencida' },
}

export function BadgeEstadoReserva({ estado }: { estado: EstadoReserva }) {
  const { tono, texto } = CONFIG[estado]
  return <Badge tono={tono}>{texto}</Badge>
}
