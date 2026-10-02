import type { EntradaRequest } from '../../tipos/inventario'

/** Cantidades escritas: productoId -> varianteId -> texto. */
export type CantidadesPorProducto = Record<number, Record<number, string>>

export const MAX_CANTIDAD = 100000
const PATRON_COSTO = /^\d{1,10}([.,]\d{1,2})?$/

/** Costo en texto -> número (0 permitido), o undefined si no es válido. */
export function parsearCosto(texto: string): number | undefined {
  const t = texto.trim()
  if (!PATRON_COSTO.test(t)) return undefined
  return Number(t.replace(',', '.'))
}

export function errorCosto(texto: string): string | undefined {
  return parsearCosto(texto) === undefined ? 'Costo inválido: usa un número desde 0 con máximo 2 decimales.' : undefined
}

export function errorCantidad(texto: string): string | undefined {
  const t = texto.trim()
  if (t === '') return undefined
  if (!/^\d+$/.test(t)) return 'Debe ser un entero'
  const n = Number(t)
  if (n > MAX_CANTIDAD) return `Máximo ${MAX_CANTIDAD}`
  return undefined
}

/** Variantes con cantidad > 0 de un producto, en el orden de entrada. */
export function lineasConCantidad(valores: Record<number, string>): { varianteId: number; cantidad: number }[] {
  return Object.entries(valores)
    .map(([id, texto]) => ({ varianteId: Number(id), cantidad: /^\d+$/.test(texto.trim()) ? Number(texto) : 0 }))
    .filter((l) => l.cantidad > 0)
}

export interface ResultadoEntrada {
  solicitud?: EntradaRequest
  /** Error por productoId (costo). */
  erroresCosto: Record<number, string>
  /** Error por varianteId (cantidad). */
  erroresCantidad: Record<number, string>
  /** Error general (p. ej. sin cantidades). */
  general?: string
}

/** Arma la solicitud: un solo costo por producto aplicado a todas sus líneas. */
export function construirEntrada(
  productoIds: number[],
  cantidades: CantidadesPorProducto,
  costos: Record<number, string>,
  observacion: string,
): ResultadoEntrada {
  const erroresCosto: Record<number, string> = {}
  const erroresCantidad: Record<number, string> = {}
  const lineas: EntradaRequest['lineas'] = []
  for (const productoId of productoIds) {
    const valores = cantidades[productoId] ?? {}
    for (const [id, texto] of Object.entries(valores)) {
      const e = errorCantidad(texto)
      if (e) erroresCantidad[Number(id)] = e
    }
    const con = lineasConCantidad(valores)
    if (con.length === 0) continue
    const costo = parsearCosto(costos[productoId] ?? '')
    if (costo === undefined) {
      erroresCosto[productoId] = errorCosto(costos[productoId] ?? '') as string
      continue
    }
    for (const l of con) lineas.push({ ...l, costoUnitario: costo })
  }
  if (Object.keys(erroresCosto).length > 0 || Object.keys(erroresCantidad).length > 0) {
    return { erroresCosto, erroresCantidad }
  }
  if (lineas.length === 0) return { erroresCosto, erroresCantidad, general: 'Escribe al menos una cantidad mayor que 0.' }
  if (lineas.length > 100) return { erroresCosto, erroresCantidad, general: 'Máximo 100 líneas por entrada.' }
  const obs = observacion.trim()
  return { erroresCosto, erroresCantidad, solicitud: { ...(obs ? { observacion: obs } : {}), lineas } }
}
