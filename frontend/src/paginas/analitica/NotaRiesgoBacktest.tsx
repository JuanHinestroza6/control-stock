import { NOTA_RIESGO_BACKTEST } from '../../lib/pronostico'

/** Aviso del riesgo aceptado del backtest (ADR-010). */
export function NotaRiesgoBacktest() {
  return (
    <p role="note" className="rounded-lg border border-alerta/50 bg-alerta/10 p-3 text-xs text-oxford">
      <strong>Ten en cuenta:</strong> {NOTA_RIESGO_BACKTEST}
    </p>
  )
}
