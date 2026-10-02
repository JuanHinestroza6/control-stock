import { useState } from 'react'
import { useRegistrarAjuste } from '../../hooks/useInventario'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import type { InventarioItemResponse, TipoAjuste } from '../../tipos/inventario'
import { Boton } from '../ui/Boton'
import { Campo } from '../ui/Campo'
import { Modal } from '../ui/Modal'
import { Selector } from '../ui/Selector'

const TIPOS = [
  { valor: 'AJUSTE_POSITIVO', etiqueta: 'Sumar unidades (positivo)' },
  { valor: 'AJUSTE_NEGATIVO', etiqueta: 'Restar unidades (negativo)' },
]

const MAX_CANTIDAD = 100000

interface ModalAjusteProps {
  variante: InventarioItemResponse
  onCerrar: () => void
}

/** Ajuste manual de stock (conteo físico, merma, corrección). La observación es obligatoria. */
export function ModalAjuste({ variante, onCerrar }: ModalAjusteProps) {
  const registrar = useRegistrarAjuste()
  const [tipo, setTipo] = useState<TipoAjuste>('AJUSTE_POSITIVO')
  const [cantidad, setCantidad] = useState('')
  const [observacion, setObservacion] = useState('')
  const [errores, setErrores] = useState<{ cantidad?: string; observacion?: string }>({})

  const delServidor = registrar.isError ? erroresPorCampo(registrar.error) : {}
  const banner = registrar.isError && Object.keys(delServidor).length === 0 ? mensajeError(registrar.error) : undefined

  const enviar = () => {
    const nuevos: { cantidad?: string; observacion?: string } = {}
    const t = cantidad.trim()
    if (!/^\d+$/.test(t) || Number(t) < 1 || Number(t) > MAX_CANTIDAD) {
      nuevos.cantidad = `Ingresa un entero entre 1 y ${MAX_CANTIDAD}`
    } else if (tipo === 'AJUSTE_NEGATIVO' && Number(t) > variante.stockDisponible) {
      nuevos.cantidad = `No puedes restar más que el disponible (${variante.stockDisponible})`
    }
    const obs = observacion.trim()
    if (obs.length < 5 || obs.length > 255) nuevos.observacion = 'La observación es obligatoria (5 a 255 caracteres)'
    setErrores(nuevos)
    if (nuevos.cantidad || nuevos.observacion) return
    registrar.mutate({ varianteId: variante.varianteId, tipo, cantidad: Number(t), observacion: obs }, { onSuccess: onCerrar })
  }

  return (
    <Modal abierto titulo={`Ajuste de ${variante.sku}`} onCerrar={onCerrar} bloqueado={registrar.isPending}>
      <form
        noValidate
        aria-label="Ajuste de stock"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">
          {variante.productoNombre} · {variante.talla} · {variante.color}. Físico {variante.stockFisico}, reservado{' '}
          {variante.stockReservado}, disponible {variante.stockDisponible}.
        </p>
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Selector etiqueta="Tipo de ajuste" opciones={TIPOS} value={tipo} onChange={(e) => setTipo(e.target.value as TipoAjuste)} />
        <Campo
          etiqueta="Cantidad"
          inputMode="numeric"
          value={cantidad}
          error={errores.cantidad ?? delServidor.cantidad}
          onChange={(e) => setCantidad(e.target.value)}
        />
        <Campo
          etiqueta="Observación"
          maxLength={255}
          placeholder="Motivo del ajuste (conteo, merma, corrección)"
          value={observacion}
          error={errores.observacion ?? delServidor.observacion}
          onChange={(e) => setObservacion(e.target.value)}
        />
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={registrar.isPending} onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={registrar.isPending}>
            Registrar ajuste
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
