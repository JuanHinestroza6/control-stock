import { describe, expect, it } from 'vitest'
import {
  puedeAnularVenta,
  puedeEditarCatalogo,
  puedeMoverStock,
  puedeVender,
  puedeVerCostos,
  puedeVerKardex,
} from './permisos'

describe('puedeEditarCatalogo', () => {
  it('solo ADMIN e INVENTARIO', () => {
    expect(puedeEditarCatalogo('ADMIN')).toBe(true)
    expect(puedeEditarCatalogo('INVENTARIO')).toBe(true)
    expect(puedeEditarCatalogo('VENDEDOR')).toBe(false)
    expect(puedeEditarCatalogo('CONTABILIDAD')).toBe(false)
    expect(puedeEditarCatalogo(undefined)).toBe(false)
  })
})

describe('permisos de la fase 2', () => {
  // Orden: ADMIN, INVENTARIO, VENDEDOR, CONTABILIDAD, sin rol.
  const roles = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD', undefined] as const
  it('puedeMoverStock', () => {
    expect(roles.map(puedeMoverStock)).toEqual([true, true, false, false, false])
  })
  it('puedeVender', () => {
    expect(roles.map(puedeVender)).toEqual([true, false, true, false, false])
  })
  it('puedeAnularVenta', () => {
    expect(roles.map(puedeAnularVenta)).toEqual([true, false, false, false, false])
  })
  it('puedeVerKardex', () => {
    expect(roles.map(puedeVerKardex)).toEqual([true, true, false, true, false])
  })
  it('puedeVerCostos', () => {
    expect(roles.map(puedeVerCostos)).toEqual([true, true, false, true, false])
  })
})
