import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { PuntoDia } from '../../tipos/panel'

interface Props {
  puntos: PuntoDia[]
  formatoValor: (valor: number) => string
  descripcion: string
}

/** `2026-10-21` -> `21/10`. */
function etiquetaDia(fecha: string): string {
  const [, mes, dia] = fecha.split('-')
  return `${dia}/${mes}`
}

/** Barras del ingreso neto de los últimos 7 días, en acento. */
export default function GraficoVentas7Dias({ puntos, formatoValor, descripcion }: Props) {
  return (
    <div role="img" aria-label={descripcion} className="h-60 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={puntos} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <CartesianGrid stroke="var(--color-oxford)" strokeOpacity={0.1} vertical={false} />
          <XAxis dataKey="fecha" tickFormatter={etiquetaDia} tick={{ fontSize: 12 }} />
          <YAxis tickFormatter={(v: number) => formatoValor(v)} tick={{ fontSize: 12 }} width={90} />
          <Tooltip labelFormatter={(f) => etiquetaDia(String(f))} formatter={(v) => [formatoValor(Number(v)), 'Ingreso neto']} />
          <Bar dataKey="total" name="Ingreso neto" fill="var(--color-acento)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
