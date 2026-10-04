import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { BadgeStock } from '../../componentes/dominio/BadgeStock'
import { ModalAjuste } from '../../componentes/dominio/ModalAjuste'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useCategorias } from '../../hooks/useCategorias'
import { useInventario } from '../../hooks/useInventario'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import { puedeMoverStock, puedeReservar, puedeVerKardex } from '../../lib/permisos'
import type { EstadoStock } from '../../tipos/catalogo'
import type { InventarioItemResponse } from '../../tipos/inventario'

const TAMANO_PAGINA = 20
const RETARDO_BUSQUEDA_MS = 300

const ORDENES = [
  { valor: 'producto.nombre,asc', etiqueta: 'Producto (A-Z)' },
  { valor: 'producto.nombre,desc', etiqueta: 'Producto (Z-A)' },
  { valor: 'sku,asc', etiqueta: 'SKU' },
  { valor: 'stockFisico,asc', etiqueta: 'Stock físico (menor a mayor)' },
  { valor: 'stockFisico,desc', etiqueta: 'Stock físico (mayor a menor)' },
]

const ESTADOS = [
  { valor: 'OK', etiqueta: 'Stock óptimo' },
  { valor: 'BAJO', etiqueta: 'Stock bajo' },
  { valor: 'AGOTADO', etiqueta: 'Agotado' },
]

const clasesLink = 'font-medium text-acento hover:underline'

export function InventarioPage() {
  const { usuario } = useSesion()
  const puedeMover = puedeMoverStock(usuario?.rol)
  const verKardex = puedeVerKardex(usuario?.rol)
  const verReservas = puedeReservar(usuario?.rol)
  const categorias = useCategorias()

  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [estadoStock, setEstadoStock] = useState('')
  const [inactivas, setInactivas] = useState(false)
  const [sort, setSort] = useState('producto.nombre,asc')
  const [pagina, setPagina] = useState(0)
  const [ajustando, setAjustando] = useState<InventarioItemResponse | null>(null)

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(texto.trim())
      setPagina(0)
    }, RETARDO_BUSQUEDA_MS)
    return () => clearTimeout(id)
  }, [texto])

  const consulta = useInventario({
    q: q || undefined,
    categoriaId: categoriaId ? Number(categoriaId) : undefined,
    estadoStock: estadoStock ? (estadoStock as EstadoStock) : undefined,
    incluirInactivas: inactivas,
    page: pagina,
    size: TAMANO_PAGINA,
    sort,
  })

  const hayFiltros = q !== '' || categoriaId !== '' || estadoStock !== '' || inactivas

  const columnas: Columna<InventarioItemResponse>[] = [
    { clave: 'sku', encabezado: 'SKU', celda: (v) => v.sku, ocultarEnMovil: true },
    {
      clave: 'producto',
      encabezado: 'Producto',
      celda: (v) => (
        <div className="flex flex-col">
          <Link to={`/productos/${v.productoId}`} className={clasesLink}>
            {v.productoNombre}
          </Link>
          <span className="text-xs text-oxford/70">
            {v.talla} · {v.color}
            {!v.activo && ' · Inactiva'}
          </span>
        </div>
      ),
    },
    { clave: 'categoria', encabezado: 'Categoría', celda: (v) => v.categoria.nombre, ocultarEnMovil: true },
    { clave: 'fisico', encabezado: 'Físico', celda: (v) => v.stockFisico, className: 'text-right', ocultarEnMovil: true },
    { clave: 'reservado', encabezado: 'Reservado', celda: (v) =>
        v.stockReservado > 0 && verReservas ? (
          <Link to={`/reservas?varianteId=${v.varianteId}`} className={clasesLink} aria-label={`Ver apartados de ${v.sku}`}>
            {v.stockReservado}
          </Link>
        ) : (
          v.stockReservado
        ),
      className: 'text-right',
      ocultarEnMovil: true,
    },
    { clave: 'disponible', encabezado: 'Disponible', celda: (v) => v.stockDisponible, className: 'text-right' },
    { clave: 'estado', encabezado: 'Estado', celda: (v) => <BadgeStock estado={v.estadoStock} /> },
    { clave: 'precio', encabezado: 'Precio', celda: (v) => formatearMoneda(v.precioVenta), className: 'text-right', ocultarEnMovil: true },
  ]
  if (puedeMover || verKardex) {
    columnas.push({
      clave: 'acciones',
      encabezado: 'Acciones',
      celda: (v) => (
        <div className="flex flex-wrap gap-3">
          {puedeMover && (
            <button type="button" onClick={() => setAjustando(v)} className={clasesLink} aria-label={`Ajuste de ${v.sku}`}>
              Ajuste
            </button>
          )}
          {puedeMover && v.activo && (
            <Link to={`/inventario/entradas/nueva?productoId=${v.productoId}`} className={clasesLink} aria-label={`Entrada de ${v.sku}`}>
              Entrada
            </Link>
          )}
          {verKardex && (
            <Link to={`/inventario/kardex?varianteId=${v.varianteId}`} className={clasesLink} aria-label={`Kardex de ${v.sku}`}>
              Kardex
            </Link>
          )}
        </div>
      ),
    })
  }

  const limpiar = () => {
    setTexto('')
    setQ('')
    setCategoriaId('')
    setEstadoStock('')
    setInactivas(false)
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
        mensaje="Ninguna variante coincide con los filtros."
        accion={
          <button type="button" onClick={limpiar} className={clasesLink}>
            Limpiar filtros
          </button>
        }
      />
    ) : (
      <EstadoVacio
        mensaje="Aún no hay variantes en el inventario."
        accion={
          <Link to="/productos" className={clasesLink}>
            Ir a productos
          </Link>
        }
      />
    )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(v) => v.varianteId} descripcion="Inventario actual por variante" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1>Inventario</h1>
        <div className="flex flex-wrap items-center gap-4">
          {verKardex && (
            <Link to="/inventario/kardex" className={clasesLink}>
              Kardex
            </Link>
          )}
          {puedeMover && (
            <Link
              to="/inventario/entradas/nueva"
              className="inline-flex min-h-10 items-center rounded-lg bg-oxford px-4 py-2 text-sm font-medium text-fondo hover:bg-oxford/90"
            >
              Nueva entrada
            </Link>
          )}
        </div>
      </div>

      <form
        role="search"
        aria-label="Filtros de inventario"
        onSubmit={(e) => e.preventDefault()}
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5 lg:items-end"
      >
        <Campo
          etiqueta="Buscar"
          type="search"
          maxLength={60}
          placeholder="SKU, código o nombre"
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
          etiqueta="Estado de stock"
          placeholder="Todos"
          opciones={ESTADOS}
          value={estadoStock}
          onChange={(e) => {
            setEstadoStock(e.target.value)
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
        <label className="flex min-h-10 items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={inactivas}
            onChange={(e) => {
              setInactivas(e.target.checked)
              setPagina(0)
            }}
          />
          Incluir inactivas
        </label>
      </form>

      {contenido}
      {ajustando && <ModalAjuste variante={ajustando} onCerrar={() => setAjustando(null)} />}
    </section>
  )
}
