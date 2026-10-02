import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CLAVE_SESION, guardarSesion } from '../lib/auth'
import { alExpirarSesion, alRequerirCambioPassword, cliente } from './cliente'

const sesion = {
  token: 'tok123',
  expiraEn: new Date(Date.now() + 3_600_000).toISOString(),
  usuario: { id: 1, nombre: 'Ana', email: 'ana@livorno.co', rol: 'ADMIN' as const, debeCambiarPassword: false },
}

function adaptadorCon(status: number, capturar?: (c: InternalAxiosRequestConfig) => void, cuerpo: unknown = {}): AxiosAdapter {
  return (config) => {
    capturar?.(config)
    const respuesta = { status, data: cuerpo, statusText: '', headers: {}, config }
    if (status >= 400) {
      return Promise.reject(new AxiosError('fallo', 'ERR_BAD_REQUEST', config, undefined, respuesta))
    }
    return Promise.resolve(respuesta)
  }
}

describe('cliente Axios', () => {
  beforeEach(() => localStorage.clear())

  it('agrega el header Bearer cuando hay sesión', async () => {
    guardarSesion(sesion)
    let auth: unknown
    await cliente.get('/x', {
      adapter: adaptadorCon(200, (c) => {
        auth = c.headers.get('Authorization')
      }),
    })
    expect(auth).toBe('Bearer tok123')
  })

  it('ante 401 borra la sesión y notifica', async () => {
    guardarSesion(sesion)
    const callback = vi.fn()
    const cancelar = alExpirarSesion(callback)
    await expect(cliente.get('/productos', { adapter: adaptadorCon(401) })).rejects.toBeInstanceOf(AxiosError)
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull()
    expect(callback).toHaveBeenCalledTimes(1)
    cancelar()
  })

  it('un 401 en /auth/login no se trata como sesión expirada', async () => {
    guardarSesion(sesion)
    const callback = vi.fn()
    const cancelar = alExpirarSesion(callback)
    await expect(cliente.post('/auth/login', {}, { adapter: adaptadorCon(401) })).rejects.toBeInstanceOf(AxiosError)
    expect(callback).not.toHaveBeenCalled()
    expect(localStorage.getItem(CLAVE_SESION)).not.toBeNull()
    cancelar()
  })

  it('otros errores no tocan la sesión', async () => {
    guardarSesion(sesion)
    await expect(cliente.get('/x', { adapter: adaptadorCon(500) })).rejects.toBeInstanceOf(AxiosError)
    expect(localStorage.getItem(CLAVE_SESION)).not.toBeNull()
  })

  it('sin sesión no envía Authorization', async () => {
    let auth: unknown = 'sin-capturar'
    await cliente.get('/x', {
      adapter: adaptadorCon(200, (c) => {
        auth = c.headers.get('Authorization')
      }),
    })
    expect(auth).toBeUndefined()
  })

  it('con la sesión vencida no envía Authorization y la limpia', async () => {
    guardarSesion({ ...sesion, expiraEn: new Date(Date.now() - 1000).toISOString() })
    let auth: unknown = 'sin-capturar'
    await cliente.get('/x', {
      adapter: adaptadorCon(200, (c) => {
        auth = c.headers.get('Authorization')
      }),
    })
    expect(auth).toBeUndefined()
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull()
  })

  it('un 403 no se trata como sesión expirada', async () => {
    guardarSesion(sesion)
    const callback = vi.fn()
    const cancelar = alExpirarSesion(callback)
    await expect(cliente.get('/usuarios', { adapter: adaptadorCon(403) })).rejects.toBeInstanceOf(AxiosError)
    expect(callback).not.toHaveBeenCalled()
    expect(localStorage.getItem(CLAVE_SESION)).not.toBeNull()
    cancelar()
  })

  it('alExpirarSesion devuelve una función que cancela la suscripción', async () => {
    const callback = vi.fn()
    alExpirarSesion(callback)()
    await expect(cliente.get('/x', { adapter: adaptadorCon(401) })).rejects.toBeInstanceOf(AxiosError)
    expect(callback).not.toHaveBeenCalled()
  })

  it('un 403 con codigo CAMBIO_PASSWORD_REQUERIDO marca la sesión y notifica', async () => {
    guardarSesion(sesion)
    const callback = vi.fn()
    const cancelar = alRequerirCambioPassword(callback)
    await expect(
      cliente.get('/productos', { adapter: adaptadorCon(403, undefined, { codigo: 'CAMBIO_PASSWORD_REQUERIDO' }) }),
    ).rejects.toBeInstanceOf(AxiosError)
    expect(callback).toHaveBeenCalledTimes(1)
    expect(JSON.parse(localStorage.getItem(CLAVE_SESION) ?? '{}').usuario.debeCambiarPassword).toBe(true)
    cancelar()
  })

  it('un 403 sin ese código no toca la sesión', async () => {
    guardarSesion(sesion)
    const callback = vi.fn()
    const cancelar = alRequerirCambioPassword(callback)
    await expect(cliente.get('/usuarios', { adapter: adaptadorCon(403, undefined, { detail: 'Prohibido' }) })).rejects.toBeInstanceOf(AxiosError)
    expect(callback).not.toHaveBeenCalled()
    expect(JSON.parse(localStorage.getItem(CLAVE_SESION) ?? '{}').usuario.debeCambiarPassword).toBe(false)
    cancelar()
  })
})
