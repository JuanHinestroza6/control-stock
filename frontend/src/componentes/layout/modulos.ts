import type { Rol } from '../../tipos/auth'

export interface Modulo {
  clave: string
  etiqueta: string
  ruta: string
  descripcion: string
  /** Roles con acceso, según la matriz de permisos de docs/dominio.md. */
  roles: Rol[]
}

const TODOS: Rol[] = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD']

export const MODULOS: Modulo[] = [
  { clave: 'inicio', etiqueta: 'Inicio', ruta: '/', descripcion: 'Resumen general', roles: TODOS },
  {
    clave: 'productos',
    etiqueta: 'Productos',
    ruta: '/productos',
    descripcion: 'Catálogo de productos y variantes (talla x color)',
    roles: TODOS,
  },
  {
    clave: 'inventario',
    etiqueta: 'Inventario',
    ruta: '/inventario',
    descripcion: 'Stock, entradas, ajustes y kardex',
    roles: ['ADMIN', 'INVENTARIO', 'CONTABILIDAD'],
  },
  {
    clave: 'ventas',
    etiqueta: 'Ventas',
    ruta: '/ventas',
    descripcion: 'Registro de ventas y devoluciones',
    roles: ['ADMIN', 'VENDEDOR', 'CONTABILIDAD'],
  },
  {
    clave: 'reservas',
    etiqueta: 'Reservas',
    ruta: '/reservas',
    descripcion: 'Apartados de clientes (stock reservado)',
    roles: ['ADMIN', 'INVENTARIO', 'VENDEDOR'],
  },
  {
    clave: 'alertas',
    etiqueta: 'Alertas',
    ruta: '/alertas',
    descripcion: 'Stock bajo, agotados y sin rotación',
    roles: ['ADMIN', 'INVENTARIO', 'VENDEDOR'],
  },
  {
    clave: 'analitica',
    etiqueta: 'Analítica',
    ruta: '/analitica',
    descripcion: 'Clasificación ABC, punto de reorden y pronóstico',
    roles: TODOS,
  },
  {
    clave: 'compras',
    etiqueta: 'Compras',
    ruta: '/compras',
    descripcion: 'Planes de compra sugeridos',
    roles: ['ADMIN', 'INVENTARIO', 'CONTABILIDAD'],
  },
  {
    clave: 'reportes',
    etiqueta: 'Reportes',
    ruta: '/reportes',
    descripcion: 'Reportes contables y de rentabilidad',
    roles: ['ADMIN', 'CONTABILIDAD'],
  },
  {
    clave: 'usuarios',
    etiqueta: 'Usuarios',
    ruta: '/usuarios',
    descripcion: 'Usuarios y roles',
    roles: ['ADMIN'],
  },
]

export function modulosPermitidos(rol: Rol): Modulo[] {
  return MODULOS.filter((m) => m.roles.includes(rol))
}

/** Indica si el rol puede ver la ruta dada (las rutas que no son de un módulo se consideran permitidas). */
export function rutaPermitida(ruta: string, rol: Rol): boolean {
  const camino = ruta.split(/[?#]/)[0]
  const modulo = MODULOS.find((m) => m.ruta !== '/' && (camino === m.ruta || camino.startsWith(`${m.ruta}/`)))
  return !modulo || modulo.roles.includes(rol)
}

/** La campana de alertas solo aplica a roles con acceso al módulo de alertas. */
export function puedeVerAlertas(rol: Rol): boolean {
  return rutaPermitida('/alertas', rol)
}
