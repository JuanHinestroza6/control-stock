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
import { construirLineasPlan, MAX_LINEAS_PLAN } from './textosPlan'
import {
  desdePlan,
  fusionar,
  instantanea,
  type ConflictoLinea,
  type ConflictoObservacion,
  type LineaEditable,
} from './fusionBorrador'

interface BaseEdicion {
  version: number
  lineas: LineaEditable[]
  observacion: string
}

function resumen(l: LineaEditable | null, quitada: string): string {
  return l ? `${l.cantidad.trim() || '—'} u. · costo ${l.costo.trim() || 'por defecto'}` : quitada
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

/** Edición del BORRADOR: cantidades, costos, quitar y agregar líneas; guarda con la `version` de la base de edición. */
export function EditorBorrador({ plan, onSucioCambio, onRecargar, onDescartar }: EditorBorradorProps) {
  const actualizar = useActualizarPlan(plan.id)
  const [lineas, setLineas] = useState<LineaEditable[]>(() => desdePlan(plan))
  const [observacion, setObservacion] = useState(plan.observacion ?? '')
  // Base: el plan (líneas, observación) y la versión sobre los que empecé a editar. El PUT usa SU versión.
  const [base, setBase] = useState<BaseEdicion>(() => ({
    version: plan.version,
    lineas: desdePlan(plan),
    observacion: (plan.observacion ?? '').trim(),
  }))
  const [conflictos, setConflictos] = useState<ConflictoLinea[]>([])
  const [conflictoObs, setConflictoObs] = useState<ConflictoObservacion | null>(null)
  const [aviso, setAviso] = useState<string>()
  const [errores, setErrores] = useState<Record<number, string>>({})
  const [general, setGeneral] = useState<string>()
  const [guardado, setGuardado] = useState(false)

  // Llegó una versión más nueva (tras un 409 o un refresco): fusión de tres vías base / mío / vigente.
  if (plan.version > base.version) {
    const vigentes = desdePlan(plan)
    const obsVigente = plan.observacion ?? ''
    const mias = [...lineas, ...conflictos.flatMap((c) => (c.mia ? [c.mia] : []))]
    const r = fusionar(base.lineas, mias, vigentes, base.observacion, conflictoObs ? conflictoObs.mia : observacion, obsVigente)
    setLineas(r.lineas)
    setConflictos(r.conflictos)
    setConflictoObs(r.conflictoObservacion)
    if (!r.conflictoObservacion) setObservacion(r.observacion)
    setBase({ version: plan.version, lineas: vigentes, observacion: obsVigente.trim() })
    const nConf = r.conflictos.length + (r.conflictoObservacion ? 1 : 0)
    setAviso(
      nConf > 0
        ? `Se cargó la versión vigente del plan y hay ${nConf} conflicto(s) con tus cambios: resuélvelos para poder guardar.`
        : r.huboCambiosAjenos
          ? 'Se cargó la versión vigente del plan y se incorporaron los cambios de la otra persona a tus cambios.'
          : undefined,
    )
  }

  const hayConflictos = conflictos.length > 0 || conflictoObs !== null
  const sucio = useMemo(
    () => hayConflictos || instantanea(lineas, observacion) !== instantanea(base.lineas, base.observacion),
    [lineas, observacion, base, hayConflictos],
  )
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
      ls.some((l) => l.varianteId === v.varianteId) || conflictos.some((c) => c.varianteId === v.varianteId) || ls.length >= MAX_LINEAS_PLAN
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

  const resolverLinea = (c: ConflictoLinea, usar: 'mia' | 'vigente') => {
    setGuardado(false)
    const elegida = c[usar]
    setConflictos((cs) => cs.filter((x) => x.varianteId !== c.varianteId))
    if (elegida) setLineas((ls) => [...ls.filter((l) => l.varianteId !== c.varianteId), elegida])
  }
  const resolverObservacion = (usar: 'mia' | 'vigente') => {
    if (!conflictoObs) return
    setGuardado(false)
    setObservacion(conflictoObs[usar])
    setConflictoObs(null)
  }

  const enviar = (e: FormEvent) => {
    e.preventDefault()
    if (hayConflictos) return
    const r = construirLineasPlan(lineas)
    setErrores(r.errores)
    setGeneral(r.general)
    if (!r.lineas) return
    const textoObs = observacion.trim()
    const nuevaBase: BaseEdicion = { version: base.version, lineas, observacion: textoObs }
    actualizar.mutate(
      {
        version: base.version,
        lineas: r.lineas,
        // Vaciar una observación que existía exige enviar null explícito.
        ...(textoObs ? { observacion: textoObs } : base.observacion ? { observacion: null } : {}),
      },
      {
        onSuccess: (respuesta) => {
          setBase({ ...nuevaBase, version: respuesta.version })
          setAviso(undefined)
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
        excluirIds={[...lineas.map((l) => l.varianteId), ...conflictos.map((c) => c.varianteId)]}
      />
      {lineas.length >= MAX_LINEAS_PLAN && <p className="text-sm text-alerta">Llegaste al máximo de {MAX_LINEAS_PLAN} líneas.</p>}

      <form onSubmit={enviar} noValidate aria-label="Editar borrador" className="flex flex-col gap-4">
        {aviso && (
          <p role="status" className="rounded-lg border border-acento/40 bg-acento/5 p-3 text-sm">
            {aviso}
          </p>
        )}
        {hayConflictos && (
          <section aria-label="Conflictos por resolver" className="flex flex-col gap-3 rounded-lg border-2 border-critico bg-critico/5 p-4">
            <h2 className="text-base">Conflictos por resolver</h2>
            <p className="text-sm">
              Tú y otra persona cambiaron lo mismo de forma distinta. Elige qué conservar; no podrás guardar hasta resolverlos todos.
            </p>
            <ul className="flex flex-col gap-3">
              {conflictos.map((c) => (
                <li key={c.varianteId} className="flex flex-col gap-2 rounded-lg border border-critico/40 bg-fondo p-3 text-sm">
                  <p className="flex flex-wrap items-center gap-2 font-medium">
                    <Badge tono="critico">Conflicto</Badge>
                    {c.sku}
                    <span className="text-xs font-normal text-oxford/70">{c.descripcion}</span>
                  </p>
                  <p>
                    <strong>Lo mío:</strong> {resumen(c.mia, 'quité esta línea')}
                  </p>
                  <p>
                    <strong>Lo vigente:</strong> {resumen(c.vigente, 'la otra persona quitó esta línea')}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Boton variante="secundario" onClick={() => resolverLinea(c, 'mia')}>
                      Usar el mío ({c.sku})
                    </Boton>
                    <Boton onClick={() => resolverLinea(c, 'vigente')}>Usar el vigente ({c.sku})</Boton>
                  </div>
                </li>
              ))}
              {conflictoObs && (
                <li className="flex flex-col gap-2 rounded-lg border border-critico/40 bg-fondo p-3 text-sm">
                  <p className="flex flex-wrap items-center gap-2 font-medium">
                    <Badge tono="critico">Conflicto</Badge>
                    Observación
                  </p>
                  <p>
                    <strong>Lo mío:</strong> {conflictoObs.mia || '(vacía)'}
                  </p>
                  <p>
                    <strong>Lo vigente:</strong> {conflictoObs.vigente || '(vacía)'}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Boton variante="secundario" onClick={() => resolverObservacion('mia')}>
                      Usar el mío (observación)
                    </Boton>
                    <Boton onClick={() => resolverObservacion('vigente')}>Usar el vigente (observación)</Boton>
                  </div>
                </li>
              )}
            </ul>
          </section>
        )}

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
                  El plan cambió mientras lo editabas. Tus cambios se conservan en pantalla: al cargar la versión vigente se
                  fusionan con los de la otra persona (si ambos cambian lo mismo, tú eliges); luego vuelve a guardar, o descarta
                  lo escrito.
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
          <Boton type="submit" cargando={actualizar.isPending} disabled={!sucio || hayConflictos}>
            Guardar cambios
          </Boton>
          {hayConflictos ? (
            <span className="text-xs font-medium text-critico">Resuelve los conflictos para guardar.</span>
          ) : (
            sucio && <span className="text-xs text-oxford/70">Hay cambios sin guardar.</span>
          )}
        </div>
      </form>
    </div>
  )
}
