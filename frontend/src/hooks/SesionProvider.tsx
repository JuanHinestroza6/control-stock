import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { alExpirarSesion, alRequerirCambioPassword } from '../api/cliente'
import { CLAVE_SESION, borrarSesion, guardarSesion, leerSesion, type Sesion } from '../lib/auth'
import type { EstadoLogin } from '../tipos/navegacion'
import { ContextoSesion, type SesionContexto } from './sesionContexto'

/** Mantiene la sesión en estado y escucha la expiración emitida por el cliente Axios (401). Requiere un Router. */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(() => leerSesion())
  const [cierreVoluntario, setCierreVoluntario] = useState(false)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const ubicacion = useLocation()
  const rutaActual = useRef('')
  const rutaEnUso = ubicacion.pathname + ubicacion.search
  useEffect(() => {
    rutaActual.current = rutaEnUso
  }, [rutaEnUso])

  const iniciar = useCallback((nueva: Sesion) => {
    guardarSesion(nueva)
    setSesion(nueva)
    setCierreVoluntario(false)
  }, [])

  const cerrar = useCallback(() => {
    borrarSesion()
    setCierreVoluntario(true)
    setSesion(null)
    queryClient.clear()
    navigate('/login', { replace: true })
  }, [navigate, queryClient])

  useEffect(
    () =>
      alExpirarSesion(() => {
        setCierreVoluntario(false)
        setSesion(null)
        queryClient.clear()
        const estado: EstadoLogin = { desde: rutaActual.current }
        navigate('/login', { replace: true, state: estado })
      }),
    [navigate, queryClient],
  )

  useEffect(
    () =>
      alRequerirCambioPassword((marcada) => {
        setSesion(marcada)
        navigate('/cuenta', { replace: true })
      }),
    [navigate],
  )

  // Sincroniza entre pestañas: cerrar sesión (o iniciarla) en una pestaña se refleja en las demás.
  useEffect(() => {
    const alCambiarAlmacenamiento = (e: StorageEvent) => {
      if (e.key !== null && e.key !== CLAVE_SESION) return
      const actual = leerSesion()
      setSesion(actual)
      if (actual === null) {
        setCierreVoluntario(true)
        queryClient.clear()
        navigate('/login', { replace: true })
      } else {
        setCierreVoluntario(false)
      }
    }
    window.addEventListener('storage', alCambiarAlmacenamiento)
    return () => window.removeEventListener('storage', alCambiarAlmacenamiento)
  }, [navigate, queryClient])

  const valor = useMemo<SesionContexto>(
    () => ({ sesion, usuario: sesion?.usuario ?? null, cierreVoluntario, iniciar, cerrar }),
    [sesion, cierreVoluntario, iniciar, cerrar],
  )

  return <ContextoSesion.Provider value={valor}>{children}</ContextoSesion.Provider>
}
