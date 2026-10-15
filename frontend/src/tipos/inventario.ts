import type { ClaseAbc } from './analitica'
import type { CategoriaRef, EstadoStock, Talla } from './catalogo'
import type { Pagina, ParametrosPagina } from './paginacion'

export type TipoMovimiento =
  | 'ENTRADA'
  | 'VENTA'
  | 'VENTA_RESERVA'
  | 'DEVOLUCION'
  | 'AJUSTE_POSITIVO'
  | 'AJUSTE_NEGATIVO'
  | 'RESERVA'
  | 'LIBERACION'
  | 'ANULACION_VENTA'

export type TipoAjuste = 'AJUSTE_POSITIVO' | 'AJUSTE_NEGATIVO'

export interface InventarioItemResponse {
  varianteId: number
  sku: string
  productoId: number
  productoCodigo: string
  productoNombre: string
  categoria: CategoriaRef
  talla: Talla
  color: string
  stockFisico: number
  stockReservado: number
  stockDisponible: number
  stockMinimo: number
  puntoReorden: number | null
  claseAbc: ClaseAbc | null
  estadoStock: EstadoStock
  precioVenta: number
  activo: boolean
}

export interface FiltrosInventario extends ParametrosPagina {
  q?: string
  categoriaId?: number
  estadoStock?: EstadoStock
  incluirInactivas?: boolean
}

export type PaginaInventario = Pagina<InventarioItemResponse>

export interface LineaEntradaRequest {
  varianteId: number
  cantidad: number
  costoUnitario: number
}

export interface EntradaRequest {
  observacion?: string
  lineas: LineaEntradaRequest[]
}

export interface AjusteRequest {
  varianteId: number
  tipo: TipoAjuste
  cantidad: number
  observacion: string
}

export interface MovimientoResponse {
  id: number
  fecha: string
  tipo: TipoMovimiento
  cantidad: number
  fisicoAntes: number
  fisicoDespues: number
  reservadoAntes: number
  reservadoDespues: number
  /** Solo en ENTRADA; null en el resto. */
  costoUnitario: number | null
  varianteId: number
  sku: string
  productoNombre: string
  /** null = movimiento del sistema. */
  usuarioNombre: string | null
  referenciaTipo: string | null
  referenciaId: number | null
  observacion: string | null
}

/** Fechas ISO `YYYY-MM-DD`, ambas inclusivas. Orden fijo en el servidor (`sort` se ignora). */
export interface FiltrosMovimientos extends ParametrosPagina {
  varianteId?: number
  tipo?: TipoMovimiento
  desde?: string
  hasta?: string
}

export type PaginaMovimientos = Pagina<MovimientoResponse>
