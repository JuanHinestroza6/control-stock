import { describe, expect, it } from 'vitest'
import {
  puedeAtenderAlertas,
  puedeConvertirReserva,
  puedeReservar,
  puedeAnularVenta,
  puedeEditarCatalogo,
  puedeMoverStock,
  puedeVender,
  puedeVerCostos,
  puedeVerKardex,
  puedeRecalcularAnalitica,
  puedeVerAnaliticaCompleta,
  puedeVerPronostico,
  puedeVerCompras,
  puedeGestionarPlanes,
  puedeAprobarPlan,
  puedeRecibirPlan,
  puedeCancelarPlan,
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

describe('permisos de la fase 3', () => {
  const roles = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD', undefined] as const
  it('puedeReservar', () => {
    expect(roles.map(puedeReservar)).toEqual([true, true, true, false, false])
  })
  it('puedeConvertirReserva', () => {
    expect(roles.map(puedeConvertirReserva)).toEqual([true, false, true, false, false])
  })
  it('puedeAtenderAlertas', () => {
    expect(roles.map(puedeAtenderAlertas)).toEqual([true, true, false, false, false])
  })
})

describe('permisos de la fase 5', () => {
  const roles = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD', undefined] as const
  it('puedeVerAnaliticaCompleta', () => {
    expect(roles.map(puedeVerAnaliticaCompleta)).toEqual([true, true, false, true, false])
  })
  it('puedeRecalcularAnalitica', () => {
    expect(roles.map(puedeRecalcularAnalitica)).toEqual([true, false, false, false, false])
  })
})

describe('permisos de la fase 6', () => {
  const roles = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD', undefined] as const
  it('puedeVerPronostico y puedeVerCompras', () => {
    expect(roles.map(puedeVerPronostico)).toEqual([true, true, false, true, false])
    expect(roles.map(puedeVerCompras)).toEqual([true, true, false, true, false])
  })
  it('puedeGestionarPlanes y puedeRecibirPlan', () => {
    expect(roles.map(puedeGestionarPlanes)).toEqual([true, true, false, false, false])
    expect(roles.map(puedeRecibirPlan)).toEqual([true, true, false, false, false])
  })
  it('puedeAprobarPlan', () => {
    expect(roles.map(puedeAprobarPlan)).toEqual([true, false, false, false, false])
  })
  it('puedeCancelarPlan depende del rol y del estado', () => {
    expect(puedeCancelarPlan('ADMIN', 'BORRADOR')).toBe(true)
    expect(puedeCancelarPlan('ADMIN', 'APROBADO')).toBe(true)
    expect(puedeCancelarPlan('ADMIN', 'RECIBIDO')).toBe(false)
    expect(puedeCancelarPlan('ADMIN', 'CANCELADO')).toBe(false)
    expect(puedeCancelarPlan('INVENTARIO', 'BORRADOR')).toBe(true)
    expect(puedeCancelarPlan('INVENTARIO', 'APROBADO')).toBe(false)
    expect(puedeCancelarPlan('VENDEDOR', 'BORRADOR')).toBe(false)
    expect(puedeCancelarPlan('CONTABILIDAD', 'BORRADOR')).toBe(false)
    expect(puedeCancelarPlan(undefined, 'BORRADOR')).toBe(false)
  })
})
