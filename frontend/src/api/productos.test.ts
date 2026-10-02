import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { cliente } from './cliente'
import { listarProductos, parametrosProductos } from './productos'

describe('parametrosProductos', () => {
  it('omite filtros vacíos o indefinidos y recorta la búsqueda', () => {
    expect(parametrosProductos({ q: '   ', categoriaId: undefined, activo: undefined })).toEqual({})
    expect(parametrosProductos({ q: ' jean ', categoriaId: 2, activo: false, page: 0, size: 20, sort: 'nombre,asc' })).toEqual({
      q: 'jean',
      categoriaId: 2,
      activo: false,
      page: 0,
      size: 20,
      sort: 'nombre,asc',
    })
  })
})

describe('listarProductos', () => {
  const original = cliente.defaults.adapter
  afterEach(() => {
    cliente.defaults.adapter = original
  })

  it('hace GET /productos con los params y devuelve la página', async () => {
    const peticiones: InternalAxiosRequestConfig[] = []
    const adaptador: AxiosAdapter = (config) => {
      peticiones.push(config)
      const data = { content: [], page: { size: 20, number: 1, totalElements: 0, totalPages: 0 } }
      return Promise.resolve({ status: 200, data, statusText: '', headers: {}, config })
    }
    cliente.defaults.adapter = adaptador
    const pagina = await listarProductos({ page: 1, sort: 'codigo,asc' })
    expect(pagina.page.number).toBe(1)
    expect(peticiones[0].url).toBe('/productos')
    expect(peticiones[0].params).toEqual({ page: 1, sort: 'codigo,asc' })
  })
})
