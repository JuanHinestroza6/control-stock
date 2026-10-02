import { isAxiosError } from 'axios'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { BuscadorVariante } from '../../componentes/dominio/BuscadorVariante'
import { CarritoVenta } from '../../componentes/dominio/CarritoVenta'
import { Boton } from '../../componentes/ui/Boton'
import { useRegistrarVenta } from '../../hooks/useVentas'
import { agregarAlCarrito, construirVenta, totalCarrito, type ErroresLinea, type LineaCarrito } from '../../lib/carrito'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import type { InventarioItemResponse } from '../../tipos/inventario'

export function VentaNuevaPage() {
  const navigate = useNavigate()
  const registrar = useRegistrarVenta()
  const [lineas, setLineas] = useState<LineaCarrito[]>([])
  const [errores, setErrores] = useState<Record<number, ErroresLinea>>({})
  const [general, setGeneral] = useState<string>()
  const [conflicto, setConflicto] = useState(false)

  const agregar = (v: InventarioItemResponse) => {
    const r = agregarAlCarrito(lineas, v)
    setLineas(r.lineas)
    setGeneral(r.error)
  }

  const cambiar = (varianteId: number, cambio: Partial<Pick<LineaCarrito, 'cantidad' | 'precio'>>) =>
    setLineas((ls) => ls.map((l) => (l.variante.varianteId === varianteId ? { ...l, ...cambio } : l)))

  const enviar = () => {
    const r = construirVenta(lineas)
    setErrores(r.errores)
    setGeneral(r.general)
    setConflicto(false)
    if (!r.solicitud) return
    registrar.mutate(r.solicitud, {
      onSuccess: (venta) => void navigate(`/ventas/${venta.id}`),
      onError: (error) => setConflicto(isAxiosError(error) && error.response?.status === 409),
    })
  }

  const alEnviar = (e: FormEvent) => {
    e.preventDefault()
    enviar()
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <Link to="/ventas" className="text-sm font-medium text-acento hover:underline">
          Volver a ventas
        </Link>
        <h1>Nueva venta</h1>
      </div>

      <BuscadorVariante etiqueta="Agregar variante (SKU, código o nombre)" onSeleccionar={agregar} />

      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4">
        {lineas.length === 0 ? (
          <p className="rounded-lg border border-dashed border-oxford/20 bg-fondo p-6 text-center text-sm text-oxford/70">
            El carrito está vacío. Busca una variante para agregarla.
          </p>
        ) : (
          <CarritoVenta
            lineas={lineas}
            errores={errores}
            onCantidad={(id, valor) => cambiar(id, { cantidad: valor })}
            onPrecio={(id, valor) => cambiar(id, { precio: valor })}
            onQuitar={(id) => setLineas((ls) => ls.filter((l) => l.variante.varianteId !== id))}
          />
        )}

        {general && (
          <p role="alert" className="text-sm font-medium text-critico">
            {general}
          </p>
        )}
        {registrar.isError && (
          <div role="alert" className="flex flex-col gap-2 rounded-lg border border-critico/40 bg-critico/5 p-4">
            <p className="text-sm font-medium text-oxford">{mensajeError(registrar.error)}</p>
            {conflicto && (
              <div>
                <Boton variante="secundario" onClick={enviar} cargando={registrar.isPending}>
                  Volver a confirmar
                </Boton>
              </div>
            )}
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-lg font-bold">Total: {formatearMoneda(totalCarrito(lineas))}</p>
          <div className="flex gap-3">
            <Link
              to="/ventas"
              className="inline-flex min-h-10 items-center rounded-lg px-4 py-2 text-sm font-medium text-oxford hover:bg-oxford/5"
            >
              Cancelar
            </Link>
            <Boton type="submit" cargando={registrar.isPending}>
              Registrar venta
            </Boton>
          </div>
        </div>
      </form>
    </section>
  )
}
