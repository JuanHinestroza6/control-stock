import type { HTMLAttributes } from 'react'

export type TonoBadge = 'ok' | 'alerta' | 'critico' | 'acento' | 'neutro'

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tono?: TonoBadge
}

const estilos: Record<TonoBadge, string> = {
  ok: 'bg-ok/15 text-oxford ring-ok/40',
  alerta: 'bg-alerta/15 text-oxford ring-alerta/50',
  critico: 'bg-critico/15 text-oxford ring-critico/40',
  acento: 'bg-acento/10 text-acento ring-acento/30',
  neutro: 'bg-oxford/5 text-oxford ring-oxford/15',
}

export function Badge({ tono = 'neutro', className = '', children, ...resto }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${estilos[tono]} ${className}`}
      {...resto}
    >
      {children}
    </span>
  )
}
