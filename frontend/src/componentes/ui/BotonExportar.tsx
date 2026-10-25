import { useState } from 'react'
import { mensajeError } from '../../lib/errores'
import type { FormatoExportacion } from '../../tipos/reportes'
import { Boton } from './Boton'

interface Props {
  /** Pide y descarga el archivo; debe rechazar si falla. */
  exportar: (formato: FormatoExportacion) => Promise<unknown>
  /** Deshabilita ambos botones (p. ej. filtros inválidos). */
  deshabilitado?: boolean
  /** Complemento accesible, p. ej. "rentabilidad". */
  nombre?: string
  /** Formatos ofrecidos (por defecto PDF y Excel). */
  formatos?: FormatoExportacion[]
}

const ETIQUETA: Record<FormatoExportacion, string> = { PDF: 'PDF', XLSX: 'Excel' }
const OTRO: Record<FormatoExportacion, FormatoExportacion> = { PDF: 'XLSX', XLSX: 'PDF' }

/** Exportar a PDF o Excel. Si falla, muestra el error y ofrece el otro formato (CU-06, alternativa 2). */
export function BotonExportar({ exportar, deshabilitado = false, nombre, formatos = ['PDF', 'XLSX'] }: Props) {
  const [pendiente, setPendiente] = useState<FormatoExportacion | null>(null)
  const [fallo, setFallo] = useState<{ formato: FormatoExportacion; mensaje: string } | null>(null)

  const ejecutar = async (formato: FormatoExportacion) => {
    setPendiente(formato)
    setFallo(null)
    try {
      await exportar(formato)
    } catch (error) {
      setFallo({ formato, mensaje: mensajeError(error) })
    } finally {
      setPendiente(null)
    }
  }

  const sufijo = nombre ? ` ${nombre}` : ''
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {formatos.map((formato) => (
          <Boton
            key={formato}
            variante="secundario"
            disabled={deshabilitado || pendiente !== null}
            cargando={pendiente === formato}
            onClick={() => void ejecutar(formato)}
            aria-label={`Exportar${sufijo} a ${ETIQUETA[formato]}`}
          >
            Exportar {ETIQUETA[formato]}
          </Boton>
        ))}
      </div>
      {fallo && (
        <div role="alert" className="flex flex-col gap-2 rounded-lg border border-critico/40 bg-critico/5 p-3 text-sm text-oxford">
          <p>
            No se pudo exportar a {ETIQUETA[fallo.formato]}: {fallo.mensaje}
          </p>
          <div>
            <Boton variante="primario" disabled={pendiente !== null} onClick={() => void ejecutar(OTRO[fallo.formato])}>
              Descargar en {ETIQUETA[OTRO[fallo.formato]]}
            </Boton>
          </div>
        </div>
      )}
    </div>
  )
}
