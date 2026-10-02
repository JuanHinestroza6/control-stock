import { Link, useParams } from 'react-router'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { useVenta } from '../../hooks/useVentas'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import type { LineaVentaResponse } from '../../tipos/ventas'
import { BadgeEstadoVenta } from '../../componentes/dominio/BadgeEstadoVenta'

function Precio({ linea }: { linea: LineaVentaResponse }) {
  const descuento = linea.precioLista - linea.precioUnitario
  if (descuento <= 0) return <>{formatearMoneda(linea.precioUnitario)}</>
  const pct = Math.round((descuento / linea.precioLista) * 100)
  return (
    <div className="flex flex-col items-end">
      <span className="text-xs text-oxford/60 line-through">{formatearMoneda(linea.precioLista)}</span>
      <span>{formatearMoneda(linea.precioUnitario)}</span>
      <span className="text-xs font-medium text-alerta">Descuento {pct}%</span>
    </div>
  )
}

export function VentaDetallePage() {
  const { id } = useParams()
  const ventaId = id !== undefined && /^\d+$/.test(id) ? Number(id) : undefined
  const consulta = useVenta(ventaId)
  // El costo llega null para VENDEDOR: la columna solo aparece si algún valor viene informado.
  const verCosto = consulta.data?.lineas.some((l) => l.costoUnitario !== null) ?? false

  let contenido
  if (ventaId === undefined) {
    contenido = <EstadoError mensaje="La venta solicitada no existe." />
  } else if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else {
    const v = consulta.data
    contenido = (
      <>
        <div className="flex flex-wrap items-center gap-3">
          <h1>Venta {v.numero}</h1>
          <BadgeEstadoVenta estado={v.estado} />
        </div>

        <dl className="grid grid-cols-1 gap-3 rounded-lg border border-oxford/10 bg-fondo p-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt className="text-xs font-medium text-oxford/70">Fecha</dt>
            <dd>{formatearFecha(v.fecha, true)}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-oxford/70">Vendedor</dt>
            <dd>{v.vendedor}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-oxford/70">Descuento total</dt>
            <dd>{formatearMoneda(v.descuentoTotal)}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-oxford/70">Total</dt>
            <dd className="text-lg font-bold">{formatearMoneda(v.total)}</dd>
          </div>
        </dl>

        {v.estado === 'ANULADA' && (
          <div role="status" className="rounded-lg border border-critico/40 bg-critico/5 p-4 text-sm">
            <p className="font-medium">
              Anulada{v.anuladaEn ? ` el ${formatearFecha(v.anuladaEn, true)}` : ''}
              {v.anuladaPor ? ` por ${v.anuladaPor}` : ''}.
            </p>
            {v.motivoAnulacion && <p className="text-oxford/70">Motivo: {v.motivoAnulacion}</p>}
          </div>
        )}

        <div className="overflow-x-auto rounded-lg border border-oxford/10 bg-fondo">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Líneas de la venta</caption>
            <thead className="bg-oxford text-fondo">
              <tr>
                <th scope="col" className="px-3 py-3 text-xs font-medium">Variante</th>
                <th scope="col" className="px-3 py-3 text-right text-xs font-medium">Cantidad</th>
                <th scope="col" className="px-3 py-3 text-right text-xs font-medium">Precio unitario</th>
                {verCosto && <th scope="col" className="hidden px-3 py-3 text-right text-xs font-medium md:table-cell">Costo unitario</th>}
                <th scope="col" className="px-3 py-3 text-right text-xs font-medium">Subtotal</th>
                <th scope="col" className="hidden px-3 py-3 text-right text-xs font-medium md:table-cell">Devueltas</th>
              </tr>
            </thead>
            <tbody>
              {v.lineas.map((l) => (
                <tr key={l.detalleId} className="align-top odd:bg-fondo even:bg-fondo-suave">
                  <td className="px-3 py-2">
                    <div className="flex flex-col">
                      <span className="font-medium">{l.sku}</span>
                      <span className="text-xs text-oxford/70">
                        {l.productoNombre} · {l.talla} · {l.color}
                      </span>
                      {l.devoluciones.length > 0 && (
                        <ul aria-label={`Devoluciones de ${l.sku}`} className="mt-1 flex flex-col gap-0.5 text-xs text-oxford/70">
                          {l.devoluciones.map((d) => (
                            <li key={d.id}>
                              {d.cantidad} u. el {formatearFecha(d.fecha, true)} por {d.usuarioNombre}: {d.motivo}
                              {d.aptaParaVenta ? ' (reingresa al stock)' : ' (no reingresa al stock)'}
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </td>
                  <td className="px-3 py-2 text-right">{l.cantidad}</td>
                  <td className="px-3 py-2 text-right">
                    <Precio linea={l} />
                  </td>
                  {verCosto && (
                    <td className="hidden px-3 py-2 text-right md:table-cell">
                      {l.costoUnitario === null ? '—' : formatearMoneda(l.costoUnitario)}
                    </td>
                  )}
                  <td className="px-3 py-2 text-right">{formatearMoneda(l.subtotal)}</td>
                  <td className="hidden px-3 py-2 text-right md:table-cell">{l.cantidadDevuelta}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <Link to="/ventas" className="text-sm font-medium text-acento hover:underline">
        Volver a ventas
      </Link>
      {contenido}
    </section>
  )
}
