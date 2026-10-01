/** Devuelve una ruta interna segura para redirigir tras el login; cualquier otra cosa (externa, /login) cae a "/". */
export function destinoSeguro(desde: string | undefined): string {
  if (!desde || !desde.startsWith('/')) return '/'
  try {
    const url = new URL(desde, window.location.origin)
    if (url.origin !== window.location.origin) return '/'
    if (url.pathname === '/login' || url.pathname === '/login/') return '/'
    return url.pathname + url.search + url.hash
  } catch {
    return '/'
  }
}
