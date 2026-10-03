import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useConvertirReservas } from '../../hooks/useReservas'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import {
  agruparPorVariante,
  construirConversion,
  descuentoConversion,
  precioConversion,
  totalConversion,
} from '../../lib/reservas'
import type { ReservaResponse } from '../../tipos/reservas'
import { Boton } from '../ui/Boton'
import { Modal } from '../ui/Modal'

interface ModalConvertirReservasProps {
  reservas: ReservaResponse[]
  onCerrar: () => void
}

/** Convierte reservas ACTIVAS de un mismo cliente en una venta; el precio de cada variante puede bajarse hasta > 0. */
export function ModalConvertirReservas({ reservas, onCerrar }: ModalConvertirReservasProps) {
  const navigate = useNavigate()
  const convertir = useConvertirReservas()
  const lineas = agruparPorVariante(reservas)
  const [precios, setPrecios] = useState<Record<number, string>>({})
  const [errores, setErrores] = useState<Record<number, string>>({})
  const [general, setGeneral] = useState<string>()

  const conflicto = convertir.isError && isAxiosError(convertir.error) && convertir.error.response?.status === 409

  const enviar = () => {
    const r = construirConversion(reservas, precios)
    setErrores(r.errores)
    setGeneral(r.general)
    if (!r.solicitud) return
    convertir.mutate(r.solicitud, {
      onSuccess: (venta) => {
        onCerrar()
        void navigate(`/ventas/${venta.id}`)
      },
    })
  }

  const descuento = descuentoConversion(lineas, precios)

  return (
    <Modal abierto titulo={`Convertir en venta (${reservas.length})`} onCerrar={onCerrar} bloqueado={convertir.isPending}>
      <form
        noValidate
        aria-label="Convertir reservas en venta"
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/70">
          Cliente: {reservas[0]?.clienteNombre}. Reservas: {reservas.map((r) => r.numero).join(', ')}. El precio vacío usa el de lista.
        </p>
        <ul className="flex flex-col gap-3">
          {lineas.map((l) => {
            const err = errores[l.varianteId]
            return (
              <li key={l.varianteId} className="flex flex-col gap-1 rounded-lg border border-oxford/10 p-3">
                <span className="text-sm font-medium">
                  {l.sku} · {l.cantidad} u.
                </span>
                <span className="text-xs text-oxford/70">
                  {l.productoNombre} · {l.talla} · {l.color} · Lista {formatearMoneda(l.precioLista)}
                </span>
                <input
                  type="text"
                  inputMode="decimal"
                  aria-label={`Precio ${l.sku}`}
                  aria-invalid={err ? true : undefined}
                  placeholder={String(l.precioLista)}
                  value={precios[l.varianteId] ?? ''}
                  onChange={(e) => setPrecios((p) => ({ ...p, [l.varianteId]: e.target.value }))}
                  className={`min-h-10 w-40 rounded-lg border bg-fondo px-2 py-1 text-sm outline-none focus:border-acento focus:ring-1 focus:ring-acento ${err ? 'border-critico' : 'border-oxford/20'}`}
                />
                {err && (
                  <p role="alert" className="text-xs font-medium text-critico">
                    {err}
                  </p>
                )}
                <span className="text-xs text-oxford/70">
                  Subtotal: {formatearMoneda(l.cantidad * (precioConversion(l, precios[l.varianteId] ?? '') ?? l.precioLista))}
                </span>
              </li>
            )
          })}
        </ul>

        {general && (
          <p role="alert" className="text-sm font-medium text-critico">
            {general}
          </p>
        )}
        {convertir.isError && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {mensajeError(convertir.error)}
          </p>
        )}

        <div className="flex flex-col gap-1">
          <p className="text-lg font-bold">Total: {formatearMoneda(totalConversion(lineas, precios))}</p>
          {descuento > 0 && <p className="text-xs text-oxford/70">Descuento: {formatearMoneda(descuento)}</p>}
        </div>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={convertir.isPending} onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={convertir.isPending}>
            {conflicto ? 'Volver a confirmar' : 'Convertir en venta'}
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
