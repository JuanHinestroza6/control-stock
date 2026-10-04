import { useEffect, useState } from 'react'
import { useInventario } from '../../hooks/useInventario'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import type { InventarioItemResponse } from '../../tipos/inventario'
import { Campo } from '../ui/Campo'

const RETARDO_BUSQUEDA_MS = 300
const MAX_RESULTADOS = 8
const MIN_CARACTERES = 2

interface BuscadorVarianteProps {
  onSeleccionar: (variante: InventarioItemResponse) => void
  etiqueta?: string
  /** Variantes que no deben ofrecerse (p. ej. ya agregadas al carrito). */
  excluirIds?: number[]
}

/** Busca variantes activas por SKU, código o nombre (debounce 300 ms) y notifica la elegida. */
export function BuscadorVariante({ onSeleccionar, etiqueta = 'Buscar variante', excluirIds = [] }: BuscadorVarianteProps) {
  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')

  useEffect(() => {
    const id = setTimeout(() => setQ(texto.trim()), RETARDO_BUSQUEDA_MS)
    return () => clearTimeout(id)
  }, [texto])

  const activo = q.length >= MIN_CARACTERES
  const consulta = useInventario({ q: activo ? q : undefined, incluirInactivas: false, page: 0, size: MAX_RESULTADOS, sort: 'sku,asc' }, activo)

  const elegir = (v: InventarioItemResponse) => {
    onSeleccionar(v)
    setTexto('')
    setQ('')
  }

  let resultados
  if (!activo) {
    resultados = null
  } else if (consulta.isPending) {
    resultados = <p role="status" className="px-3 py-2 text-sm text-oxford/70">Buscando...</p>
  } else if (consulta.isError) {
    resultados = (
      <p role="alert" className="px-3 py-2 text-sm font-medium text-critico">
        {mensajeError(consulta.error)}
      </p>
    )
  } else {
    const filas = consulta.data.content.filter((v) => !excluirIds.includes(v.varianteId))
    resultados =
      filas.length === 0 ? (
        <p className="px-3 py-2 text-sm text-oxford/70">Sin resultados para la búsqueda.</p>
      ) : (
        <ul aria-label="Resultados de variantes" className="divide-y divide-oxford/10">
          {filas.map((v) => (
            <li key={v.varianteId}>
              <button
                type="button"
                onClick={() => elegir(v)}
                className="flex min-h-11 w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-fondo-suave focus:bg-fondo-suave focus:outline-none"
              >
                <span className="flex flex-col">
                  <span className="font-medium text-oxford">{v.sku}</span>
                  <span className="text-xs text-oxford/70">
                    {v.productoNombre} · {v.talla} · {v.color}
                  </span>
                </span>
                <span className="flex flex-col items-end text-xs">
                  <span className={v.stockDisponible === 0 ? 'font-medium text-critico' : 'text-oxford'}>
                    Disponible: {v.stockDisponible}
                  </span>
                  {v.stockReservado > 0 && <span className="text-oxford/70">Reservado: {v.stockReservado}</span>}
                  <span className="text-oxford/70">{formatearMoneda(v.precioVenta)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )
  }

  return (
    <div className="flex flex-col gap-1">
      <Campo
        etiqueta={etiqueta}
        type="search"
        autoComplete="off"
        maxLength={60}
        placeholder="SKU, código o nombre"
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
      />
      {resultados && <div className="rounded-lg border border-oxford/20 bg-fondo">{resultados}</div>}
    </div>
  )
}
