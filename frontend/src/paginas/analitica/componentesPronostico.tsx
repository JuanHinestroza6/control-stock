import { Badge } from '../../componentes/ui/Badge'
import { etiquetaMes, formatoCantidad, METODOS_PRONOSTICO, porcentaje, tonoWape } from '../../lib/pronostico'
import type { MetodoPronostico, PuntoPronostico } from '../../tipos/analitica'

export function BadgeMetodoPronostico({ metodo }: { metodo: MetodoPronostico | null }) {
  if (metodo === null) return <Badge tono="neutro">Sin historia</Badge>
  return <Badge tono="acento">{METODOS_PRONOSTICO[metodo]}</Badge>
}

/** El texto incluye el valor: el color no es el único canal. */
export function BadgeWape({ wape }: { wape: number | null }) {
  if (wape === null) return <span className="text-oxford/50">—</span>
  return <Badge tono={tonoWape(wape)}>{porcentaje(wape)}</Badge>
}

export function ProximosMeses({ puntos }: { puntos: PuntoPronostico[] }) {
  if (puntos.length === 0) return <span className="text-oxford/50">—</span>
  return (
    <ul className="flex gap-3 text-xs tabular-nums">
      {puntos.map((p) => (
        <li key={p.periodo} className="flex flex-col">
          <span className="text-oxford/60">{etiquetaMes(p.periodo)}</span>
          <span className="font-medium text-oxford">{formatoCantidad.format(p.cantidad)}</span>
        </li>
      ))}
    </ul>
  )
}
