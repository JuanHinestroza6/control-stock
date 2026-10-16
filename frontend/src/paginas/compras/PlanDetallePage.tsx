import { Link, useLocation, useParams } from 'react-router'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { usePlan } from '../../hooks/useCompras'
import { mensajeError } from '../../lib/errores'
import type { EstadoNavegacionPlan } from './ModalSugerirPlan'

/** Marcador de F4: el detalle completo (edición, aprobar, cancelar, recibir) llega con F5. */
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
        <>
          <h1>Plan {consulta.data.numero}</h1>
          <p className="text-sm text-oxford/70">El detalle del plan estará disponible próximamente.</p>
        </>
      )}
    </section>
  )
}
