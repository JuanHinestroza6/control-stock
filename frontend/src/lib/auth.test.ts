import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CLAVE_SESION, borrarSesion, guardarSesion, leerSesion, sesionDesdeLogin, sesionVencida, tieneRol, type Sesion } from './auth'

const ahora = new Date('2026-10-01T10:00:00Z')

function sesion(expiraEn: string, rol: Sesion['usuario']['rol'] = 'ADMIN'): Sesion {
  return { token: 'abc', expiraEn, usuario: { id: 1, nombre: 'Ana', email: 'ana@livorno.co', rol, debeCambiarPassword: false } }
}

describe('lib/auth', () => {
  beforeEach(() => localStorage.clear())

  it('guarda y lee la sesión', () => {
    const s = sesion('2026-10-01T18:00:00Z')
    guardarSesion(s)
    expect(leerSesion(ahora)).toEqual(s)
  })

  it('devuelve null y limpia si la sesión está vencida', () => {
    guardarSesion(sesion('2026-10-01T09:00:00Z'))
    expect(leerSesion(ahora)).toBeNull()
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull()
  })

  it('sesionVencida considera vencida si expiraEn es ahora o inválida', () => {
    expect(sesionVencida(sesion('2026-10-01T10:00:00Z'), ahora)).toBe(true)
    expect(sesionVencida(sesion('no-es-fecha'), ahora)).toBe(true)
    expect(sesionVencida(sesion('2026-10-01T10:00:01Z'), ahora)).toBe(false)
  })

  it('ignora JSON corrupto o con forma inválida', () => {
    localStorage.setItem(CLAVE_SESION, '{no json')
    expect(leerSesion(ahora)).toBeNull()
    localStorage.setItem(CLAVE_SESION, JSON.stringify({ token: 1 }))
    expect(leerSesion(ahora)).toBeNull()
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull()
  })

  it('borrarSesion elimina la sesión', () => {
    guardarSesion(sesion('2026-10-01T18:00:00Z'))
    borrarSesion()
    expect(leerSesion(ahora)).toBeNull()
  })

  it('tieneRol verifica el rol del usuario', () => {
    const s = sesion('2026-10-01T18:00:00Z', 'VENDEDOR')
    expect(tieneRol(s, 'ADMIN', 'VENDEDOR')).toBe(true)
    expect(tieneRol(s, 'ADMIN', 'INVENTARIO')).toBe(false)
    expect(tieneRol(null, 'ADMIN')).toBe(false)
  })

  it('si el almacenamiento falla no lanza: leerSesion devuelve null y guardar/borrar se ignoran', () => {
    const espiaGet = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    const espiaSet = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    const espiaRemove = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    try {
      expect(leerSesion(ahora)).toBeNull()
      expect(() => guardarSesion(sesion('2026-10-01T18:00:00Z'))).not.toThrow()
      expect(() => borrarSesion()).not.toThrow()
    } finally {
      espiaGet.mockRestore()
      espiaSet.mockRestore()
      espiaRemove.mockRestore()
    }
  })

  it('sesionDesdeLogin conserva token, expiración y usuario y descarta el resto', () => {
    const usuario = { id: 2, nombre: 'Luis', email: 'luis@livorno.co', rol: 'INVENTARIO' as const, debeCambiarPassword: false }
    expect(sesionDesdeLogin({ token: 't', tipo: 'Bearer', expiraEn: '2026-10-01T18:00:00Z', usuario })).toEqual({
      token: 't',
      expiraEn: '2026-10-01T18:00:00Z',
      usuario,
    })
  })

  it('leerSesion normaliza sesiones antiguas sin debeCambiarPassword a false', () => {
    const { debeCambiarPassword: _omitido, ...usuario } = sesion('2026-10-01T18:00:00Z').usuario
    localStorage.setItem(CLAVE_SESION, JSON.stringify({ token: 'abc', expiraEn: '2026-10-01T18:00:00Z', usuario }))
    expect(leerSesion(ahora)?.usuario.debeCambiarPassword).toBe(false)
  })
})
