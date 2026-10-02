import type { Rol } from '../tipos/auth'

/** Crear/editar catálogo (categorías, productos, variantes): ADMIN e INVENTARIO (docs/dominio.md). */
export function puedeEditarCatalogo(rol: Rol | undefined): boolean {
  return rol === 'ADMIN' || rol === 'INVENTARIO'
}
