import type { ButtonHTMLAttributes } from 'react'

type Variante = 'primario' | 'secundario' | 'peligro'

interface BotonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante
  cargando?: boolean
}

const estilos: Record<Variante, string> = {
  primario: 'bg-oxford text-fondo hover:bg-oxford/90 focus-visible:outline-oxford',
  secundario: 'bg-acento text-fondo hover:bg-acento/90 focus-visible:outline-acento',
  peligro: 'bg-critico text-fondo hover:bg-critico/90 focus-visible:outline-critico',
}

export function Boton({
  variante = 'primario',
  cargando = false,
  disabled,
  type = 'button',
  className = '',
  children,
  ...resto
}: BotonProps) {
  return (
    <button
      type={type}
      disabled={disabled || cargando}
      aria-busy={cargando || undefined}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-60 ${estilos[variante]} ${className}`}
      {...resto}
    >
      {cargando && (
        <span
          aria-hidden="true"
          className="size-4 animate-spin rounded-full border-2 border-fondo/40 border-t-fondo"
        />
      )}
      {children}
    </button>
  )
}
