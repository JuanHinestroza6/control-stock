import { isAxiosError } from 'axios'
import { useState } from 'react'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Modal } from '../../componentes/ui/Modal'
import { useCancelarPlan } from '../../hooks/useCompras'
import { erroresPorCampo, mensajeError } from '../../lib/errores'

interface ModalCancelarPlanProps {
  planId: number
  numero: string
  /** Un APROBADO cancelado deja de estar en camino y puede reabrir alertas. */
  aprobado: boolean
  onCerrar: () => void
}

/** Cancela un plan. El motivo es obligatorio (5 a 200 caracteres). */
export function ModalCancelarPlan({ planId, numero, aprobado, onCerrar }: ModalCancelarPlanProps) {
  const cancelar = useCancelarPlan(planId)
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
    <Modal abierto titulo={`Cancelar plan ${numero}`} onCerrar={onCerrar} bloqueado={cancelar.isPending}>
      <form
        noValidate
        aria-label="Cancelar plan"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">
          {aprobado
            ? 'Las unidades pedidas dejarán de contarse como "en camino" y pueden reabrirse alertas de stock bajo.'
            : 'El borrador se descarta y no se podrá reabrir.'}
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
            {conflicto ? 'Volver a confirmar' : 'Cancelar plan'}
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
