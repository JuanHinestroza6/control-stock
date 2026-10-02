import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Badge } from '../../componentes/ui/Badge'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useCategorias } from '../../hooks/useCategorias'
import { useProductos } from '../../hooks/useProductos'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import { puedeEditarCatalogo } from '../../lib/permisos'
import type { ProductoResumenResponse } from '../../tipos/catalogo'

const TAMANO_PAGINA = 20
const RETARDO_BUSQUEDA_MS = 300

const ORDENES = [
  { valor: 'nombre,asc', etiqueta: 'Nombre (A-Z)' },
  { valor: 'nombre,desc', etiqueta: 'Nombre (Z-A)' },
  { valor: 'codigo,asc', etiqueta: 'Código' },
  { valor: 'precioVenta,asc', etiqueta: 'Precio (menor a mayor)' },
  { valor: 'precioVenta,desc', etiqueta: 'Precio (mayor a menor)' },
  { valor: 'creadoEn,desc', etiqueta: 'Más recientes' },
]

const ESTADOS = [
  { valor: 'true', etiqueta: 'Activos' },
  { valor: 'false', etiqueta: 'Inactivos' },
]

const clasesLink = 'font-medium text-acento hover:underline'

export function ProductosPage() {
  const { usuario } = useSesion()
  const puedeEditar = puedeEditarCatalogo(usuario?.rol)
  const categorias = useCategorias()

  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [activo, setActivo] = useState('')
  const [sort, setSort] = useState('nombre,asc')
  const [pagina, setPagina] = useState(0)

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(texto.trim())
      setPagina(0)
    }, RETARDO_BUSQUEDA_MS)
    return () => clearTimeout(id)
  }, [texto])

  const consulta = useProductos({
    q: q || undefined,
    categoriaId: categoriaId ? Number(categoriaId) : undefined,
    activo: activo === '' ? undefined : activo === 'true',
    page: pagina,
    size: TAMANO_PAGINA,
    sort,
  })

  const hayFiltros = q !== '' || categoriaId !== '' || activo !== ''

  const columnas: Columna<ProductoResumenResponse>[] = [
    { clave: 'codigo', encabezado: 'Código', celda: (p) => p.codigo, ocultarEnMovil: true },
    {
      clave: 'nombre',
      encabezado: 'Producto',
      celda: (p) => (
        <Link to={`/productos/${p.id}`} className={clasesLink}>
          {p.nombre}
        </Link>
      ),
    },
    { clave: 'categoria', encabezado: 'Categoría', celda: (p) => p.categoria.nombre, ocultarEnMovil: true },
    { clave: 'precio', encabezado: 'Precio venta', celda: (p) => formatearMoneda(p.precioVenta), className: 'text-right' },
    { clave: 'variantes', encabezado: 'Variantes', celda: (p) => p.totalVariantes, className: 'text-right', ocultarEnMovil: true },
    { clave: 'stock', encabezado: 'Disponible', celda: (p) => p.stockDisponibleTotal, className: 'text-right' },
    {
      clave: 'estado',
      encabezado: 'Estado',
      celda: (p) => <Badge tono={p.activo ? 'ok' : 'neutro'}>{p.activo ? 'Activo' : 'Inactivo'}</Badge>,
    },
  ]

  const limpiar = () => {
    setTexto('')
    setQ('')
    setCategoriaId('')
    setActivo('')
    setPagina(0)
  }

  let contenido
  if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else if (consulta.data.content.length === 0) {
    contenido = hayFiltros ? (
      <EstadoVacio
        mensaje="Ningún producto coincide con los filtros."
        accion={
          <button type="button" onClick={limpiar} className={clasesLink}>
            Limpiar filtros
          </button>
        }
      />
    ) : (
      <EstadoVacio
        mensaje="Aún no hay productos."
        accion={
          puedeEditar ? (
            <Link to="/productos/nuevo" className={clasesLink}>
              Crear el primer producto
            </Link>
          ) : undefined
        }
      />
    )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(p) => p.id} descripcion="Listado de productos" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1>Productos</h1>
        <div className="flex flex-wrap items-center gap-4">
          <Link to="/productos/categorias" className={clasesLink}>
            Categorías
          </Link>
          {puedeEditar && (
            <Link
              to="/productos/nuevo"
              className="inline-flex min-h-10 items-center rounded-lg bg-oxford px-4 py-2 text-sm font-medium text-fondo hover:bg-oxford/90"
            >
              Nuevo producto
            </Link>
          )}
        </div>
      </div>

      <form
        role="search"
        aria-label="Filtros de productos"
        onSubmit={(e) => e.preventDefault()}
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4"
      >
        <Campo
          etiqueta="Buscar"
          type="search"
          maxLength={60}
          placeholder="Código o nombre"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
        />
        <Selector
          etiqueta="Categoría"
          placeholder="Todas"
          opciones={(categorias.data ?? []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
          value={categoriaId}
          onChange={(e) => {
            setCategoriaId(e.target.value)
            setPagina(0)
          }}
        />
        <Selector
          etiqueta="Estado"
          placeholder="Todos"
          opciones={ESTADOS}
          value={activo}
          onChange={(e) => {
            setActivo(e.target.value)
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
