import { describe, expect, it } from 'vitest'
import { parsearStockMinimo, textoEstadoProducto, textoEstadoVariante } from './textosEstado'

describe('parsearStockMinimo', () => {
  it('acepta enteros 0-100000 y rechaza el resto', () => {
    expect(parsearStockMinimo('0')).toBe(0)
    expect(parsearStockMinimo(' 100000 ')).toBe(100000)
    for (const malo of ['', '-1', '1.5', '100001', 'abc', '1234567']) expect(parsearStockMinimo(malo)).toBeNull()
  })
})

describe('textos de confirmación', () => {
  it('desactivar producto avisa de la cascada y de que reactivar no reactiva variantes', () => {
    const t = textoEstadoProducto({ nombre: 'Hoodie', totalVariantes: 6 }, false)
    expect(t.mensaje).toContain('todas sus variantes (6)')
    expect(t.mensaje).toContain('no se reactivan')
  })

  it('activar producto aclara que las variantes siguen inactivas', () => {
    expect(textoEstadoProducto({ nombre: 'Hoodie', totalVariantes: 6 }, true).mensaje).toContain('seguirán inactivas')
  })

  it('variante menciona el SKU', () => {
    expect(textoEstadoVariante({ sku: 'LIV001-M-NEG' }, false).mensaje).toContain('LIV001-M-NEG')
  })
})
