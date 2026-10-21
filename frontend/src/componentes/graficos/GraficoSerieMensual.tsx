import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export interface PuntoSerieMensual {
  periodo: string
  actual: number
  anterior: number
}

interface Props {
  puntos: PuntoSerieMensual[]
  /** Nombre de la serie actual en la leyenda (p. ej. "Unidades"). */
  etiquetaActual: string
  etiquetaAnterior: string
  formatoValor: (valor: number) => string
  /** Texto alternativo del gráfico (la tabla vecina trae los mismos datos). */
  descripcion: string
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

/** `2026-03` -> `mar 26` (o `mar 2026` si `larga`). */
function etiquetaPeriodo(periodo: string, larga = false): string {
  const [anio, mes] = periodo.split('-')
  const nombre = MESES[Number(mes) - 1] ?? mes
  return larga ? `${nombre} ${anio}` : `${nombre} ${anio.slice(2)}`
}

/** Líneas mensuales: serie actual sólida en acento, mismo mes del año anterior punteado en gris. */
export default function GraficoSerieMensual({ puntos, etiquetaActual, etiquetaAnterior, formatoValor, descripcion }: Props) {
  return (
    <div role="img" aria-label={descripcion} className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={puntos} margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
          <CartesianGrid stroke="var(--color-oxford)" strokeOpacity={0.1} vertical={false} />
          <XAxis dataKey="periodo" tickFormatter={(p: string) => etiquetaPeriodo(p)} tick={{ fontSize: 12 }} minTickGap={16} />
          <YAxis tickFormatter={(v: number) => formatoValor(v)} tick={{ fontSize: 12 }} width={80} />
          <Tooltip labelFormatter={(p) => etiquetaPeriodo(String(p), true)} formatter={(v) => formatoValor(Number(v))} />
          <Legend />
          <Line
            type="monotone"
            dataKey="actual"
            name={etiquetaActual}
            stroke="var(--color-acento)"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="anterior"
            name={etiquetaAnterior}
            stroke="var(--color-oxford)"
            strokeOpacity={0.5}
            strokeWidth={2}
            strokeDasharray="5 4"
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
