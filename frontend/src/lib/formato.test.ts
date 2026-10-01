import { describe, expect, it } from 'vitest'
import { formatearFecha, formatearMoneda } from './formato'

describe('formatearMoneda', () => {
  it('formatea en pesos colombianos sin decimales', () => {
    const texto = formatearMoneda(1234567.5).replace(/\s/g, ' ')
    expect(texto).toMatch(/^\$ ?1\.234\.568$/)
  })

  it('formatea cero', () => {
    expect(formatearMoneda(0).replace(/\s/g, ' ')).toMatch(/^\$ ?0$/)
  })
})

describe('formatearFecha', () => {
  it('usa la zona America/Bogota (UTC-5): 02:00 UTC del 2 de octubre sigue siendo 1 de octubre', () => {
    expect(formatearFecha('2026-10-02T02:00:00Z')).toContain('1')
    expect(formatearFecha('2026-10-02T02:00:00Z')).not.toContain('2 oct')
  })

  it('incluye la hora local cuando se pide', () => {
    expect(formatearFecha(new Date('2026-10-01T15:30:00Z'), true)).toMatch(/10:30/)
  })

  it('devuelve cadena vacía con una fecha inválida', () => {
    expect(formatearFecha('no-es-fecha')).toBe('')
  })
})
