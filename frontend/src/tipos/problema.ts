export interface ErrorCampo {
  campo: string
  mensaje: string
}

/** Respuesta de error estándar (RFC 9457) del backend. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  /** Código de negocio opcional (p. ej. CAMBIO_PASSWORD_REQUERIDO en 403). */
  codigo?: string
  errores?: ErrorCampo[]
}
