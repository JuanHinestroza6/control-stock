/** Metadatos de página de Spring Data (serialización VIA_DTO). */
export interface MetaPagina {
  size: number
  /** Número de página, desde 0. */
  number: number
  totalElements: number
  totalPages: number
}

/** Respuesta paginada del backend. */
export interface Pagina<T> {
  content: T[]
  page: MetaPagina
}

/** Parámetros de paginación y orden que aceptan los listados (`sort=campo,asc|desc`). */
export interface ParametrosPagina {
  page?: number
  size?: number
  sort?: string
}
