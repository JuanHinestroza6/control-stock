import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { BadgeEstadoVenta } from '../../componentes/dominio/BadgeEstadoVenta'
import { ModalAnularVenta } from '../../componentes/dominio/ModalAnularVenta'
import { ModalDevolucion } from '../../componentes/dominio/ModalDevolucion'
import { Boton } from '../../componentes/ui/Boton'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { useSesion } from '../../hooks/useSesion'
import { useVenta } from '../../hooks/useVentas'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { puedeAnularVenta, puedeVender } from '../../lib/permisos'
import type { LineaVentaResponse } from '../../tipos/ventas'

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
  const { usuario } = useSesion()
  const [devolviendo, setDevolviendo] = useState<LineaVentaResponse | null>(null)
  const [anulando, setAnulando] = useState(false)
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
    const registrada = v.estado === 'REGISTRADA'
    const puedeDevolver = registrada && puedeVender(usuario?.rol)
    const puedeAnular = registrada && puedeAnularVenta(usuario?.rol)
    contenido = (
      <>
        <div className="flex flex-wrap items-center gap-3">
          <h1>Venta {v.numero}</h1>
          <BadgeEstadoVenta estado={v.estado} />
          {puedeAnular && (
            <Boton variante="peligro" className="ml-auto" onClick={() => setAnulando(true)}>
              Anular venta
            </Boton>
          )}
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
                {puedeDevolver && <th scope="col" className="px-3 py-3 text-xs font-medium"><span className="sr-only">Acciones</span></th>}
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
                  {puedeDevolver && (
                    <td className="px-3 py-2 text-right">
                      {l.cantidadDevuelta < l.cantidad && (
                        <button
                          type="button"
                          onClick={() => setDevolviendo(l)}
                          aria-label={`Devolver ${l.sku}`}
                          className="font-medium text-acento hover:underline"
                        >
                          Devolver
                        </button>
                      )}
                    </td>
                  )}
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
      {ventaId !== undefined && devolviendo && (
        <ModalDevolucion ventaId={ventaId} linea={devolviendo} onCerrar={() => setDevolviendo(null)} />
      )}
      {ventaId !== undefined && anulando && consulta.data && (
        <ModalAnularVenta ventaId={ventaId} numero={consulta.data.numero} onCerrar={() => setAnulando(false)} />
      )}
    </section>
  )
}
