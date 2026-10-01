import { useContext } from 'react'
import { ContextoSesion, type SesionContexto } from './sesionContexto'

export function useSesion(): SesionContexto {
  const contexto = useContext(ContextoSesion)
  if (!contexto) throw new Error('useSesion debe usarse dentro de <SesionProvider>')
  return contexto
}
