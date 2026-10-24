import { Link } from 'react-router'
import { BadgeTipoAlerta } from '../../componentes/dominio/BadgeTipoAlerta'
import { Badge } from '../../componentes/ui/Badge'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useAlertasUrgentes } from '../../hooks/useAlertas'
import { useMasVendidos, useUltimaEjecucion } from '../../hooks/useAnalitica'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { hoyIso, restarDias } from '../../lib/rangoFechas'

const num = (n: number) => n.toLocaleString('es-CO')

const LIMITE_URGENTES = 5

/** Las alertas de stock más urgentes; los conteos por tipo están solo en las tarjetas de Inventario del panel. */
export function BloqueAlertas() {
  const q = useAlertasUrgentes(LIMITE_URGENTES)
  return (
    <Tarjeta>
      <h2 className="text-base">Alertas más urgentes</h2>
      {q.isPending ? (
        <EstadoCarga filas={3} />
      ) : q.isError ? (
        <EstadoError mensaje={mensajeError(q.error)} onReintentar={() => void q.refetch()} />
      ) : q.data.length === 0 ? (
        <EstadoVacio mensaje="Sin alertas de stock activas." />
      ) : (
        <ul className="mt-2 flex flex-col gap-2 text-sm">
          {q.data.map((a) => (
            <li key={a.id} className="flex items-start justify-between gap-2">
              <span className="min-w-0">
                <span className="block truncate">{a.productoNombre}</span>
                <span className="block text-xs text-oxford/60">
                  {a.sku} · {a.talla} · {a.color}
                </span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-1">
                <BadgeTipoAlerta tipo={a.tipo} />
                <span className="text-xs text-oxford/70">
                  {num(a.stockDisponible)} / {num(a.umbral)} u
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
      <Link to="/alertas" className="mt-3 inline-block text-sm text-acento hover:underline">
        Ver todas las alertas
      </Link>
    </Tarjeta>
  )
}

export function BloqueMasVendidos() {
  const hoy = hoyIso()
  const q = useMasVendidos({ desde: restarDias(hoy, 29), hasta: hoy, criterio: 'UNIDADES', agrupacion: 'PRODUCTO', limite: 5 })
  return (
    <Tarjeta>
      <h2 className="text-base">Más vendidos (30 días)</h2>
      {q.isPending ? (
        <EstadoCarga filas={3} />
      ) : q.isError ? (
        <EstadoError mensaje={mensajeError(q.error)} onReintentar={() => void q.refetch()} />
      ) : q.data.items.length === 0 ? (
        <EstadoVacio mensaje="Sin ventas en los últimos 30 días." />
      ) : (
        <ol className="mt-2 flex flex-col gap-1 text-sm">
          {q.data.items.map((i) => (
            <li key={i.productoId} className="flex justify-between gap-2">
              <span className="truncate">
                {i.posicion}. {i.productoNombre}
              </span>
              <span className="shrink-0 text-oxford/70">
                {num(i.unidades)} u · {formatearMoneda(i.ingreso)}
              </span>
            </li>
          ))}
        </ol>
      )}
    </Tarjeta>
  )
}

export function BloqueEjecucion() {
  const q = useUltimaEjecucion()
  return (
    <Tarjeta>
      <h2 className="text-base">Última ejecución analítica</h2>
      {q.isPending ? (
        <EstadoCarga filas={2} />
      ) : q.isError ? (
        <EstadoError mensaje={mensajeError(q.error)} onReintentar={() => void q.refetch()} />
      ) : !q.data ? (
        <EstadoVacio mensaje="Aún no hay ejecuciones." />
      ) : (
        <div className="mt-2 text-sm">
          <Badge tono={q.data.estado === 'EXITOSA' ? 'ok' : q.data.estado === 'FALLIDA' ? 'critico' : 'acento'}>
            {q.data.estado === 'EXITOSA' ? 'Exitosa' : q.data.estado === 'FALLIDA' ? 'Fallida' : 'En curso'}
          </Badge>
          <p className="mt-2 text-oxford/70">{formatearFecha(q.data.iniciadaEn, true)}</p>
          {q.data.porcentajeAlertaStock != null && (
            <p className="mt-1">
              En alerta de stock: {q.data.porcentajeAlertaStock.toLocaleString('es-CO', { maximumFractionDigits: 1 })} %
            </p>
          )}
          <Link to="/analitica" className="mt-3 inline-block text-acento hover:underline">
            Ver analítica
          </Link>
        </div>
      )}
    </Tarjeta>
  )
}
