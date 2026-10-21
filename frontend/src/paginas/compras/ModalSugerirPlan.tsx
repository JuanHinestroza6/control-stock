import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Modal } from '../../componentes/ui/Modal'
import { Selector } from '../../componentes/ui/Selector'
import { useCategorias } from '../../hooks/useCategorias'
import { useSugerirPlan } from '../../hooks/useCompras'
import { mensajeError } from '../../lib/errores'
import type { SugerirPlanRequest } from '../../tipos/compras'

const SIN_CANDIDATAS = 'No hay variantes que necesiten reposición por ahora.'

/** Estado de navegación hacia el detalle: avisa las variantes omitidas (el detalle posterior no las trae). */
export interface EstadoNavegacionPlan {
  omitidas?: string[]
  excluidasPorTope?: number
}

export function ModalSugerirPlan({ onCerrar }: { onCerrar: () => void }) {
  const navigate = useNavigate()
  const categorias = useCategorias()
  const sugerir = useSugerirPlan()
  const [categoriaId, setCategoriaId] = useState('')
  const [observacion, setObservacion] = useState('')

  const enviar = () => {
    const datos: SugerirPlanRequest = {}
    if (categoriaId) datos.categoriaId = Number(categoriaId)
    if (observacion.trim()) datos.observacion = observacion.trim()
    sugerir.mutate(datos, {
      onSuccess: (plan) => {
        const estado: EstadoNavegacionPlan = { omitidas: plan.omitidas ?? [], excluidasPorTope: plan.excluidasPorTope ?? 0 }
        void navigate(`/compras/${plan.id}`, { state: estado })
      },
    })
  }

  const sinCandidatas = sugerir.isError && isAxiosError(sugerir.error) && sugerir.error.response?.status === 422
  const opciones = (categorias.data ?? []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))

  return (
    <Modal abierto titulo="Generar sugerencia de compra" onCerrar={onCerrar} bloqueado={sugerir.isPending}>
      <form
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          enviar()
        }}
      >
        <p className="text-sm text-oxford/80">
          Crea un borrador con las variantes que ya están en su umbral de reorden, calculado con el pronóstico. Podrás editarlo antes de
          aprobarlo.
        </p>
        <Selector
          etiqueta="Categoría (opcional)"
          placeholder="Todas las categorías"
          opciones={opciones}
          value={categoriaId}
          onChange={(e) => setCategoriaId(e.target.value)}
        />
        <Campo etiqueta="Observación (opcional)" maxLength={255} value={observacion} onChange={(e) => setObservacion(e.target.value)} />
        {sugerir.isError && (
          <p
            role="alert"
            className={`rounded-lg p-3 text-sm ${sinCandidatas ? 'border border-alerta/50 bg-alerta/10' : 'font-medium text-critico'}`}
          >
            {sinCandidatas ? `${SIN_CANDIDATAS} ${mensajeError(sugerir.error)}` : mensajeError(sugerir.error)}
          </p>
        )}
        <div className="flex flex-wrap justify-end gap-3">
          <button
            type="button"
            onClick={onCerrar}
            disabled={sugerir.isPending}
            className="inline-flex min-h-10 items-center rounded-lg px-4 py-2 text-sm font-medium text-oxford hover:bg-oxford/5 disabled:opacity-60"
          >
            Cerrar
          </button>
          <Boton type="submit" variante="secundario" cargando={sugerir.isPending}>
            Generar sugerencia
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
