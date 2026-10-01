import type { Rol } from '../tipos/auth'

export const ETIQUETA_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  INVENTARIO: 'Inventario',
  VENDEDOR: 'Vendedor',
  CONTABILIDAD: 'Contabilidad',
}

export function etiquetaRol(rol: Rol): string {
  return ETIQUETA_ROL[rol] ?? rol
}
