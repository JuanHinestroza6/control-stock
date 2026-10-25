import { Link } from 'react-router'
import { Tarjeta } from '../ui/Tarjeta'

export type TonoIndicador = 'neutro' | 'ok' | 'alerta' | 'critico'

const COLORES: Record<TonoIndicador, string> = {
  neutro: 'text-oxford',
  ok: 'text-ok',
  alerta: 'text-alerta',
  critico: 'text-critico',
}

interface Props {
  titulo: string
  valor: string
  /** Línea secundaria (p. ej. "12 ventas"). */
  detalle?: string
  tono?: TonoIndicador
  /** Si se indica, la tarjeta es un enlace. */
  a?: string
}

/** Indicador numérico del panel: título, valor grande y detalle opcional; enlaza si recibe `a`. */
export function TarjetaIndicador({ titulo, valor, detalle, tono = 'neutro', a }: Props) {
  const cuerpo = (
    <Tarjeta className={`h-full ${a ? 'transition-shadow hover:shadow-md' : ''}`}>
      <p className="text-xs font-medium text-oxford/60">{titulo}</p>
      <p className={`mt-1 font-titulo text-2xl font-bold ${COLORES[tono]}`}>{valor}</p>
      {detalle && <p className="mt-1 text-xs text-oxford/70">{detalle}</p>}
    </Tarjeta>
  )
  if (!a) return cuerpo
  return (
    <Link to={a} className="block rounded-lg focus-visible:outline-2 focus-visible:outline-acento">
      {cuerpo}
    </Link>
  )
}
