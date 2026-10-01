import { describe, expect, it } from 'vitest'
import type { Rol } from '../../tipos/auth'
import { MODULOS, modulosPermitidos, rutaPermitida } from './modulos'

/** Módulos visibles por rol según la matriz de permisos de docs/dominio.md. */
const MATRIZ: Record<Rol, string[]> = {
  ADMIN: ['inicio', 'productos', 'inventario', 'ventas', 'reservas', 'alertas', 'analitica', 'compras', 'reportes', 'usuarios'],
  INVENTARIO: ['inicio', 'productos', 'inventario', 'reservas', 'alertas', 'analitica', 'compras'],
  VENDEDOR: ['inicio', 'productos', 'ventas', 'reservas', 'alertas', 'analitica'],
  CONTABILIDAD: ['inicio', 'productos', 'inventario', 'ventas', 'analitica', 'compras', 'reportes'],
}

describe('modulosPermitidos', () => {
  it.each(Object.keys(MATRIZ) as Rol[])('%s ve exactamente los módulos de la matriz de dominio', (rol) => {
    expect(modulosPermitidos(rol).map((m) => m.clave)).toEqual(MATRIZ[rol])
  })

  it('las claves y rutas de los módulos son únicas', () => {
    expect(new Set(MODULOS.map((m) => m.clave)).size).toBe(MODULOS.length)
    expect(new Set(MODULOS.map((m) => m.ruta)).size).toBe(MODULOS.length)
  })
})

describe('rutaPermitida', () => {
  it('bloquea la ruta de un módulo no permitido y sus subrutas, con query o hash', () => {
    expect(rutaPermitida('/usuarios', 'VENDEDOR')).toBe(false)
    expect(rutaPermitida('/usuarios/5', 'VENDEDOR')).toBe(false)
    expect(rutaPermitida('/reportes?mes=2026-09', 'INVENTARIO')).toBe(false)
    expect(rutaPermitida('/reservas#nueva', 'CONTABILIDAD')).toBe(false)
  })

  it('permite la ruta de un módulo permitido', () => {
    expect(rutaPermitida('/usuarios', 'ADMIN')).toBe(true)
    expect(rutaPermitida('/ventas/12', 'CONTABILIDAD')).toBe(true)
  })

  it('no confunde rutas que solo comparten prefijo de texto con un módulo', () => {
    expect(rutaPermitida('/usuarios-demo', 'VENDEDOR')).toBe(true)
  })

  it('la raíz y las rutas desconocidas se consideran permitidas', () => {
    expect(rutaPermitida('/', 'CONTABILIDAD')).toBe(true)
    expect(rutaPermitida('/no-existe', 'VENDEDOR')).toBe(true)
  })
})
