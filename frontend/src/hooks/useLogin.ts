import { useMutation } from '@tanstack/react-query'
import { login } from '../api/auth'
import { sesionDesdeLogin } from '../lib/auth'
import type { LoginRequest, LoginResponse } from '../tipos/auth'
import { useSesion } from './useSesion'

export function useLogin() {
  const { iniciar } = useSesion()
  return useMutation<LoginResponse, unknown, LoginRequest>({
    mutationFn: login,
    onSuccess: (respuesta) => iniciar(sesionDesdeLogin(respuesta)),
  })
}
