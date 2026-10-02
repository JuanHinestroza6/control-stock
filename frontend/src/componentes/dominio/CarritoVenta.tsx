import { formatearMoneda } from '../../lib/formato'
import { parsearCantidad, parsearPrecio, type ErroresLinea, type LineaCarrito } from '../../lib/carrito'

interface CarritoVentaProps {
  lineas: LineaCarrito[]
  /** Errores por varianteId. */
  errores?: Record<number, ErroresLinea>
  onCantidad: (varianteId: number, valor: string) => void
  onPrecio: (varianteId: number, valor: string) => void
  onQuitar: (varianteId: number) => void
}

const clasesInput = (error?: string) =>
  `min-h-10 rounded-lg border bg-fondo px-2 py-1 text-sm outline-none focus:border-acento focus:ring-1 focus:ring-acento ${error ? 'border-critico' : 'border-oxford/20'}`

/** Líneas de la venta en curso: cantidad y precio unitario editables (vacío = precio de lista). */
export function CarritoVenta({ lineas, errores = {}, onCantidad, onPrecio, onQuitar }: CarritoVentaProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-oxford/10 bg-fondo">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Líneas de la venta</caption>
        <thead className="bg-oxford text-fondo">
          <tr>
            <th scope="col" className="px-3 py-3 text-xs font-medium">Variante</th>
            <th scope="col" className="px-3 py-3 text-xs font-medium">Cantidad</th>
            <th scope="col" className="px-3 py-3 text-xs font-medium">Precio unitario</th>
            <th scope="col" className="px-3 py-3 text-right text-xs font-medium">Subtotal</th>
            <th scope="col" className="px-3 py-3 text-xs font-medium"><span className="sr-only">Quitar</span></th>
          </tr>
        </thead>
        <tbody>
          {lineas.map((l) => {
            const v = l.variante
            const e = errores[v.varianteId] ?? {}
            const cantidad = parsearCantidad(l.cantidad)
            const precio = parsearPrecio(l.precio, v.precioVenta)
            const subtotal = cantidad !== undefined && precio !== undefined ? cantidad * precio : undefined
            return (
              <tr key={v.varianteId} className="align-top odd:bg-fondo even:bg-fondo-suave">
                <td className="px-3 py-2">
                  <div className="flex flex-col">
                    <span className="font-medium">{v.sku}</span>
                    <span className="text-xs text-oxford/70">
                      {v.productoNombre} · {v.talla} · {v.color}
                    </span>
                    <span className="text-xs text-oxford/70">Disponible: {v.stockDisponible}</span>
                  </div>
                </td>
                <td className="px-3 py-2">
                  <input
                    type="text"
                    inputMode="numeric"
                    aria-label={`Cantidad ${v.sku}`}
                    aria-invalid={e.cantidad ? true : undefined}
                    value={l.cantidad}
                    onChange={(ev) => onCantidad(v.varianteId, ev.target.value)}
                    className={`w-20 ${clasesInput(e.cantidad)}`}
                  />
                  {e.cantidad && (
                    <p role="alert" className="mt-1 text-xs font-medium text-critico">
                      {e.cantidad}
                    </p>
                  )}
                </td>
                <td className="px-3 py-2">
                  <input
                    type="text"
                    inputMode="decimal"
                    aria-label={`Precio ${v.sku}`}
                    aria-invalid={e.precio ? true : undefined}
                    placeholder={String(v.precioVenta)}
                    value={l.precio}
                    onChange={(ev) => onPrecio(v.varianteId, ev.target.value)}
                    className={`w-28 ${clasesInput(e.precio)}`}
                  />
                  <p className="mt-1 text-xs text-oxford/70">Lista: {formatearMoneda(v.precioVenta)}</p>
                  {e.precio && (
                    <p role="alert" className="text-xs font-medium text-critico">
                      {e.precio}
                    </p>
                  )}
                </td>
                <td className="px-3 py-2 text-right">{subtotal === undefined ? '—' : formatearMoneda(subtotal)}</td>
                <td className="px-3 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => onQuitar(v.varianteId)}
                    aria-label={`Quitar ${v.sku}`}
                    className="font-medium text-acento hover:underline"
                  >
                    Quitar
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
