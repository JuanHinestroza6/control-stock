import { useState, type ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { Badge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { DialogoConfirmacion } from '../../componentes/ui/DialogoConfirmacion'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { useAprobarPlan, usePlan } from '../../hooks/useCompras'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { puedeAprobarPlan, puedeCancelarPlan, puedeGestionarPlanes } from '../../lib/permisos'
import type { PlanCompraDetalleResponse } from '../../tipos/compras'
import { EditorBorrador } from './EditorBorrador'
import { ModalCancelarPlan } from './ModalCancelarPlan'
import type { EstadoNavegacionPlan } from './ModalSugerirPlan'
import { TablaLineasPlan } from './TablaLineasPlan'
import { ETIQUETA_ORIGEN, etiquetaEstadoPlan, TONO_ESTADO_PLAN } from './textosPlan'

function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="flex flex-col">
      <dt className="text-xs font-medium text-oxford/70">{etiqueta}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}

function conUsuario(fecha: string | null, usuario: string | null) {
  if (!fecha) return null
  return `${formatearFecha(fecha, true)}${usuario ? ` · ${usuario}` : ''}`
}

/** Detalle del plan de compras: cabecera, edición del borrador, aprobar y cancelar. Recibir llega con F6. */
export function PlanDetallePage() {
  const { id } = useParams()
  const numero = Number(id)
  const valido = Number.isInteger(numero) && numero > 0
  const consulta = usePlan(valido ? numero : undefined)
  const estado = useLocation().state as EstadoNavegacionPlan | null
  const omitidas = estado?.omitidas ?? []

  return (
    <section className="flex flex-col gap-4">
      <Link to="/compras" className="text-sm font-medium text-acento hover:underline">
        Volver a compras
      </Link>
      {omitidas.length > 0 && (
        <p role="status" className="rounded-lg border border-alerta/50 bg-alerta/10 p-3 text-sm">
          Se omitieron {omitidas.length} variante(s) por estar ya en otro borrador: {omitidas.join(', ')}.
        </p>
      )}
      {!valido ? (
        <EstadoError mensaje="Plan no válido." />
      ) : consulta.isPending ? (
        <EstadoCarga />
      ) : consulta.isError ? (
        <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
      ) : (
        <ContenidoPlan plan={consulta.data} recargar={() => consulta.refetch()} />
      )}
    </section>
  )
}

interface ContenidoPlanProps {
  plan: PlanCompraDetalleResponse
  recargar: () => Promise<unknown>
}

function ContenidoPlan({ plan, recargar }: ContenidoPlanProps) {
  const { usuario } = useSesion()
  const rol = usuario?.rol
  const aprobar = useAprobarPlan(plan.id)
  const [sucio, setSucio] = useState(false)
  const [reinicio, setReinicio] = useState(0)
  const [confirmando, setConfirmando] = useState(false)
  const [cancelando, setCancelando] = useState(false)

  const borrador = plan.estado === 'BORRADOR'
  const editable = borrador && puedeGestionarPlanes(rol)
  const puedeAprobar = borrador && puedeAprobarPlan(rol)
  const puedeCancelar = puedeCancelarPlan(rol, plan.estado)

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1>Plan {plan.numero}</h1>
        <Badge tono={TONO_ESTADO_PLAN[plan.estado]}>{etiquetaEstadoPlan(plan.estado)}</Badge>
        {plan.atrasado && <Badge tono="alerta">Atrasado</Badge>}
      </div>

      <dl className="grid gap-3 rounded-lg border border-oxford/10 bg-fondo p-4 sm:grid-cols-2 lg:grid-cols-3">
        <Dato etiqueta="Origen">{ETIQUETA_ORIGEN[plan.origen]}</Dato>
        <Dato etiqueta="Creado">{conUsuario(plan.creadoEn, plan.creadoPor)}</Dato>
        {plan.aprobadoEn && <Dato etiqueta="Aprobado">{conUsuario(plan.aprobadoEn, plan.aprobadoPor)}</Dato>}
        {plan.llegadaEsperada && <Dato etiqueta="Llegada esperada">{formatearFecha(plan.llegadaEsperada)}</Dato>}
        {plan.recibidoEn && <Dato etiqueta="Recibido">{formatearFecha(plan.recibidoEn, true)}</Dato>}
        {plan.canceladoEn && <Dato etiqueta="Cancelado">{conUsuario(plan.canceladoEn, plan.canceladoPor)}</Dato>}
        <Dato etiqueta="Costo estimado total">{formatearMoneda(plan.costoEstimadoTotal)}</Dato>
        {!editable && plan.observacion && <Dato etiqueta="Observación">{plan.observacion}</Dato>}
      </dl>

      {plan.estado === 'CANCELADO' && (
        <p className="rounded-lg border border-critico/40 bg-critico/5 p-3 text-sm">
          <strong>Motivo de la cancelación:</strong> {plan.motivoCancelacion ?? 'Sin motivo registrado.'}
        </p>
      )}

      {(puedeAprobar || puedeCancelar) && (
        <div className="flex flex-wrap items-center gap-3">
          {puedeAprobar && (
            <Boton
              variante="secundario"
              disabled={sucio || plan.lineas.length === 0}
              onClick={() => {
                aprobar.reset()
                setConfirmando(true)
              }}
            >
              Aprobar plan
            </Boton>
          )}
          {puedeCancelar && (
            <Boton variante="peligro" onClick={() => setCancelando(true)}>
              {borrador ? 'Descartar borrador' : 'Cancelar plan'}
            </Boton>
          )}
          {puedeAprobar && sucio && <span className="text-xs text-oxford/70">Guarda los cambios antes de aprobar.</span>}
        </div>
      )}

      {editable ? (
        <EditorBorrador
          key={`${plan.id}-${reinicio}`}
          plan={plan}
          onSucioCambio={setSucio}
          onRecargar={() => void recargar()}
          onDescartar={() => {
            void recargar().then(() => {
              setSucio(false)
              setReinicio((n) => n + 1)
            })
          }}
        />
      ) : plan.lineas.length === 0 ? (
        <p className="rounded-lg border border-dashed border-oxford/20 bg-fondo p-6 text-center text-sm text-oxford/70">
          El plan no tiene líneas.
        </p>
      ) : (
        <TablaLineasPlan lineas={plan.lineas} conRecibido={plan.estado === 'RECIBIDO'} />
      )}

      <DialogoConfirmacion
        abierto={confirmando}
        titulo={`Aprobar plan ${plan.numero}`}
        mensaje={`Se aprobará el plan con ${plan.lineas.length} línea(s) y un costo estimado de ${formatearMoneda(plan.costoEstimadoTotal)}. Las unidades pasarán a "en camino" y el plan ya no se podrá editar.`}
        textoConfirmar="Aprobar"
        cargando={aprobar.isPending}
        error={aprobar.isError ? mensajeError(aprobar.error) : undefined}
        onConfirmar={() => aprobar.mutate({ version: plan.version }, { onSuccess: () => setConfirmando(false) })}
        onCancelar={() => setConfirmando(false)}
      />
      {cancelando && (
        <ModalCancelarPlan planId={plan.id} numero={plan.numero} aprobado={plan.estado === 'APROBADO'} onCerrar={() => setCancelando(false)} />
      )}
    </>
  )
}
