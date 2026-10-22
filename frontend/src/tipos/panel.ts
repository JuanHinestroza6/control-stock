/** Tipos del panel de inicio (`PanelResponse`, ADR-011). Las secciones no permitidas para el rol llegan null. */

export interface PuntoDia {
  /** `YYYY-MM-DD`. */
  fecha: string
  /** Ingreso neto por fecha de venta (igual que ventasHoy y ventasMes). */
  total: number
  numeroVentas: number
}

export interface ResumenVentas {
  ventasHoy: number
  numeroVentasHoy: number
  ventasMes: number
  numeroVentasMes: number
  /** Solo ADMIN y CONTABILIDAD; null para VENDEDOR. */
  margenMes: number | null
  margenMesPorcentaje: number | null
  /** 7 puntos ascendentes terminando hoy. */
  ultimos7Dias: PuntoDia[]
}

export interface ResumenInventario {
  variantesActivas: number
  unidadesFisicas: number
  unidadesDisponibles: number
  alertasAgotado: number
  alertasStockBajo: number
  alertasSinRotacion: number
  /** Solo ADMIN y CONTABILIDAD. */
  valorCosto: number | null
  valorVenta: number | null
}

export interface ResumenReservas {
  activas: number
  unidadesReservadas: number
  porVencer24h: number
}

export interface ResumenCompras {
  planesBorrador: number
  planesEnCamino: number
  unidadesEnCamino: number
  planesAtrasados: number
  /** Solo ADMIN y CONTABILIDAD; null para INVENTARIO. */
  valorEnCamino: number | null
}

export interface PanelResponse {
  generadoEn: string
  /** ADMIN, VENDEDOR y CONTABILIDAD. */
  ventas: ResumenVentas | null
  /** Todos los roles. */
  inventario: ResumenInventario | null
  /** ADMIN, INVENTARIO y VENDEDOR. */
  reservas: ResumenReservas | null
  /** ADMIN, INVENTARIO y CONTABILIDAD. */
  compras: ResumenCompras | null
}
