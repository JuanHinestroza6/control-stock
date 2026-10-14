import { useState } from 'react'
import { isAxiosError } from 'axios'
import { Badge, type TonoBadge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { DialogoConfirmacion } from '../../componentes/ui/DialogoConfirmacion'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useRecalcular, useUltimaEjecucion } from '../../hooks/useAnalitica'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearFecha } from '../../lib/formato'
import { puedeRecalcularAnalitica, puedeVerAnaliticaCompleta } from '../../lib/permisos'
import type { EjecucionAnaliticaResponse, EstadoEjecucion, OrigenEjecucion } from '../../tipos/analitica'

const MENSAJE_EN_CURSO = 'Ya hay un recálculo en curso.'

const ESTADOS: Record<EstadoEjecucion, { etiqueta: string; tono: TonoBadge }> = {
  EN_CURSO: { etiqueta: 'En curso', tono: 'acento' },
  EXITOSA: { etiqueta: 'Exitosa', tono: 'ok' },
  FALLIDA: { etiqueta: 'Fallida', tono: 'critico' },
}
const ORIGENES: Record<OrigenEjecucion, string> = { NOCTURNA: 'Nocturna', MANUAL: 'Manual', SEMILLA: 'Datos semilla' }

function formatearDuracion(ms: number | null): string {
  if (ms === null) return '—'
  if (ms < 1000) return `${ms} ms`
  const s = ms / 1000
  if (s < 60) return `${s.toLocaleString('es-CO', { maximumFractionDigits: 1 })} s`
  return `${Math.floor(s / 60)} min ${Math.round(s % 60)} s`
}

function Dato({ etiqueta, children }: { etiqueta: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium text-oxford/60">{etiqueta}</dt>
      <dd className="text-sm text-oxford">{children}</dd>
    </div>
  )
}

function DetalleEjecucion({ e }: { e: EjecucionAnaliticaResponse }) {
  const estado = ESTADOS[e.estado]
  return (
    <>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Dato etiqueta="Estado">
          <Badge tono={estado.tono}>{estado.etiqueta}</Badge>
        </Dato>
        <Dato etiqueta="Origen">{ORIGENES[e.origen]}{e.usuario ? ` · ${e.usuario}` : ''}</Dato>
        <Dato etiqueta="Inicio">{formatearFecha(e.iniciadaEn, true)}</Dato>
        <Dato etiqueta="Duración">{formatearDuracion(e.duracionMs)}</Dato>
        <Dato etiqueta="Clases A / B / C">
          {e.claseA} / {e.claseB} / {e.claseC}
        </Dato>
        <Dato etiqueta="Variantes en alerta de stock">
          {e.enAlertaStock} de {e.variantesActivas} ({e.porcentajeAlertaStock.toLocaleString('es-CO', { maximumFractionDigits: 1 })} %)
        </Dato>
        <Dato etiqueta="Sin rotación activas">{e.sinRotacionActivas}</Dato>
        <Dato etiqueta="Con punto de reorden">{e.conPuntoReorden}</Dato>
      </dl>
      {e.estado === 'FALLIDA' && (
        <p role="alert" className="mt-3 text-sm font-medium text-critico">
          {e.mensaje ?? 'El cálculo falló. Intenta recalcular.'}
        </p>
      )}
    </>
  )
}

/** Última ejecución de la analítica y, solo para ADMIN, el botón para recalcular. No se muestra al VENDEDOR. */
export function PanelEjecucion() {
  const { usuario } = useSesion()
  const visible = puedeVerAnaliticaCompleta(usuario?.rol)
  const puedeRecalcular = puedeRecalcularAnalitica(usuario?.rol)
  const consulta = useUltimaEjecucion(visible)
  const recalcular = useRecalcular()
  const [confirmando, setConfirmando] = useState(false)
  const [errorRecalculo, setErrorRecalculo] = useState<string>()
  const [aviso, setAviso] = useState<string>()

  if (!visible) return null

  const ultima = consulta.data
  const corriendo = recalcular.isPending || ultima?.estado === 'EN_CURSO'

  const confirmar = () => {
    setErrorRecalculo(undefined)
    recalcular.mutate(undefined, {
      onSuccess: (e) => {
        setConfirmando(false)
        setAviso(e.estado === 'FALLIDA' ? 'El recálculo terminó con errores.' : 'Recálculo terminado.')
      },
      onError: (error) => {
        setErrorRecalculo(isAxiosError(error) && error.response?.status === 409 ? MENSAJE_EN_CURSO : mensajeError(error))
      },
    })
  }

  return (
    <Tarjeta aria-labelledby="titulo-ejecucion">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 id="titulo-ejecucion">Última ejecución</h2>
        {puedeRecalcular && (
          <Boton
            variante="secundario"
            disabled={corriendo}
            cargando={recalcular.isPending}
            onClick={() => {
              setAviso(undefined)
              setErrorRecalculo(undefined)
              setConfirmando(true)
            }}
          >
            {corriendo ? 'Recalculando…' : 'Recalcular'}
          </Boton>
        )}
      </div>

      {aviso && (
        <p role="status" className="mb-3 text-sm font-medium text-oxford">
          {aviso}
        </p>
      )}

      {consulta.isPending ? (
        <EstadoCarga filas={2} />
      ) : consulta.isError ? (
        <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
      ) : ultima ? (
        <DetalleEjecucion e={ultima} />
      ) : (
        <p className="text-sm text-oxford/70">Aún no se ha calculado la analítica.</p>
      )}

      {puedeRecalcular && (
        <DialogoConfirmacion
          abierto={confirmando}
          titulo="Recalcular analítica"
          mensaje="Se recalcularán la clasificación ABC, los puntos de reorden y las alertas de todas las variantes. Puede tardar unos segundos."
          textoConfirmar="Recalcular"
          cargando={recalcular.isPending}
          error={errorRecalculo}
          onConfirmar={confirmar}
          onCancelar={() => setConfirmando(false)}
        />
      )}
    </Tarjeta>
  )
}
