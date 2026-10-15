const ZONA = 'America/Bogota'
const MS_DIA = 86_400_000

/** Máximo de días de rango que acepta el backend en más vendidos. */
export const MAX_DIAS_RANGO = 731

/** Fecha de hoy en America/Bogota como `YYYY-MM-DD`. */
export function hoyIso(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' }).format(ahora)
}

function aUtc(iso: string): number {
  const [a, m, d] = iso.split('-').map(Number)
  return Date.UTC(a, m - 1, d)
}

/** Resta `dias` a una fecha ISO. */
export function restarDias(iso: string, dias: number): string {
  return new Date(aUtc(iso) - dias * MS_DIA).toISOString().slice(0, 10)
}

/** Días inclusivos entre dos fechas ISO. */
export function diasEntre(desde: string, hasta: string): number {
  return Math.round((aUtc(hasta) - aUtc(desde)) / MS_DIA) + 1
}

/** Valida un rango personalizado; devuelve el mensaje de error o `null` si es válido. */
export function validarRango(desde: string, hasta: string): string | null {
  if (!desde || !hasta) return 'Indica las fechas desde y hasta.'
  if (desde > hasta) return 'La fecha desde no puede ser posterior a la fecha hasta.'
  if (diasEntre(desde, hasta) > MAX_DIAS_RANGO) return `El rango no puede superar ${MAX_DIAS_RANGO} días.`
  return null
}
