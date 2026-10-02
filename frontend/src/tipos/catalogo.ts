import type { Pagina, ParametrosPagina } from './paginacion'

export type Talla = 'XS' | 'S' | 'M' | 'L' | 'XL' | 'XXL' | 'UNICA'
export type EstadoStock = 'OK' | 'BAJO' | 'AGOTADO'

export interface EstadoRequest {
  activo: boolean
}

export interface CategoriaRef {
  id: number
  nombre: string
}

export interface CategoriaRequest {
  nombre: string
}

export interface CategoriaResponse {
  id: number
  nombre: string
  totalProductos: number
}

export interface ProductoResumenResponse {
  id: number
  codigo: string
  nombre: string
  categoria: CategoriaRef
  precioVenta: number
  activo: boolean
  totalVariantes: number
  stockDisponibleTotal: number
}

export interface ProductoDetalleResponse {
  id: number
  codigo: string
  nombre: string
  categoria: CategoriaRef
  /** null para el rol VENDEDOR. */
  precioCompra: number | null
  precioVenta: number
  leadTimeDias: number
  loteMinimo: number
  activo: boolean
  totalVariantes: number
  creadoEn: string
  actualizadoEn: string
}

export interface CrearProductoRequest {
  codigo: string
  nombre: string
  categoriaId: number
  precioCompra: number
  precioVenta: number
  leadTimeDias?: number | null
  loteMinimo?: number | null
}

export interface ActualizarProductoRequest {
  nombre: string
  categoriaId: number
  precioCompra: number
  precioVenta: number
  leadTimeDias: number
  loteMinimo: number
}

export interface FiltrosProductos extends ParametrosPagina {
  q?: string
  categoriaId?: number
  activo?: boolean
}

export type PaginaProductos = Pagina<ProductoResumenResponse>

export interface VarianteResponse {
  id: number
  productoId: number
  sku: string
  talla: Talla
  color: string
  stockFisico: number
  stockReservado: number
  stockDisponible: number
  stockMinimo: number
  puntoReorden: number | null
  claseAbc: 'A' | 'B' | 'C' | null
  estadoStock: EstadoStock
  activo: boolean
}

export interface GenerarVariantesRequest {
  tallas: Talla[]
  colores: string[]
  stockMinimo: number
}

export interface VarianteOmitida {
  talla: Talla
  color: string
  sku: string
  motivo: 'EXISTE' | 'EXISTE_INACTIVA'
}

export interface GenerarVariantesResponse {
  creadas: VarianteResponse[]
  omitidas: VarianteOmitida[]
}

export interface ActualizarVarianteRequest {
  stockMinimo: number
}

export interface CeldaMatriz {
  talla: Talla
  color: string
  varianteId: number
  sku: string
  stockFisico: number
  stockReservado: number
  stockDisponible: number
  estadoStock: EstadoStock
  activo: boolean
}

export interface MatrizResponse {
  productoId: number
  tallas: Talla[]
  colores: string[]
  celdas: CeldaMatriz[]
}
