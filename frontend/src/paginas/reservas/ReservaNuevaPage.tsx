import { isAxiosError } from 'axios'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { BuscadorVariante } from '../../componentes/dominio/BuscadorVariante'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { useCrearReserva } from '../../hooks/useReservas'
import { mensajeError } from '../../lib/errores'
import { agregarLineaReserva, construirReserva, type ErroresReserva, type LineaReserva } from '../../lib/reservas'
import type { InventarioItemResponse } from '../../tipos/inventario'

const SIN_ERRORES: ErroresReserva = { lineas: {} }

export function ReservaNuevaPage() {
  const navigate = useNavigate()
  const crear = useCrearReserva()
  const [nombre, setNombre] = useState('')
  const [contacto, setContacto] = useState('')
  const [vigencia, setVigencia] = useState('')
  const [lineas, setLineas] = useState<LineaReserva[]>([])
  const [errores, setErrores] = useState<ErroresReserva>(SIN_ERRORES)
  const [conflicto, setConflicto] = useState(false)

  const agregar = (v: InventarioItemResponse) => {
    const r = agregarLineaReserva(lineas, v)
    setLineas(r.lineas)
    setErrores((e) => ({ ...e, general: r.error }))
  }

  const enviar = () => {
    const r = construirReserva(nombre, contacto, vigencia, lineas)
    setErrores(r.errores)
    setConflicto(false)
    if (!r.solicitud) return
    crear.mutate(r.solicitud, {
      onSuccess: () => void navigate('/reservas'),
      onError: (error) => setConflicto(isAxiosError(error) && error.response?.status === 409),
    })
  }

  const alEnviar = (e: FormEvent) => {
    e.preventDefault()
    enviar()
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <Link to="/reservas" className="text-sm font-medium text-acento hover:underline">
          Volver a apartados
        </Link>
        <h1>Nuevo apartado</h1>
      </div>

      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <Campo
            etiqueta="Nombre del cliente"
            maxLength={120}
            value={nombre}
            error={errores.clienteNombre}
            onChange={(e) => setNombre(e.target.value)}
          />
          <Campo
            etiqueta="Contacto (teléfono o usuario)"
            maxLength={120}
            value={contacto}
            error={errores.clienteContacto}
            onChange={(e) => setContacto(e.target.value)}
          />
          <Campo
            etiqueta="Vigencia en horas (opcional)"
            inputMode="numeric"
            placeholder="48"
            value={vigencia}
            error={errores.vigenciaHoras}
            onChange={(e) => setVigencia(e.target.value)}
          />
        </div>

        <BuscadorVariante etiqueta="Agregar variante (SKU, código o nombre)" onSeleccionar={agregar} />

        {lineas.length === 0 ? (
          <p className="rounded-lg border border-dashed border-oxford/20 bg-fondo p-6 text-center text-sm text-oxford/70">
            El apartado está vacío. Busca una variante para agregarla.
          </p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-oxford/10 bg-fondo">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Líneas del apartado</caption>
              <thead className="bg-oxford text-fondo">
                <tr>
                  <th scope="col" className="px-3 py-3 text-xs font-medium">Variante</th>
                  <th scope="col" className="px-3 py-3 text-xs font-medium">Cantidad</th>
                  <th scope="col" className="px-3 py-3 text-xs font-medium"><span className="sr-only">Quitar</span></th>
                </tr>
              </thead>
              <tbody>
                {lineas.map((l) => {
                  const v = l.variante
                  const err = errores.lineas[v.varianteId]
                  return (
                    <tr key={v.varianteId} className="align-top odd:bg-fondo even:bg-fondo-suave">
                      <td className="px-3 py-2">
                        <div className="flex flex-col">
                          <span className="font-medium">{v.sku}</span>
                          <span className="text-xs text-oxford/70">
                            {v.productoNombre} · {v.talla} · {v.color}
                          </span>
                          <span className="text-xs text-oxford/70">Disponible: {v.stockDisponible}</span>
                        </div>
                      </td>
                      <td className="px-3 py-2">
                        <input
                          type="text"
                          inputMode="numeric"
                          aria-label={`Cantidad ${v.sku}`}
                          aria-invalid={err ? true : undefined}
                          value={l.cantidad}
                          onChange={(ev) =>
                            setLineas((ls) => ls.map((x) => (x.variante.varianteId === v.varianteId ? { ...x, cantidad: ev.target.value } : x)))
                          }
                          className={`min-h-10 w-20 rounded-lg border bg-fondo px-2 py-1 text-sm outline-none focus:border-acento focus:ring-1 focus:ring-acento ${err ? 'border-critico' : 'border-oxford/20'}`}
                        />
                        {err && (
                          <p role="alert" className="mt-1 text-xs font-medium text-critico">
                            {err}
                          </p>
                        )}
                      </td>
                      <td className="px-3 py-2 text-right">
                        <button
                          type="button"
                          aria-label={`Quitar ${v.sku}`}
                          onClick={() => setLineas((ls) => ls.filter((x) => x.variante.varianteId !== v.varianteId))}
                          className="font-medium text-acento hover:underline"
                        >
                          Quitar
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {errores.general && (
          <p role="alert" className="text-sm font-medium text-critico">
            {errores.general}
          </p>
        )}
        {crear.isError && (
          <div role="alert" className="flex flex-col gap-2 rounded-lg border border-critico/40 bg-critico/5 p-4">
            <p className="text-sm font-medium text-oxford">{mensajeError(crear.error)}</p>
            {conflicto && (
              <div>
                <Boton variante="secundario" onClick={enviar} cargando={crear.isPending}>
                  Volver a confirmar
                </Boton>
              </div>
            )}
          </div>
        )}

        <div className="flex justify-end gap-3">
          <Link
            to="/reservas"
            className="inline-flex min-h-10 items-center rounded-lg px-4 py-2 text-sm font-medium text-oxford hover:bg-oxford/5"
          >
            Cancelar
          </Link>
          <Boton type="submit" cargando={crear.isPending}>
            Crear apartado
          </Boton>
        </div>
      </form>
    </section>
  )
}
