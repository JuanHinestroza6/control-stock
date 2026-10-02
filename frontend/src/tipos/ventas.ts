import type { Talla } from './catalogo'
import type { Pagina, ParametrosPagina } from './paginacion'

export type EstadoVenta = 'REGISTRADA' | 'ANULADA'

export interface LineaVentaRequest {
  varianteId: number
  cantidad: number
  /** Opcional; si falta se usa el precio de lista. No puede superarlo. */
  precioUnitario?: number
}

export interface RegistrarVentaRequest {
  lineas: LineaVentaRequest[]
}

export interface AnularVentaRequest {
  motivo: string
}

export interface DevolucionRequest {
  detalleVentaId: number
  cantidad: number
  motivo: string
  aptaParaVenta: boolean
}

export interface DevolucionResponse {
  id: number
  detalleVentaId: number
  cantidad: number
  motivo: string
  aptaParaVenta: boolean
  fecha: string
  usuarioNombre: string
}

export interface LineaVentaResponse {
  detalleId: number
  varianteId: number
  sku: string
  productoNombre: string
  talla: Talla
  color: string
  cantidad: number
  precioLista: number
  precioUnitario: number
  /** null para el rol VENDEDOR. */
  costoUnitario: number | null
  subtotal: number
  cantidadDevuelta: number
  devoluciones: DevolucionResponse[]
}

export interface VentaResumenResponse {
  id: number
  numero: string
  fecha: string
  vendedor: string
  unidades: number
  total: number
  estado: EstadoVenta
}

export interface VentaDetalleResponse {
  id: number
  numero: string
  fecha: string
  vendedor: string
  estado: EstadoVenta
  total: number
  descuentoTotal: number
  anuladaEn: string | null
  anuladaPor: string | null
  motivoAnulacion: string | null
  lineas: LineaVentaResponse[]
}

/** Fechas ISO `YYYY-MM-DD`, ambas inclusivas. Orden permitido: `fecha`, `total`. */
export interface FiltrosVentas extends ParametrosPagina {
  desde?: string
  hasta?: string
  estado?: EstadoVenta
}

export type PaginaVentas = Pagina<VentaResumenResponse>
