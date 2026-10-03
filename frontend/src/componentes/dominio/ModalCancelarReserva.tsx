import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useCancelarReserva } from '../../hooks/useReservas'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import type { ReservaResponse } from '../../tipos/reservas'
import { Boton } from '../ui/Boton'
import { Campo } from '../ui/Campo'
import { Modal } from '../ui/Modal'

interface ModalCancelarReservaProps {
  reserva: ReservaResponse
  onCerrar: () => void
}

/** Cancela una reserva activa y libera su stock. El motivo es obligatorio (5 a 200 caracteres). */
export function ModalCancelarReserva({ reserva, onCerrar }: ModalCancelarReservaProps) {
  const cancelar = useCancelarReserva(reserva.id)
  const [motivo, setMotivo] = useState('')
  const [errorLocal, setErrorLocal] = useState<string>()

  const delServidor = cancelar.isError ? erroresPorCampo(cancelar.error) : {}
  const banner = cancelar.isError && Object.keys(delServidor).length === 0 ? mensajeError(cancelar.error) : undefined
  const conflicto = cancelar.isError && isAxiosError(cancelar.error) && cancelar.error.response?.status === 409

  const enviar = () => {
    const m = motivo.trim()
    if (m.length < 5 || m.length > 200) {
      setErrorLocal('El motivo es obligatorio (5 a 200 caracteres)')
      return
    }
    setErrorLocal(undefined)
    cancelar.mutate({ motivo: m }, { onSuccess: onCerrar })
  }

  return (
    <Modal abierto titulo={`Cancelar apartado ${reserva.numero}`} onCerrar={onCerrar} bloqueado={cancelar.isPending}>
      <form
        noValidate
        aria-label="Cancelar apartado"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">
          Se liberarán {reserva.cantidad} unidad(es) de {reserva.sku} apartadas por {reserva.clienteNombre}.
        </p>
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Campo
          etiqueta="Motivo de la cancelación"
          maxLength={200}
          value={motivo}
          error={errorLocal ?? delServidor.motivo}
          onChange={(e) => setMotivo(e.target.value)}
        />
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={cancelar.isPending} onClick={onCerrar}>
            Volver
          </Boton>
          <Boton type="submit" variante="peligro" cargando={cancelar.isPending}>
            {conflicto ? 'Volver a confirmar' : 'Cancelar apartado'}
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
