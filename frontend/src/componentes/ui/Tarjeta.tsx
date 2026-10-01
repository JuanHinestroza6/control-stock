import type { HTMLAttributes } from 'react'

export function Tarjeta({ className = '', children, ...resto }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={`rounded-lg border border-oxford/10 bg-fondo p-4 shadow-sm ${className}`} {...resto}>
      {children}
    </div>
  )
}
