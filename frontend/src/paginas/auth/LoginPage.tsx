import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation } from 'react-router'
import { z } from 'zod'
import { rutaPermitida } from '../../componentes/layout/modulos'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useLogin } from '../../hooks/useLogin'
import { useSesion } from '../../hooks/useSesion'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import { destinoSeguro } from '../../lib/navegacion'
import type { EstadoLogin } from '../../tipos/navegacion'

const esquema = z.object({
  email: z.string().trim().min(1, 'Ingresa tu correo').pipe(z.email('Ingresa un correo válido')),
  password: z.string().min(1, 'Ingresa tu contraseña'),
})

type DatosLogin = z.infer<typeof esquema>

export function LoginPage() {
  const { usuario } = useSesion()
  const ubicacion = useLocation()
  const login = useLogin()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<DatosLogin>({ resolver: zodResolver(esquema), defaultValues: { email: '', password: '' } })

  if (usuario) {
    const estado = ubicacion.state as EstadoLogin | null
    const destino = destinoSeguro(estado?.desde)
    return <Navigate to={rutaPermitida(destino, usuario.rol) ? destino : '/'} replace />
  }

  const alEnviar = (datos: DatosLogin) => {
    login.mutate(datos, {
      onError: (error) => {
        const campos = erroresPorCampo(error)
        if (campos.email) setError('email', { message: campos.email })
        if (campos.password) setError('password', { message: campos.password })
      },
    })
  }

  const camposDelBackend = login.error ? erroresPorCampo(login.error) : {}
  const mostrarBanner = login.isError && !camposDelBackend.email && !camposDelBackend.password

  return (
    <main className="flex min-h-screen items-center justify-center bg-fondo-suave p-4">
      <Tarjeta className="w-full max-w-sm p-6">
        <h1>Control Stock</h1>
        <h2 className="text-acento">Livorno Clothing</h2>
        <form
          noValidate
          onSubmit={handleSubmit(alEnviar)}
          className="mt-6 flex flex-col gap-4"
          aria-label="Iniciar sesión"
        >
          {mostrarBanner && (
            <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
              {mensajeError(login.error)}
            </p>
          )}
          <Campo
            etiqueta="Correo electrónico"
            type="email"
            autoComplete="username"
            error={errors.email?.message}
            {...register('email')}
          />
          <Campo
            etiqueta="Contraseña"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <Boton type="submit" cargando={login.isPending}>
            Ingresar
          </Boton>
        </form>
      </Tarjeta>
    </main>
  )
}
