import { useState } from 'react'
import { Link } from 'react-router'
import { BadgeEstadoVenta } from '../../componentes/dominio/BadgeEstadoVenta'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useSesion } from '../../hooks/useSesion'
import { useVentas } from '../../hooks/useVentas'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { puedeVender } from '../../lib/permisos'
import type { EstadoVenta, VentaResumenResponse } from '../../tipos/ventas'

const TAMANO_PAGINA = 20
const clasesLink = 'font-medium text-acento hover:underline'

const ORDENES = [
  { valor: 'fecha,desc', etiqueta: 'Más recientes' },
  { valor: 'fecha,asc', etiqueta: 'Más antiguas' },
  { valor: 'total,desc', etiqueta: 'Total (mayor a menor)' },
  { valor: 'total,asc', etiqueta: 'Total (menor a mayor)' },
]

const ESTADOS = [
  { valor: 'REGISTRADA', etiqueta: 'Registradas' },
  { valor: 'ANULADA', etiqueta: 'Anuladas' },
]


export function VentasPage() {
  const { usuario } = useSesion()
  const puedeNueva = puedeVender(usuario?.rol)

  const [estado, setEstado] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [sort, setSort] = useState('fecha,desc')
  const [pagina, setPagina] = useState(0)

  const rangoInvalido = desde !== '' && hasta !== '' && desde > hasta
  const consulta = useVentas({
    estado: estado ? (estado as EstadoVenta) : undefined,
    desde: !rangoInvalido ? desde || undefined : undefined,
    hasta: !rangoInvalido ? hasta || undefined : undefined,
    page: pagina,
    size: TAMANO_PAGINA,
    sort,
  })

  const hayFiltros = estado !== '' || desde !== '' || hasta !== ''

  const columnas: Columna<VentaResumenResponse>[] = [
    {
      clave: 'numero',
      encabezado: 'Venta',
      celda: (v) => (
        <Link to={`/ventas/${v.id}`} className={clasesLink}>
          {v.numero}
        </Link>
      ),
    },
    { clave: 'fecha', encabezado: 'Fecha', celda: (v) => formatearFecha(v.fecha, true) },
    { clave: 'vendedor', encabezado: 'Vendedor', celda: (v) => v.vendedor, ocultarEnMovil: true },
    { clave: 'unidades', encabezado: 'Unidades', celda: (v) => v.unidades, className: 'text-right', ocultarEnMovil: true },
    { clave: 'total', encabezado: 'Total', celda: (v) => formatearMoneda(v.total), className: 'text-right' },
    { clave: 'estado', encabezado: 'Estado', celda: (v) => <BadgeEstadoVenta estado={v.estado} /> },
  ]

  const limpiar = () => {
    setEstado('')
    setDesde('')
    setHasta('')
    setPagina(0)
  }

  let contenido
  if (rangoInvalido) {
    contenido = null
  } else if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else if (consulta.data.content.length === 0) {
    contenido = hayFiltros ? (
      <EstadoVacio
        mensaje="Ninguna venta coincide con los filtros."
        accion={
          <button type="button" onClick={limpiar} className={clasesLink}>
            Limpiar filtros
          </button>
        }
      />
    ) : (
      <EstadoVacio
        mensaje="Aún no hay ventas registradas."
        accion={
          puedeNueva ? (
            <Link to="/ventas/nueva" className={clasesLink}>
              Registrar la primera venta
            </Link>
          ) : undefined
        }
      />
    )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(v) => v.id} descripcion="Listado de ventas" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1>Ventas</h1>
        {puedeNueva && (
          <Link
            to="/ventas/nueva"
            className="inline-flex min-h-10 items-center rounded-lg bg-oxford px-4 py-2 text-sm font-medium text-fondo hover:bg-oxford/90"
          >
            Nueva venta
          </Link>
        )}
      </div>

      <form
        role="search"
        aria-label="Filtros de ventas"
        onSubmit={(e) => e.preventDefault()}
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4"
      >
        <Selector
          etiqueta="Estado"
          placeholder="Todos"
          opciones={ESTADOS}
          value={estado}
          onChange={(e) => {
            setEstado(e.target.value)
            setPagina(0)
          }}
        />
        <Campo
          etiqueta="Desde"
          type="date"
          value={desde}
          max={hasta || undefined}
          onChange={(e) => {
            setDesde(e.target.value)
            setPagina(0)
          }}
        />
        <Campo
          etiqueta="Hasta"
          type="date"
          value={hasta}
          min={desde || undefined}
          error={rangoInvalido ? 'La fecha inicial no puede ser posterior a la final.' : undefined}
          onChange={(e) => {
            setHasta(e.target.value)
            setPagina(0)
          }}
        />
        <Selector
          etiqueta="Ordenar por"
          opciones={ORDENES}
          value={sort}
          onChange={(e) => {
            setSort(e.target.value)
            setPagina(0)
          }}
        />
      </form>

      {contenido}
    </section>
  )
}
