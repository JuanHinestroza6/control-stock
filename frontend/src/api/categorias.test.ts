import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { actualizarCategoria, crearCategoria, eliminarCategoria, listarCategorias } from './categorias'
import { cliente } from './cliente'

const adaptadorOriginal = cliente.defaults.adapter

function capturar(data: unknown, status = 200) {
  const peticiones: InternalAxiosRequestConfig[] = []
  const adaptador: AxiosAdapter = (config) => {
    peticiones.push(config)
    return Promise.resolve({ status, data, statusText: '', headers: {}, config })
  }
  cliente.defaults.adapter = adaptador
  return peticiones
}

describe('api/categorias', () => {
  afterEach(() => {
    cliente.defaults.adapter = adaptadorOriginal
  })

  it('listar hace GET /categorias', async () => {
    const p = capturar([{ id: 1, nombre: 'Jeans', totalProductos: 2 }])
    expect(await listarCategorias()).toHaveLength(1)
    expect(p[0].method).toBe('get')
    expect(p[0].url).toBe('/categorias')
  })

  it('crear hace POST con el nombre', async () => {
    const p = capturar({ id: 7, nombre: 'Gorras', totalProductos: 0 }, 201)
    await crearCategoria({ nombre: 'Gorras' })
    expect(p[0].method).toBe('post')
    expect(JSON.parse(p[0].data as string)).toEqual({ nombre: 'Gorras' })
  })

  it('actualizar hace PUT /categorias/{id}', async () => {
    const p = capturar({ id: 3, nombre: 'X', totalProductos: 0 })
    await actualizarCategoria(3, { nombre: 'X' })
    expect(p[0].method).toBe('put')
    expect(p[0].url).toBe('/categorias/3')
  })

  it('eliminar hace DELETE /categorias/{id}', async () => {
    const p = capturar(undefined, 204)
    await eliminarCategoria(3)
    expect(p[0].method).toBe('delete')
    expect(p[0].url).toBe('/categorias/3')
  })
})
