import { useState } from 'react'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Modal } from '../../componentes/ui/Modal'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import type { VarianteResponse } from '../../tipos/catalogo'
import { parsearStockMinimo } from './textosEstado'

interface ModalStockMinimoProps {
  variante: VarianteResponse
  guardando: boolean
  error: unknown
  onGuardar: (stockMinimo: number) => void
  onCancelar: () => void
}

export function ModalStockMinimo({ variante, guardando, error, onGuardar, onCancelar }: ModalStockMinimoProps) {
  const [texto, setTexto] = useState(String(variante.stockMinimo))
  const [errorLocal, setErrorLocal] = useState<string>()

  const campos = error ? erroresPorCampo(error) : {}
  const errorCampo = errorLocal ?? campos.stockMinimo
  const banner = error && !campos.stockMinimo ? mensajeError(error) : undefined

  return (
    <Modal abierto titulo={`Stock mínimo de ${variante.sku}`} onCerrar={onCancelar} bloqueado={guardando}>
      <form
        noValidate
        aria-label="Stock mínimo"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          const valor = parsearStockMinimo(texto)
          if (valor === null) {
            setErrorLocal('Ingresa un entero entre 0 y 100000')
            return
          }
          setErrorLocal(undefined)
          onGuardar(valor)
        }}
      >
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Campo etiqueta="Stock mínimo" inputMode="numeric" value={texto} onChange={(e) => setTexto(e.target.value)} error={errorCampo} />
        <p className="text-sm text-oxford/70">
          Con punto de reorden calculado, el stock mínimo solo se usa si no hay ROP.
          {variante.puntoReorden !== null && ` Punto de reorden actual: ${variante.puntoReorden}.`}
        </p>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={guardando} onClick={onCancelar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={guardando}>
            Guardar
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
