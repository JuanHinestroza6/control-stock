import { lazy, Suspense, useMemo, useState } from 'react'
import { BotonExportar } from '../../componentes/ui/BotonExportar'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useTendencias } from '../../hooks/useAnalitica'
import { useCategorias } from '../../hooks/useCategorias'
import { useProductos } from '../../hooks/useProductos'
import { useExportarTendencias } from '../../hooks/useReportes'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import { mesRelativo } from '../../lib/meses'
import { puedeExportarTendencias } from '../../lib/permisos'
import type { FiltrosTendencias, PuntoTendencia } from '../../tipos/analitica'

// Recharts pesa: el gráfico se descarga solo al abrir esta pestaña.
const GraficoSerieMensual = lazy(() => import('../../componentes/graficos/GraficoSerieMensual'))

type Metrica = 'UNIDADES' | 'INGRESO'

const METRICAS = [
  { valor: 'UNIDADES', etiqueta: 'Unidades' },
  { valor: 'INGRESO', etiqueta: 'Ingreso' },
]
const RANGOS = [
  { valor: '12', etiqueta: 'Últimos 12 meses' },
  { valor: '24', etiqueta: 'Últimos 24 meses' },
  { valor: '36', etiqueta: 'Últimos 36 meses' },
]

const formatoNumero = new Intl.NumberFormat('es-CO')
const formatoVariacion = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1, signDisplay: 'exceptZero' })
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

function etiquetaMes(periodo: string): string {
  const [anio, mes] = periodo.split('-')
  return `${MESES[Number(mes) - 1] ?? mes} ${anio}`
}

function Variacion({ valor }: { valor: number | null }) {
  if (valor === null) return <span className="text-oxford/50">—</span>
  const tono = valor > 0 ? 'text-ok' : valor < 0 ? 'text-critico' : 'text-oxford'
  return <span className={`font-medium ${tono}`}>{formatoVariacion.format(valor)} %</span>
}

/** Tendencias históricas (REQ-09): demanda comercial mensual frente al mismo mes del año anterior. */
export function PestanaTendencias() {
  const [meses, setMeses] = useState('24')
  const [categoria, setCategoria] = useState('')
  const [producto, setProducto] = useState('')
  const [metrica, setMetrica] = useState<Metrica>('UNIDADES')

  const { usuario } = useSesion()
  const exportar = useExportarTendencias()
  const categorias = useCategorias()
  const productos = useProductos({
    ...(categoria ? { categoriaId: Number(categoria) } : {}),
    size: 100,
    sort: 'nombre,asc',
  })

  // Solo meses completos: hasta el mes anterior al actual.
  const filtros = useMemo<FiltrosTendencias>(
    () => ({
      desde: mesRelativo(Number(meses)),
      hasta: mesRelativo(1),
      ...(categoria ? { categoriaId: Number(categoria) } : {}),
      ...(producto ? { productoId: Number(producto) } : {}),
    }),
    [meses, categoria, producto],
  )
  const consulta = useTendencias(filtros)
  const datos = consulta.data

  const esIngreso = metrica === 'INGRESO'
  const nombreMetrica = esIngreso ? 'Ingreso' : 'Unidades'
  const formatoValor = (v: number) => (esIngreso ? formatearMoneda(v) : formatoNumero.format(v))

  const serie = useMemo(
    () =>
      (datos?.puntos ?? []).map((p) => ({
        periodo: p.periodo,
        actual: esIngreso ? p.ingreso : p.unidades,
        anterior: esIngreso ? p.ingresoAnioAnterior : p.unidadesAnioAnterior,
      })),
    [datos, esIngreso],
  )

  const columnas: Columna<PuntoTendencia>[] = [
    { clave: 'periodo', encabezado: 'Mes', celda: (f) => etiquetaMes(f.periodo), className: 'whitespace-nowrap' },
    {
      clave: 'actual',
      encabezado: nombreMetrica,
      celda: (f) => formatoValor(esIngreso ? f.ingreso : f.unidades),
      className: 'text-right tabular-nums',
    },
    {
      clave: 'anterior',
      encabezado: `${nombreMetrica} año anterior`,
      celda: (f) => formatoValor(esIngreso ? f.ingresoAnioAnterior : f.unidadesAnioAnterior),
      className: 'text-right tabular-nums',
      ocultarEnMovil: true,
    },
    {
      clave: 'variacion',
      encabezado: 'Variación',
      celda: (f) => <Variacion valor={esIngreso ? f.variacionIngreso : f.variacionUnidades} />,
      className: 'text-right tabular-nums',
    },
  ]

  const sinVentas = datos && (!datos.puntos?.length || (datos.totalUnidades === 0 && datos.totalIngreso === 0))

  return (
    <div className="flex flex-col gap-4">
      <Tarjeta className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-4">
        <Selector etiqueta="Periodo" opciones={RANGOS} value={meses} onChange={(e) => setMeses(e.target.value)} />
        <Selector
          etiqueta="Categoría"
          placeholder="Todas"
          opciones={(Array.isArray(categorias.data) ? categorias.data : []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
          value={categoria}
          onChange={(e) => {
            setCategoria(e.target.value)
            setProducto('')
          }}
        />
        <Selector
          etiqueta="Producto"
          placeholder="Todos"
          opciones={(productos.data?.content ?? []).map((p) => ({ valor: String(p.id), etiqueta: `${p.codigo} · ${p.nombre}` }))}
          value={producto}
          onChange={(e) => setProducto(e.target.value)}
        />
        <Selector etiqueta="Métrica" opciones={METRICAS} value={metrica} onChange={(e) => setMetrica(e.target.value as Metrica)} />
      </Tarjeta>

      {puedeExportarTendencias(usuario?.rol) && (
        <BotonExportar nombre="tendencias" exportar={(formato) => exportar.mutateAsync({ filtros, formato })} />
      )}

      {consulta.isPending ? (
        <EstadoCarga />
      ) : consulta.isError && !datos ? (
        <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
      ) : sinVentas ? (
        <EstadoVacio mensaje="No hay ventas en el periodo y los filtros elegidos." />
      ) : datos ? (
        <>
          <div className="grid grid-cols-2 gap-3">
            <Tarjeta className="p-4">
              <p className="text-xs font-medium text-oxford/60">Unidades netas del periodo</p>
              <p data-testid="total-unidades" className="text-lg font-bold tabular-nums text-oxford">
                {formatoNumero.format(datos.totalUnidades)}
              </p>
            </Tarjeta>
            <Tarjeta className="p-4">
              <p className="text-xs font-medium text-oxford/60">Ingreso neto del periodo</p>
              <p data-testid="total-ingreso" className="text-lg font-bold tabular-nums text-oxford">
                {formatearMoneda(datos.totalIngreso)}
              </p>
            </Tarjeta>
          </div>
          <Tarjeta className="p-4">
            <Suspense fallback={<EstadoCarga filas={3} />}>
              <GraficoSerieMensual
                puntos={serie}
                etiquetaActual={nombreMetrica}
                etiquetaAnterior={`${nombreMetrica} año anterior`}
                formatoValor={formatoValor}
                descripcion={`Gráfico de líneas: ${nombreMetrica.toLowerCase()} netas por mes frente al mismo mes del año anterior, de ${etiquetaMes(datos.desde)} a ${etiquetaMes(datos.hasta)}. Los mismos datos están en la tabla siguiente.`}
              />
            </Suspense>
          </Tarjeta>
          <Tabla columnas={columnas} filas={datos.puntos} claveFila={(f) => f.periodo} descripcion="Demanda mensual y comparación con el año anterior" />
        </>
      ) : null}
    </div>
  )
}
