import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export interface PuntoGraficoPronostico {
  periodo: string
  observada?: number | null
  corregida?: number | null
  backtestSes?: number | null
  backtestHw?: number | null
  pronostico?: number | null
}

interface Props {
  puntos: PuntoGraficoPronostico[]
  /** Texto alternativo del gráfico (las tablas vecinas traen los mismos datos). */
  descripcion: string
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
const formatoNumero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 })

function etiquetaPeriodo(periodo: string, larga = false): string {
  const [anio, mes] = periodo.split('-')
  const nombre = MESES[Number(mes) - 1] ?? mes
  return larga ? `${nombre} ${anio}` : `${nombre} ${anio.slice(2)}`
}

/** Demanda mensual: observada en oxford, corregida por agotados, backtest tenue y proyección punteada en acento. */
export default function GraficoPronostico({ puntos, descripcion }: Props) {
  return (
    <div role="img" aria-label={descripcion} className="h-80 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={puntos} margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
          <CartesianGrid stroke="var(--color-oxford)" strokeOpacity={0.1} vertical={false} />
          <XAxis dataKey="periodo" tickFormatter={(p: string) => etiquetaPeriodo(p)} tick={{ fontSize: 12 }} minTickGap={16} />
          <YAxis tickFormatter={(v: number) => formatoNumero.format(v)} tick={{ fontSize: 12 }} width={56} />
          <Tooltip labelFormatter={(p) => etiquetaPeriodo(String(p), true)} formatter={(v) => formatoNumero.format(Number(v))} />
          <Legend />
          <Line type="monotone" dataKey="observada" name="Observada" stroke="var(--color-oxford)" strokeWidth={2} dot={false} connectNulls isAnimationActive={false} />
          <Line
            type="monotone"
            dataKey="corregida"
            name="Corregida por agotados"
            stroke="var(--color-oxford)"
            strokeOpacity={0.55}
            strokeWidth={2}
            strokeDasharray="2 3"
            dot={false}
            connectNulls
            isAnimationActive={false}
          />
          <Line type="monotone" dataKey="backtestSes" name="Backtest SES" stroke="var(--color-alerta)" strokeWidth={1.5} dot={false} connectNulls isAnimationActive={false} />
          <Line type="monotone" dataKey="backtestHw" name="Backtest Holt-Winters" stroke="var(--color-ok)" strokeWidth={1.5} dot={false} connectNulls isAnimationActive={false} />
          <Line
            type="monotone"
            dataKey="pronostico"
            name="Pronóstico"
            stroke="var(--color-acento)"
            strokeWidth={2.5}
            strokeDasharray="6 4"
            dot={{ r: 3 }}
            connectNulls
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
