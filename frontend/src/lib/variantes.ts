import type { Talla } from '../tipos/catalogo'

/** Tallas válidas en el orden del enum del backend. */
export const TALLAS: Talla[] = ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'UNICA']

export const MAX_COLORES = 20
export const MAX_LONGITUD_COLOR = 30

/** Quita espacios sobrantes y pasa a mayúsculas (los colores se guardan normalizados). */
export function normalizarColor(texto: string): string {
  return texto.trim().replace(/\s+/g, ' ').toLocaleUpperCase('es')
}

/** Mensaje de error si el color (ya normalizado) no es válido; undefined si lo es. */
export function errorColor(color: string): string | undefined {
  if (color === '') return 'Escribe un color'
  if (color.length > MAX_LONGITUD_COLOR) return `Máximo ${MAX_LONGITUD_COLOR} caracteres por color`
  if (!/^[\p{L}\p{N} ]+$/u.test(color)) return 'El color solo admite letras, números y espacios'
  return undefined
}

/** Agrega colores separados por coma, normalizados y sin duplicados. Devuelve la lista nueva y el primer error. */
export function agregarColores(actuales: string[], entrada: string): { colores: string[]; error?: string } {
  const colores = [...actuales]
  for (const parte of entrada.split(',')) {
    const color = normalizarColor(parte)
    if (color === '') continue
    const error = errorColor(color)
    if (error) return { colores, error }
    if (colores.includes(color)) continue
    if (colores.length >= MAX_COLORES) return { colores, error: `Máximo ${MAX_COLORES} colores` }
    colores.push(color)
  }
  return { colores }
}

/** Alterna una talla; UNICA es exclusiva: elegirla quita las demás y elegir otra quita UNICA. */
export function alternarTalla(actuales: Talla[], talla: Talla): Talla[] {
  if (actuales.includes(talla)) return actuales.filter((t) => t !== talla)
  const siguiente = talla === 'UNICA' ? ['UNICA' as Talla] : [...actuales.filter((t) => t !== 'UNICA'), talla]
  return TALLAS.filter((t) => siguiente.includes(t))
}

export interface Combinacion {
  talla: Talla
  color: string
}

/** Producto cartesiano talla x color en orden talla, color. */
export function combinaciones(tallas: Talla[], colores: string[]): Combinacion[] {
  return tallas.flatMap((talla) => colores.map((color) => ({ talla, color })))
}
