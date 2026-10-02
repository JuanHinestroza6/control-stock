import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { BadgeTipoMovimiento } from '../../componentes/dominio/BadgeTipoMovimiento'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useMovimientos } from '../../hooks/useInventario'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { CONFIG_TIPO_MOVIMIENTO } from '../../lib/movimientos'
import type { MovimientoResponse, TipoMovimiento } from '../../tipos/inventario'

const TAMANO_PAGINA = 20
const clasesLink = 'font-medium text-acento hover:underline'

const TIPOS = (Object.keys(CONFIG_TIPO_MOVIMIENTO) as TipoMovimiento[]).map((t) => ({
  valor: t,
  etiqueta: CONFIG_TIPO_MOVIMIENTO[t].etiqueta,
}))

function delta(antes: number, despues: number) {
  return antes === despues ? String(despues) : `${antes} → ${despues}`
}

export function KardexPage() {
  const [params, setParams] = useSearchParams()
  const idParam = Number(params.get('varianteId'))
  const varianteId = Number.isInteger(idParam) && idParam > 0 ? idParam : undefined

  const [tipo, setTipo] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [pagina, setPagina] = useState(0)

  const rangoInvalido = desde !== '' && hasta !== '' && desde > hasta

  const consulta = useMovimientos(
    {
      varianteId,
      tipo: tipo ? (tipo as TipoMovimiento) : undefined,
      desde: desde || undefined,
      hasta: hasta || undefined,
      page: pagina,
      size: TAMANO_PAGINA,
    },
    !rangoInvalido,
  )

  const hayFiltros = tipo !== '' || desde !== '' || hasta !== '' || varianteId !== undefined

  const columnas: Columna<MovimientoResponse>[] = [
    { clave: 'fecha', encabezado: 'Fecha', celda: (m) => formatearFecha(m.fecha, true) },
    { clave: 'tipo', encabezado: 'Tipo', celda: (m) => <BadgeTipoMovimiento tipo={m.tipo} /> },
    {
      clave: 'variante',
      encabezado: 'Variante',
      celda: (m) => (
        <div className="flex flex-col">
          <span className="font-medium">{m.sku}</span>
          <span className="text-xs text-oxford/70">{m.productoNombre}</span>
        </div>
      ),
    },
    { clave: 'cantidad', encabezado: 'Cantidad', celda: (m) => m.cantidad, className: 'text-right' },
    { clave: 'fisico', encabezado: 'Físico', celda: (m) => delta(m.fisicoAntes, m.fisicoDespues), className: 'text-right' },
    {
      clave: 'reservado',
      encabezado: 'Reservado',
      celda: (m) => delta(m.reservadoAntes, m.reservadoDespues),
      className: 'text-right',
      ocultarEnMovil: true,
    },
    {
      clave: 'costo',
      encabezado: 'Costo unit.',
      celda: (m) => (m.costoUnitario === null ? '—' : formatearMoneda(m.costoUnitario)),
      className: 'text-right',
      ocultarEnMovil: true,
    },
    { clave: 'usuario', encabezado: 'Usuario', celda: (m) => m.usuarioNombre ?? 'Sistema', ocultarEnMovil: true },
    {
      clave: 'referencia',
      encabezado: 'Referencia',
      celda: (m) => (m.referenciaTipo ? `${m.referenciaTipo} #${m.referenciaId}` : '—'),
      ocultarEnMovil: true,
    },
    { clave: 'observacion', encabezado: 'Observación', celda: (m) => m.observacion ?? '—', ocultarEnMovil: true },
  ]

  const quitarVariante = () => {
    setParams({})
    setPagina(0)
  }

  const limpiar = () => {
    setTipo('')
    setDesde('')
    setHasta('')
    setParams({})
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
        mensaje="Ningún movimiento coincide con los filtros."
        accion={
          <button type="button" onClick={limpiar} className={clasesLink}>
            Limpiar filtros
          </button>
        }
      />
    ) : (
      <EstadoVacio
        mensaje="Aún no hay movimientos en el kardex."
        accion={
          <Link to="/inventario" className={clasesLink}>
            Ir al inventario
          </Link>
        }
      />
    )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(m) => m.id} descripcion="Kardex de movimientos de stock" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <Link to="/inventario" className="text-sm font-medium text-acento hover:underline">
          Volver al inventario
        </Link>
        <h1>Kardex</h1>
      </div>

      {varianteId !== undefined && (
        <p className="flex flex-wrap items-center gap-3 text-sm">
          <span>Mostrando solo la variante #{varianteId}.</span>
          <button type="button" onClick={quitarVariante} className={clasesLink}>
            Ver todas las variantes
          </button>
        </p>
      )}

      <form
        role="search"
        aria-label="Filtros del kardex"
        onSubmit={(e) => e.preventDefault()}
        className="grid grid-cols-1 gap-3 sm:grid-cols-3"
      >
        <Selector
          etiqueta="Tipo de movimiento"
          placeholder="Todos"
          opciones={TIPOS}
          value={tipo}
          onChange={(e) => {
            setTipo(e.target.value)
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
      </form>

      {contenido}
    </section>
  )
}
