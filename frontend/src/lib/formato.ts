const ZONA = 'America/Bogota'

const formatoMoneda = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

export function formatearMoneda(valor: number): string {
  return formatoMoneda.format(valor)
}

/** Formatea una fecha (Date o ISO-8601) en zona America/Bogota. */
export function formatearFecha(valor: Date | string, conHora = false): string {
  const fecha = typeof valor === 'string' ? new Date(valor) : valor
  if (Number.isNaN(fecha.getTime())) return ''
  return new Intl.DateTimeFormat('es-CO', {
    timeZone: ZONA,
    dateStyle: 'medium',
    ...(conHora ? { timeStyle: 'short' } : {}),
  }).format(fecha)
}

const formatoNumero = new Intl.NumberFormat('es-CO')
const formatoPorcentaje = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

export function formatearNumero(valor: number): string {
  return formatoNumero.format(valor)
}

/** Porcentaje con un decimal; un guion cuando el backend manda null (base cero). */
export function formatearPorcentaje(valor: number | null): string {
  return valor === null ? '—' : `${formatoPorcentaje.format(valor)} %`
}
