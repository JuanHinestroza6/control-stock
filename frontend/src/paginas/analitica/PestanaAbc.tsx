import { useEffect, useState } from 'react'
import { BadgeClaseAbc } from '../../componentes/dominio/BadgeClaseAbc'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useAbc, useResumenAbc } from '../../hooks/useAnalitica'
import { useCategorias } from '../../hooks/useCategorias'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { puedeRecalcularAnalitica } from '../../lib/permisos'
import type { AbcVarianteResponse, ClaseAbc, ResumenClaseAbc } from '../../tipos/analitica'

const TAMANO_PAGINA = 20
const RETARDO_BUSQUEDA_MS = 300
const CLASES: ClaseAbc[] = ['A', 'B', 'C']
const OPCIONES_CLASE = CLASES.map((c) => ({ valor: c, etiqueta: `Clase ${c}` }))
const ORDENES = [
  { valor: 'posicion,asc', etiqueta: 'Posición' },
  { valor: 'ingresoNeto,desc', etiqueta: 'Mayor ingreso' },
  { valor: 'sku,asc', etiqueta: 'SKU' },
]

const formatoNumero = new Intl.NumberFormat('es-CO')
const formatoPorcentaje = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatoPorcentaje1 = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

const COLUMNAS: Columna<AbcVarianteResponse>[] = [
  { clave: 'posicion', encabezado: '#', celda: (f) => f.posicion, className: 'w-12 tabular-nums' },
  { clave: 'sku', encabezado: 'SKU', celda: (f) => f.sku, ocultarEnMovil: true },
  {
    clave: 'producto',
    encabezado: 'Producto',
    celda: (f) => (
      <div className="flex flex-col">
        <span className="font-medium text-oxford">{f.productoNombre}</span>
        <span className="text-xs text-oxford/60 md:hidden">{f.sku}</span>
      </div>
    ),
  },
  { clave: 'talla', encabezado: 'Talla', celda: (f) => f.talla, ocultarEnMovil: true },
  { clave: 'color', encabezado: 'Color', celda: (f) => f.color, ocultarEnMovil: true },
  { clave: 'categoria', encabezado: 'Categoría', celda: (f) => f.categoria.nombre, ocultarEnMovil: true },
  { clave: 'unidades', encabezado: 'Unidades netas', celda: (f) => formatoNumero.format(f.unidadesNetas), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'ingreso', encabezado: 'Ingreso neto', celda: (f) => formatearMoneda(f.ingresoNeto), className: 'text-right tabular-nums' },
  { clave: 'participacion', encabezado: 'Particip.', celda: (f) => `${formatoPorcentaje.format(f.participacion)} %`, className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'acumulado', encabezado: 'Acumulado', celda: (f) => `${formatoPorcentaje.format(f.acumulado)} %`, className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'clase', encabezado: 'Clase', celda: (f) => <BadgeClaseAbc clase={f.claseAbc} />, className: 'text-center' },
]

function TarjetaClase({ clase, resumen }: { clase: ClaseAbc; resumen: ResumenClaseAbc | undefined }) {
  return (
    <Tarjeta data-testid={`tarjeta-clase-${clase}`} className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <BadgeClaseAbc clase={clase} />
        <span className="text-xs font-medium text-oxford/60">Clase {clase}</span>
      </div>
      <p className="text-lg font-bold tabular-nums text-oxford">{formatearMoneda(resumen?.ingreso ?? 0)}</p>
      <p className="text-xs text-oxford/70">
        {formatoNumero.format(resumen?.variantes ?? 0)} variantes · {formatoPorcentaje1.format(resumen?.participacion ?? 0)} % del ingreso
      </p>
    </Tarjeta>
  )
}

/** Clasificación ABC por variante; solo ADMIN, INVENTARIO y CONTABILIDAD (la página ya filtra por rol). */
export function PestanaAbc() {
  const { usuario } = useSesion()
  const resumen = useResumenAbc()
  const categorias = useCategorias()

  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const [clase, setClase] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [sort, setSort] = useState('posicion,asc')
  const [pagina, setPagina] = useState(0)

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(texto.trim())
      setPagina(0)
    }, RETARDO_BUSQUEDA_MS)
    return () => clearTimeout(id)
  }, [texto])

  const sinEjecucion = resumen.data !== undefined && resumen.data.ejecucion === null
  const lista = useAbc(
    {
      q: q || undefined,
      clase: clase ? (clase as ClaseAbc) : undefined,
      categoriaId: categoriaId ? Number(categoriaId) : undefined,
      page: pagina,
      size: TAMANO_PAGINA,
      sort,
    },
    resumen.data !== undefined && !sinEjecucion,
  )

  if (resumen.isPending) return <EstadoCarga />
  if (resumen.isError && !resumen.data) {
    return <EstadoError mensaje={mensajeError(resumen.error)} onReintentar={() => void resumen.refetch()} />
  }
  const datos = resumen.data
  if (!datos || datos.ejecucion === null) {
    return (
      <EstadoVacio
        mensaje={
          puedeRecalcularAnalitica(usuario?.rol)
            ? 'Aún no se ha ejecutado la analítica. Usa "Recalcular" en el panel superior para clasificar las variantes.'
            : 'Aún no se ha ejecutado la analítica. Pide a un administrador que la recalcule.'
        }
      />
    )
  }

  const porClase = (c: ClaseAbc) => datos.clases.find((r) => r.clase === c)
  const hayFiltros = q !== '' || clase !== '' || categoriaId !== ''
  const pag = lista.data

  return (
    <div className="flex flex-col gap-4">
      <p data-testid="ventana-abc" className="text-sm text-oxford/70">
        Ventana analizada: {formatearFecha(datos.ventanaDesde)} a {formatearFecha(datos.ventanaHasta)} · Ingreso neto total{' '}
        <strong className="text-oxford">{formatearMoneda(datos.totalIngreso)}</strong>
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {CLASES.map((c) => (
          <TarjetaClase key={c} clase={c} resumen={porClase(c)} />
        ))}
      </div>

      <form
        role="search"
        aria-label="Filtros de clasificación ABC"
        onSubmit={(e) => e.preventDefault()}
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:items-end"
      >
        <Campo etiqueta="Buscar" type="search" maxLength={60} placeholder="SKU o producto" value={texto} onChange={(e) => setTexto(e.target.value)} />
        <Selector
          etiqueta="Clase"
          placeholder="Todas"
          opciones={OPCIONES_CLASE}
          value={clase}
          onChange={(e) => {
            setClase(e.target.value)
            setPagina(0)
          }}
        />
        <Selector
          etiqueta="Categoría"
          placeholder="Todas"
          opciones={(Array.isArray(categorias.data) ? categorias.data : []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
          value={categoriaId}
          onChange={(e) => {
            setCategoriaId(e.target.value)
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

      {lista.isPending ? (
        <EstadoCarga />
      ) : lista.isError && !pag ? (
        <EstadoError mensaje={mensajeError(lista.error)} onReintentar={() => void lista.refetch()} />
      ) : pag && pag.content.length === 0 ? (
        <EstadoVacio mensaje={hayFiltros ? 'Ninguna variante coincide con los filtros.' : 'No hay variantes clasificadas.'} />
      ) : pag ? (
        <>
          <Tabla columnas={COLUMNAS} filas={pag.content} claveFila={(f) => f.varianteId} descripcion="Clasificación ABC por variante" />
          <Paginacion pagina={pag.page} onCambiar={setPagina} />
        </>
      ) : null}
    </div>
  )
}
