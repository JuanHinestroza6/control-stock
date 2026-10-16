import type { ClaseAbc } from './analitica'
import type { Talla } from './catalogo'
import type { Pagina, ParametrosPagina } from './paginacion'

export type EstadoPlan = 'BORRADOR' | 'APROBADO' | 'RECIBIDO' | 'CANCELADO'
export type OrigenPlan = 'SUGERIDO' | 'MANUAL'

export interface FiltrosPlanes extends ParametrosPagina {
  estado?: EstadoPlan
}

export interface PlanCompraResumenResponse {
  id: number
  /** Visible, p. ej. `PC-000012`. */
  numero: string
  estado: EstadoPlan
  origen: OrigenPlan
  creadoEn: string
  creadoPor: string | null
  aprobadoEn: string | null
  aprobadoPor: string | null
  recibidoEn: string | null
  lineas: number
  unidades: number
  costoEstimadoTotal: number
  /** `aprobadoEn + max(leadTimeDias)`; null si no está aprobado. */
  llegadaEsperada: string | null
  atrasado: boolean
}

export interface LineaPlanResponse {
  id: number
  varianteId: number
  sku: string
  productoId: number
  productoCodigo: string
  productoNombre: string
  talla: Talla
  color: string
  claseAbc: ClaseAbc | null
  /** null en líneas agregadas a mano. */
  cantidadSugerida: number | null
  cantidadAprobada: number
  costoEstimado: number
  subtotal: number
  justificacion: string | null
  cantidadRecibida: number | null
  costoRecibido: number | null
  disponibleActual: number
  enCaminoActual: number
  varianteActiva: boolean
}

/** En el detalle `lineas` pasa de conteo (resumen) a arreglo de líneas. */
export interface PlanCompraDetalleResponse extends Omit<PlanCompraResumenResponse, 'lineas'> {
  /** Para el control de concurrencia optimista (409 si está vieja). */
  version: number
  observacion: string | null
  motivoCancelacion: string | null
  canceladoEn: string | null
  canceladoPor: string | null
  /** SKUs omitidos por estar ya en otro borrador; solo al sugerir. */
  omitidas: string[] | null
  lineas: LineaPlanResponse[]
}

export type PaginaPlanes = Pagina<PlanCompraResumenResponse>

export interface SugerirPlanRequest {
  categoriaId?: number
  observacion?: string
}

export interface LineaPlanRequest {
  varianteId: number
  /** 1-100.000. */
  cantidad: number
  /** >= 0; por defecto el `precioCompra` del producto. */
  costoEstimado?: number
}

export interface CrearPlanRequest {
  observacion?: string
  /** 1-300, sin variantes repetidas. */
  lineas: LineaPlanRequest[]
}

export interface ActualizarPlanRequest extends CrearPlanRequest {
  version: number
}

export interface AprobarPlanRequest {
  version: number
}

export interface CancelarPlanRequest {
  /** 5-200 caracteres. */
  motivo: string
}

export interface LineaRecepcionRequest {
  detalleId: number
  /** 0-100.000. */
  cantidadRecibida: number
  costoUnitario: number
}

export interface RecibirPlanRequest {
  observacion?: string
  /** Todas las líneas del plan, exactamente una vez. */
  lineas: LineaRecepcionRequest[]
}
