import { isAxiosError } from 'axios'
import { useMemo, useState, type FormEvent } from 'react'
import { BuscadorVariante } from '../../componentes/dominio/BuscadorVariante'
import { Badge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { useActualizarPlan } from '../../hooks/useCompras'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import type { InventarioItemResponse } from '../../tipos/inventario'
import type { PlanCompraDetalleResponse } from '../../tipos/compras'
import { construirLineasPlan, MAX_LINEAS_PLAN, type LineaBorrador } from './textosPlan'

interface LineaEditable extends LineaBorrador {
  sugerida: number | null
  justificacion: string | null
  inactiva: boolean
}

function desdePlan(plan: PlanCompraDetalleResponse): LineaEditable[] {
  return plan.lineas.map((l) => ({
    varianteId: l.varianteId,
    sku: l.sku,
    descripcion: `${l.productoNombre} · ${l.talla} · ${l.color}`,
    cantidad: String(l.cantidadAprobada),
    costo: String(l.costoEstimado),
    sugerida: l.cantidadSugerida,
    justificacion: l.justificacion,
    inactiva: !l.varianteActiva,
  }))
}

function instantanea(lineas: LineaEditable[], observacion: string): string {
  return JSON.stringify([observacion.trim(), lineas.map((l) => [l.varianteId, l.cantidad.trim(), l.costo.trim()])])
}

interface EditorBorradorProps {
  plan: PlanCompraDetalleResponse
  /** Se avisa al padre cuando hay cambios sin guardar (bloquea aprobar). */
  onSucioCambio: (sucio: boolean) => void
  /** Vuelve a cargar el plan desde el servidor (trae la versión vigente). */
  onRecargar: () => void
  /** Descarta lo escrito y reinicia el editor con el plan del servidor. */
  onDescartar: () => void
}

/** Edición del BORRADOR: cantidades, costos, quitar y agregar líneas; guarda con la `version` del plan. */
export function EditorBorrador({ plan, onSucioCambio, onRecargar, onDescartar }: EditorBorradorProps) {
  const actualizar = useActualizarPlan(plan.id)
  const [lineas, setLineas] = useState<LineaEditable[]>(() => desdePlan(plan))
  const [observacion, setObservacion] = useState(plan.observacion ?? '')
  const [base, setBase] = useState(() => instantanea(desdePlan(plan), plan.observacion ?? ''))
  const [errores, setErrores] = useState<Record<number, string>>({})
  const [general, setGeneral] = useState<string>()
  const [guardado, setGuardado] = useState(false)

  const sucio = useMemo(() => instantanea(lineas, observacion) !== base, [lineas, observacion, base])
  const [avisado, setAvisado] = useState(false)
  if (sucio !== avisado) {
    setAvisado(sucio)
    onSucioCambio(sucio)
  }

  const conflicto = actualizar.isError && isAxiosError(actualizar.error) && actualizar.error.response?.status === 409

  const total = lineas.reduce((suma, l) => {
    const c = Number(l.cantidad.trim())
    const k = Number(l.costo.trim().replace(',', '.'))
    return Number.isFinite(c) && Number.isFinite(k) && l.costo.trim() !== '' ? suma + c * k : suma
  }, 0)

  const cambiar = (id: number, campo: 'cantidad' | 'costo', valor: string) => {
    setGuardado(false)
    setLineas((ls) => ls.map((l) => (l.varianteId === id ? { ...l, [campo]: valor } : l)))
  }
  const quitar = (id: number) => {
    setGuardado(false)
    setLineas((ls) => ls.filter((l) => l.varianteId !== id))
  }
  const agregar = (v: InventarioItemResponse) => {
    setGuardado(false)
    setLineas((ls) =>
      ls.some((l) => l.varianteId === v.varianteId) || ls.length >= MAX_LINEAS_PLAN
        ? ls
        : [
            ...ls,
            {
              varianteId: v.varianteId,
              sku: v.sku,
              descripcion: `${v.productoNombre} · ${v.talla} · ${v.color}`,
              cantidad: '',
              costo: '',
              sugerida: null,
              justificacion: null,
              inactiva: false,
            },
          ],
    )
  }

  const enviar = (e: FormEvent) => {
    e.preventDefault()
    const r = construirLineasPlan(lineas)
    setErrores(r.errores)
    setGeneral(r.general)
    if (!r.lineas) return
    const nuevaBase = instantanea(lineas, observacion)
    actualizar.mutate(
      { version: plan.version, lineas: r.lineas, ...(observacion.trim() ? { observacion: observacion.trim() } : {}) },
      {
        onSuccess: () => {
          setBase(nuevaBase)
          setGuardado(true)
        },
      },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <BuscadorVariante
        etiqueta="Agregar variante (busca por SKU, código o nombre)"
        onSeleccionar={agregar}
        excluirIds={lineas.map((l) => l.varianteId)}
      />
      {lineas.length >= MAX_LINEAS_PLAN && <p className="text-sm text-alerta">Llegaste al máximo de {MAX_LINEAS_PLAN} líneas.</p>}

      <form onSubmit={enviar} noValidate aria-label="Editar borrador" className="flex flex-col gap-4">
        {lineas.length === 0 ? (
          <p className="rounded-lg border border-dashed border-oxford/20 bg-fondo p-6 text-center text-sm text-oxford/70">
            El plan no tiene líneas. Busca una variante para agregarla.
          </p>
        ) : (
          <ul aria-label="Líneas del plan" className="flex flex-col gap-3">
            {lineas.map((l) => (
              <li key={l.varianteId} className="flex flex-col gap-3 rounded-lg border border-oxford/10 bg-fondo p-4 sm:flex-row sm:items-start">
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 font-medium">
                    {l.sku}
                    {l.inactiva && <Badge tono="alerta">Variante inactiva</Badge>}
                  </p>
                  <p className="text-xs text-oxford/70">{l.descripcion}</p>
                  {l.sugerida !== null && (
                    <p className="mt-1 text-xs text-oxford/70">
                      Sugerido: {l.sugerida.toLocaleString('es-CO')} u.
                      {l.cantidad.trim() !== String(l.sugerida) && ' (editado)'}
                    </p>
                  )}
                  {l.justificacion && <p className="mt-1 text-xs text-oxford/70">{l.justificacion}</p>}
                </div>
                <div className="grid grid-cols-1 gap-3 sm:w-96 sm:grid-cols-2">
                  <Campo
                    etiqueta={`Cantidad de ${l.sku}`}
                    inputMode="numeric"
                    value={l.cantidad}
                    error={errores[l.varianteId]?.startsWith('La cantidad') ? errores[l.varianteId] : undefined}
                    onChange={(e) => cambiar(l.varianteId, 'cantidad', e.target.value)}
                  />
                  <Campo
                    etiqueta={`Costo estimado de ${l.sku}`}
                    inputMode="decimal"
                    placeholder="Precio de compra actual"
                    value={l.costo}
                    error={errores[l.varianteId]?.startsWith('El costo') ? errores[l.varianteId] : undefined}
                    onChange={(e) => cambiar(l.varianteId, 'costo', e.target.value)}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => quitar(l.varianteId)}
                  className="self-start text-sm font-medium text-acento hover:underline sm:mt-6"
                >
                  Quitar {l.sku}
                </button>
              </li>
            ))}
          </ul>
        )}

        <Campo
          etiqueta="Observación (opcional)"
          maxLength={255}
          value={observacion}
          onChange={(e) => {
            setGuardado(false)
            setObservacion(e.target.value)
          }}
        />
        <p className="text-sm text-oxford/70">
          Total estimado de lo escrito: <strong className="text-oxford">{formatearMoneda(total)}</strong>
        </p>

        {general && (
          <p role="alert" className="text-sm font-medium text-critico">
            {general}
          </p>
        )}
        {actualizar.isError && (
          <div role="alert" className="flex flex-col gap-2 rounded-lg border border-critico/40 bg-critico/5 p-3 text-sm">
            <p className="font-medium">{mensajeError(actualizar.error)}</p>
            {conflicto && (
              <>
                <p>
                  El plan cambió mientras lo editabas. Tus cambios se conservan en pantalla: carga la versión vigente y vuelve a
                  guardar, o descarta lo escrito.
                </p>
                <div className="flex flex-wrap gap-2">
                  <Boton
                    variante="secundario"
                    onClick={() => {
                      actualizar.reset()
                      onRecargar()
                    }}
                  >
                    Cargar versión vigente
                  </Boton>
                  <Boton
                    onClick={() => {
                      actualizar.reset()
                      onDescartar()
                    }}
                  >
                    Descartar mis cambios
                  </Boton>
                </div>
              </>
            )}
          </div>
        )}
        {guardado && !sucio && (
          <p role="status" className="text-sm font-medium text-ok">
            Cambios guardados.
          </p>
        )}
        <div className="flex flex-wrap items-center gap-3">
          <Boton type="submit" cargando={actualizar.isPending} disabled={!sucio}>
            Guardar cambios
          </Boton>
          {sucio && <span className="text-xs text-oxford/70">Hay cambios sin guardar.</span>}
        </div>
      </form>
    </div>
  )
}
