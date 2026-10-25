import { lazy, Suspense, useMemo, useState } from 'react'
import { BotonExportar } from '../../componentes/ui/BotonExportar'
import { EstadoCarga } from '../../componentes/ui/Estados'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useExportarRentabilidad, useRentabilidad } from '../../hooks/useReportes'
import { formatearMoneda, formatearNumero, formatearPorcentaje } from '../../lib/formato'
import type {
  AgrupacionReporte,
  CriterioRentabilidad,
  FilaRentabilidad,
  FiltrosRentabilidad,
  RentabilidadResponse,
} from '../../tipos/reportes'
import { FiltrosReporte } from './FiltrosReporte'
import { EstadosConsulta, Monto, PieReporte, ReporteVacio } from './piezas'

// Recharts pesa: el gráfico se descarga solo cuando hay ventas que graficar.
const GraficoMargen = lazy(() => import('../../componentes/graficos/GraficoMargen'))

const AGRUPACIONES = [
  { valor: 'CATEGORIA', etiqueta: 'Por categoría' },
  { valor: 'PRODUCTO', etiqueta: 'Por producto' },
  { valor: 'VARIANTE', etiqueta: 'Por variante' },
]
const ORDENES = [
  { valor: 'MARGEN', etiqueta: 'Margen bruto' },
  { valor: 'INGRESO', etiqueta: 'Ingreso neto' },
  { valor: 'UNIDADES', etiqueta: 'Unidades' },
]

const ID_TOTAL = -1

function conTotales(datos: RentabilidadResponse): FilaRentabilidad[] {
  const t = datos.totales
  const total: FilaRentabilidad = {
    id: ID_TOTAL, codigo: null, nombre: 'Total', categoria: null, ...t, participacionMargen: null,
  }
  return [...datos.filas, total]
}

function nombreFila(f: FilaRentabilidad) {
  if (f.id === ID_TOTAL) return <strong>Total</strong>
  return (
    <span>
      {f.codigo && <span className="mr-1 text-xs text-oxford/60">{f.codigo}</span>}
      {f.nombre}
    </span>
  )
}

const dinero = (campo: keyof FilaRentabilidad): Columna<FilaRentabilidad>['celda'] => (f) => (
  <span className={f.id === ID_TOTAL ? 'font-semibold' : undefined}>
    <Monto valor={f[campo] as number} />
  </span>
)

const COLUMNAS: Columna<FilaRentabilidad>[] = [
  { clave: 'nombre', encabezado: 'Nombre', celda: nombreFila },
  { clave: 'unidades', encabezado: 'Unidades netas', celda: (f) => formatearNumero(f.unidadesNetas), className: 'text-right tabular-nums' },
  { clave: 'lista', encabezado: 'Ventas a lista', celda: dinero('ventasLista'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'descuentos', encabezado: 'Descuentos', celda: dinero('descuentos'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'ingreso', encabezado: 'Ingreso neto', celda: dinero('ingresoNeto'), className: 'text-right tabular-nums' },
  { clave: 'costo', encabezado: 'Costo', celda: dinero('costoVentas'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'margen', encabezado: 'Margen bruto', celda: dinero('margenBruto'), className: 'text-right tabular-nums' },
  { clave: 'margenPct', encabezado: 'Margen %', celda: (f) => formatearPorcentaje(f.margenPorcentaje), className: 'text-right tabular-nums' },
  { clave: 'merma', encabezado: 'Merma no apta', celda: dinero('mermaNoApta'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'resultado', encabezado: 'Resultado tras mermas', celda: dinero('resultadoDespuesMermas'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'participacion', encabezado: 'Participación', celda: (f) => (f.id === ID_TOTAL ? '' : formatearPorcentaje(f.participacionMargen)), className: 'text-right tabular-nums', ocultarEnMovil: true },
]

/** Reporte de rentabilidad (REQ-03, CU-06). */
export function PestanaRentabilidad() {
  const [filtros, setFiltros] = useState<FiltrosRentabilidad>({ agrupacion: 'CATEGORIA', orden: 'MARGEN' })
  const consulta = useRentabilidad(filtros)
  const exportar = useExportarRentabilidad()

  const barras = useMemo(
    () =>
      [...(consulta.data?.filas ?? [])]
        .sort((a, b) => b.margenBruto - a.margenBruto)
        .slice(0, 10)
        .map((f) => ({ nombre: f.nombre, margen: f.margenBruto })),
    [consulta.data],
  )

  const cambiar = (parcial: Partial<FiltrosRentabilidad>) => setFiltros((f) => ({ ...f, ...parcial }))
  const datos = consulta.data

  return (
    <div className="flex flex-col gap-4">
      <FiltrosReporte
        rango={datos ? { desde: datos.desde, hasta: datos.hasta, onCambio: (desde, hasta) => cambiar({ desde, hasta }) } : undefined}
        categoria={{
          valor: filtros.categoriaId !== undefined ? String(filtros.categoriaId) : '',
          onCambio: (v) => setFiltros((f) => ({ ...f, categoriaId: v ? Number(v) : undefined })),
        }}
      >
        <Selector
          etiqueta="Agrupar por"
          opciones={AGRUPACIONES}
          value={filtros.agrupacion}
          onChange={(e) => cambiar({ agrupacion: e.target.value as AgrupacionReporte })}
        />
        <Selector
          etiqueta="Ordenar por"
          opciones={ORDENES}
          value={filtros.orden}
          onChange={(e) => cambiar({ orden: e.target.value as CriterioRentabilidad })}
        />
      </FiltrosReporte>

      <BotonExportar nombre="rentabilidad" exportar={(formato) => exportar.mutateAsync({ filtros, formato })} />

      <EstadosConsulta consulta={consulta}>
        {(r) => (
          <>
            {r.sinVentas ? (
              <ReporteVacio mensaje={r.mensaje} />
            ) : (
              <>
                <Tarjeta className="p-4">
                  <h2 className="mb-2 text-lg font-medium">Margen bruto: mayores {barras.length}</h2>
                  <Suspense fallback={<EstadoCarga filas={3} />}>
                    <GraficoMargen
                      barras={barras}
                      formatoValor={formatearMoneda}
                      descripcion={`Gráfico de barras del margen bruto de las ${barras.length} filas con mayor margen. Los mismos datos están en la tabla siguiente.`}
                    />
                  </Suspense>
                </Tarjeta>
                <Tabla columnas={COLUMNAS} filas={conTotales(r)} claveFila={(f) => f.id} descripcion="Rentabilidad por agrupación con fila de totales" />
              </>
            )}
            <PieReporte notas={r.notas} generadoEn={r.generadoEn} />
          </>
        )}
      </EstadosConsulta>
    </div>
  )
}
