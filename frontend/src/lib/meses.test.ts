import { describe, expect, it } from 'vitest'
import { mesRelativo } from './meses'

describe('mesRelativo', () => {
  it('con 0 meses devuelve el mes actual', () => {
    expect(mesRelativo(0, new Date('2026-10-15T12:00:00Z'))).toBe('2026-10')
  })

  it('cruza el año hacia atrás desde enero', () => {
    const ahora = new Date('2026-01-10T12:00:00Z')
    expect(mesRelativo(1, ahora)).toBe('2025-12')
    expect(mesRelativo(12, ahora)).toBe('2025-01')
    expect(mesRelativo(13, ahora)).toBe('2024-12')
  })

  it('36 meses atrás cae en el mismo mes tres años antes', () => {
    expect(mesRelativo(36, new Date('2026-10-15T12:00:00Z'))).toBe('2023-10')
  })

  it('usa America/Bogota: el 1 de enero 02:00 UTC aún es diciembre en Bogotá', () => {
    // 2026-01-01T02:00Z = 2025-12-31 21:00 en Bogotá (UTC-5)
    const ahora = new Date('2026-01-01T02:00:00Z')
    expect(mesRelativo(0, ahora)).toBe('2025-12')
    expect(mesRelativo(1, ahora)).toBe('2025-11')
  })

  it('usa America/Bogota: a las 05:00 UTC del 1 ya es el mes nuevo', () => {
    expect(mesRelativo(0, new Date('2026-02-01T05:00:00Z'))).toBe('2026-02')
    expect(mesRelativo(0, new Date('2026-02-01T04:59:00Z'))).toBe('2026-01')
  })
})
