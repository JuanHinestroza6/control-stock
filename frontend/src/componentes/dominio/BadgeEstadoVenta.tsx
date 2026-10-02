import type { EstadoVenta } from '../../tipos/ventas'
import { Badge } from '../ui/Badge'

export function BadgeEstadoVenta({ estado }: { estado: EstadoVenta }) {
  return <Badge tono={estado === 'REGISTRADA' ? 'ok' : 'critico'}>{estado === 'REGISTRADA' ? 'Registrada' : 'Anulada'}</Badge>
}
