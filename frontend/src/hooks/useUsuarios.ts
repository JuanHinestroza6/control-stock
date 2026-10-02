import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { actualizarUsuario, crearUsuario, listarUsuarios } from '../api/usuarios'
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
