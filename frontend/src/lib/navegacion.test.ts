import { describe, expect, it } from 'vitest'
import { destinoSeguro } from './navegacion'

describe('destinoSeguro', () => {
  it('acepta rutas internas con query', () => {
    expect(destinoSeguro('/ventas?x=1')).toBe('/ventas?x=1')
  })

  it('rechaza rutas externas y variantes con barra invertida', () => {
    const barraInvertida = String.fromCharCode(92)
    const malos = [
      '//evil.com',
      `/${barraInvertida}evil.com`,
      `/${barraInvertida}/evil.com`,
      'https://evil.com',
      'evil.com',
      '',
      undefined,
    ]
    for (const malo of malos) {
      expect(destinoSeguro(malo)).toBe('/')
    }
  })

  it('rechaza /login exacto pero no rutas que solo empiezan igual', () => {
    expect(destinoSeguro('/login')).toBe('/')
    expect(destinoSeguro('/login?x=1')).toBe('/')
    expect(destinoSeguro('/loginx')).toBe('/loginx')
  })
})
