import type { TipoMovimiento } from '../tipos/inventario'

export const CONFIG_TIPO_MOVIMIENTO: Record<TipoMovimiento, { tono: 'ok' | 'alerta' | 'critico' | 'acento' | 'neutro'; etiqueta: string }> = {
  ENTRADA: { tono: 'ok', etiqueta: 'Entrada' },
  VENTA: { tono: 'acento', etiqueta: 'Venta' },
  VENTA_RESERVA: { tono: 'acento', etiqueta: 'Venta de reserva' },
  DEVOLUCION: { tono: 'ok', etiqueta: 'Devolución' },
  AJUSTE_POSITIVO: { tono: 'alerta', etiqueta: 'Ajuste (+)' },
  AJUSTE_NEGATIVO: { tono: 'alerta', etiqueta: 'Ajuste (-)' },
  RESERVA: { tono: 'neutro', etiqueta: 'Reserva' },
  LIBERACION: { tono: 'neutro', etiqueta: 'Liberación' },
  ANULACION_VENTA: { tono: 'critico', etiqueta: 'Anulación de venta' },
}
