import { useEffect, useState } from 'react'
import { BadgeClaseAbc } from '../../componentes/dominio/BadgeClaseAbc'
import { BadgeStock } from '../../componentes/dominio/BadgeStock'
import { Badge, type TonoBadge } from '../../componentes/ui/Badge'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useReorden, useResumenAbc } from '../../hooks/useAnalitica'
import { useCategorias } from '../../hooks/useCategorias'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { puedeRecalcularAnalitica } from '../../lib/permisos'
import type { ClaseAbc, FuenteDemanda, MetodoReorden, ReordenVarianteResponse } from '../../tipos/analitica'

const TAMANO_PAGINA = 20
const RETARDO_BUSQUEDA_MS = 300
const OPCIONES_CLASE = (['A', 'B', 'C'] as ClaseAbc[]).map((c) => ({ valor: c, etiqueta: `Clase ${c}` }))
const ORDENES = [
  { valor: 'sku,asc', etiqueta: 'SKU' },
  { valor: 'puntoReorden,desc', etiqueta: 'Mayor punto de reorden' },
  { valor: 'demandaMedia,desc', etiqueta: 'Mayor demanda' },
]

const METODOS: Record<MetodoReorden, { etiqueta: string; tono: TonoBadge }> = {
  NORMAL: { etiqueta: 'Normal', tono: 'acento' },
  POISSON: { etiqueta: 'Poisson (demanda baja)', tono: 'acento' },
  SIN_DEMANDA: { etiqueta: 'Sin demanda', tono: 'neutro' },
  SIN_HISTORIA: { etiqueta: 'Sin historia', tono: 'alerta' },
}
const OPCIONES_METODO = (Object.keys(METODOS) as MetodoReorden[]).map((m) => ({ valor: m, etiqueta: METODOS[m].etiqueta }))

const FUENTES: Record<FuenteDemanda, string> = { PRONOSTICO: 'Pronóstico', HISTORICA: 'Histórica' }

const num2 = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const num1 = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const entero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 })

function BadgeMetodoReorden({ metodo }: { metodo: MetodoReorden }) {
  const { etiqueta, tono } = METODOS[metodo]
  return <Badge tono={tono}>{etiqueta}</Badge>
}

const COLUMNAS: Columna<ReordenVarianteResponse>[] = [
  {
    clave: 'producto',
    encabezado: 'SKU / producto',
    celda: (f) => (
      <div className="flex flex-col">
        <span className="font-medium text-oxford">{f.productoNombre}</span>
        <span className="text-xs text-oxford/60">{f.sku}</span>
      </div>
    ),
  },
  { clave: 'talla', encabezado: 'Talla', celda: (f) => f.talla, ocultarEnMovil: true },
  { clave: 'color', encabezado: 'Color', celda: (f) => f.color, ocultarEnMovil: true },
  { clave: 'clase', encabezado: 'Clase', celda: (f) => <BadgeClaseAbc clase={f.claseAbc} />, className: 'text-center', ocultarEnMovil: true },
  { clave: 'demanda', encabezado: 'Demanda/día', celda: (f) => num2.format(f.demandaMedia), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'desviacion', encabezado: 'Desv. (σ)', celda: (f) => num2.format(f.desviacion), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'lead', encabezado: 'Lead time (días)', celda: (f) => f.leadTimeDias, className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'servicio', encabezado: 'Nivel de servicio', celda: (f) => `${Math.round(f.nivelServicio * 100)} %`, className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'ss', encabezado: 'Stock de seguridad', celda: (f) => (f.stockSeguridad === null ? '—' : num1.format(f.stockSeguridad)), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'rop', encabezado: 'ROP', celda: (f) => (f.puntoReorden === null ? '—' : entero.format(f.puntoReorden)), className: 'text-right tabular-nums' },
  { clave: 'metodo', encabezado: 'Método', celda: (f) => <BadgeMetodoReorden metodo={f.metodo} />, ocultarEnMovil: true },
  { clave: 'minimo', encabezado: 'Stock mínimo', celda: (f) => f.stockMinimo, className: 'text-right tabular-nums', ocultarEnMovil: true },
  {
    clave: 'umbral',
    encabezado: 'Umbral efectivo',
    celda: (f) => (
      <div className="flex flex-col items-end">
        <span className="font-medium tabular-nums">{entero.format(f.umbralEfectivo)}</span>
        <span className="text-xs text-oxford/60">{f.fuenteUmbral === 'ROP' ? 'Punto de reorden' : 'Stock mínimo'}</span>
      </div>
    ),
    className: 'text-right',
  },
  {
    clave: 'fuente',
    encabezado: 'Fuente de demanda',
    celda: (f) => (f.fuenteDemanda ? FUENTES[f.fuenteDemanda] : '—'),
    ocultarEnMovil: true,
  },
  { clave: 'disponible', encabezado: 'Disponible', celda: (f) => f.stockDisponible, className: 'text-right tabular-nums' },
  { clave: 'camino', encabezado: 'En camino', celda: (f) => entero.format(f.enCamino ?? 0), className: 'text-right tabular-nums' },
  { clave: 'posicion', encabezado: 'Posición', celda: (f) => entero.format(f.posicion ?? f.stockDisponible), className: 'text-right font-medium tabular-nums' },
  { clave: 'estado', encabezado: 'Estado', celda: (f) => <BadgeStock estado={f.estadoStock} /> },
]

