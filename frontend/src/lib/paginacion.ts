import type { MetaPagina } from '../tipos/paginacion'

/** Rango "a–b de n" mostrado (1-based) para la página dada. */
export function rangoVisible({ number, size, totalElements }: MetaPagina): { desde: number; hasta: number } {
  if (totalElements === 0) return { desde: 0, hasta: 0 }
  return { desde: number * size + 1, hasta: Math.min((number + 1) * size, totalElements) }
}
