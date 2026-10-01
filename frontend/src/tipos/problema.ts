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
  errores?: ErrorCampo[]
}
