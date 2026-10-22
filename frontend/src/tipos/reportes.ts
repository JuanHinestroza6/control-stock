/** Tipos que reflejan los DTOs de `reporte.dto` del backend (ADR-011). Fechas ISO como string; BigDecimal como number. */

export type AgrupacionReporte = 'CATEGORIA' | 'PRODUCTO' | 'VARIANTE'
export type CriterioRentabilidad = 'MARGEN' | 'INGRESO' | 'UNIDADES'
export type PeriodoLibro = 'DIA' | 'MES'
export type FormatoExportacion = 'PDF' | 'XLSX'

/** `desde`/`hasta`: `YYYY-MM-DD`, inclusivos. Los omitidos toman el valor por defecto del servidor. */
export interface FiltrosRentabilidad {
  desde?: string
  hasta?: string
  categoriaId?: number
  agrupacion?: AgrupacionReporte
  orden?: CriterioRentabilidad
}

export interface FiltrosLibroVentas {
  desde?: string
  hasta?: string
  agrupacion?: PeriodoLibro
}

export interface FiltrosValorizacion {
  categoriaId?: number
  agrupacion?: AgrupacionReporte
  incluirInactivas?: boolean
}

export interface FilaRentabilidad {
  /** Id de la categoría, producto o variante según la agrupación. */
  id: number
  /** Código del producto o SKU; null al agrupar por categoría. */
  codigo: string | null
  nombre: string
  /** Null al agrupar por categoría. */
  categoria: string | null
  ventasLista: number
  descuentos: number
  facturado: number
  devoluciones: number
  ingresoNeto: number
  costoVentas: number
  margenBruto: number
  /** Escala 1; null si el ingreso neto es 0. */
  margenPorcentaje: number | null
  mermaNoApta: number
  resultadoDespuesMermas: number
  unidadesVendidas: number
  unidadesDevueltas: number
  unidadesNetas: number
  /** Escala 1, % del margen bruto total; null si el margen total es 0. */
  participacionMargen: number | null
}

export interface TotalesRentabilidad {
  ventasLista: number
  descuentos: number
  facturado: number
  devoluciones: number
  ingresoNeto: number
  costoVentas: number
  margenBruto: number
  margenPorcentaje: number | null
  mermaNoApta: number
  resultadoDespuesMermas: number
  unidadesVendidas: number
  unidadesDevueltas: number
  unidadesNetas: number
}

export interface RentabilidadResponse {
  desde: string
  hasta: string
  categoriaId: number | null
  categoria: string | null
  agrupacion: AgrupacionReporte
  orden: CriterioRentabilidad
  sinVentas: boolean
  /** Null si hay ventas. */
  mensaje: string | null
  filas: FilaRentabilidad[]
  totales: TotalesRentabilidad
  notas: string[]
  generadoEn: string
}

/** `periodo`: `YYYY-MM-DD` (DIA) o `YYYY-MM` (MES); null en la fila de totales. Los netos pueden ser negativos. */
export interface FilaLibro {
  periodo: string | null
  numeroVentas: number
  ventasLista: number
  descuentos: number
  facturado: number
  devoluciones: number
  ingresoNeto: number
  costoVentas: number
  margenBruto: number
  mermaNoApta: number
  unidadesVendidas: number
  unidadesDevueltas: number
}

export interface VentaAnuladaItem {
  ventaId: number
  /** `V-000123`. */
  numero: string
  fecha: string
  total: number
  anuladaEn: string
  anuladaPor: string
  motivoAnulacion: string
}

export interface LibroVentasResponse {
  desde: string
  hasta: string
  agrupacion: PeriodoLibro
  sinVentas: boolean
  /** Null si hay ventas. */
  mensaje: string | null
  filas: FilaLibro[]
  totales: FilaLibro
  anuladas: VentaAnuladaItem[]
  totalAnuladas: number
  montoAnuladas: number
  /** Líneas de venta del rango (el detalle solo se exporta a Excel, máximo 50.000). */
  lineasDetalle: number
  notas: string[]
  generadoEn: string
}

export interface FilaValorizacion {
  /** Id de la categoría, producto o variante; null en la fila de totales. */
  id: number | null
  codigo: string | null
  nombre: string
  categoria: string | null
  unidadesFisicas: number
  unidadesReservadas: number
  valorCosto: number
  valorVenta: number
  margenPotencial: number
  /** Escala 1, sobre el valor a precio de venta; null si ese valor es 0. */
  margenPotencialPorcentaje: number | null
  /** Informativos: no suman al valor del inventario. */
  unidadesEnCamino: number
  valorEnCamino: number
}

export interface ValorizacionResponse {
  categoriaId: number | null
  categoria: string | null
  agrupacion: AgrupacionReporte
  incluirInactivas: boolean
  sinExistencias: boolean
  /** Null si hay existencias. */
  mensaje: string | null
  filas: FilaValorizacion[]
  totales: FilaValorizacion
  notas: string[]
  generadoEn: string
}
