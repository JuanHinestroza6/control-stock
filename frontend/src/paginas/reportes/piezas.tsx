import type { UseQueryResult } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'

/** Importe en COP; los negativos (p. ej. devoluciones de ventas de otro periodo) se pintan en crítico. */
export function Monto({ valor }: { valor: number }) {
  return <span className={valor < 0 ? 'text-critico' : undefined}>{formatearMoneda(valor)}</span>
}

/** Notas del reporte y marca de generación, al pie. */
export function PieReporte({ notas, generadoEn }: { notas: string[]; generadoEn: string }) {
  return (
    <div className="flex flex-col gap-1 text-xs text-oxford/70">
      {notas.length > 0 && (
        <ul className="list-disc pl-4">
          {notas.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      )}
      <p>Generado {formatearFecha(generadoEn, true)}</p>
    </div>
  )
}

/** Cargando y error comunes a los tres reportes; con datos delega en `children`. */
export function EstadosConsulta<T>({ consulta, children }: { consulta: UseQueryResult<T>; children: (datos: T) => ReactNode }) {
  if (consulta.isPending) return <EstadoCarga />
  if (consulta.isError && !consulta.data) {
    return <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  }
  if (!consulta.data) return <EstadoVacio mensaje="Sin datos." />
  return <>{children(consulta.data)}</>
}

/** Mensaje de reporte vacío del servidor (CU-06, alternativa 1). */
export function ReporteVacio({ mensaje }: { mensaje: string | null }) {
  return <EstadoVacio mensaje={mensaje ?? 'No hay datos para los filtros elegidos.'} />
}
