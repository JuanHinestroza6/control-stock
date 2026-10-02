import type { Rol } from '../tipos/auth'

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

/** Ver costos: todos los roles menos VENDEDOR. */
export function puedeVerCostos(rol: Rol | undefined): boolean {
  return rol !== undefined && rol !== 'VENDEDOR'
}
