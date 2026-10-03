import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { BadgeEstadoReserva } from '../../componentes/dominio/BadgeEstadoReserva'
import { Badge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { ModalCancelarReserva } from '../../componentes/dominio/ModalCancelarReserva'
import { ModalConvertirReservas } from '../../componentes/dominio/ModalConvertirReservas'
import { ModalExtenderReserva } from '../../componentes/dominio/ModalExtenderReserva'
import { useReservas } from '../../hooks/useReservas'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearFecha } from '../../lib/formato'
import { puedeConvertirReserva, puedeReservar } from '../../lib/permisos'
import { claveCliente, vencimientoRelativo } from '../../lib/reservas'
import type { EstadoReserva, ReservaResponse } from '../../tipos/reservas'

const TAMANO_PAGINA = 20
const clasesLink = 'font-medium text-acento hover:underline'

const ESTADOS = [
  { valor: 'ACTIVA', etiqueta: 'Activas' },
  { valor: 'CONVERTIDA', etiqueta: 'Convertidas' },
  { valor: 'CANCELADA', etiqueta: 'Canceladas' },
  { valor: 'VENCIDA', etiqueta: 'Vencidas' },
]

const ORDENES = [
  { valor: 'venceEn,asc', etiqueta: 'Vencen primero' },
  { valor: 'venceEn,desc', etiqueta: 'Vencen al final' },
  { valor: 'creadaEn,desc', etiqueta: 'Más recientes' },
  { valor: 'creadaEn,asc', etiqueta: 'Más antiguas' },
]

