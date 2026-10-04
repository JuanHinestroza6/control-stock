import type { TipoAlerta } from '../../tipos/alertas'
import { Badge, type TonoBadge } from '../ui/Badge'

const CONFIG: Record<TipoAlerta, { tono: TonoBadge; texto: string }> = {
  AGOTADO: { tono: 'critico', texto: 'Agotado' },
  STOCK_BAJO: { tono: 'alerta', texto: 'Stock bajo' },
  SIN_ROTACION: { tono: 'neutro', texto: 'Sin rotación' },
}

export function BadgeTipoAlerta({ tipo }: { tipo: TipoAlerta }) {
  const { tono, texto } = CONFIG[tipo]
  return <Badge tono={tono}>{texto}</Badge>
}
