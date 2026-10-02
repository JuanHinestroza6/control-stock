import { useState } from 'react'
import { useAnularVenta } from '../../hooks/useVentas'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import { Boton } from '../ui/Boton'
import { Campo } from '../ui/Campo'
import { Modal } from '../ui/Modal'

interface ModalAnularVentaProps {
  ventaId: number
  numero: string
  onCerrar: () => void
}

/** Anula una venta (solo ADMIN): revierte el stock de todas sus líneas. El motivo es obligatorio. */
export function ModalAnularVenta({ ventaId, numero, onCerrar }: ModalAnularVentaProps) {
  const anular = useAnularVenta(ventaId)
  const [motivo, setMotivo] = useState('')
  const [errorLocal, setErrorLocal] = useState<string>()

  const delServidor = anular.isError ? erroresPorCampo(anular.error) : {}
  const banner = anular.isError && Object.keys(delServidor).length === 0 ? mensajeError(anular.error) : undefined

  const enviar = () => {
    const m = motivo.trim()
    if (m.length < 5 || m.length > 255) {
      setErrorLocal('El motivo es obligatorio (5 a 255 caracteres)')
      return
    }
    setErrorLocal(undefined)
    anular.mutate({ motivo: m }, { onSuccess: onCerrar })
  }

  return (
    <Modal abierto titulo={`Anular venta ${numero}`} onCerrar={onCerrar} bloqueado={anular.isPending}>
      <form
        noValidate
        aria-label="Anular venta"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">Las unidades vendidas volverán al stock. Esta acción no se puede deshacer.</p>
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Campo
          etiqueta="Motivo de la anulación"
          maxLength={255}
          value={motivo}
          error={errorLocal ?? delServidor.motivo}
          onChange={(e) => setMotivo(e.target.value)}
        />
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={anular.isPending} onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" variante="peligro" cargando={anular.isPending}>
            Anular venta
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
