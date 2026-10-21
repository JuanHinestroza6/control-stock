import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

/**
 * Envuelve un adaptador falso para que solo atienda `POST /auth/login`. Cualquier otra petición (las consultas de la
 * página a la que se navega después) queda pendiente para siempre: la página se queda en su estado de carga, de forma
 * determinista, en vez de recibir la respuesta del login con una forma que no espera.
 */
export function soloLogin(responder: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>): AxiosAdapter {
  return (config) => (esLogin(config) ? responder(config) : new Promise<AxiosResponse>(() => {}))
}

function esLogin(config: InternalAxiosRequestConfig): boolean {
  return config.method?.toLowerCase() === 'post' && (config.url ?? '').endsWith('/auth/login')
}
