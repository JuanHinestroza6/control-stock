import type { PlanCompraDetalleResponse } from '../../tipos/compras'
import type { LineaBorrador } from './textosPlan'

export interface LineaEditable extends LineaBorrador {
  sugerida: number | null
  justificacion: string | null
  inactiva: boolean
}

/** Lo que el servidor guarda de una línea (lo que se compara al fusionar). */
export interface ValoresLinea {
  cantidad: string
  costo: string
}

export interface ConflictoLinea {
  varianteId: number
  sku: string
  descripcion: string
  /** null = la quité yo. */
  mia: LineaEditable | null
  /** null = la quitó la otra persona. */
  vigente: LineaEditable | null
}

export interface ConflictoObservacion {
  mia: string
  vigente: string
}

export interface ResultadoFusion {
  lineas: LineaEditable[]
  conflictos: ConflictoLinea[]
  observacion: string
  conflictoObservacion: ConflictoObservacion | null
  /** Hubo cambios de la otra persona que se incorporaron o quedaron en conflicto. */
  huboCambiosAjenos: boolean
}

export function desdePlan(plan: PlanCompraDetalleResponse): LineaEditable[] {
  return plan.lineas.map((l) => ({
    varianteId: l.varianteId,
    sku: l.sku,
    descripcion: `${l.productoNombre} · ${l.talla} · ${l.color}`,
    cantidad: String(l.cantidadAprobada),
    costo: String(l.costoEstimado),
    sugerida: l.cantidadSugerida,
    justificacion: l.justificacion,
    inactiva: !l.varianteActiva,
  }))
}

function normal(l: LineaEditable | undefined): ValoresLinea | null {
  return l ? { cantidad: l.cantidad.trim(), costo: l.costo.trim().replace(',', '.') } : null
}

function iguales(a: LineaEditable | undefined, b: LineaEditable | undefined): boolean {
  const x = normal(a)
  const y = normal(b)
  if (x === null || y === null) return x === y
  return x.cantidad === y.cantidad && x.costo === y.costo
}

/** Huella para saber si hay cambios sin guardar (independiente del orden). */
export function instantanea(lineas: LineaEditable[], observacion: string): string {
  const ordenadas = [...lineas].sort((a, b) => a.varianteId - b.varianteId)
  return JSON.stringify([observacion.trim(), ordenadas.map((l) => [l.varianteId, l.cantidad.trim(), l.costo.trim().replace(',', '.')])])
}

/**
 * Fusión de tres vías por variante entre la base (sobre la que empecé a editar), lo mío y lo vigente.
 * Si solo uno cambió una línea (cantidad, costo o presencia) gana ese cambio; si ambos la cambiaron
 * distinto queda en conflicto. Igual con la observación.
 */
export function fusionar(
  base: LineaEditable[],
  mias: LineaEditable[],
  vigentes: LineaEditable[],
  observacionBase: string,
  observacionMia: string,
  observacionVigente: string,
): ResultadoFusion {
  const porId = (ls: LineaEditable[]) => new Map(ls.map((l) => [l.varianteId, l]))
  const b = porId(base)
  const m = porId(mias)
  const v = porId(vigentes)
  const ids = [...new Set([...mias.map((l) => l.varianteId), ...vigentes.map((l) => l.varianteId), ...base.map((l) => l.varianteId)])]

  const lineas: LineaEditable[] = []
  const conflictos: ConflictoLinea[] = []
  let huboCambiosAjenos = false

  for (const id of ids) {
    const lb = b.get(id)
    const lm = m.get(id)
    const lv = v.get(id)
    if (!iguales(lb, lv)) huboCambiosAjenos = true
    if (iguales(lm, lv)) {
      if (lm) lineas.push(lv ? { ...lv, cantidad: lm.cantidad, costo: lm.costo } : lm)
    } else if (iguales(lb, lm)) {
      if (lv) lineas.push(lv)
    } else if (iguales(lb, lv)) {
      if (lm) lineas.push(lv ? { ...lv, cantidad: lm.cantidad, costo: lm.costo } : lm)
    } else {
      const ref = lm ?? lv ?? (lb as LineaEditable)
      conflictos.push({ varianteId: id, sku: ref.sku, descripcion: ref.descripcion, mia: lm ?? null, vigente: lv ?? null })
    }
  }

  const ob = observacionBase.trim()
  const om = observacionMia.trim()
  const ov = observacionVigente.trim()
  if (ob !== ov) huboCambiosAjenos = true
  let observacion = observacionMia
  let conflictoObservacion: ConflictoObservacion | null = null
  if (om === ov) observacion = observacionMia
  else if (ob === om) observacion = observacionVigente
  else if (ob === ov) observacion = observacionMia
  else conflictoObservacion = { mia: om, vigente: ov }

  return { lineas, conflictos, observacion, conflictoObservacion, huboCambiosAjenos }
}
