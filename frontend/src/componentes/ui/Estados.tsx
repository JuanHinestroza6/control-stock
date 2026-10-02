import type { ReactNode } from 'react'
import { Boton } from './Boton'

/** Estado cargando: skeleton accesible con `filas` líneas. */
export function EstadoCarga({ filas = 5 }: { filas?: number }) {
  return (
    <div role="status" aria-busy="true" aria-label="Cargando" className="flex flex-col gap-2">
      {Array.from({ length: filas }, (_, i) => (
        <div key={i} className="h-10 animate-pulse rounded-lg bg-oxford/10" />
      ))}
    </div>
  )
}

interface EstadoErrorProps {
  mensaje: string
  onReintentar?: () => void
}

export function EstadoError({ mensaje, onReintentar }: EstadoErrorProps) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-lg border border-critico/40 bg-critico/5 p-6 text-center">
      <p className="text-sm font-medium text-oxford">{mensaje}</p>
      {onReintentar && (
        <Boton variante="secundario" onClick={onReintentar}>
          Reintentar
        </Boton>
      )}
    </div>
  )
}

interface EstadoVacioProps {
  mensaje: string
  /** Acción sugerida (p. ej. un botón "Crear producto"). */
  accion?: ReactNode
}

export function EstadoVacio({ mensaje, accion }: EstadoVacioProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-oxford/20 bg-fondo p-8 text-center">
      <p className="text-sm text-oxford/70">{mensaje}</p>
      {accion}
    </div>
  )
}
