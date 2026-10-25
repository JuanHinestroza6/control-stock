import { useState } from 'react'
import { BotonExportar } from '../../componentes/ui/BotonExportar'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useExportarLibroVentas, useLibroVentas } from '../../hooks/useReportes'
import { formatearFecha, formatearNumero } from '../../lib/formato'
import type { FilaLibro, FiltrosLibroVentas, PeriodoLibro, VentaAnuladaItem } from '../../tipos/reportes'
import { FiltrosReporte } from './FiltrosReporte'
import { EstadosConsulta, Monto, PieReporte, ReporteVacio } from './piezas'

const AGRUPACIONES = [
  { valor: 'DIA', etiqueta: 'Por día' },
  { valor: 'MES', etiqueta: 'Por mes' },
]

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

/** `2026-10-21` -> `21 oct 2026`; `2026-10` -> `oct 2026`; null (fila de totales) -> Total. */
function etiquetaPeriodo(periodo: string | null): string {
  if (periodo === null) return 'Total'
  const [anio, mes, dia] = periodo.split('-')
  const nombre = `${MESES[Number(mes) - 1] ?? mes} ${anio}`
  return dia ? `${Number(dia)} ${nombre}` : nombre
}

const dinero = (campo: keyof FilaLibro): Columna<FilaLibro>['celda'] => (f) => (
  <span className={f.periodo === null ? 'font-semibold' : undefined}>
    <Monto valor={f[campo] as number} />
  </span>
)

const COLUMNAS: Columna<FilaLibro>[] = [
  {
    clave: 'periodo',
    encabezado: 'Periodo',
    celda: (f) => <span className={f.periodo === null ? 'font-semibold' : undefined}>{etiquetaPeriodo(f.periodo)}</span>,
    className: 'whitespace-nowrap',
  },
  { clave: 'ventas', encabezado: 'Ventas', celda: (f) => formatearNumero(f.numeroVentas), className: 'text-right tabular-nums' },
  { clave: 'unidades', encabezado: 'Unidades', celda: (f) => formatearNumero(f.unidadesVendidas), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'lista', encabezado: 'Ventas a lista', celda: dinero('ventasLista'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'descuentos', encabezado: 'Descuentos', celda: dinero('descuentos'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'facturado', encabezado: 'Facturado', celda: dinero('facturado'), className: 'text-right tabular-nums' },
  { clave: 'devoluciones', encabezado: 'Devoluciones', celda: dinero('devoluciones'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'neto', encabezado: 'Ingreso neto', celda: dinero('ingresoNeto'), className: 'text-right tabular-nums' },
  { clave: 'costo', encabezado: 'Costo', celda: dinero('costoVentas'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'margen', encabezado: 'Margen bruto', celda: dinero('margenBruto'), className: 'text-right tabular-nums' },
  { clave: 'merma', encabezado: 'Merma no apta', celda: dinero('mermaNoApta'), className: 'text-right tabular-nums', ocultarEnMovil: true },
]

const COLUMNAS_ANULADAS: Columna<VentaAnuladaItem>[] = [
  { clave: 'numero', encabezado: 'Venta', celda: (a) => a.numero, className: 'whitespace-nowrap font-medium' },
  { clave: 'fecha', encabezado: 'Fecha', celda: (a) => formatearFecha(a.fecha, true), className: 'whitespace-nowrap', ocultarEnMovil: true },
  { clave: 'total', encabezado: 'Total', celda: (a) => <Monto valor={a.total} />, className: 'text-right tabular-nums' },
  { clave: 'anuladaEn', encabezado: 'Anulada en', celda: (a) => formatearFecha(a.anuladaEn, true), className: 'whitespace-nowrap', ocultarEnMovil: true },
  { clave: 'anuladaPor', encabezado: 'Anulada por', celda: (a) => a.anuladaPor, ocultarEnMovil: true },
  { clave: 'motivo', encabezado: 'Motivo', celda: (a) => a.motivoAnulacion },
]

/** Libro de ventas (REQ-07): ventas, devoluciones y márgenes por día o mes, con las ventas anuladas aparte. */
export function PestanaLibroVentas() {
  const [filtros, setFiltros] = useState<FiltrosLibroVentas>({ agrupacion: 'DIA' })
  const consulta = useLibroVentas(filtros)
  const exportar = useExportarLibroVentas()
  const datos = consulta.data

  return (
    <div className="flex flex-col gap-4">
      <FiltrosReporte
        rango={datos ? { desde: datos.desde, hasta: datos.hasta, onCambio: (desde, hasta) => setFiltros((f) => ({ ...f, desde, hasta })) } : undefined}
      >
        <Selector
          etiqueta="Agrupar por"
          opciones={AGRUPACIONES}
          value={filtros.agrupacion}
          onChange={(e) => setFiltros((f) => ({ ...f, agrupacion: e.target.value as PeriodoLibro }))}
        />
      </FiltrosReporte>

      <BotonExportar nombre="libro de ventas" exportar={(formato) => exportar.mutateAsync({ filtros, formato })} />

      <EstadosConsulta consulta={consulta}>
        {(r) => (
          <>
            {r.sinVentas ? (
              <ReporteVacio mensaje={r.mensaje} />
            ) : (
              <>
                <p className="text-xs text-oxford/70">
                  Las ventas cuentan en la fecha de la venta y las devoluciones en la fecha en que se registraron, por eso algunos
                  periodos pueden tener valores negativos.
                </p>
                <Tabla columnas={COLUMNAS} filas={[...r.filas, r.totales]} claveFila={(f) => f.periodo ?? 'total'} descripcion="Libro de ventas por periodo con fila de totales" />
                <p className="rounded-lg border border-acento/30 bg-acento/5 p-3 text-sm text-oxford">
                  El detalle de {formatearNumero(r.lineasDetalle)} líneas de venta solo se incluye al exportar a Excel.
                </p>
              </>
            )}

            <section aria-labelledby="titulo-anuladas" className="flex flex-col gap-3">
              <h2 id="titulo-anuladas" className="text-lg font-medium">
                Ventas anuladas
              </h2>
              <Tarjeta className="flex flex-wrap gap-x-8 gap-y-1 p-4">
                <p className="text-sm">
                  Cantidad: <strong data-testid="total-anuladas">{formatearNumero(r.totalAnuladas)}</strong>
                </p>
                <p className="text-sm">
                  Monto: <strong data-testid="monto-anuladas"><Monto valor={r.montoAnuladas} /></strong>
                </p>
              </Tarjeta>
              {r.anuladas.length === 0 ? (
                <p className="text-sm text-oxford/70">No hay ventas anuladas en el periodo.</p>
              ) : (
                <Tabla columnas={COLUMNAS_ANULADAS} filas={r.anuladas} claveFila={(a) => a.ventaId} descripcion="Ventas anuladas del periodo" />
              )}
            </section>
            <PieReporte notas={r.notas} generadoEn={r.generadoEn} />
          </>
        )}
      </EstadosConsulta>
    </div>
  )
}
