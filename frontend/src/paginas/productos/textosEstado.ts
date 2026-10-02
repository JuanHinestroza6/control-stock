import type { ProductoDetalleResponse, VarianteResponse } from '../../tipos/catalogo'

export interface TextoConfirmacion {
  titulo: string
  mensaje: string
  boton: string
}

/** Textos de confirmación al cambiar el estado del producto (la cascada solo ocurre al desactivar). */
export function textoEstadoProducto(producto: Pick<ProductoDetalleResponse, 'nombre' | 'totalVariantes'>, activar: boolean): TextoConfirmacion {
  if (activar) {
    return {
      titulo: 'Activar producto',
      mensaje: `Se activará "${producto.nombre}". Sus variantes seguirán inactivas: reactivarlo no las reactiva, tendrás que activarlas una por una.`,
      boton: 'Activar',
    }
  }
  return {
    titulo: 'Desactivar producto',
    mensaje: `Se desactivará "${producto.nombre}" y también todas sus variantes (${producto.totalVariantes}). Si luego lo reactivas, las variantes no se reactivan automáticamente.`,
    boton: 'Desactivar',
  }
}

export function textoEstadoVariante(variante: Pick<VarianteResponse, 'sku'>, activar: boolean): TextoConfirmacion {
  return activar
    ? { titulo: 'Activar variante', mensaje: `Se activará la variante ${variante.sku}.`, boton: 'Activar' }
    : {
        titulo: 'Desactivar variante',
        mensaje: `Se desactivará la variante ${variante.sku}: dejará de contarse en los totales de stock del producto.`,
        boton: 'Desactivar',
      }
}

/** Devuelve el stock mínimo como entero 0-100000 o null si el texto no es válido. */
export function parsearStockMinimo(texto: string): number | null {
  const limpio = texto.trim()
  if (!/^\d{1,6}$/.test(limpio)) return null
  const n = Number(limpio)
  return n <= 100000 ? n : null
}
