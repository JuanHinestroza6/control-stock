import { useRef, type KeyboardEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { PestanaLibroVentas } from './PestanaLibroVentas'
import { PestanaMovimientos } from './PestanaMovimientos'
import { PestanaRentabilidad } from './PestanaRentabilidad'
import { PestanaValorizacion } from './PestanaValorizacion'

type ClavePestana = 'rentabilidad' | 'libro-ventas' | 'valorizacion' | 'movimientos'


const PESTANAS: { clave: ClavePestana; etiqueta: string }[] = [
  { clave: 'rentabilidad', etiqueta: 'Rentabilidad' },
  { clave: 'libro-ventas', etiqueta: 'Libro de ventas' },
  { clave: 'valorizacion', etiqueta: 'Valorización' },
  { clave: 'movimientos', etiqueta: 'Movimientos' },
]

const contenido: Record<ClavePestana, () => ReactNode> = {
  rentabilidad: () => <PestanaRentabilidad />,
  'libro-ventas': () => <PestanaLibroVentas />,
  valorizacion: () => <PestanaValorizacion />,
  movimientos: () => <PestanaMovimientos />,
}

export function ReportesPage() {
  const [params, setParams] = useSearchParams()
  const refs = useRef<(HTMLButtonElement | null)[]>([])
  const activa = PESTANAS.find((p) => p.clave === params.get('pestana')) ?? PESTANAS[0]

  const seleccionar = (clave: ClavePestana) => setParams({ pestana: clave }, { replace: true })

  const alTeclear = (e: KeyboardEvent, indice: number) => {
    const paso = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0
    if (!paso) return
    e.preventDefault()
    const destino = (indice + paso + PESTANAS.length) % PESTANAS.length
    seleccionar(PESTANAS[destino].clave)
    refs.current[destino]?.focus()
  }

  return (
    <section className="flex flex-col gap-4">
      <h1>Reportes</h1>
      <div role="tablist" aria-label="Reportes" className="flex gap-1 overflow-x-auto border-b border-oxford/10">
        {PESTANAS.map((p, i) => {
          const seleccionada = p.clave === activa.clave
          return (
            <button
              key={p.clave}
              ref={(el) => {
                refs.current[i] = el
              }}
              type="button"
              role="tab"
              id={`tab-${p.clave}`}
              aria-selected={seleccionada}
              aria-controls={`panel-${p.clave}`}
              tabIndex={seleccionada ? 0 : -1}
              onClick={() => seleccionar(p.clave)}
              onKeyDown={(e) => alTeclear(e, i)}
              className={`-mb-px whitespace-nowrap border-b-2 px-4 py-2 text-sm font-medium ${
                seleccionada ? 'border-acento text-acento' : 'border-transparent text-oxford/70 hover:text-oxford'
              }`}
            >
              {p.etiqueta}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={`panel-${activa.clave}`} aria-labelledby={`tab-${activa.clave}`} tabIndex={0}>
        {contenido[activa.clave]()}
      </div>
    </section>
  )
}
