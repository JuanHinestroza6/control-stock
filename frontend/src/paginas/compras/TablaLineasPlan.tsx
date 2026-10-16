import { Badge } from '../../componentes/ui/Badge'
import { formatearMoneda } from '../../lib/formato'
import type { LineaPlanResponse } from '../../tipos/compras'

interface TablaLineasPlanProps {
  lineas: LineaPlanResponse[]
  /** Muestra las columnas de lo recibido (plan RECIBIDO). */
  conRecibido: boolean
}

/** Líneas de solo lectura (APROBADO, RECIBIDO, CANCELADO y para CONTABILIDAD). */
export function TablaLineasPlan({ lineas, conRecibido }: TablaLineasPlanProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-oxford/10 bg-fondo">
      <table className="w-full min-w-176 text-left text-sm">
        <caption className="sr-only">Líneas del plan</caption>
        <thead className="bg-oxford text-xs text-fondo">
          <tr>
            <th scope="col" className="px-3 py-2">Variante</th>
            <th scope="col" className="px-3 py-2 text-right">Sugerida</th>
            <th scope="col" className="px-3 py-2 text-right">Aprobada</th>
            <th scope="col" className="px-3 py-2 text-right">Costo est.</th>
            <th scope="col" className="px-3 py-2 text-right">Subtotal</th>
            {conRecibido && (
              <>
                <th scope="col" className="px-3 py-2 text-right">Recibida</th>
                <th scope="col" className="px-3 py-2 text-right">Costo recibido</th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {lineas.map((l) => (
            <tr key={l.id} className="odd:bg-fondo even:bg-fondo-suave align-top">
              <td className="px-3 py-2">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  {l.sku}
                  {!l.varianteActiva && <Badge tono="alerta">Variante inactiva</Badge>}
                </p>
                <p className="text-xs text-oxford/70">
                  {l.productoNombre} · {l.talla} · {l.color}
                </p>
                {l.justificacion && <p className="mt-1 text-xs text-oxford/70">{l.justificacion}</p>}
              </td>
              <td className="px-3 py-2 text-right">{l.cantidadSugerida === null ? '—' : l.cantidadSugerida.toLocaleString('es-CO')}</td>
              <td className="px-3 py-2 text-right">{l.cantidadAprobada.toLocaleString('es-CO')}</td>
              <td className="px-3 py-2 text-right">{formatearMoneda(l.costoEstimado)}</td>
              <td className="px-3 py-2 text-right">{formatearMoneda(l.subtotal)}</td>
              {conRecibido && (
                <>
                  <td className="px-3 py-2 text-right">{l.cantidadRecibida === null ? '—' : l.cantidadRecibida.toLocaleString('es-CO')}</td>
                  <td className="px-3 py-2 text-right">{l.costoRecibido === null ? '—' : formatearMoneda(l.costoRecibido)}</td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
