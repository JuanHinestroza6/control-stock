import { useState } from 'react'
import { useRegistrarDevolucion } from '../../hooks/useVentas'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import type { LineaVentaResponse } from '../../tipos/ventas'
import { Boton } from '../ui/Boton'
import { Campo } from '../ui/Campo'
import { Modal } from '../ui/Modal'

interface ModalDevolucionProps {
  ventaId: number
  linea: LineaVentaResponse
  onCerrar: () => void
}

/** Devolución de unidades de una línea de venta; apta para venta = reingresa al stock. */
export function ModalDevolucion({ ventaId, linea, onCerrar }: ModalDevolucionProps) {
  const registrar = useRegistrarDevolucion(ventaId)
  const pendientes = linea.cantidad - linea.cantidadDevuelta
  const [cantidad, setCantidad] = useState('1')
  const [motivo, setMotivo] = useState('')
  const [apta, setApta] = useState(true)
  const [errores, setErrores] = useState<{ cantidad?: string; motivo?: string }>({})

  const delServidor = registrar.isError ? erroresPorCampo(registrar.error) : {}
  const banner = registrar.isError && Object.keys(delServidor).length === 0 ? mensajeError(registrar.error) : undefined

  const enviar = () => {
    const nuevos: { cantidad?: string; motivo?: string } = {}
    const t = cantidad.trim()
    if (!/^\d+$/.test(t) || Number(t) < 1 || Number(t) > pendientes) {
      nuevos.cantidad = `Ingresa un entero entre 1 y ${pendientes}`
    }
    const m = motivo.trim()
    if (m.length < 5 || m.length > 255) nuevos.motivo = 'El motivo es obligatorio (5 a 255 caracteres)'
    setErrores(nuevos)
    if (nuevos.cantidad || nuevos.motivo) return
    registrar.mutate({ detalleVentaId: linea.detalleId, cantidad: Number(t), motivo: m, aptaParaVenta: apta }, { onSuccess: onCerrar })
  }

  return (
    <Modal abierto titulo={`Devolución de ${linea.sku}`} onCerrar={onCerrar} bloqueado={registrar.isPending}>
      <form
        noValidate
        aria-label="Devolución"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">
          {linea.productoNombre} · {linea.talla} · {linea.color}. Vendidas {linea.cantidad}, por devolver {pendientes}.
        </p>
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Campo
          etiqueta="Cantidad a devolver"
          inputMode="numeric"
          value={cantidad}
          error={errores.cantidad ?? delServidor.cantidad}
          onChange={(e) => setCantidad(e.target.value)}
        />
        <Campo
          etiqueta="Motivo"
          maxLength={255}
          value={motivo}
          error={errores.motivo ?? delServidor.motivo}
          onChange={(e) => setMotivo(e.target.value)}
        />
        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-1" checked={apta} onChange={(e) => setApta(e.target.checked)} />
          <span>Apta para venta (las unidades vuelven al stock)</span>
        </label>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={registrar.isPending} onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={registrar.isPending}>
            Registrar devolución
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
