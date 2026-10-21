import type { Rol } from '../tipos/auth'
import type { EstadoPlan } from '../tipos/compras'

/** Crear/editar catálogo (categorías, productos, variantes): ADMIN e INVENTARIO (docs/dominio.md). */
export function puedeEditarCatalogo(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO'
}

/** Entradas y ajustes de stock: ADMIN e INVENTARIO. */
export function puedeMoverStock(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO'
}

/** Registrar ventas y devoluciones: ADMIN y VENDEDOR. */
export function puedeVender(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'VENDEDOR'
}

/** Anular ventas: solo ADMIN. */
export function puedeAnularVenta(rol: Rol | undefined): boolean {
  return rol === 'ADMIN'
}

/** Ver el kardex: ADMIN, INVENTARIO y CONTABILIDAD. */
export function puedeVerKardex(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO' || rol === 'CONTABILIDAD'
}

/** Crear, cancelar y extender reservas: ADMIN, INVENTARIO y VENDEDOR. */
export function puedeReservar(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO' || rol === 'VENDEDOR'
}

/** Convertir reservas en venta: ADMIN y VENDEDOR. */
export function puedeConvertirReserva(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'VENDEDOR'
}

/** Atender alertas: ADMIN e INVENTARIO (el VENDEDOR solo las ve). */
export function puedeAtenderAlertas(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO'
}

/** Ver costos: todos los roles menos VENDEDOR. */
export function puedeVerCostos(rol: Rol | undefined): boolean {
  return rol !== undefined && rol !== 'VENDEDOR'
}

/** ABC, punto de reorden y última ejecución: ADMIN, INVENTARIO y CONTABILIDAD (VENDEDOR solo "Más vendidos"). */
export function puedeVerAnaliticaCompleta(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO' || rol === 'CONTABILIDAD'
}

/** Recalcular la analítica: solo ADMIN. */
export function puedeRecalcularAnalitica(rol: Rol | undefined): boolean {
  return rol === 'ADMIN'
}

/** Ver tendencias y pronóstico: ADMIN, INVENTARIO y CONTABILIDAD (VENDEDOR 403). */
export function puedeVerPronostico(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO' || rol === 'CONTABILIDAD'
}

/** Ver planes de compra (con costos): ADMIN, INVENTARIO y CONTABILIDAD. */
export function puedeVerCompras(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO' || rol === 'CONTABILIDAD'
}

/** Crear y editar borradores (sugerido o manual): ADMIN e INVENTARIO. */
export function puedeGestionarPlanes(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO'
}

/** Aprobar un plan: solo ADMIN. */
export function puedeAprobarPlan(rol: Rol | undefined): boolean {
  return rol === 'ADMIN'
}

/** Recibir un plan aprobado (genera entradas): ADMIN e INVENTARIO. */
export function puedeRecibirPlan(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO'
}

/** Cancelar: ADMIN en BORRADOR o APROBADO; INVENTARIO solo descarta BORRADOR. */
export function puedeCancelarPlan(rol: Rol | undefined, estado: EstadoPlan): boolean {
  if (rol === 'ADMIN') return estado === 'BORRADOR' || estado === 'APROBADO'
  if (rol === 'INVENTARIO') return estado === 'BORRADOR'
  return false
}
