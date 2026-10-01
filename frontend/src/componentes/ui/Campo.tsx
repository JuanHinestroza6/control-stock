import { forwardRef, useId, type InputHTMLAttributes } from 'react'

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  etiqueta: string
  error?: string
}

export const Campo = forwardRef<HTMLInputElement, CampoProps>(function Campo(
  { etiqueta, error, id, className = '', ...resto },
  ref,
) {
  const idGenerado = useId()
  const idCampo = id ?? idGenerado
  const idError = `${idCampo}-error`

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={idCampo} className="text-xs font-medium text-oxford">
        {etiqueta}
      </label>
      <input
        ref={ref}
        id={idCampo}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? idError : undefined}
        className={`min-h-10 rounded-lg border bg-fondo px-3 py-2 text-sm text-oxford outline-none focus:border-acento focus:ring-1 focus:ring-acento disabled:bg-fondo-suave ${error ? 'border-critico' : 'border-oxford/20'} ${className}`}
        {...resto}
      />
      {error && (
        <p id={idError} role="alert" className="text-xs font-medium text-critico">
          {error}
        </p>
      )}
    </div>
  )
})
