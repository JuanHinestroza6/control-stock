import { describe, expect, it } from 'vitest'
import {
  aActualizarRequest,
  aCrearRequest,
  crearEsquemaProducto,
  parsearPrecio,
  ventaMenorQueCosto,
  type DatosFormularioProducto,
} from './esquemaProducto'

const base: DatosFormularioProducto = {
  codigo: 'LIV001',
  nombre: ' Camiseta Oversize ',
  categoriaId: '1',
  precioCompra: '30000',
  precioVenta: '59900,50',
  leadTimeDias: '',
  loteMinimo: '',
}

describe('parsearPrecio', () => {
  it('acepta enteros y hasta 2 decimales con punto o coma', () => {
    expect(parsearPrecio('59900')).toBe(59900)
    expect(parsearPrecio(' 59900,5 ')).toBe(59900.5)
    expect(parsearPrecio('0.25')).toBe(0.25)
  })

  it('rechaza vacío, negativos, 3 decimales y texto', () => {
    for (const malo of ['', '-1', '1.234', 'abc', '1e3', '12345678901']) expect(parsearPrecio(malo)).toBeNull()
  })
})

describe('ventaMenorQueCosto', () => {
  it('advierte solo si la venta es estrictamente menor que la compra', () => {
    expect(ventaMenorQueCosto('30000', '20000')).toBe(true)
    expect(ventaMenorQueCosto('30000', '30000')).toBe(false)
    expect(ventaMenorQueCosto('30000', '')).toBe(false)
    expect(ventaMenorQueCosto('abc', '100')).toBe(false)
  })
})

describe('crearEsquemaProducto', () => {
  it('creación: lead time y lote son opcionales; código con formato', () => {
    const esquema = crearEsquemaProducto(false)
    expect(esquema.safeParse(base).success).toBe(true)
    expect(esquema.safeParse({ ...base, codigo: 'a b' }).success).toBe(false)
    expect(esquema.safeParse({ ...base, codigo: 'A' }).success).toBe(false)
  })

  it('valida rangos de lead time (1-365) y lote (1-10000)', () => {
    const esquema = crearEsquemaProducto(false)
    expect(esquema.safeParse({ ...base, leadTimeDias: '0' }).success).toBe(false)
    expect(esquema.safeParse({ ...base, leadTimeDias: '366' }).success).toBe(false)
    expect(esquema.safeParse({ ...base, leadTimeDias: '365', loteMinimo: '10000' }).success).toBe(true)
    expect(esquema.safeParse({ ...base, loteMinimo: '10001' }).success).toBe(false)
  })

  it('precio de venta debe ser > 0 y de compra puede ser 0', () => {
    const esquema = crearEsquemaProducto(false)
    expect(esquema.safeParse({ ...base, precioCompra: '0' }).success).toBe(true)
    expect(esquema.safeParse({ ...base, precioVenta: '0' }).success).toBe(false)
  })

  it('edición: lead time y lote son obligatorios', () => {
    const esquema = crearEsquemaProducto(true)
    expect(esquema.safeParse(base).success).toBe(false)
    expect(esquema.safeParse({ ...base, leadTimeDias: '15', loteMinimo: '1' }).success).toBe(true)
  })
})

describe('conversión a request', () => {
  it('crear: normaliza texto y deja null los opcionales vacíos', () => {
    expect(aCrearRequest(base)).toEqual({
      codigo: 'LIV001',
      nombre: 'Camiseta Oversize',
      categoriaId: 1,
      precioCompra: 30000,
      precioVenta: 59900.5,
      leadTimeDias: null,
      loteMinimo: null,
    })
  })

  it('actualizar: no envía código y convierte lead time y lote', () => {
    const r = aActualizarRequest({ ...base, leadTimeDias: '20', loteMinimo: '6' })
    expect(r).not.toHaveProperty('codigo')
    expect(r).toMatchObject({ leadTimeDias: 20, loteMinimo: 6 })
  })
})
