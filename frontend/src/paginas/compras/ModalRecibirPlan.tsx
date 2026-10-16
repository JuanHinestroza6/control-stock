import { useMemo, useState } from 'react'
import { Boton } from '../../componentes/ui/Boton'
import { Modal } from '../../componentes/ui/Modal'
import { useRecibirPlan } from '../../hooks/useCompras'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import type { PlanCompraDetalleResponse } from '../../tipos/compras'

const MAX_CANTIDAD = 100_000

interface ModalRecibirPlanProps {
  plan: PlanCompraDetalleResponse
  onCerrar: () => void
}

const CLASE_INPUT =
  'min-h-10 w-full rounded-lg border bg-fondo px-3 py-2 text-sm text-oxford outline-none focus:border-acento focus:ring-1 focus:ring-acento'

function numeroEntero(texto: string): number | null {
  const t = texto.trim()
  if (!/^\d+$/.test(t)) return null
  return Number(t)
}

function numeroCosto(texto: string): number | null {
  const t = texto.trim().replace(',', '.')
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null
  return Number(t)
}

/**
 * Recepción única de un plan APROBADO. Un solo costo por producto (regla de entradas) y una cantidad por línea.
 * Lo recibido de más se acepta; las líneas en 0 dejan de estar en camino.
 */
