import { z } from 'zod'
import type { ActualizarProductoRequest, CrearProductoRequest } from '../../tipos/catalogo'

/** Valores del formulario: todos como texto; se convierten al enviar. */
export interface DatosFormularioProducto {
  codigo: string
  nombre: string
  categoriaId: string
  precioCompra: string
  precioVenta: string
  leadTimeDias: string
  loteMinimo: string
}

/** Convierte "59900", "59900.5" o "59900,50" a número con hasta 2 decimales; null si no es válido. */
export function parsearPrecio(texto: string): number | null {
  const limpio = texto.trim().replace(',', '.')
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(limpio)) return null
  return Number(limpio)
}

/** True si ambos precios son válidos y la venta queda por debajo del costo (se permite, pero se advierte). */
export function ventaMenorQueCosto(compra: string, venta: string): boolean {
  const c = parsearPrecio(compra)
  const v = parsearPrecio(venta)
  return c !== null && v !== null && v > 0 && v < c
}

function entero(min: number, max: number, obligatorio: boolean, mensaje: string) {
  return z
    .string()
    .trim()
    .refine((v) => (v === '' ? !obligatorio : /^\d+$/.test(v) && Number(v) >= min && Number(v) <= max), mensaje)
}

/** `edicion`: el código no se valida (es inmutable) y lead time / lote son obligatorios. */
export function crearEsquemaProducto(edicion: boolean) {
  return z.object({
    codigo: edicion
      ? z.string()
      : z.string().trim().regex(/^[A-Za-z0-9-]{2,20}$/, 'Código de 2 a 20 caracteres: letras, números y guiones'),
    nombre: z.string().trim().min(1, 'Ingresa el nombre').max(120, 'Máximo 120 caracteres'),
    categoriaId: z.string().min(1, 'Selecciona una categoría'),
    precioCompra: z
      .string()
      .refine((v) => parsearPrecio(v) !== null, 'Ingresa un precio válido (máx. 2 decimales)'),
    precioVenta: z
      .string()
      .refine((v) => {
        const n = parsearPrecio(v)
        return n !== null && n > 0
      }, 'Ingresa un precio mayor que 0 (máx. 2 decimales)'),
    leadTimeDias: entero(1, 365, edicion, 'Entre 1 y 365 días'),
    loteMinimo: entero(1, 10000, edicion, 'Entre 1 y 10000 unidades'),
  })
}

const aNumeroOpcional = (v: string): number | null => (v.trim() === '' ? null : Number(v))

export function aCrearRequest(d: DatosFormularioProducto): CrearProductoRequest {
  return {
    codigo: d.codigo.trim(),
    nombre: d.nombre.trim(),
    categoriaId: Number(d.categoriaId),
    precioCompra: parsearPrecio(d.precioCompra) ?? 0,
    precioVenta: parsearPrecio(d.precioVenta) ?? 0,
    leadTimeDias: aNumeroOpcional(d.leadTimeDias),
    loteMinimo: aNumeroOpcional(d.loteMinimo),
  }
}

export function aActualizarRequest(d: DatosFormularioProducto): ActualizarProductoRequest {
  return {
    nombre: d.nombre.trim(),
    categoriaId: Number(d.categoriaId),
    precioCompra: parsearPrecio(d.precioCompra) ?? 0,
    precioVenta: parsearPrecio(d.precioVenta) ?? 0,
    leadTimeDias: Number(d.leadTimeDias),
    loteMinimo: Number(d.loteMinimo),
  }
}
