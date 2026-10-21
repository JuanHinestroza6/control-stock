import { useState } from 'react'
import { Link } from 'react-router'
import { Badge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { usePlanes } from '../../hooks/useCompras'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { puedeGestionarPlanes } from '../../lib/permisos'
import type { EstadoPlan, PlanCompraResumenResponse } from '../../tipos/compras'
import { ModalSugerirPlan } from './ModalSugerirPlan'
import { ESTADOS_PLAN, ETIQUETA_ORIGEN, etiquetaEstadoPlan, TONO_ESTADO_PLAN } from './textosPlan'

const TAMANO_PAGINA = 20
const clasesLink = 'font-medium text-acento hover:underline'

export function ComprasPage() {
  const { usuario } = useSesion()
  const puedeGestionar = puedeGestionarPlanes(usuario?.rol)
  const [estado, setEstado] = useState('')
  const [pagina, setPagina] = useState(0)
  const [sugiriendo, setSugiriendo] = useState(false)

  const consulta = usePlanes({
    estado: estado ? (estado as EstadoPlan) : undefined,
    page: pagina,
    size: TAMANO_PAGINA,
    sort: 'creadoEn,desc',
  })

  const columnas: Columna<PlanCompraResumenResponse>[] = [
    {
      clave: 'numero',
      encabezado: 'Plan',
      celda: (p) => (
        <Link to={`/compras/${p.id}`} className={clasesLink}>
          {p.numero}
        </Link>
      ),
    },
    {
      clave: 'estado',
      encabezado: 'Estado',
      celda: (p) => (
        <div className="flex flex-col items-start gap-1">
          <Badge tono={TONO_ESTADO_PLAN[p.estado]}>{etiquetaEstadoPlan(p.estado)}</Badge>
          {p.atrasado && <Badge tono="alerta">Atrasado</Badge>}
        </div>
      ),
    },
    { clave: 'origen', encabezado: 'Origen', ocultarEnMovil: true, celda: (p) => ETIQUETA_ORIGEN[p.origen] },
    {
      clave: 'creado',
      encabezado: 'Creado',
      ocultarEnMovil: true,
      celda: (p) => (
        <div>
          <div>{formatearFecha(p.creadoEn, true)}</div>
          {p.creadoPor && <div className="text-xs text-oxford/60">{p.creadoPor}</div>}
        </div>
      ),
    },
    {
      clave: 'llegada',
      encabezado: 'Llegada esperada',
      ocultarEnMovil: true,
      celda: (p) => (p.llegadaEsperada ? formatearFecha(p.llegadaEsperada) : '—'),
    },
    { clave: 'lineas', encabezado: 'Líneas', className: 'text-right', celda: (p) => p.lineas },
    { clave: 'unidades', encabezado: 'Unidades', className: 'text-right', ocultarEnMovil: true, celda: (p) => p.unidades },
    { clave: 'total', encabezado: 'Total estimado', className: 'text-right', celda: (p) => formatearMoneda(p.costoEstimadoTotal) },
  ]

  let contenido
  if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else if (consulta.data.content.length === 0) {
    contenido =
      estado !== '' ? (
        <EstadoVacio
          mensaje="Ningún plan coincide con el filtro."
          accion={
            <button type="button" className={clasesLink} onClick={() => setEstado('')}>
              Limpiar filtro
            </button>
          }
        />
      ) : (
        <EstadoVacio
          mensaje="Aún no hay planes de compra."
          accion={
            puedeGestionar ? (
              <button type="button" className={clasesLink} onClick={() => setSugiriendo(true)}>
                Generar una sugerencia
              </button>
            ) : undefined
          }
        />
      )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(p) => p.id} descripcion="Listado de planes de compra" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1>Compras</h1>
        {puedeGestionar && (
          <div className="flex flex-wrap gap-2">
            <Boton variante="secundario" onClick={() => setSugiriendo(true)}>
              Generar sugerencia
            </Boton>
            <Link
              to="/compras/nueva"
              className="inline-flex min-h-10 items-center rounded-lg bg-oxford px-4 py-2 text-sm font-medium text-fondo hover:bg-oxford/90"
            >
              Nuevo plan
            </Link>
          </div>
        )}
      </div>

      <form role="search" aria-label="Filtros de planes" onSubmit={(e) => e.preventDefault()} className="max-w-xs">
        <Selector
          etiqueta="Estado"
          placeholder="Todos"
          opciones={ESTADOS_PLAN.map((e) => ({ valor: e.valor, etiqueta: e.etiqueta }))}
          value={estado}
          onChange={(e) => {
            setEstado(e.target.value)
            setPagina(0)
          }}
        />
      </form>

      {contenido}

      {sugiriendo && <ModalSugerirPlan onCerrar={() => setSugiriendo(false)} />}
    </section>
  )
}
