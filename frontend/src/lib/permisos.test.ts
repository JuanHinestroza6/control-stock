import { describe, expect, it } from 'vitest'
import { puedeEditarCatalogo } from './permisos'

describe('puedeEditarCatalogo', () => {
  it('solo ADMIN e INVENTARIO', () => {
    expect(puedeEditarCatalogo('ADMIN')).toBe(true)
    expect(puedeEditarCatalogo('INVENTARIO')).toBe(true)
    expect(puedeEditarCatalogo('VENDEDOR')).toBe(false)
    expect(puedeEditarCatalogo('CONTABILIDAD')).toBe(false)
    expect(puedeEditarCatalogo(undefined)).toBe(false)
  })
})
