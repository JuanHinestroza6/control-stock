import { lazy, Suspense, useMemo, type ReactNode } from 'react'
import type { PuntoGraficoPronostico } from '../../componentes/graficos/GraficoPronostico'
import { Boton } from '../../componentes/ui/Boton'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { usePronostico } from '../../hooks/useAnalitica'
import { mensajeError } from '../../lib/errores'
import { etiquetaMes, formatoCantidad, porcentaje, textoMotivo } from '../../lib/pronostico'
import type { PronosticoVarianteResponse } from '../../tipos/analitica'
import { BadgeMetodoPronostico, BadgeWape } from './componentesPronostico'
import { NotaRiesgoBacktest } from './NotaRiesgoBacktest'

// Recharts pesa: el gráfico se descarga solo al abrir el detalle.
const GraficoPronostico = lazy(() => import('../../componentes/graficos/GraficoPronostico'))

function Dato({ etiqueta, children, testid }: { etiqueta: string; children: ReactNode; testid?: string }) {
  return (
    <Tarjeta className="p-4">
      <p className="text-xs font-medium text-oxford/60">{etiqueta}</p>
      <div data-testid={testid} className="text-lg font-bold tabular-nums text-oxford">
        {children}
      </div>
    </Tarjeta>
  )
}

/** Detalle del pronóstico de un producto: serie, backtest, proyección y reparto por variante. */
export function DetallePronostico({ productoId, onVolver }: { productoId: number; onVolver: () => void }) {
  const consulta = usePronostico(productoId)
  const d = consulta.data

  const puntos = useMemo<PuntoGraficoPronostico[]>(() => {
    if (!d) return []
    const porPeriodo = new Map<string, PuntoGraficoPronostico>()
    for (const s of d.serie) {
      porPeriodo.set(s.periodo, { periodo: s.periodo, observada: s.observada, corregida: s.corregida, backtestSes: s.backtestSes, backtestHw: s.backtestHw })
    }
    for (const p of d.pronostico) {
      porPeriodo.set(p.periodo, { ...(porPeriodo.get(p.periodo) ?? { periodo: p.periodo }), pronostico: p.cantidad })
    }
    return [...porPeriodo.values()].sort((a, b) => a.periodo.localeCompare(b.periodo))
  }, [d])

  const periodos = d?.pronostico.map((p) => p.periodo) ?? []
  const columnasVariante: Columna<PronosticoVarianteResponse>[] = [
    { clave: 'sku', encabezado: 'SKU', celda: (f) => f.sku, className: 'whitespace-nowrap' },
    { clave: 'talla', encabezado: 'Talla', celda: (f) => f.talla },
    { clave: 'color', encabezado: 'Color', celda: (f) => f.color, ocultarEnMovil: true },
    { clave: 'participacion', encabezado: 'Participación', celda: (f) => porcentaje(f.participacion), className: 'text-right tabular-nums' },
    ...periodos.map((periodo) => ({
      clave: periodo,
      encabezado: etiquetaMes(periodo),
      celda: (f: PronosticoVarianteResponse) => {
        const punto = f.pronostico.find((p) => p.periodo === periodo)
        return punto ? formatoCantidad.format(punto.cantidad) : '—'
      },
      className: 'text-right tabular-nums',
    })),
  ]

  const motivo = d ? textoMotivo(d) : null
  const params = d?.parametros
  const textoParametros =
    params && params.alfa !== null
      ? ` · α ${params.alfa}${params.beta !== null ? `, β ${params.beta}` : ''}${params.gamma !== null ? `, γ ${params.gamma}` : ''}`
      : ''

  return (
    <div className="flex flex-col gap-4">
      <div>
        <Boton variante="secundario" onClick={onVolver}>
          Volver al listado
        </Boton>
      </div>

      {consulta.isPending ? (
        <EstadoCarga />
      ) : consulta.isError && !d ? (
        <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
      ) : d && d.metodo === null ? (
        <>
          <h2>{d.nombre}</h2>
          <EstadoVacio mensaje="Este producto aún no tiene historia suficiente para pronosticar (menos de 3 meses). El punto de reorden usa la demanda histórica." />
        </>
      ) : d ? (
        <>
          <div>
            <h2>{d.nombre}</h2>
            <p className="text-xs text-oxford/60">
              {d.codigo} · {d.categoria}
            </p>
          </div>
          <NotaRiesgoBacktest />
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Dato etiqueta="Método">
              <BadgeMetodoPronostico metodo={d.metodo} />
            </Dato>
            <Dato etiqueta="Error (WAPE)" testid="wape">
              <BadgeWape wape={d.errorWape} />
            </Dato>
            <Dato etiqueta="Meses de historia" testid="meses-historia">
              {d.mesesHistoria}
            </Dato>
            <Dato etiqueta="Últimos 12 meses (observadas / corregidas)" testid="unidades-12m">
              {formatoCantidad.format(d.unidadesUltimos12m)} / {formatoCantidad.format(d.unidadesCorregidas12m)}
            </Dato>
          </div>
          <p className="text-xs text-oxford/70">
            WAPE de SES: {d.wapeSes === null ? '—' : porcentaje(d.wapeSes)} · WAPE de Holt-Winters: {d.wapeHw === null ? '—' : porcentaje(d.wapeHw)}
            {motivo ? ` · ${motivo}` : ''}
            {textoParametros}
          </p>

          {d.serie.length === 0 && d.pronostico.length === 0 ? (
            <EstadoVacio mensaje="No hay serie ni pronóstico para mostrar." />
          ) : (
            <Tarjeta className="p-4">
              <Suspense fallback={<EstadoCarga filas={3} />}>
                <GraficoPronostico
                  puntos={puntos}
                  descripcion={`Gráfico de líneas: demanda mensual observada y corregida por agotados, backtest de SES y Holt-Winters, y pronóstico de ${d.pronostico.length} meses de ${d.nombre}. El pronóstico por mes está en la tabla de variantes.`}
                />
              </Suspense>
            </Tarjeta>
          )}

          <h3 className="text-base font-medium text-oxford">Pronóstico por variante</h3>
          {d.variantes.length === 0 ? (
            <EstadoVacio mensaje="El producto no tiene variantes activas a las que repartir el pronóstico." />
          ) : (
            <Tabla columnas={columnasVariante} filas={d.variantes} claveFila={(f) => f.varianteId} descripcion="Pronóstico mensual por variante" />
          )}
        </>
      ) : null}
    </div>
  )
}
