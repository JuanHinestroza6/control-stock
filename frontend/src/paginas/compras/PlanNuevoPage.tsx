import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { BuscadorVariante } from '../../componentes/dominio/BuscadorVariante'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { useCrearPlan } from '../../hooks/useCompras'
import { mensajeError } from '../../lib/errores'
import type { InventarioItemResponse } from '../../tipos/inventario'
import { construirLineasPlan, MAX_LINEAS_PLAN, type LineaBorrador } from './textosPlan'

export function PlanNuevoPage() {
  const navigate = useNavigate()
  const crear = useCrearPlan()
  const [lineas, setLineas] = useState<LineaBorrador[]>([])
  const [observacion, setObservacion] = useState('')
  const [errores, setErrores] = useState<Record<number, string>>({})
  const [general, setGeneral] = useState<string>()

  const agregar = (v: InventarioItemResponse) =>
    setLineas((ls) =>
      ls.some((l) => l.varianteId === v.varianteId) || ls.length >= MAX_LINEAS_PLAN
        ? ls
        : [...ls, { varianteId: v.varianteId, sku: v.sku, descripcion: `${v.productoNombre} · ${v.talla} · ${v.color}`, cantidad: '', costo: '' }],
    )
  const cambiar = (id: number, campo: 'cantidad' | 'costo', valor: string) =>
    setLineas((ls) => ls.map((l) => (l.varianteId === id ? { ...l, [campo]: valor } : l)))
  const quitar = (id: number) => setLineas((ls) => ls.filter((l) => l.varianteId !== id))

  const enviar = (e: FormEvent) => {
    e.preventDefault()
    const r = construirLineasPlan(lineas)
    setErrores(r.errores)
    setGeneral(r.general)
    if (!r.lineas) return
    crear.mutate(
      { lineas: r.lineas, ...(observacion.trim() ? { observacion: observacion.trim() } : {}) },
      { onSuccess: (plan) => void navigate(`/compras/${plan.id}`) },
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <Link to="/compras" className="text-sm font-medium text-acento hover:underline">
          Volver a compras
        </Link>
        <h1>Nuevo plan de compra</h1>
      </div>

      <BuscadorVariante
        etiqueta="Agregar variante (busca por SKU, código o nombre)"
        onSeleccionar={agregar}
        excluirIds={lineas.map((l) => l.varianteId)}
      />
      {lineas.length >= MAX_LINEAS_PLAN && <p className="text-sm text-alerta">Llegaste al máximo de {MAX_LINEAS_PLAN} líneas.</p>}

      <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
        {lineas.length === 0 ? (
          <p className="rounded-lg border border-dashed border-oxford/20 bg-fondo p-6 text-center text-sm text-oxford/70">
            Busca una variante para agregarla al plan y define cuántas unidades pedir.
          </p>
        ) : (
          <ul aria-label="Líneas del plan" className="flex flex-col gap-3">
            {lineas.map((l) => (
              <li key={l.varianteId} className="flex flex-col gap-3 rounded-lg border border-oxford/10 bg-fondo p-4 sm:flex-row sm:items-start">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{l.sku}</p>
                  <p className="text-xs text-oxford/70">{l.descripcion}</p>
                </div>
                <div className="grid grid-cols-1 gap-3 sm:w-96 sm:grid-cols-2">
                  <Campo
                    etiqueta={`Cantidad de ${l.sku}`}
                    inputMode="numeric"
                    value={l.cantidad}
                    error={errores[l.varianteId]?.startsWith('La cantidad') ? errores[l.varianteId] : undefined}
                    onChange={(e) => cambiar(l.varianteId, 'cantidad', e.target.value)}
                  />
                  <Campo
                    etiqueta={`Costo estimado de ${l.sku}`}
                    inputMode="decimal"
                    placeholder="Precio de compra actual"
                    value={l.costo}
                    error={errores[l.varianteId]?.startsWith('El costo') ? errores[l.varianteId] : undefined}
                    onChange={(e) => cambiar(l.varianteId, 'costo', e.target.value)}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => quitar(l.varianteId)}
                  className="self-start text-sm font-medium text-acento hover:underline sm:mt-6"
                >
                  Quitar {l.sku}
                </button>
              </li>
            ))}
          </ul>
        )}

        {lineas.length > 0 && (
          <Campo etiqueta="Observación (opcional)" maxLength={255} value={observacion} onChange={(e) => setObservacion(e.target.value)} />
        )}
        {general && (
          <p role="alert" className="text-sm font-medium text-critico">
            {general}
          </p>
        )}
        {crear.isError && (
          <p role="alert" className="rounded-lg border border-critico/40 bg-critico/5 p-3 text-sm font-medium">
            {mensajeError(crear.error)}
          </p>
        )}
        <div className="flex flex-wrap gap-3">
          <Boton type="submit" cargando={crear.isPending}>
            Crear borrador
          </Boton>
          <Link to="/compras" className="inline-flex min-h-10 items-center rounded-lg px-4 py-2 text-sm font-medium text-oxford hover:bg-oxford/5">
            Cancelar
          </Link>
        </div>
      </form>
    </section>
  )
}
