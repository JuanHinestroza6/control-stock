import { useMutation } from '@tanstack/react-query'
import { cambiarPassword } from '../api/auth'
import { sesionDesdeLogin } from '../lib/auth'
import type { CambiarPasswordRequest, LoginResponse } from '../tipos/auth'
import { useSesion } from './useSesion'

/** Cambia la contraseña propia y guarda la sesión nueva (el token anterior queda revocado). */
export function useCambiarPassword() {
  const { iniciar } = useSesion()
  return useMutation<LoginResponse, unknown, CambiarPasswordRequest>({
    mutationFn: (datos) => cambiarPassword(datos),
    onSuccess: (respuesta) => iniciar(sesionDesdeLogin(respuesta)),
  })
}
