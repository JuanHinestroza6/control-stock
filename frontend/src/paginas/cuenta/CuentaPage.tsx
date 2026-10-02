import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useCambiarPassword } from '../../hooks/useCambiarPassword'
import { useSesion } from '../../hooks/useSesion'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import { etiquetaRol } from '../../lib/roles'
import { esquemaCambioPassword, type FormularioCambioPasswordDatos } from './esquemaPassword'

export function CuentaPage() {
  const { usuario } = useSesion()
  const navigate = useNavigate()
  const cambiar = useCambiarPassword()
  const [exito, setExito] = useState(false)
  const obligatorio = usuario?.debeCambiarPassword === true
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormularioCambioPasswordDatos>({
    resolver: zodResolver(esquemaCambioPassword),
    defaultValues: { actual: '', nueva: '', confirmacion: '' },
  })

  const campos = cambiar.error ? erroresPorCampo(cambiar.error) : {}
  const hayErrorCampo = Boolean(campos.actual || campos.nueva)
  const banner = cambiar.error && !hayErrorCampo ? mensajeError(cambiar.error) : undefined

  const enviar = (datos: FormularioCambioPasswordDatos) => {
    setExito(false)
    const eraObligatorio = obligatorio
    cambiar.mutate(
      { actual: datos.actual, nueva: datos.nueva },
      {
        onSuccess: () => {
          reset()
          if (eraObligatorio) navigate('/', { replace: true })
          else setExito(true)
        },
      },
    )
  }

  return (
    <section className="mx-auto flex max-w-xl flex-col gap-4">
      <h1 className="font-titulo text-2xl font-bold text-oxford">Mi cuenta</h1>
      {usuario && (
        <Tarjeta>
          <p className="text-sm font-medium">{usuario.nombre}</p>
          <p className="text-sm text-oxford/70">{usuario.email}</p>
          <p className="text-xs font-medium text-oxford/60">{etiquetaRol(usuario.rol)}</p>
        </Tarjeta>
      )}
      <Tarjeta>
        <h2 className="mb-3 font-titulo text-lg font-medium text-oxford">Cambiar contraseña</h2>
        {obligatorio && (
          <p role="alert" className="mb-3 rounded-lg bg-alerta/10 px-3 py-2 text-sm font-medium text-oxford">
            Debes cambiar tu contraseña temporal antes de continuar.
          </p>
        )}
        {exito && (
          <p role="status" className="mb-3 rounded-lg bg-ok/10 px-3 py-2 text-sm font-medium text-oxford">
            Contraseña actualizada.
          </p>
        )}
        <form noValidate onSubmit={handleSubmit(enviar)} className="flex flex-col gap-4" aria-label="Cambiar contraseña">
          {banner && (
            <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
              {banner}
            </p>
          )}
          <Campo
            etiqueta="Contraseña actual"
            type="password"
            autoComplete="current-password"
            error={errors.actual?.message ?? campos.actual}
            {...register('actual')}
          />
          <Campo
            etiqueta="Contraseña nueva"
            type="password"
            autoComplete="new-password"
            error={errors.nueva?.message ?? campos.nueva}
            {...register('nueva')}
          />
          <Campo
            etiqueta="Confirmar contraseña nueva"
            type="password"
            autoComplete="new-password"
            error={errors.confirmacion?.message}
            {...register('confirmacion')}
          />
          <Boton type="submit" cargando={cambiar.isPending} className="self-end">
            Cambiar contraseña
          </Boton>
        </form>
      </Tarjeta>
    </section>
  )
}
