import { useState } from 'react'
import { BotonExportar } from '../../componentes/ui/BotonExportar'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useExportarMovimientos, useMovimientosPeriodo } from '../../hooks/useReportes'
import { formatearNumero } from '../../lib/formato'
import type { AgrupacionReporte, FilaMovimientos, FiltrosMovimientos } from '../../tipos/reportes'
import { FiltrosReporte } from './FiltrosReporte'
import { EstadosConsulta, Monto, PieReporte, ReporteVacio } from './piezas'

const AGRUPACIONES = [
  { valor: 'CATEGORIA', etiqueta: 'Por categoría' },
  { valor: 'PRODUCTO', etiqueta: 'Por producto' },
  { valor: 'VARIANTE', etiqueta: 'Por variante' },
]

/** Tope del detalle en Excel (`MovimientosPeriodoService.LIMITE_DETALLE_POR_DEFECTO`); encima, el servidor responde 422. */
const LIMITE_DETALLE_EXCEL = 50_000

const esTotal = (f: FilaMovimientos) => f.id === null

type CampoUnidades = 'saldoInicial' | 'entradas' | 'ventas' | 'anulaciones' | 'devoluciones' | 'ajustesPositivos' | 'ajustesNegativos' | 'saldoFinal'

const unidades = (campo: CampoUnidades): Columna<FilaMovimientos>['celda'] => (f) => (
  <span className={esTotal(f) ? 'font-semibold' : undefined}>{formatearNumero(f[campo])}</span>
)

const COLUMNAS: Columna<FilaMovimientos>[] = [
  {
    clave: 'nombre',
    encabezado: 'Nombre',
    celda: (f) =>
      esTotal(f) ? (
        <strong>Total</strong>
      ) : (
        <span>
          {f.codigo && <span className="mr-1 text-xs text-oxford/60">{f.codigo}</span>}
          {f.nombre}
        </span>
      ),
  },
  { clave: 'inicial', encabezado: 'Saldo inicial', celda: unidades('saldoInicial'), className: 'text-right tabular-nums' },
  { clave: 'entradas', encabezado: 'Entradas', celda: unidades('entradas'), className: 'text-right tabular-nums' },
  {
    clave: 'valorEntradas',
    encabezado: 'Valor de entradas',
    celda: (f) => (
      <span className={esTotal(f) ? 'font-semibold' : undefined}>
        <Monto valor={f.valorEntradas} />
      </span>
    ),
    className: 'text-right tabular-nums',
    ocultarEnMovil: true,
  },
  { clave: 'ventas', encabezado: 'Ventas', celda: unidades('ventas'), className: 'text-right tabular-nums' },
  { clave: 'anulaciones', encabezado: 'Anulaciones', celda: unidades('anulaciones'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'devoluciones', encabezado: 'Devoluciones', celda: unidades('devoluciones'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'ajustesPos', encabezado: 'Ajustes +', celda: unidades('ajustesPositivos'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'ajustesNeg', encabezado: 'Ajustes −', celda: unidades('ajustesNegativos'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'final', encabezado: 'Saldo final', celda: unidades('saldoFinal'), className: 'text-right tabular-nums' },
]

/** Movimientos del periodo (REQ-07, CU-03): saldo inicial, entradas, salidas y saldo final del físico desde el kardex. */
export function PestanaMovimientos() {
  const [filtros, setFiltros] = useState<FiltrosMovimientos>({ agrupacion: 'CATEGORIA' })
  const consulta = useMovimientosPeriodo(filtros)
  const exportar = useExportarMovimientos()
  const datos = consulta.data

  return (
    <div className="flex flex-col gap-4">
      <FiltrosReporte
        rango={datos ? { desde: datos.desde, hasta: datos.hasta, onCambio: (desde, hasta) => setFiltros((f) => ({ ...f, desde, hasta })) } : undefined}
        categoria={{
          valor: filtros.categoriaId !== undefined ? String(filtros.categoriaId) : '',
          onCambio: (v) => setFiltros((f) => ({ ...f, categoriaId: v ? Number(v) : undefined })),
        }}
      >
        <Selector
          etiqueta="Agrupar por"
          opciones={AGRUPACIONES}
          value={filtros.agrupacion}
          onChange={(e) => setFiltros((f) => ({ ...f, agrupacion: e.target.value as AgrupacionReporte }))}
        />
      </FiltrosReporte>

      <BotonExportar nombre="movimientos" exportar={(formato) => exportar.mutateAsync({ filtros, formato })} />

      <EstadosConsulta consulta={consulta}>
        {(r) => (
          <>
            {r.sinMovimientos && <ReporteVacio mensaje={r.mensaje} />}
            {r.filas.length > 0 && (
              <>
                <p className="text-xs text-oxford/70">
                  Unidades físicas: saldo final = saldo inicial + entradas − ventas + anulaciones + devoluciones + ajustes positivos −
                  ajustes negativos.
                </p>
                <Tabla
                  columnas={COLUMNAS}
                  filas={[...r.filas, r.totales]}
                  claveFila={(f) => f.id ?? 'total'}
                  descripcion="Movimientos del periodo con fila de totales"
                />
              </>
            )}
            {!r.sinMovimientos &&
              (r.movimientosDetalle > LIMITE_DETALLE_EXCEL ? (
                <p className="rounded-lg border border-alerta/40 bg-alerta/10 p-3 text-sm text-oxford">
                  El periodo tiene {formatearNumero(r.movimientosDetalle)} movimientos y el detalle en Excel admite hasta{' '}
                  {formatearNumero(LIMITE_DETALLE_EXCEL)}: acote el rango para exportar a Excel (el PDF no lleva detalle).
                </p>
              ) : (
                <p className="rounded-lg border border-acento/30 bg-acento/5 p-3 text-sm text-oxford">
                  El detalle de {formatearNumero(r.movimientosDetalle)} movimientos solo se incluye al exportar a Excel.
                </p>
              ))}
            <PieReporte notas={r.notas} generadoEn={r.generadoEn} />
          </>
        )}
      </EstadosConsulta>
    </div>
  )
}
