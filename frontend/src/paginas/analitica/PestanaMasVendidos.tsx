import { useMemo, useState } from 'react'
import { Badge } from '../../componentes/ui/Badge'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useMasVendidos } from '../../hooks/useAnalitica'
import { useCategorias } from '../../hooks/useCategorias'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import { hoyIso, restarDias, validarRango } from '../../lib/rangoFechas'
import type { AgrupacionMasVendidos, CriterioMasVendidos, FiltrosMasVendidos, MasVendidoItem } from '../../tipos/analitica'

const RANGOS = [
  { valor: '30', etiqueta: 'Últimos 30 días' },
  { valor: '90', etiqueta: 'Últimos 90 días' },
  { valor: '365', etiqueta: 'Últimos 365 días' },
  { valor: 'personalizado', etiqueta: 'Personalizado' },
]
const CRITERIOS = [
  { valor: 'UNIDADES', etiqueta: 'Unidades' },
  { valor: 'INGRESO', etiqueta: 'Ingreso' },
]
const AGRUPACIONES = [
  { valor: 'PRODUCTO', etiqueta: 'Producto' },
  { valor: 'VARIANTE', etiqueta: 'Variante' },
]
const TOPS = ['5', '10', '20', '50'].map((n) => ({ valor: n, etiqueta: `Top ${n}` }))

const formatoNumero = new Intl.NumberFormat('es-CO')
const formatoPorcentaje = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

function columnas(agrupacion: AgrupacionMasVendidos): Columna<MasVendidoItem>[] {
  return [
    { clave: 'posicion', encabezado: '#', celda: (f) => f.posicion, className: 'w-12 tabular-nums' },
    {
      clave: 'producto',
      encabezado: 'Producto',
      celda: (f) => (
        <div className="flex flex-col">
          <span className="flex flex-wrap items-center gap-2 font-medium text-oxford">
            {f.productoNombre}
            {!f.activo && <Badge tono="neutro">Inactivo</Badge>}
          </span>
          <span className="text-xs text-oxford/60">
            {f.productoCodigo}
            {agrupacion === 'VARIANTE' && f.sku ? ` · ${f.sku} · ${f.talla ?? ''} · ${f.color ?? ''}` : ''}
          </span>
        </div>
      ),
    },
    { clave: 'categoria', encabezado: 'Categoría', celda: (f) => f.categoria.nombre, ocultarEnMovil: true },
    { clave: 'unidades', encabezado: 'Unidades', celda: (f) => formatoNumero.format(f.unidades), className: 'text-right tabular-nums' },
    { clave: 'ingreso', encabezado: 'Ingreso', celda: (f) => formatearMoneda(f.ingreso), className: 'text-right tabular-nums', ocultarEnMovil: true },
    {
      clave: 'participacion',
      encabezado: 'Participación',
      celda: (f) => (
        <div className="flex min-w-28 items-center gap-2">
          <div className="h-2 flex-1 overflow-hidden rounded-full bg-oxford/10" aria-hidden="true">
            <div
              data-testid="barra-participacion"
              className="h-full rounded-full bg-acento"
              style={{ width: `${Math.min(100, Math.max(0, f.participacion))}%` }}
            />
          </div>
          <span className="w-14 text-right text-xs tabular-nums">{formatoPorcentaje.format(f.participacion)} %</span>
        </div>
      ),
    },
  ]
}

/** Ranking de productos o variantes más vendidos; lo ven los cuatro roles (sin costos). */
export function PestanaMasVendidos() {
  const [rango, setRango] = useState('30')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [categoria, setCategoria] = useState('')
  const [criterio, setCriterio] = useState<CriterioMasVendidos>('UNIDADES')
  const [agrupacion, setAgrupacion] = useState<AgrupacionMasVendidos>('PRODUCTO')
  const [limite, setLimite] = useState('10')

  const categorias = useCategorias()
  const personalizado = rango === 'personalizado'
  const errorRango = personalizado && (desde || hasta) ? validarRango(desde, hasta) : null
  const rangoValido = !personalizado || validarRango(desde, hasta) === null

  const filtros = useMemo<FiltrosMasVendidos>(() => {
    const fin = personalizado ? hasta : hoyIso()
    const inicio = personalizado ? desde : restarDias(fin, Number(rango) - 1)
    return {
      desde: inicio,
      hasta: fin,
      ...(categoria ? { categoriaId: Number(categoria) } : {}),
      criterio,
      agrupacion,
      limite: Number(limite),
    }
  }, [personalizado, rango, desde, hasta, categoria, criterio, agrupacion, limite])

  const consulta = useMasVendidos(filtros, rangoValido)
  const datos = consulta.data
  const cols = useMemo(() => columnas(agrupacion), [agrupacion])

  return (
    <div className="flex flex-col gap-4">
      <Tarjeta className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
        <Selector etiqueta="Periodo" opciones={RANGOS} value={rango} onChange={(e) => setRango(e.target.value)} />
        {personalizado && (
          <>
            <Campo etiqueta="Desde" type="date" value={desde} max={hoyIso()} onChange={(e) => setDesde(e.target.value)} />
            <Campo etiqueta="Hasta" type="date" value={hasta} max={hoyIso()} onChange={(e) => setHasta(e.target.value)} error={errorRango ?? undefined} />
          </>
        )}
        <Selector
          etiqueta="Categoría"
          placeholder="Todas"
          opciones={(Array.isArray(categorias.data) ? categorias.data : []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
          value={categoria}
          onChange={(e) => setCategoria(e.target.value)}
        />
        <Selector etiqueta="Criterio" opciones={CRITERIOS} value={criterio} onChange={(e) => setCriterio(e.target.value as CriterioMasVendidos)} />
        <Selector etiqueta="Agrupar por" opciones={AGRUPACIONES} value={agrupacion} onChange={(e) => setAgrupacion(e.target.value as AgrupacionMasVendidos)} />
        <Selector etiqueta="Cantidad" opciones={TOPS} value={limite} onChange={(e) => setLimite(e.target.value)} />
      </Tarjeta>

      {!rangoValido ? (
        <EstadoVacio mensaje="Indica un rango de fechas válido para ver los más vendidos." />
      ) : consulta.isPending ? (
        <EstadoCarga />
      ) : consulta.isError && !datos ? (
        <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
      ) : datos && !datos.items?.length ? (
        <EstadoVacio mensaje="No hay ventas en el periodo y los filtros elegidos." />
      ) : datos ? (
        <>
          <div className="grid grid-cols-2 gap-3">
            <Tarjeta className="p-4">
              <p className="text-xs font-medium text-oxford/60">Unidades del periodo</p>
              <p data-testid="total-unidades" className="text-lg font-bold tabular-nums text-oxford">
                {formatoNumero.format(datos.totalUnidades)}
              </p>
            </Tarjeta>
            <Tarjeta className="p-4">
              <p className="text-xs font-medium text-oxford/60">Ingreso del periodo</p>
              <p data-testid="total-ingreso" className="text-lg font-bold tabular-nums text-oxford">
                {formatearMoneda(datos.totalIngreso)}
              </p>
            </Tarjeta>
          </div>
          <Tabla
            columnas={cols}
            filas={datos.items}
            claveFila={(f) => `${f.productoId}-${f.varianteId ?? 'p'}`}
            descripcion="Productos más vendidos del periodo"
          />
        </>
      ) : null}
    </div>
  )
}
