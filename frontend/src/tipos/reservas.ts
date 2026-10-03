import type { Talla } from './catalogo'
import type { Pagina, ParametrosPagina } from './paginacion'

export type EstadoReserva = 'ACTIVA' | 'CONVERTIDA' | 'CANCELADA' | 'VENCIDA'

export interface LineaReservaRequest {
  varianteId: number
  /** 1 a 100. */
  cantidad: number
}

export interface CrearReservaRequest {
  /** 2 a 120 caracteres. */
  clienteNombre: string
  /** 3 a 120 caracteres. */
  clienteContacto: string
  /** 1 a 168; por defecto el servidor usa 48. */
  vigenciaHoras?: number
  /** 1 a 20 líneas, sin variantes repetidas. */
  lineas: LineaReservaRequest[]
}

export interface CancelarReservaRequest {
  /** 5 a 200 caracteres. */
  motivo: string
}

export interface ExtenderReservaRequest {
  /** 1 a 168. */
  horas: number
}

export interface PrecioReservaRequest {
  varianteId: number
  /** > 0 y no mayor que el precio de lista. */
  precioUnitario: number
}

export interface ConvertirReservasRequest {
  /** 1 a 20 reservas ACTIVAS del mismo cliente. */
  reservaIds: number[]
  precios?: PrecioReservaRequest[]
}

export interface ReservaResponse {
  id: number
  /** `R-000012`. */
  numero: string
  varianteId: number
  sku: string
  productoNombre: string
  talla: Talla
  color: string
  cantidad: number
  precioVenta: number
  clienteNombre: string
  clienteContacto: string
  estado: EstadoReserva
  creadaEn: string
  venceEn: string
  creadaPor: string
  cerradaEn: string | null
  /** null = proceso del sistema (vencimiento). */
  cerradaPor: string | null
  motivoCancelacion: string | null
  ventaId: number | null
  ventaNumero: string | null
}

/** Orden permitido: `venceEn`, `creadaEn` (por defecto `venceEn,asc`). */
export interface FiltrosReservas extends ParametrosPagina {
  estado?: EstadoReserva
  /** Máx. 60: nombre o contacto del cliente y SKU. */
  q?: string
  varianteId?: number
  /** ACTIVA con vencimiento en menos de 12 h. */
  porVencer?: boolean
}

export type PaginaReservas = Pagina<ReservaResponse>
