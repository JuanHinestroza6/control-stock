import type { EstadoStock } from '../../tipos/catalogo'
import { Badge, type TonoBadge } from '../ui/Badge'

const CONFIG: Record<EstadoStock, { tono: TonoBadge; etiqueta: string }> = {
  OK: { tono: 'ok', etiqueta: 'Stock óptimo' },
  BAJO: { tono: 'alerta', etiqueta: 'Stock bajo' },
  AGOTADO: { tono: 'critico', etiqueta: 'Agotado' },
}

/** Badge de estado de stock: ok / alerta / critico según el backend (OK, BAJO, AGOTADO). */
export function BadgeStock({ estado }: { estado: EstadoStock }) {
  const { tono, etiqueta } = CONFIG[estado]
  return <Badge tono={tono}>{etiqueta}</Badge>
}
