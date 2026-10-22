import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export interface BarraMargen {
  nombre: string
  margen: number
}

interface Props {
  barras: BarraMargen[]
  formatoValor: (valor: number) => string
  /** Texto alternativo (la tabla vecina trae los mismos datos). */
  descripcion: string
}

function recortar(texto: string): string {
  return texto.length > 22 ? `${texto.slice(0, 21)}…` : texto
}

/** Barras horizontales del margen bruto: positivo en acento, negativo en crítico. */
export default function GraficoMargen({ barras, formatoValor, descripcion }: Props) {
  return (
    <div role="img" aria-label={descripcion} style={{ height: Math.max(160, barras.length * 32 + 40) }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={barras} layout="vertical" margin={{ top: 4, right: 16, bottom: 0, left: 8 }}>
          <CartesianGrid stroke="var(--color-oxford)" strokeOpacity={0.1} horizontal={false} />
          <XAxis type="number" tickFormatter={(v: number) => formatoValor(v)} tick={{ fontSize: 12 }} />
          <YAxis type="category" dataKey="nombre" tickFormatter={recortar} tick={{ fontSize: 12 }} width={140} />
          <Tooltip formatter={(v) => formatoValor(Number(v))} />
          <Bar dataKey="margen" name="Margen bruto" isAnimationActive={false}>
            {barras.map((b) => (
              <Cell key={b.nombre} fill={b.margen < 0 ? 'var(--color-critico)' : 'var(--color-acento)'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
