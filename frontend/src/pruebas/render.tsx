import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'
import { SesionProvider } from '../hooks/SesionProvider'
import { guardarSesion, type Sesion } from '../lib/auth'
import type { Rol } from '../tipos/auth'

export function crearSesion(rol: Rol, nombre = 'Ana Pérez', debeCambiarPassword = false): Sesion {
  return {
    token: 'token-de-prueba',
    expiraEn: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: { id: 1, nombre, email: 'ana@livorno.co', rol, debeCambiarPassword },
  }
}

interface Opciones {
  ruta?: string
  /** Si se indica, la sesión se guarda en localStorage antes de montar. */
  rol?: Rol
  /** Sesión con contraseña temporal pendiente de cambio. */
  debeCambiarPassword?: boolean
}

/** Renderiza con QueryClient, Router en memoria y proveedor de sesión. */
export function renderizarApp(ui: ReactElement, { ruta = '/', rol, debeCambiarPassword = false }: Opciones = {}) {
  localStorage.clear()
  if (rol) guardarSesion(crearSesion(rol, undefined, debeCambiarPassword))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const resultado = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[ruta]}>
        <SesionProvider>{ui}</SesionProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...resultado, queryClient }
}
