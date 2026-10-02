import { describe, expect, it } from 'vitest'
import { agregarColores, alternarTalla, combinaciones, errorColor, MAX_COLORES, normalizarColor } from './variantes'

describe('normalizarColor', () => {
  it('recorta, colapsa espacios y pasa a mayúsculas con tildes', () => {
    expect(normalizarColor('  azul   marino ')).toBe('AZUL MARINO')
    expect(normalizarColor('marrón')).toBe('MARRÓN')
  })
})

describe('errorColor', () => {
  it('acepta letras, tildes, ñ, dígitos y espacios', () => {
    expect(errorColor('MARRÓN')).toBeUndefined()
    expect(errorColor('AÑIL 2')).toBeUndefined()
  })

  it('rechaza vacío, símbolos y más de 30 caracteres', () => {
    expect(errorColor('')).toBeDefined()
    expect(errorColor('ROJO!')).toBeDefined()
    expect(errorColor('A'.repeat(31))).toBeDefined()
  })
})

describe('agregarColores', () => {
  it('separa por coma, normaliza y elimina duplicados', () => {
    expect(agregarColores(['NEGRO'], 'negro, blanco,  Blanco ,,').colores).toEqual(['NEGRO', 'BLANCO'])
  })

  it('devuelve el error y conserva lo ya agregado ante un color inválido', () => {
    const r = agregarColores(['NEGRO'], 'rojo, az@l')
    expect(r.error).toBeDefined()
    expect(r.colores).toEqual(['NEGRO', 'ROJO'])
  })

  it('respeta el máximo de colores', () => {
    const llenos = Array.from({ length: MAX_COLORES }, (_, i) => `C${i}`)
    expect(agregarColores(llenos, 'otro').error).toContain(String(MAX_COLORES))
    expect(agregarColores(llenos, 'C1').error).toBeUndefined()
  })
})

describe('alternarTalla', () => {
  it('mantiene el orden del enum', () => {
    expect(alternarTalla(alternarTalla([], 'L'), 'S')).toEqual(['S', 'L'])
  })

  it('UNICA es exclusiva en ambos sentidos', () => {
    expect(alternarTalla(['S', 'M'], 'UNICA')).toEqual(['UNICA'])
    expect(alternarTalla(['UNICA'], 'M')).toEqual(['M'])
  })

  it('deselecciona una talla ya elegida', () => {
    expect(alternarTalla(['S', 'M'], 'S')).toEqual(['M'])
  })
})

describe('combinaciones', () => {
  it('es el producto talla x color', () => {
    expect(combinaciones(['S', 'M'], ['NEGRO', 'BLANCO'])).toEqual([
      { talla: 'S', color: 'NEGRO' },
      { talla: 'S', color: 'BLANCO' },
      { talla: 'M', color: 'NEGRO' },
      { talla: 'M', color: 'BLANCO' },
    ])
    expect(combinaciones([], ['NEGRO'])).toEqual([])
  })
})
