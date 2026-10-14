import { useRef, type KeyboardEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { useSesion } from '../../hooks/useSesion'
import { puedeVerAnaliticaCompleta } from '../../lib/permisos'
import { PanelEjecucion } from './PanelEjecucion'
import { PestanaAbc } from './PestanaAbc'
import { PestanaMasVendidos } from './PestanaMasVendidos'
import { PestanaReorden } from './PestanaReorden'

type ClavePestana = 'mas-vendidos' | 'abc' | 'reorden'

/** Para agregar una pestaña: añadir su entrada aquí y su componente en `contenido`. */
const PESTANAS: { clave: ClavePestana; etiqueta: string; soloCompleta: boolean }[] = [
  { clave: 'mas-vendidos', etiqueta: 'Más vendidos', soloCompleta: false },
  { clave: 'abc', etiqueta: 'Clasificación ABC', soloCompleta: true },
  { clave: 'reorden', etiqueta: 'Punto de reorden', soloCompleta: true },
]

const contenido: Record<ClavePestana, () => ReactNode> = {
  'mas-vendidos': () => <PestanaMasVendidos />,
  abc: () => <PestanaAbc />,
  reorden: () => <PestanaReorden />,
}

export function AnaliticaPage() {
  const { usuario } = useSesion()
  const completa = puedeVerAnaliticaCompleta(usuario?.rol)
  const [params, setParams] = useSearchParams()
  const refs = useRef<(HTMLButtonElement | null)[]>([])

  const visibles = PESTANAS.filter((p) => completa || !p.soloCompleta)
  // Una pestaña no permitida o desconocida en la URL cae en la primera visible.
  const activa = visibles.find((p) => p.clave === params.get('pestana')) ?? visibles[0]

  const seleccionar = (clave: ClavePestana) => setParams({ pestana: clave }, { replace: true })

  const alTeclear = (e: KeyboardEvent, indice: number) => {
    const paso = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0
    if (!paso) return
    e.preventDefault()
    const destino = (indice + paso + visibles.length) % visibles.length
    seleccionar(visibles[destino].clave)
    refs.current[destino]?.focus()
  }

  return (
    <section className="flex flex-col gap-4">
      <h1>Analítica</h1>
      <PanelEjecucion />

      <div role="tablist" aria-label="Secciones de analítica" className="flex gap-1 overflow-x-auto border-b border-oxford/10">
        {visibles.map((p, i) => {
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
