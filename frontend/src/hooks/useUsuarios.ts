import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  actualizarUsuario,
  cambiarEstadoUsuario,
  crearUsuario,
  listarUsuarios,
  restablecerPassword,
} from '../api/usuarios'
import type {
  ActualizarUsuarioRequest,
  CrearUsuarioRequest,
  FiltrosUsuarios,
  PaginaUsuarios,
  UsuarioResponse,
} from '../tipos/usuarios'

export function useUsuarios(filtros: FiltrosUsuarios) {
  return useQuery<PaginaUsuarios>({
    queryKey: ['usuarios', filtros],
    queryFn: () => listarUsuarios(filtros),
    placeholderData: keepPreviousData,
  })
}

export function useCrearUsuario() {
  const qc = useQueryClient()
  return useMutation<UsuarioResponse, unknown, CrearUsuarioRequest>({
    mutationFn: (datos) => crearUsuario(datos),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

export function useActualizarUsuario() {
  const qc = useQueryClient()
  return useMutation<UsuarioResponse, unknown, { id: number; datos: ActualizarUsuarioRequest }>({
    mutationFn: ({ id, datos }) => actualizarUsuario(id, datos),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

export function useCambiarEstadoUsuario() {
  const qc = useQueryClient()
  return useMutation<UsuarioResponse, unknown, { id: number; activo: boolean }>({
    mutationFn: ({ id, activo }) => cambiarEstadoUsuario(id, activo),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

export function useRestablecerPassword() {
  const qc = useQueryClient()
  return useMutation<void, unknown, { id: number; passwordTemporal: string }>({
    mutationFn: ({ id, passwordTemporal }) => restablecerPassword(id, passwordTemporal),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}