export function ModalRecibirPlan({ plan, onCerrar }: ModalRecibirPlanProps) {
  const recibir = useRecibirPlan(plan.id)
  const [cantidades, setCantidades] = useState<Record<number, string>>(() =>
    Object.fromEntries(plan.lineas.map((l) => [l.id, String(l.cantidadAprobada)])),
  )
  const productos = useMemo(() => {
    const mapa = new Map<number, { id: number; codigo: string; nombre: string; costoDefecto: number }>()
    for (const l of plan.lineas) {
      if (!mapa.has(l.productoId)) {
        mapa.set(l.productoId, { id: l.productoId, codigo: l.productoCodigo, nombre: l.productoNombre, costoDefecto: l.costoEstimado })
      }
    }
    return [...mapa.values()]
  }, [plan.lineas])
  const [costos, setCostos] = useState<Record<number, string>>(() =>
    Object.fromEntries(productos.map((p) => [p.id, String(p.costoDefecto)])),
  )
  const [observacion, setObservacion] = useState('')
  const [intento, setIntento] = useState(false)

  const cantidadDe = (id: number) => numeroEntero(cantidades[id] ?? '')
  const errorCantidad = (id: number) => {
    const n = cantidadDe(id)
    return n === null || n > MAX_CANTIDAD ? 'Entero entre 0 y 100.000' : undefined
  }
  const errorCosto = (productoId: number) => (numeroCosto(costos[productoId] ?? '') === null ? 'Costo válido (0 o más)' : undefined)

  const hayErrores = plan.lineas.some((l) => errorCantidad(l.id)) || productos.some((p) => errorCosto(p.id))
  const todasEnCero = !hayErrores && plan.lineas.every((l) => cantidadDe(l.id) === 0)

  const resumen = useMemo(() => {
    let unidades = 0
    let total = 0
    let enCero = 0
    for (const l of plan.lineas) {
      const n = numeroEntero(cantidades[l.id] ?? '')
      const c = numeroCosto(costos[l.productoId] ?? '')
      if (n === null || n > MAX_CANTIDAD) continue
      unidades += n
      if (c !== null) total += n * c
      if (n === 0) enCero += 1
    }
    return { unidades, total, enCero }
  }, [plan.lineas, cantidades, costos])

  const inactivas = plan.lineas.filter((l) => !l.varianteActiva)
  const errorServidor = recibir.isError ? mensajeError(recibir.error) : undefined

  const enviar = () => {
    setIntento(true)
    if (hayErrores || todasEnCero) return
    recibir.mutate(
      {
        observacion: observacion.trim() || undefined,
        lineas: plan.lineas.map((l) => ({
          detalleId: l.id,
          cantidadRecibida: cantidadDe(l.id) ?? 0,
          costoUnitario: numeroCosto(costos[l.productoId] ?? '') ?? 0,
        })),
      },
      { onSuccess: onCerrar },
    )
  }

  return (
    <Modal abierto titulo={`Recibir plan ${plan.numero}`} onCerrar={onCerrar} bloqueado={recibir.isPending}>
      <form
        noValidate
        aria-label="Recibir plan"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">
          La recepción es única: cada línea genera una entrada de inventario. Lo que ponga en 0 deja de estar en camino. El costo se
          aplica a todas las líneas del mismo producto.
        </p>
        {errorServidor && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {errorServidor}
          </p>
        )}
        {inactivas.length > 0 && (
          <p role="status" className="rounded-lg border border-alerta/50 bg-alerta/10 p-3 text-sm">
            Variantes inactivas ({inactivas.map((l) => l.sku).join(', ')}): la mercancía ya pedida se recibe igual.
          </p>
        )}

        <div className="flex flex-col gap-3">
          {productos.map((p) => {
            const lineas = plan.lineas.filter((l) => l.productoId === p.id)
            const e = errorCosto(p.id)
            return (
              <fieldset key={p.id} className="flex flex-col gap-2 rounded-lg border border-oxford/10 p-3">
                <legend className="px-1 text-sm font-medium">
                  {p.codigo} · {p.nombre}
                </legend>
                <div className="flex flex-col gap-1 sm:max-w-xs">
                  <label htmlFor={`costo-${p.id}`} className="text-xs font-medium text-oxford">
                    Costo unitario de {p.codigo}
                  </label>
                  <input
                    id={`costo-${p.id}`}
                    inputMode="decimal"
                    value={costos[p.id] ?? ''}
                    aria-invalid={e ? true : undefined}
                    className={`${CLASE_INPUT} ${e ? 'border-critico' : 'border-oxford/20'}`}
                    onChange={(ev) => setCostos((c) => ({ ...c, [p.id]: ev.target.value }))}
                  />
                  {e && (
                    <p role="alert" className="text-xs font-medium text-critico">
                      {e}
                    </p>
                  )}
                </div>
                <ul className="flex flex-col gap-2">
                  {lineas.map((l) => {
                    const ec = errorCantidad(l.id)
                    return (
                      <li key={l.id} className="grid items-start gap-2 sm:grid-cols-[1fr_9rem]">
                        <div className="text-sm">
                          <span className="font-medium">{l.sku}</span>
                          <span className="block text-xs text-oxford/70">
                            {l.talla} · {l.color} · Pedido: {l.cantidadAprobada} u.{!l.varianteActiva && ' · Inactiva'}
                          </span>
                        </div>
                        <div>
                          <input
                            aria-label={`Cantidad recibida de ${l.sku}`}
                            inputMode="numeric"
                            value={cantidades[l.id] ?? ''}
                            aria-invalid={ec ? true : undefined}
                            className={`${CLASE_INPUT} ${ec ? 'border-critico' : 'border-oxford/20'}`}
                            onChange={(ev) => setCantidades((c) => ({ ...c, [l.id]: ev.target.value }))}
                          />
                          {ec && (
                            <p role="alert" className="text-xs font-medium text-critico">
                              {ec}
                            </p>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              </fieldset>
            )
          })}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="obs-recibir" className="text-xs font-medium text-oxford">
            Observación (opcional)
          </label>
          <input
            id="obs-recibir"
            maxLength={255}
            value={observacion}
            className={`${CLASE_INPUT} border-oxford/20`}
            onChange={(e) => setObservacion(e.target.value)}
          />
        </div>

        {intento && todasEnCero && (
          <p role="alert" className="text-sm font-medium text-critico">
            Todas las líneas están en 0: no hay nada que recibir. Use Cancelar plan.
          </p>
        )}

        <p aria-label="Resumen de la recepción" className="rounded-lg bg-fondo-suave p-3 text-sm">
          Se recibirán <strong>{resumen.unidades}</strong> unidad(es) por <strong>{formatearMoneda(resumen.total)}</strong>.
          {resumen.enCero > 0 && ` ${resumen.enCero} línea(s) en 0 dejarán de estar en camino.`}
        </p>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={recibir.isPending} onClick={onCerrar}>
            Volver
          </Boton>
          <Boton type="submit" cargando={recibir.isPending}>
            {recibir.isError ? 'Volver a confirmar' : 'Confirmar recepción'}
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
