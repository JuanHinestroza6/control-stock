import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useExtenderReserva } from '../../hooks/useReservas'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import { formatearFecha } from '../../lib/formato'
import { horasMaximasExtension } from '../../lib/reservas'
import type { ReservaResponse } from '../../tipos/reservas'
import { Boton } from '../ui/Boton'
import { Campo } from '../ui/Campo'
import { Modal } from '../ui/Modal'

interface ModalExtenderReservaProps {
  reserva: ReservaResponse
  onCerrar: () => void
}

/** Extiende el vencimiento a partir de ahora (1 a 168 h) sin superar 7 días desde la creación. */
export function ModalExtenderReserva({ reserva, onCerrar }: ModalExtenderReservaProps) {
  const extender = useExtenderReserva(reserva.id)
  const [horas, setHoras] = useState('')
  const [errorLocal, setErrorLocal] = useState<string>()
  const [maximo] = useState(() => horasMaximasExtension(reserva.creadaEn))

  const delServidor = extender.isError ? erroresPorCampo(extender.error) : {}
  const banner = extender.isError && Object.keys(delServidor).length === 0 ? mensajeError(extender.error) : undefined
  const conflicto = extender.isError && isAxiosError(extender.error) && extender.error.response?.status === 409

  const enviar = () => {
    const t = horas.trim()
    const h = /^\d+$/.test(t) ? Number(t) : NaN
    if (!(h >= 1 && h <= 168)) {
      setErrorLocal('Entero entre 1 y 168 horas')
      return
    }
    if (h > maximo) {
      setErrorLocal('Una reserva no puede durar más de 7 días.')
      return
    }
    setErrorLocal(undefined)
    extender.mutate({ horas: h }, { onSuccess: onCerrar })
  }

  return (
    <Modal abierto titulo={`Extender apartado ${reserva.numero}`} onCerrar={onCerrar} bloqueado={extender.isPending}>
      <form
        noValidate
        aria-label="Extender apartado"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">
          Vence el {formatearFecha(reserva.venceEn, true)}. El nuevo plazo cuenta desde ahora y no puede pasar de 7 días desde la
          creación ({maximo > 0 ? `máximo ${maximo} h` : 'ya no admite más tiempo'}).
        </p>
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Campo
          etiqueta="Horas a partir de ahora"
          inputMode="numeric"
          value={horas}
          error={errorLocal ?? delServidor.horas}
          onChange={(e) => setHoras(e.target.value)}
        />
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={extender.isPending} onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={extender.isPending}>
            {conflicto ? 'Volver a confirmar' : 'Extender'}
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
