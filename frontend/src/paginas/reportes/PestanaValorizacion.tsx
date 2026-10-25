import { useState } from 'react'
import { BotonExportar } from '../../componentes/ui/BotonExportar'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useExportarValorizacion, useValorizacion } from '../../hooks/useReportes'
import { formatearNumero, formatearPorcentaje } from '../../lib/formato'
import type { AgrupacionReporte, FilaValorizacion, FiltrosValorizacion } from '../../tipos/reportes'
import { FiltrosReporte } from './FiltrosReporte'
import { EstadosConsulta, Monto, PieReporte, ReporteVacio } from './piezas'

const AGRUPACIONES = [
  { valor: 'CATEGORIA', etiqueta: 'Por categoría' },
  { valor: 'PRODUCTO', etiqueta: 'Por producto' },
  { valor: 'VARIANTE', etiqueta: 'Por variante' },
]

const esTotal = (f: FilaValorizacion) => f.id === null

const dinero = (campo: 'valorCosto' | 'valorVenta' | 'margenPotencial' | 'valorEnCamino'): Columna<FilaValorizacion>['celda'] => (f) => (
  <span className={esTotal(f) ? 'font-semibold' : undefined}>
    <Monto valor={f[campo]} />
  </span>
)

const COLUMNAS: Columna<FilaValorizacion>[] = [
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
  { clave: 'fisicas', encabezado: 'Unidades físicas', celda: (f) => formatearNumero(f.unidadesFisicas), className: 'text-right tabular-nums' },
  { clave: 'reservadas', encabezado: 'Reservadas', celda: (f) => formatearNumero(f.unidadesReservadas), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'costo', encabezado: 'Valor a costo', celda: dinero('valorCosto'), className: 'text-right tabular-nums' },
  { clave: 'venta', encabezado: 'Valor a venta', celda: dinero('valorVenta'), className: 'text-right tabular-nums', ocultarEnMovil: true },
  { clave: 'margen', encabezado: 'Margen potencial', celda: dinero('margenPotencial'), className: 'text-right tabular-nums' },
  { clave: 'margenPct', encabezado: 'Margen %', celda: (f) => formatearPorcentaje(f.margenPotencialPorcentaje), className: 'text-right tabular-nums', ocultarEnMovil: true },
  {
    clave: 'enCamino',
    encabezado: 'En camino (u.)',
    celda: (f) => <span className="text-oxford/70">{formatearNumero(f.unidadesEnCamino)}</span>,
    className: 'text-right tabular-nums',
    ocultarEnMovil: true,
  },
  {
    clave: 'valorEnCamino',
    encabezado: 'En camino (valor)',
    celda: (f) => <span className="text-oxford/70"><Monto valor={f.valorEnCamino} /></span>,
    className: 'text-right tabular-nums',
    ocultarEnMovil: true,
  },
]

/** Valorización del inventario a costo actual (REQ-07). */
export function PestanaValorizacion() {
  const [filtros, setFiltros] = useState<FiltrosValorizacion>({ agrupacion: 'CATEGORIA', incluirInactivas: true })
  const consulta = useValorizacion(filtros)
  const exportar = useExportarValorizacion()

  return (
    <div className="flex flex-col gap-4">
      <FiltrosReporte
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
        <label className="flex min-h-10 items-center gap-2 self-end text-sm text-oxford">
          <input
            type="checkbox"
            className="size-4 accent-acento"
            checked={filtros.incluirInactivas ?? true}
            onChange={(e) => setFiltros((f) => ({ ...f, incluirInactivas: e.target.checked }))}
          />
          Incluir inactivas con existencias
        </label>
      </FiltrosReporte>

      <BotonExportar nombre="valorización" exportar={(formato) => exportar.mutateAsync({ filtros, formato })} />

      <EstadosConsulta consulta={consulta}>
        {(r) => (
          <>
            {r.sinExistencias ? (
              <ReporteVacio mensaje={r.mensaje} />
            ) : (
              <>
                <p className="text-xs text-oxford/70">
                  Lo que está en camino es informativo: no suma al valor del inventario hasta que se recibe.
                </p>
                <Tabla
                  columnas={COLUMNAS}
                  filas={[...r.filas, r.totales]}
                  claveFila={(f) => f.id ?? 'total'}
                  descripcion="Valorización del inventario con fila de totales"
                />
              </>
            )}
            <PieReporte notas={r.notas} generadoEn={r.generadoEn} />
          </>
        )}
      </EstadosConsulta>
    </div>
  )
}