function AyudaFormula() {
  return (
    <details className="rounded-lg border border-oxford/10 bg-fondo p-3 text-sm text-oxford">
      <summary className="cursor-pointer font-medium text-acento">¿Cómo se calcula el punto de reorden?</summary>
      <div className="mt-3 flex flex-col gap-2 text-oxford/80">
        <p>
          El punto de reorden (ROP) es el stock disponible en el que conviene pedir más. Se calcula por variante con las ventas de los últimos 90 días.
        </p>
        <p>
          <strong>Normal:</strong> <code>ROP = ⌈d̄·L + z·σ·√L⌉</code>, donde d̄ es la demanda media por día, L el lead time (días de reposición), σ la
          desviación de la demanda diaria y z el factor del nivel de servicio. El término z·σ·√L es el stock de seguridad.
        </p>
        <p>
          <strong>Poisson (demanda baja):</strong> cuando la demanda esperada durante el lead time (d̄·L) es menor que 5 unidades, se usa la distribución
          de Poisson: el ROP es la menor cantidad que cubre la demanda con el nivel de servicio de la clase.
        </p>
        <p>
          <strong>Niveles de servicio por clase:</strong> A 95 %, B 90 %, C 80 % (sin clase se usa C).
        </p>
        <p>
          <strong>Sin demanda:</strong> si no hubo ventas, el ROP es 0 y solo se alerta al agotarse. <strong>Sin historia:</strong> con menos de 30 días de
          historia no hay ROP y se usa el stock mínimo como umbral.
        </p>
        <p>
          <strong>Fuente de la demanda:</strong> con "Pronóstico", la demanda esperada durante el lead time (d̄·L) sale de la tasa diaria pronosticada de
          la variante; con "Histórica", de las ventas de los últimos 90 días. La desviación σ siempre sale de las ventas observadas.
        </p>
        <p>
          <strong>Posición e "En alerta":</strong> posición = disponible + unidades en camino (planes de compra aprobados). Una variante está en alerta si
          está agotada (disponible 0) o si su posición es menor o igual al umbral. El estado de stock sigue calculándose sobre el disponible.
        </p>
        <p>La analítica se recalcula cada noche; el ADMIN también puede recalcularla desde el panel superior.</p>
      </div>
    </details>
  )
}

