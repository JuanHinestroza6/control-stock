import { describe, expect, it } from 'vitest'
import type { InventarioItemResponse } from '../tipos/inventario'
import { agregarAlCarrito, construirVenta, MAX_LINEAS_VENTA, totalCarrito, type LineaCarrito } from './carrito'

function variante(id: number, extra: Partial<InventarioItemResponse> = {}): InventarioItemResponse {
  return {
    varianteId: id,
    sku: `SKU-${id}`,
    productoId: 1,
    productoCodigo: 'LIV001',
    productoNombre: 'Camiseta',
    categoria: { id: 1, nombre: 'Camisetas' },
    talla: 'M',
    color: 'NEGRO',
    stockFisico: 10,
    stockReservado: 0,
    stockDisponible: 10,
    stockMinimo: 1,
    puntoReorden: null, claseAbc: null,
    estadoStock: 'OK',
    precioVenta: 50000,
    activo: true,
    ...extra,
  }
}

const linea = (id: number, cantidad: string, precio = '', extra: Partial<InventarioItemResponse> = {}): LineaCarrito => ({
  variante: variante(id, extra),
  cantidad,
  precio,
})

describe('agregarAlCarrito', () => {
  it('agrega con cantidad 1 y una variante repetida suma cantidad', () => {
    let r = agregarAlCarrito([], variante(1))
    expect(r.lineas).toHaveLength(1)
    r = agregarAlCarrito(r.lineas, variante(1))
    expect(r.lineas).toHaveLength(1)
    expect(r.lineas[0].cantidad).toBe('2')
  })

  it('rechaza más de 50 líneas', () => {
    const llenas = Array.from({ length: MAX_LINEAS_VENTA }, (_, i) => linea(i + 1, '1'))
    const r = agregarAlCarrito(llenas, variante(999))
    expect(r.lineas).toHaveLength(MAX_LINEAS_VENTA)
    expect(r.error).toMatch(/Máximo 50/)
  })
})

describe('totalCarrito', () => {
  it('usa el precio de lista si no hay precio y el escrito si lo hay', () => {
    expect(totalCarrito([linea(1, '2'), linea(2, '1', '40000,50')])).toBe(140000.5)
  })
})

describe('construirVenta', () => {
  it('envía precioUnitario solo si difiere de la lista', () => {
    const r = construirVenta([linea(1, '2'), linea(2, '1', '45000'), linea(3, '1', '50000')])
    expect(r.solicitud).toEqual({
      lineas: [
        { varianteId: 1, cantidad: 2 },
        { varianteId: 2, cantidad: 1, precioUnitario: 45000 },
        { varianteId: 3, cantidad: 1 },
      ],
    })
  })

  it('rechaza precio mayor a la lista, cantidad inválida o mayor al disponible', () => {
    const r = construirVenta([linea(1, '1', '50001'), linea(2, '0'), linea(3, '11')])
    expect(r.solicitud).toBeUndefined()
    expect(r.errores[1].precio).toMatch(/superar/)
    expect(r.errores[2].cantidad).toBeDefined()
    expect(r.errores[3].cantidad).toMatch(/Solo hay 10/)
  })

  it('exige al menos una línea', () => {
    expect(construirVenta([]).general).toMatch(/al menos una/)
  })
})