export function ReservasPage() {
  const { usuario } = useSesion()
  const puedeNueva = puedeReservar(usuario?.rol)
  const [params, setParams] = useSearchParams()
  const varianteParam = Number(params.get('varianteId'))
  const varianteId = Number.isInteger(varianteParam) && varianteParam > 0 ? varianteParam : undefined

  const [estado, setEstado] = useState<string>('ACTIVA')
  const [q, setQ] = useState('')
  const [porVencer, setPorVencer] = useState(false)
  const [sort, setSort] = useState('venceEn,asc')
  const [pagina, setPagina] = useState(0)
  const puedeConvertir = puedeConvertirReserva(usuario?.rol)
  const [seleccion, setSeleccion] = useState<ReservaResponse[]>([])
  const [convirtiendo, setConvirtiendo] = useState(false)
  const [cancelando, setCancelando] = useState<ReservaResponse>()
  const [extendiendo, setExtendiendo] = useState<ReservaResponse>()

  const alternar = (r: ReservaResponse) =>
    setSeleccion((s) => (s.some((x) => x.id === r.id) ? s.filter((x) => x.id !== r.id) : [...s, r]))
  const clienteSeleccionado = seleccion.length > 0 ? claveCliente(seleccion[0]) : undefined

  const consulta = useReservas({
    estado: estado ? (estado as EstadoReserva) : undefined,
    q: q || undefined,
    varianteId,
    porVencer: porVencer || undefined,
    page: pagina,
    size: TAMANO_PAGINA,
    sort,
  })

  const hayFiltros = estado !== 'ACTIVA' || q !== '' || porVencer || varianteId !== undefined

  const limpiar = () => {
    setEstado('ACTIVA')
    setQ('')
    setPorVencer(false)
    setPagina(0)
    setParams({})
  }

  const columnas: Columna<ReservaResponse>[] = [
    ...(puedeConvertir
      ? [
          {
            clave: 'seleccion',
            encabezado: 'Sel.',
            celda: (r: ReservaResponse) => {
              if (r.estado !== 'ACTIVA') return null
              const otro = clienteSeleccionado !== undefined && clienteSeleccionado !== claveCliente(r)
              return (
                <input
                  type="checkbox"
                  aria-label={`Seleccionar ${r.numero}`}
                  checked={seleccion.some((x) => x.id === r.id)}
                  disabled={otro}
                  title={otro ? 'Solo se pueden convertir reservas del mismo cliente' : undefined}
                  onChange={() => alternar(r)}
                />
              )
            },
          },
        ]
      : []),
    { clave: 'numero', encabezado: 'Reserva', celda: (r) => <span className="font-medium">{r.numero}</span> },
    {
      clave: 'cliente',
      encabezado: 'Cliente',
      celda: (r) => (
        <div>
          <div>{r.clienteNombre}</div>
          <div className="text-xs text-oxford/60">{r.clienteContacto}</div>
        </div>
      ),
    },
    {
      clave: 'variante',
      encabezado: 'Variante',
      celda: (r) => (
        <div>
          <div>{r.productoNombre}</div>
          <div className="text-xs text-oxford/60">
            {r.sku} · {r.talla} · {r.color}
          </div>
        </div>
      ),
    },
    { clave: 'cantidad', encabezado: 'Cant.', celda: (r) => r.cantidad, className: 'text-right' },
    {
      clave: 'vence',
      encabezado: 'Vencimiento',
      ocultarEnMovil: true,
      celda: (r) => {
        if (r.estado !== 'ACTIVA') return formatearFecha(r.cerradaEn ?? r.venceEn, true)
        const { texto, tono } = vencimientoRelativo(r.venceEn)
        return (
          <div>
            <Badge tono={tono}>{texto}</Badge>
            <div className="mt-1 text-xs text-oxford/60">{formatearFecha(r.venceEn, true)}</div>
          </div>
        )
      },
    },
    {
      clave: 'estado',
      encabezado: 'Estado',
      celda: (r) => (
        <div className="flex flex-col items-start gap-1">
          <BadgeEstadoReserva estado={r.estado} />
          {r.ventaId !== null && r.ventaNumero && (
            <Link to={`/ventas/${r.ventaId}`} className={`${clasesLink} text-xs`}>
              {r.ventaNumero}
            </Link>
          )}
        </div>
      ),
    },
    ...(puedeNueva
      ? [
          {
            clave: 'acciones',
            encabezado: 'Acciones',
            celda: (r: ReservaResponse) =>
              r.estado === 'ACTIVA' ? (
                <div className="flex gap-3">
                  <button type="button" className={clasesLink} aria-label={`Extender ${r.numero}`} onClick={() => setExtendiendo(r)}>
                    Extender
                  </button>
                  <button type="button" className={clasesLink} aria-label={`Cancelar ${r.numero}`} onClick={() => setCancelando(r)}>
                    Cancelar
                  </button>
                </div>
              ) : null,
          },
        ]
      : []),
  ]

  let contenido
  if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else if (consulta.data.content.length === 0) {
    contenido = hayFiltros ? (
      <EstadoVacio
        mensaje="Ninguna reserva coincide con los filtros."
        accion={
          <button type="button" onClick={limpiar} className={clasesLink}>
            Limpiar filtros
          </button>
        }
      />
    ) : (
      <EstadoVacio
        mensaje="No hay apartados activos."
        accion={
          puedeNueva ? (
            <Link to="/reservas/nueva" className={clasesLink}>
              Crear un apartado
            </Link>
          ) : undefined
        }
      />
    )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(r) => r.id} descripcion="Listado de apartados" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1>Apartados</h1>
        {puedeNueva && (
          <Link
            to="/reservas/nueva"
            className="inline-flex min-h-10 items-center rounded-lg bg-oxford px-4 py-2 text-sm font-medium text-fondo hover:bg-oxford/90"
          >
            Nuevo apartado
          </Link>
        )}
      </div>

      <form
        role="search"
        aria-label="Filtros de apartados"
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
          etiqueta="Buscar"
          type="search"
          maxLength={60}
          placeholder="Cliente, contacto o SKU"
          value={q}
          onChange={(e) => {
            setQ(e.target.value)
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
        <label className="flex items-center gap-2 self-end pb-2 text-sm">
          <input
            type="checkbox"
            checked={porVencer}
            onChange={(e) => {
              setPorVencer(e.target.checked)
              setPagina(0)
            }}
          />
          Por vencer (menos de 12 h)
        </label>
      </form>

      {varianteId !== undefined && (
        <p className="text-sm">
          Filtrando por la variante #{varianteId}.{' '}
          <button
            type="button"
            className={clasesLink}
            onClick={() => {
              setParams({})
              setPagina(0)
            }}
          >
            Quitar filtro
          </button>
        </p>
      )}

      {puedeConvertir && seleccion.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-acento/30 bg-acento/5 p-3">
          <p className="text-sm">
            {seleccion.length} reserva(s) seleccionada(s) de {seleccion[0].clienteNombre}.
          </p>
          <div className="flex gap-2">
            <Boton variante="secundario" onClick={() => setSeleccion([])}>
              Limpiar selección
            </Boton>
            <Boton onClick={() => setConvirtiendo(true)}>Convertir en venta</Boton>
          </div>
        </div>
      )}

      {contenido}

      {convirtiendo && (
        <ModalConvertirReservas
          reservas={seleccion}
          onCerrar={() => {
            setConvirtiendo(false)
            setSeleccion([])
          }}
        />
      )}
      {cancelando && (
        <ModalCancelarReserva
          reserva={cancelando}
          onCerrar={() => {
            setSeleccion((s) => s.filter((x) => x.id !== cancelando.id))
            setCancelando(undefined)
          }}
        />
      )}
      {extendiendo && <ModalExtenderReserva reserva={extendiendo} onCerrar={() => setExtendiendo(undefined)} />}
    </section>
  )
}
