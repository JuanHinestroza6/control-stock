import { CONFIG_TIPO_MOVIMIENTO } from '../../lib/movimientos'
import type { TipoMovimiento } from '../../tipos/inventario'
import { Badge } from '../ui/Badge'

export function BadgeTipoMovimiento({ tipo }: { tipo: TipoMovimiento }) {
  const { tono, etiqueta } = CONFIG_TIPO_MOVIMIENTO[tipo]
  return <Badge tono={tono}>{etiqueta}</Badge>
}
