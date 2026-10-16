import type { TonoBadge } from '../componentes/ui/Badge'
import type { MetodoPronostico, MotivoSinHw, PronosticoProductoResponse } from '../tipos/analitica'

export const METODOS_PRONOSTICO: Record<MetodoPronostico, string> = {
  SES: 'Suavizado simple (SES)',
  HOLT_WINTERS: 'Holt-Winters',
}

export const MOTIVOS_SIN_HW: Record<MotivoSinHw, string> = {
  POCA_HISTORIA: 'Menos de 30 meses de historia',
  MES_EN_CERO: 'Tiene meses sin demanda',
  NIVEL_NO_POSITIVO: 'Nivel de demanda no positivo',
}

/** WAPE (fracción 0-1): hasta 20 % bueno, hasta 40 % aceptable, más es débil. null = sin backtest. */
export function tonoWape(wape: number | null): TonoBadge {
  if (wape === null) return 'neutro'
  if (wape <= 0.2) return 'ok'
  if (wape <= 0.4) return 'alerta'
  return 'critico'
}

export const NOTA_RIESGO_BACKTEST =
  'El backtest evalúa abril a septiembre, por lo que no cubre los picos de noviembre y diciembre: el error real en esas fechas puede ser mayor al WAPE mostrado.'

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
export const formatoCantidad = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 })
const formatoDecimal = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** `2026-03` -> `mar 2026`. */
export function etiquetaMes(periodo: string): string {
  const [anio, mes] = periodo.split('-')
  return `${MESES[Number(mes) - 1] ?? mes} ${anio}`
}

/** Fracción 0-1 como porcentaje (`0.153` -> `15,3 %`). */
export function porcentaje(fraccion: number): string {
  return `${formatoDecimal.format(fraccion * 100)} %`
}

/** Por qué no compitió Holt-Winters, o aviso de producto sin historia; null si no hay nada que explicar. */
export function textoMotivo(p: Pick<PronosticoProductoResponse, 'metodo' | 'motivoSinHw'>): string | null {
  if (p.metodo === null) return 'Sin historia suficiente: se usa la demanda histórica'
  return p.motivoSinHw ? `Sin Holt-Winters: ${MOTIVOS_SIN_HW[p.motivoSinHw].toLowerCase()}` : null
}
