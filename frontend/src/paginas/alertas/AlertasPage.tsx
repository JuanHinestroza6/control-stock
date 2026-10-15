import { useState } from 'react'
import { Link } from 'react-router'
import { BadgeTipoAlerta } from '../../componentes/dominio/BadgeTipoAlerta'
import { Boton } from '../../componentes/ui/Boton'
import { Badge } from '../../componentes/ui/Badge'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useAlertas, useAtenderAlerta } from '../../hooks/useAlertas'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearFecha } from '../../lib/formato'
import { puedeAtenderAlertas, puedeVerKardex } from '../../lib/permisos'
import type { AlertaResponse, EstadoAlerta, TipoAlerta } from '../../tipos/alertas'

const TAMANO_PAGINA = 20
const clasesLink = 'font-medium text-acento hover:underline'

const ESTADOS = [
  { valor: 'ACTIVA', etiqueta: 'Activas' },
  { valor: 'ATENDIDA', etiqueta: 'Atendidas' },
]
const TIPOS = [
  { valor: 'AGOTADO', etiqueta: 'Agotado' },
  { valor: 'STOCK_BAJO', etiqueta: 'Stock bajo' },
  { valor: 'SIN_ROTACION', etiqueta: 'Sin rotación' },
]

export function AlertasPage() {
  const { usuario } = useSesion()
  const puedeAtender = puedeAtenderAlertas(usuario?.rol)
  const verKardex = puedeVerKardex(usuario?.rol)
  const atender = useAtenderAlerta()

  const [estado, setEstado] = useState('ACTIVA')
  const [tipo, setTipo] = useState('')
  const [pagina, setPagina] = useState(0)

  const consulta = useAlertas({
    estado: estado ? (estado as EstadoAlerta) : undefined,
    tipo: tipo ? (tipo as TipoAlerta) : undefined,
    page: pagina,
    size: TAMANO_PAGINA,
  })

  const hayFiltros = estado !== 'ACTIVA' || tipo !== ''
  const limpiar = () => {
    setEstado('ACTIVA')
    setTipo('')
    setPagina(0)
  }

  const columnas: Columna<AlertaResponse>[] = [
    { clave: 'tipo', encabezado: 'Alerta', celda: (a) => (
        <div className="flex flex-col items-start gap-1">
          <BadgeTipoAlerta tipo={a.tipo} />
          {a.tipo === 'SIN_ROTACION' && <span className="text-xs text-oxford/70">El producto no vende hace 60 días o más</span>}
        </div>
      ),
    },
    {
      clave: 'variante',
      encabezado: 'Variante',
      celda: (a) => (
        <div>
          <div>{a.productoNombre}</div>
          <div className="text-xs text-oxford/60">
            {a.sku} · {a.talla} · {a.color}
          </div>
        </div>
      ),
    },
    { clave: 'disponible', encabezado: 'Disponible', celda: (a) => a.stockDisponible, className: 'text-right' },
    { clave: 'umbral', encabezado: 'Umbral', celda: (a) => a.umbral, className: 'text-right', ocultarEnMovil: true },
    { clave: 'creada', encabezado: 'Desde', celda: (a) => formatearFecha(a.creadaEn, true), ocultarEnMovil: true },
    {
      clave: 'estado',
      encabezado: 'Estado',
      celda: (a) =>
        a.estado === 'ACTIVA' ? (
          <Badge tono="acento">Activa</Badge>
        ) : (
          <div>
            <Badge tono="ok">Atendida</Badge>
            <div className="mt-1 text-xs text-oxford/60">{a.atendidaPor ?? 'Automático'}</div>
          </div>
        ),
    },
    {
      clave: 'acciones',
      encabezado: 'Acciones',
      celda: (a) => (
        <div className="flex flex-wrap gap-3">
          {verKardex && (
            <Link to={`/inventario/kardex?varianteId=${a.varianteId}`} className={clasesLink} aria-label={`Kardex de ${a.sku}`}>
              Kardex
            </Link>
          )}
          {puedeAtender && a.estado === 'ACTIVA' && (
            <button
              type="button"
              className={clasesLink}
              aria-label={`Atender ${a.sku}`}
              disabled={atender.isPending}
              onClick={() => atender.mutate(a.id)}
            >
              Atender
            </button>
          )}
        </div>
      ),
    },
  ]

  let contenido
  if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else if (consulta.data.content.length === 0) {
    contenido = hayFiltros ? (
      <EstadoVacio
        mensaje="Ninguna alerta coincide con los filtros."
        accion={
          <button type="button" onClick={limpiar} className={clasesLink}>
            Limpiar filtros
          </button>
        }
      />
    ) : (
      <EstadoVacio mensaje="No hay alertas activas. El inventario está al día." />
    )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(a) => a.id} descripcion="Listado de alertas" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <h1>Alertas</h1>

      <form
        role="search"
        aria-label="Filtros de alertas"
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
        <Selector
          etiqueta="Tipo"
          placeholder="Todos"
          opciones={TIPOS}
          value={tipo}
          onChange={(e) => {
            setTipo(e.target.value)
            setPagina(0)
          }}
        />
      </form>

      {atender.isError && (
        <div role="alert" className="flex items-center gap-3 rounded-lg border border-critico/40 bg-critico/5 p-3 text-sm font-medium">
          {mensajeError(atender.error)}
          <Boton variante="secundario" onClick={() => atender.reset()}>
            Cerrar
          </Boton>
        </div>
      )}

      {contenido}
    </section>
  )
}
