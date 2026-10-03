import type { TonoBadge } from '../componentes/ui/Badge'
import type { InventarioItemResponse } from '../tipos/inventario'
import type { CrearReservaRequest } from '../tipos/reservas'

const HORA_MS = 3_600_000
const UMBRAL_POR_VENCER_H = 12

function duracion(ms: number): string {
  const horas = Math.floor(ms / HORA_MS)
  const minutos = Math.floor((ms % HORA_MS) / 60_000)
  if (horas >= 24) return `${Math.floor(horas / 24)} d ${horas % 24} h`
  if (horas > 0) return `${horas} h ${minutos} min`
  return `${Math.max(minutos, 1)} min`
}

/** Texto y tono del vencimiento de una reserva activa: `critico` si ya pasó, `alerta` si falta menos de 12 h. */
export function vencimientoRelativo(venceEn: string, ahora: Date = new Date()): { texto: string; tono: TonoBadge } {
  const diff = new Date(venceEn).getTime() - ahora.getTime()
  if (Number.isNaN(diff)) return { texto: '', tono: 'neutro' }
  if (diff < 0) return { texto: `Venció hace ${duracion(-diff)}`, tono: 'critico' }
  return { texto: `Vence en ${duracion(diff)}`, tono: diff < UMBRAL_POR_VENCER_H * HORA_MS ? 'alerta' : 'neutro' }
}

const DIA_MS = 24 * HORA_MS

/** Horas máximas que se pueden extender ahora: sin pasar de 168 ni de `creadaEn` + 7 días (puede ser 0). */
export function horasMaximasExtension(creadaEn: string, ahora: Date = new Date()): number {
  const limite = new Date(creadaEn).getTime() + 7 * DIA_MS
  const horas = Math.floor((limite - ahora.getTime()) / HORA_MS)
  return Number.isNaN(horas) ? 168 : Math.max(0, Math.min(168, horas))
}

export const MAX_LINEAS_RESERVA = 20
export const MAX_CANTIDAD_RESERVA = 100

export interface LineaReserva {
  variante: InventarioItemResponse
  /** Texto escrito por el usuario. */
  cantidad: string
}

export interface ErroresReserva {
  clienteNombre?: string
  clienteContacto?: string
  vigenciaHoras?: string
  general?: string
  /** Por varianteId. */
  lineas: Record<number, string>
}

const normalizar = (t: string) => t.trim().replace(/\s+/g, ' ')

/** Suma 1 si la variante ya está; si no, agrega una línea (límite de 20). */
export function agregarLineaReserva(
  lineas: LineaReserva[],
  variante: InventarioItemResponse,
): { lineas: LineaReserva[]; error?: string } {
  const i = lineas.findIndex((l) => l.variante.varianteId === variante.varianteId)
  if (i >= 0) {
    const actual = Number(lineas[i].cantidad)
    const siguiente = Number.isInteger(actual) && actual >= 0 ? actual + 1 : 1
    return { lineas: lineas.map((l, j) => (j === i ? { ...l, cantidad: String(siguiente) } : l)) }
  }
  if (lineas.length >= MAX_LINEAS_RESERVA) return { lineas, error: `Máximo ${MAX_LINEAS_RESERVA} líneas por apartado.` }
  return { lineas: [...lineas, { variante, cantidad: '1' }] }
}

export interface ResultadoReserva {
  solicitud?: CrearReservaRequest
  errores: ErroresReserva
}

/** Valida en cliente y arma la solicitud; el backend sigue siendo la validación definitiva. */
export function construirReserva(
  nombre: string,
  contacto: string,
  vigencia: string,
  lineas: LineaReserva[],
): ResultadoReserva {
  const errores: ErroresReserva = { lineas: {} }
  const clienteNombre = normalizar(nombre)
  const clienteContacto = normalizar(contacto)
  if (clienteNombre.length < 2 || clienteNombre.length > 120) errores.clienteNombre = 'Entre 2 y 120 caracteres'
  if (clienteContacto.length < 3 || clienteContacto.length > 120) errores.clienteContacto = 'Entre 3 y 120 caracteres'
  const v = vigencia.trim()
  let vigenciaHoras: number | undefined
  if (v !== '') {
    vigenciaHoras = /^\d+$/.test(v) ? Number(v) : NaN
    if (!(vigenciaHoras >= 1 && vigenciaHoras <= 168)) errores.vigenciaHoras = 'Entero entre 1 y 168 horas'
  }
  if (lineas.length === 0) errores.general = 'Agrega al menos una variante al apartado.'
  for (const l of lineas) {
    const t = l.cantidad.trim()
    const c = /^\d+$/.test(t) ? Number(t) : NaN
    if (!(c >= 1 && c <= MAX_CANTIDAD_RESERVA)) errores.lineas[l.variante.varianteId] = `Entero entre 1 y ${MAX_CANTIDAD_RESERVA}`
    else if (c > l.variante.stockDisponible) errores.lineas[l.variante.varianteId] = `Solo hay ${l.variante.stockDisponible} disponibles`
  }
  const hayErrores =
    errores.clienteNombre || errores.clienteContacto || errores.vigenciaHoras || errores.general || Object.keys(errores.lineas).length > 0
  if (hayErrores) return { errores }
  return {
    errores,
    solicitud: {
      clienteNombre,
      clienteContacto,
      ...(vigenciaHoras !== undefined ? { vigenciaHoras } : {}),
      lineas: lineas.map((l) => ({ varianteId: l.variante.varianteId, cantidad: Number(l.cantidad) })),
    },
  }
}
