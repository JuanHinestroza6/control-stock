import { useEffect, useState } from 'react'
import { Badge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { usePronosticos } from '../../hooks/useAnalitica'
import { useCategorias } from '../../hooks/useCategorias'
import { mensajeError } from '../../lib/errores'
import { textoMotivo } from '../../lib/pronostico'
import type { MetodoPronostico, PronosticoProductoResponse } from '../../tipos/analitica'
import { BadgeMetodoPronostico, BadgeWape, ProximosMeses } from './componentesPronostico'
import { DetallePronostico } from './DetallePronostico'
import { NotaRiesgoBacktest } from './NotaRiesgoBacktest'

const TAMANO_PAGINA = 20
const RETARDO_BUSQUEDA_MS = 300
const OPCIONES_METODO = [
  { valor: 'SES', etiqueta: 'Suavizado simple (SES)' },
  { valor: 'HOLT_WINTERS', etiqueta: 'Holt-Winters' },
]
const ORDENES = [
  { valor: 'codigo,asc', etiqueta: 'Código' },
  { valor: 'errorWape,asc', etiqueta: 'Menor error (WAPE)' },
  { valor: 'errorWape,desc', etiqueta: 'Mayor error (WAPE)' },
]

/** Pronóstico mensual por producto (REQ-09/REQ-10); ADMIN, INVENTARIO y CONTABILIDAD (la página ya filtra por rol). */
export function PestanaPronostico() {
  const categorias = useCategorias()
  const [seleccionado, setSeleccionado] = useState<number | null>(null)
  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [metodo, setMetodo] = useState('')
  const [sort, setSort] = useState('codigo,asc')
  const [pagina, setPagina] = useState(0)

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(texto.trim())
      setPagina(0)
    }, RETARDO_BUSQUEDA_MS)
    return () => clearTimeout(id)
  }, [texto])

  const lista = usePronosticos(
    {
      q: q || undefined,
      categoriaId: categoriaId ? Number(categoriaId) : undefined,
      metodo: metodo ? (metodo as MetodoPronostico) : undefined,
      page: pagina,
      size: TAMANO_PAGINA,
      sort,
    },
    seleccionado === null,
  )

  if (seleccionado !== null) return <DetallePronostico productoId={seleccionado} onVolver={() => setSeleccionado(null)} />

  const pag = lista.data
  const hayFiltros = q !== '' || categoriaId !== '' || metodo !== ''
  const reiniciar = (set: (v: string) => void) => (e: { target: { value: string } }) => {
    set(e.target.value)
    setPagina(0)
  }

  const columnas: Columna<PronosticoProductoResponse>[] = [
    {
      clave: 'producto',
      encabezado: 'Producto',
      celda: (f) => (
        <div className="flex flex-col">
          <span className="font-medium text-oxford">{f.nombre}</span>
          <span className="text-xs text-oxford/60">{f.codigo}</span>
        </div>
      ),
    },
    { clave: 'categoria', encabezado: 'Categoría', celda: (f) => f.categoria, ocultarEnMovil: true },
    { clave: 'metodo', encabezado: 'Método', celda: (f) => <BadgeMetodoPronostico metodo={f.metodo} /> },
    { clave: 'wape', encabezado: 'Error (WAPE)', celda: (f) => <BadgeWape wape={f.errorWape} />, className: 'text-right' },
    { clave: 'proximos', encabezado: 'Próximos 3 meses', celda: (f) => <ProximosMeses puntos={f.proximos} />, ocultarEnMovil: true },
    {
      clave: 'motivo',
      encabezado: 'Nota',
      celda: (f) => {
        const t = textoMotivo(f)
        return t ? <span className="text-xs text-oxford/70">{t}</span> : <span className="text-oxford/50">—</span>
      },
      ocultarEnMovil: true,
    },
    {
      clave: 'detalle',
      encabezado: 'Detalle',
      celda: (f) => (
        <Boton variante="secundario" onClick={() => setSeleccionado(f.productoId)} aria-label={`Ver detalle de ${f.nombre}`}>
          Ver detalle
        </Boton>
      ),
      className: 'text-right',
    },
  ]

  return (
    <div className="flex flex-col gap-4">
      <NotaRiesgoBacktest />

      <Tarjeta>
        <form
          role="search"
          aria-label="Filtros de pronóstico"
          onSubmit={(e) => e.preventDefault()}
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:items-end"
        >
          <Campo etiqueta="Buscar" type="search" maxLength={60} placeholder="Código o producto" value={texto} onChange={(e) => setTexto(e.target.value)} />
          <Selector
            etiqueta="Categoría"
            placeholder="Todas"
            opciones={(Array.isArray(categorias.data) ? categorias.data : []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
            value={categoriaId}
            onChange={reiniciar(setCategoriaId)}
          />
          <Selector etiqueta="Método" placeholder="Todos" opciones={OPCIONES_METODO} value={metodo} onChange={reiniciar(setMetodo)} />
          <Selector etiqueta="Ordenar por" opciones={ORDENES} value={sort} onChange={reiniciar(setSort)} />
        </form>
      </Tarjeta>

      {lista.isPending ? (
        <EstadoCarga />
      ) : lista.isError && !pag ? (
        <EstadoError mensaje={mensajeError(lista.error)} onReintentar={() => void lista.refetch()} />
      ) : pag && pag.content.length === 0 ? (
        <EstadoVacio
          mensaje={hayFiltros ? 'Ningún producto coincide con los filtros.' : 'Aún no hay pronósticos. Se calculan en la ejecución nocturna o con "Recalcular".'}
        />
      ) : pag ? (
        <>
          <Tabla columnas={columnas} filas={pag.content} claveFila={(f) => f.productoId} descripcion="Pronóstico de demanda por producto" />
          <Paginacion pagina={pag.page} onCambiar={setPagina} />
          <p className="flex flex-wrap items-center gap-2 text-xs text-oxford/60">
            Semáforo del error (WAPE): <Badge tono="ok">hasta 20 %</Badge> <Badge tono="alerta">20 a 40 %</Badge> <Badge tono="critico">más de 40 %</Badge>
          </p>
        </>
      ) : null}
    </div>
  )
}
