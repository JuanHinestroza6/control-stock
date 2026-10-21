import type { TonoBadge } from '../../componentes/ui/Badge'
import type { EstadoPlan, LineaPlanRequest, OrigenPlan } from '../../tipos/compras'

export const MAX_LINEAS_PLAN = 300
export const MAX_CANTIDAD_LINEA = 100_000

export const ESTADOS_PLAN: { valor: EstadoPlan; etiqueta: string }[] = [
  { valor: 'BORRADOR', etiqueta: 'Borrador' },
  { valor: 'APROBADO', etiqueta: 'Aprobado' },
  { valor: 'RECIBIDO', etiqueta: 'Recibido' },
  { valor: 'CANCELADO', etiqueta: 'Cancelado' },
]

export const TONO_ESTADO_PLAN: Record<EstadoPlan, TonoBadge> = {
  BORRADOR: 'neutro',
  APROBADO: 'acento',
  RECIBIDO: 'ok',
  CANCELADO: 'critico',
}

export const ETIQUETA_ORIGEN: Record<OrigenPlan, string> = { SUGERIDO: 'Sugerido', MANUAL: 'Manual' }

export function etiquetaEstadoPlan(estado: EstadoPlan): string {
  return ESTADOS_PLAN.find((e) => e.valor === estado)?.etiqueta ?? estado
}

export interface LineaBorrador {
  varianteId: number
  sku: string
  descripcion: string
  cantidad: string
  costo: string
}

export interface ResultadoPlanManual {
  lineas?: LineaPlanRequest[]
  /** Error por varianteId. */
  errores: Record<number, string>
  general?: string
}

/** Valida las líneas del plan manual en cliente (el backend vuelve a validar). */
export function construirLineasPlan(borrador: LineaBorrador[]): ResultadoPlanManual {
  const errores: Record<number, string> = {}
  if (borrador.length === 0) return { errores, general: 'Agrega al menos una variante.' }
  if (borrador.length > MAX_LINEAS_PLAN) return { errores, general: `Un plan admite máximo ${MAX_LINEAS_PLAN} líneas.` }
  const lineas: LineaPlanRequest[] = []
  for (const l of borrador) {
    const textoCantidad = l.cantidad.trim()
    const cantidad = Number(textoCantidad)
    if (!/^\d+$/.test(textoCantidad) || cantidad < 1 || cantidad > MAX_CANTIDAD_LINEA) {
      errores[l.varianteId] = `La cantidad debe ser un entero entre 1 y ${MAX_CANTIDAD_LINEA.toLocaleString('es-CO')}.`
      continue
    }
    const textoCosto = l.costo.trim().replace(',', '.')
    const linea: LineaPlanRequest = { varianteId: l.varianteId, cantidad }
    if (textoCosto !== '') {
      const costo = Number(textoCosto)
      if (!Number.isFinite(costo) || costo < 0) {
        errores[l.varianteId] = 'El costo debe ser un número mayor o igual a 0.'
        continue
      }
      linea.costoEstimado = Math.round(costo * 100) / 100
    }
    lineas.push(linea)
  }
  return Object.keys(errores).length > 0 ? { errores } : { lineas, errores }
}
