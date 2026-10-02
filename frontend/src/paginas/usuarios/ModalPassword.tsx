import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Modal } from '../../componentes/ui/Modal'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import type { UsuarioResponse } from '../../tipos/usuarios'
import { esquemaPasswordTemporal } from './esquemaUsuario'

const esquema = z.object({ passwordTemporal: esquemaPasswordTemporal })
type Datos = z.output<typeof esquema>

interface ModalPasswordProps {
  usuario: UsuarioResponse
  guardando: boolean
  error: unknown
  onGuardar: (passwordTemporal: string) => void
  onCancelar: () => void
}

export function ModalPassword({ usuario, guardando, error, onGuardar, onCancelar }: ModalPasswordProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Datos>({ resolver: zodResolver(esquema), defaultValues: { passwordTemporal: '' } })

  const campos = error ? erroresPorCampo(error) : {}
  const banner = error && !campos.passwordTemporal ? mensajeError(error) : undefined

  return (
    <Modal abierto titulo={`Restablecer contraseña de ${usuario.nombre}`} onCerrar={onCancelar} bloqueado={guardando}>
      <form
        noValidate
        onSubmit={handleSubmit((d) => onGuardar(d.passwordTemporal))}
        className="flex flex-col gap-4"
        aria-label="Restablecer contraseña"
      >
        <p className="text-sm text-oxford/70">
          Se cerrarán las sesiones de {usuario.nombre} y deberá cambiar esta contraseña temporal al ingresar.
        </p>
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Campo
          etiqueta="Contraseña temporal"
          type="password"
          autoComplete="new-password"
          error={errors.passwordTemporal?.message ?? campos.passwordTemporal}
          {...register('passwordTemporal')}
        />
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Boton variante="secundario" disabled={guardando} onClick={onCancelar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={guardando}>
            Restablecer
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
