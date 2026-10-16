/** Mes `YYYY-MM` de hoy en America/Bogota, `meses` hacia atrás. */
export function mesRelativo(meses: number, ahora: Date = new Date()): string {
  const [anio, mes] = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit' })
    .format(ahora)
    .split('-')
    .map(Number)
  const indice = anio * 12 + (mes - 1) - meses
  return `${Math.floor(indice / 12)}-${String((indice % 12) + 1).padStart(2, '0')}`
}
