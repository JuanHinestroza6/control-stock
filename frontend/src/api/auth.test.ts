import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { login, yo } from './auth'
import { cliente } from './cliente'

const adaptadorOriginal = cliente.defaults.adapter

function capturar(data: unknown) {
  const peticiones: InternalAxiosRequestConfig[] = []
  const adaptador: AxiosAdapter = (config) => {
    peticiones.push(config)
    return Promise.resolve({ status: 200, data, statusText: '', headers: {}, config })
  }
  cliente.defaults.adapter = adaptador
  return peticiones
}

describe('api/auth', () => {
  afterEach(() => {
    cliente.defaults.adapter = adaptadorOriginal
  })

  it('login hace POST a /auth/login con las credenciales y devuelve el cuerpo', async () => {
    const peticiones = capturar({ token: 't' })
    const respuesta = await login({ email: 'ana@livorno.co', password: 'x' })
    expect(respuesta).toEqual({ token: 't' })
    expect(peticiones[0].method).toBe('post')
    expect(peticiones[0].url).toBe('/auth/login')
    expect(JSON.parse(peticiones[0].data as string)).toEqual({ email: 'ana@livorno.co', password: 'x' })
  })

  it('yo hace GET a /auth/yo y devuelve el usuario', async () => {
    const peticiones = capturar({ id: 1 })
    expect(await yo()).toEqual({ id: 1 })
    expect(peticiones[0].method).toBe('get')
    expect(peticiones[0].url).toBe('/auth/yo')
  })
})
