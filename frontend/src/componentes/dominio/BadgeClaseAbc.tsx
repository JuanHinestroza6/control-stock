import type { ClaseAbc } from '../../tipos/analitica'
import { Badge, type TonoBadge } from '../ui/Badge'

const TONOS: Record<ClaseAbc, TonoBadge> = { A: 'ok', B: 'acento', C: 'neutro' }

/** Clase ABC de una variante; sin clase (aún no calculada) muestra un guion. */
export function BadgeClaseAbc({ clase }: { clase: ClaseAbc | null | undefined }) {
  if (!clase) {
    return (
      <span className="text-oxford/50" aria-label="Sin clasificar">
        —
      </span>
    )
  }
  return (
    <Badge tono={TONOS[clase]} aria-label={`Clase ${clase}`}>
      {clase}
    </Badge>
  )
}