/** Punto de reorden por variante; solo ADMIN, INVENTARIO y CONTABILIDAD (la página ya filtra por rol). */
export function PestanaReorden() {
  const { usuario } = useSesion()
  const resumen = useResumenAbc()
  const categorias = useCategorias()

  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const [clase, setClase] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [metodo, setMetodo] = useState('')
  const [enAlerta, setEnAlerta] = useState(false)
  const [sort, setSort] = useState('sku,asc')
  const [pagina, setPagina] = useState(0)

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(texto.trim())
      setPagina(0)
    }, RETARDO_BUSQUEDA_MS)
    return () => clearTimeout(id)
  }, [texto])

  const sinEjecucion = resumen.data !== undefined && resumen.data.ejecucion === null
  const lista = useReorden(
    {
      q: q || undefined,
      clase: clase ? (clase as ClaseAbc) : undefined,
      categoriaId: categoriaId ? Number(categoriaId) : undefined,
      metodo: metodo ? (metodo as MetodoReorden) : undefined,
      enAlerta: enAlerta ? true : undefined,
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
  if (!resumen.data || resumen.data.ejecucion === null) {
    return (
      <EstadoVacio
        mensaje={
          puedeRecalcularAnalitica(usuario?.rol)
            ? 'Aún no se ha ejecutado la analítica. Usa "Recalcular" en el panel superior para calcular los puntos de reorden.'
            : 'Aún no se ha ejecutado la analítica. Pide a un administrador que la recalcule.'
        }
      />
    )
  }

  const hayFiltros = q !== '' || clase !== '' || categoriaId !== '' || metodo !== '' || enAlerta
  const pag = lista.data
  const reiniciar = (set: (v: string) => void) => (e: { target: { value: string } }) => {
    set(e.target.value)
    setPagina(0)
  }

  return (
    <div className="flex flex-col gap-4">
      <AyudaFormula />

      <Tarjeta>
        <form
          role="search"
          aria-label="Filtros de punto de reorden"
          onSubmit={(e) => e.preventDefault()}
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 lg:items-end"
        >
          <Campo etiqueta="Buscar" type="search" maxLength={60} placeholder="SKU o producto" value={texto} onChange={(e) => setTexto(e.target.value)} />
          <Selector etiqueta="Clase" placeholder="Todas" opciones={OPCIONES_CLASE} value={clase} onChange={reiniciar(setClase)} />
          <Selector
            etiqueta="Categoría"
            placeholder="Todas"
            opciones={(Array.isArray(categorias.data) ? categorias.data : []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
            value={categoriaId}
            onChange={reiniciar(setCategoriaId)}
          />
          <Selector etiqueta="Método" placeholder="Todos" opciones={OPCIONES_METODO} value={metodo} onChange={reiniciar(setMetodo)} />
          <Selector etiqueta="Ordenar por" opciones={ORDENES} value={sort} onChange={reiniciar(setSort)} />
          <label className="flex items-center gap-2 text-sm font-medium text-oxford">
            <input
              type="checkbox"
              checked={enAlerta}
              onChange={(e) => {
                setEnAlerta(e.target.checked)
                setPagina(0)
              }}
              className="h-4 w-4 accent-acento"
            />
            Solo en alerta
          </label>
        </form>
      </Tarjeta>

      {lista.isPending ? (
        <EstadoCarga />
      ) : lista.isError && !pag ? (
        <EstadoError mensaje={mensajeError(lista.error)} onReintentar={() => void lista.refetch()} />
      ) : pag && pag.content.length === 0 ? (
        <EstadoVacio mensaje={hayFiltros ? 'Ninguna variante coincide con los filtros.' : 'No hay puntos de reorden calculados.'} />
      ) : pag ? (
        <>
          <Tabla columnas={COLUMNAS} filas={pag.content}claveFila={(f) => f.varianteId} descripcion="Punto de reorden por variante" />
          <Paginacion pagina={pag.page} onCambiar={setPagina} />
        </>
      ) : null}
    </div>
  )
}
