import { describe, expect, it } from 'vitest'
import { diasEntre, hoyIso, restarDias, validarRango } from './rangoFechas'

describe('rangoFechas', () => {
  it('hoyIso usa la zona America/Bogota', () => {
    expect(hoyIso(new Date('2026-10-15T03:00:00Z'))).toBe('2026-10-14') // 22:00 del día anterior en Bogotá
    expect(hoyIso(new Date('2026-10-15T05:00:00Z'))).toBe('2026-10-15')
  })

  it('restarDias cruza mes, año y bisiesto', () => {
    expect(restarDias('2026-03-01', 1)).toBe('2026-02-28')
    expect(restarDias('2024-03-01', 1)).toBe('2024-02-29')
    expect(restarDias('2026-01-01', 1)).toBe('2025-12-31')
    expect(restarDias('2026-10-14', 29)).toBe('2026-09-15')
  })

  it('diasEntre es inclusivo', () => {
    expect(diasEntre('2026-10-14', '2026-10-14')).toBe(1)
    expect(diasEntre('2026-09-15', '2026-10-14')).toBe(30)
  })

  it('validarRango: fechas vacías, invertido, límite de 731 y rango de un día', () => {
    expect(validarRango('', '')).toMatch(/Indica/)
    expect(validarRango('2026-01-01', '')).toMatch(/Indica/)
    expect(validarRango('2026-02-01', '2026-01-01')).toMatch(/no puede ser posterior/)
    expect(validarRango('2026-01-01', '2026-01-01')).toBeNull()
    expect(validarRango('2024-01-01', '2025-12-31')).toBeNull() // 731 días exactos
    expect(validarRango('2024-01-01', '2026-01-01')).toMatch(/731/) // 732 días
  })
})
