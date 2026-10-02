import type { InventarioItemResponse } from '../tipos/inventario'
import type { RegistrarVentaRequest } from '../tipos/ventas'

export const MAX_LINEAS_VENTA = 50
export const MAX_CANTIDAD_VENTA = 10000

export interface LineaCarrito {
  variante: InventarioItemResponse
  /** Texto escrito por el usuario. */
  cantidad: string
  /** Texto escrito; vacío = precio de lista. */
  precio: string
}

export interface ErroresLinea {
  cantidad?: string
  precio?: string
}

const PATRON_PRECIO = /^\d{1,10}([.,]\d{1,2})?$/

/** Suma `cantidad` si la variante ya está en el carrito; si no, agrega una línea (límite de líneas). */
export function agregarAlCarrito(
  lineas: LineaCarrito[],
  variante: InventarioItemResponse,
): { lineas: LineaCarrito[]; error?: string } {
  const i = lineas.findIndex((l) => l.variante.varianteId === variante.varianteId)
  if (i >= 0) {
    const actual = Number(lineas[i].cantidad)
    const siguiente = Number.isInteger(actual) && actual >= 0 ? actual + 1 : 1
    return { lineas: lineas.map((l, j) => (j === i ? { ...l, cantidad: String(siguiente) } : l)) }
  }
  if (lineas.length >= MAX_LINEAS_VENTA) return { lineas, error: `Máximo ${MAX_LINEAS_VENTA} líneas por venta.` }
  return { lineas: [...lineas, { variante, cantidad: '1', precio: '' }] }
}

/** Cantidad entera >= 1 o undefined. */
export function parsearCantidad(texto: string): number | undefined {
  const t = texto.trim()
  return /^\d+$/.test(t) && Number(t) >= 1 ? Number(t) : undefined
}

/** Precio efectivo: vacío = lista; undefined si el texto no es un precio válido. */
export function parsearPrecio(texto: string, lista: number): number | undefined {
  const t = texto.trim()
  if (t === '') return lista
  if (!PATRON_PRECIO.test(t)) return undefined
  return Number(t.replace(',', '.'))
}

export function errorLinea(linea: LineaCarrito): ErroresLinea {
  const errores: ErroresLinea = {}
  const { variante } = linea
  const cantidad = parsearCantidad(linea.cantidad)
  if (cantidad === undefined || cantidad > MAX_CANTIDAD_VENTA) {
    errores.cantidad = `Entero entre 1 y ${MAX_CANTIDAD_VENTA}`
  } else if (cantidad > variante.stockDisponible) {
    errores.cantidad = `Solo hay ${variante.stockDisponible} disponibles`
  }
  const precio = parsearPrecio(linea.precio, variante.precioVenta)
  if (precio === undefined || precio <= 0) {
    errores.precio = 'Precio inválido'
  } else if (precio > variante.precioVenta) {
    errores.precio = 'No puede superar el precio de lista'
  }
  return errores
}

/** Total del carrito con las líneas válidas, redondeado a centavos. */
export function totalCarrito(lineas: LineaCarrito[]): number {
  const suma = lineas.reduce((acc, l) => {
    const cantidad = parsearCantidad(l.cantidad)
    const precio = parsearPrecio(l.precio, l.variante.precioVenta)
    return cantidad !== undefined && precio !== undefined ? acc + cantidad * precio : acc
  }, 0)
  return Math.round(suma * 100) / 100
}

export interface ResultadoCarrito {
  solicitud?: RegistrarVentaRequest
  /** Errores por varianteId. */
  errores: Record<number, ErroresLinea>
  general?: string
}

/** Valida el carrito y arma la solicitud; el precio solo se envía si difiere del de lista. */
export function construirVenta(lineas: LineaCarrito[]): ResultadoCarrito {
  if (lineas.length === 0) return { errores: {}, general: 'Agrega al menos una variante a la venta.' }
  const errores: Record<number, ErroresLinea> = {}
  for (const l of lineas) {
    const e = errorLinea(l)
    if (e.cantidad || e.precio) errores[l.variante.varianteId] = e
  }
  if (Object.keys(errores).length > 0) return { errores }
  return {
    errores,
    solicitud: {
      lineas: lineas.map((l) => {
        const precio = parsearPrecio(l.precio, l.variante.precioVenta) as number
        return {
          varianteId: l.variante.varianteId,
          cantidad: Number(l.cantidad),
          ...(precio !== l.variante.precioVenta ? { precioUnitario: precio } : {}),
        }
      }),
    },
  }
}
