import { Boton } from './Boton'
import { Modal } from './Modal'

interface DialogoConfirmacionProps {
  abierto: boolean
  titulo: string
  mensaje: string
  textoConfirmar?: string
  /** Usa el estilo de peligro en el botón de confirmar (acciones destructivas). */
  peligro?: boolean
  /** Mientras es true, confirma queda en carga y el diálogo no se puede cerrar. */
  cargando?: boolean
  /** Error de la acción (p. ej. 409 del backend), se muestra dentro del diálogo. */
  error?: string
  onConfirmar: () => void
  onCancelar: () => void
}

export function DialogoConfirmacion({
  abierto,
  titulo,
  mensaje,
  textoConfirmar = 'Confirmar',
  peligro = false,
  cargando = false,
  error,
  onConfirmar,
  onCancelar,
}: DialogoConfirmacionProps) {
  return (
    <Modal abierto={abierto} titulo={titulo} onCerrar={onCancelar} bloqueado={cargando}>
      <p className="text-sm text-oxford">{mensaje}</p>
      {error && (
        <p role="alert" className="mt-3 text-sm font-medium text-critico">
          {error}
        </p>
      )}
      <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Boton variante="secundario" disabled={cargando} onClick={onCancelar}>
          Cancelar
        </Boton>
        <Boton variante={peligro ? 'peligro' : 'primario'} cargando={cargando} onClick={onConfirmar}>
          {textoConfirmar}
        </Boton>
      </div>
    </Modal>
  )
}
