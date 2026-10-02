import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Modal } from '../../componentes/ui/Modal'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import type { CategoriaRequest, CategoriaResponse } from '../../tipos/catalogo'

const esquemaCategoria = z.object({
  nombre: z.string().trim().min(1, 'Ingresa el nombre').max(60, 'Máximo 60 caracteres'),
})

interface FormularioCategoriaProps {
  /** Categoría a editar; si es undefined el formulario crea una nueva. */
  categoria?: CategoriaResponse
  guardando: boolean
  /** Error de la última petición (ProblemDetail del backend). */
  error: unknown
  onGuardar: (datos: CategoriaRequest) => void
  onCancelar: () => void
}

export function FormularioCategoria({ categoria, guardando, error, onGuardar, onCancelar }: FormularioCategoriaProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CategoriaRequest>({
    resolver: zodResolver(esquemaCategoria),
    defaultValues: { nombre: categoria?.nombre ?? '' },
  })

  const campos = error ? erroresPorCampo(error) : {}
  const errorNombre = errors.nombre?.message ?? campos.nombre
  const bannerGeneral = error && !campos.nombre ? mensajeError(error) : undefined

  return (
    <Modal abierto titulo={categoria ? 'Editar categoría' : 'Nueva categoría'} onCerrar={onCancelar} bloqueado={guardando}>
      <form noValidate onSubmit={handleSubmit(onGuardar)} className="flex flex-col gap-4" aria-label="Formulario de categoría">
        {bannerGeneral && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {bannerGeneral}
          </p>
        )}
        <Campo etiqueta="Nombre" autoComplete="off" error={errorNombre} {...register('nombre')} />
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
