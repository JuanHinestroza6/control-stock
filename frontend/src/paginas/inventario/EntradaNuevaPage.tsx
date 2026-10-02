import { isAxiosError } from 'axios'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useQueries } from '@tanstack/react-query'
import { obtenerProducto } from '../../api/productos'
import { BuscadorVariante } from '../../componentes/dominio/BuscadorVariante'
import { MatrizCantidades } from '../../componentes/dominio/MatrizCantidades'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { useRegistrarEntrada } from '../../hooks/useInventario'
import { useMatriz } from '../../hooks/useVariantes'
import { mensajeError } from '../../lib/errores'
import type { ProductoDetalleResponse } from '../../tipos/catalogo'
import { construirEntrada, parsearCosto, type CantidadesPorProducto } from './entrada'

function SeccionProducto({
  producto,
  valores,
  costo,
  errorCosto,
  erroresCantidad,
  onCantidad,
  onCosto,
  onQuitar,
}: {
  producto: ProductoDetalleResponse
  valores: Record<number, string>
  costo: string
  errorCosto?: string
  erroresCantidad: Record<number, string>
  onCantidad: (varianteId: number, valor: string) => void
  onCosto: (valor: string) => void
  onQuitar: () => void
}) {
  const matriz = useMatriz(producto.id, false)
  return (
    <section aria-label={`Entrada de ${producto.codigo}`} className="flex flex-col gap-3 rounded-lg border border-oxford/10 bg-fondo p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2>{producto.nombre}</h2>
          <p className="text-xs text-oxford/70">{producto.codigo}</p>
        </div>
        <button type="button" onClick={onQuitar} className="text-sm font-medium text-acento hover:underline">
          Quitar {producto.codigo}
        </button>
      </div>
      <div className="max-w-xs">
        <Campo
          etiqueta={`Costo unitario de ${producto.codigo}`}
          type="text"
          inputMode="decimal"
          value={costo}
          error={errorCosto}
          onChange={(e) => onCosto(e.target.value)}
        />
        <p className="mt-1 text-xs text-oxford/70">Un solo costo para todas las variantes del producto en esta entrada.</p>
        {parsearCosto(costo) === 0 && (
          <p role="status" className="mt-1 text-xs font-medium text-alerta">
            Costo 0: se registra, pero no cambia el precio de compra del producto.
          </p>
        )}
      </div>
      {matriz.isPending ? (
        <EstadoCarga filas={2} />
      ) : matriz.isError ? (
        <EstadoError mensaje={mensajeError(matriz.error)} onReintentar={() => void matriz.refetch()} />
      ) : matriz.data.celdas.length === 0 ? (
        <p className="text-sm text-oxford/70">Este producto no tiene variantes activas. Genera variantes en el detalle del producto.</p>
      ) : (
        <MatrizCantidades matriz={matriz.data} valores={valores} onCambiar={onCantidad} errores={erroresCantidad} />
      )}
    </section>
  )
}

export function EntradaNuevaPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const inicial = Number(params.get('productoId'))
  const [productoIds, setProductoIds] = useState<number[]>(Number.isInteger(inicial) && inicial > 0 ? [inicial] : [])
  const [cantidades, setCantidades] = useState<CantidadesPorProducto>({})
  const [costos, setCostos] = useState<Record<number, string>>({})
  const [observacion, setObservacion] = useState('')
  const [erroresCosto, setErroresCosto] = useState<Record<number, string>>({})
  const [erroresCantidad, setErroresCantidad] = useState<Record<number, string>>({})
  const [general, setGeneral] = useState<string>()
  const [conflicto, setConflicto] = useState(false)
  const registrar = useRegistrarEntrada()

  const consultas = useQueries({
    queries: productoIds.map((id) => ({ queryKey: ['producto', id], queryFn: () => obtenerProducto(id) })),
  })

  const costoDe = (id: number, producto: ProductoDetalleResponse | undefined) =>
    costos[id] ?? (producto?.precioCompra != null ? String(producto.precioCompra) : '')

  const enviar = () => {
    const efectivos: Record<number, string> = {}
    productoIds.forEach((id, i) => {
      efectivos[id] = costoDe(id, consultas[i].data)
    })
    const r = construirEntrada(productoIds, cantidades, efectivos, observacion)
    setErroresCosto(r.erroresCosto)
    setErroresCantidad(r.erroresCantidad)
    setGeneral(r.general)
    setConflicto(false)
    if (!r.solicitud) return
    registrar.mutate(r.solicitud, {
      onSuccess: () => void navigate('/inventario'),
      onError: (error) => {
        if (isAxiosError(error) && error.response?.status === 409) setConflicto(true)
      },
    })
  }

  const alEnviar = (e: FormEvent) => {
    e.preventDefault()
    enviar()
  }

  const agregarProducto = (id: number) => setProductoIds((ids) => (ids.includes(id) ? ids : [...ids, id]))
  const quitarProducto = (id: number) => {
    setProductoIds((ids) => ids.filter((i) => i !== id))
    setCantidades(({ [id]: _quitado, ...resto }) => resto)
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <Link to="/inventario" className="text-sm font-medium text-acento hover:underline">
          Volver al inventario
        </Link>
        <h1>Nueva entrada de mercancía</h1>
      </div>

      <BuscadorVariante etiqueta="Agregar producto (busca por SKU, código o nombre)" onSeleccionar={(v) => agregarProducto(v.productoId)} />

      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4">
        {productoIds.length === 0 && (
          <p className="rounded-lg border border-dashed border-oxford/20 bg-fondo p-6 text-center text-sm text-oxford/70">
            Busca una variante para agregar su producto y escribir las cantidades que ingresan.
          </p>
        )}

        {productoIds.map((id, i) => {
          const q = consultas[i]
          if (q.isPending) return <EstadoCarga key={id} filas={2} />
          if (q.isError) return <EstadoError key={id} mensaje={mensajeError(q.error)} onReintentar={() => void q.refetch()} />
          return (
            <SeccionProducto
              key={id}
              producto={q.data}
              valores={cantidades[id] ?? {}}
              costo={costoDe(id, q.data)}
              errorCosto={erroresCosto[id]}
              erroresCantidad={erroresCantidad}
              onCantidad={(varianteId, valor) =>
                setCantidades((c) => ({ ...c, [id]: { ...(c[id] ?? {}), [varianteId]: valor } }))
              }
              onCosto={(valor) => setCostos((c) => ({ ...c, [id]: valor }))}
              onQuitar={() => quitarProducto(id)}
            />
          )
        })}

        {productoIds.length > 0 && (
          <>
            <Campo
              etiqueta="Observación (opcional)"
              maxLength={255}
              value={observacion}
              onChange={(e) => setObservacion(e.target.value)}
            />
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
            <div className="flex flex-wrap gap-3">
              <Boton type="submit" cargando={registrar.isPending}>
                Registrar entrada
              </Boton>
              <Link
                to="/inventario"
                className="inline-flex min-h-10 items-center rounded-lg px-4 py-2 text-sm font-medium text-oxford hover:bg-oxford/5"
              >
                Cancelar
              </Link>
            </div>
          </>
        )}
      </form>
    </section>
  )
}
