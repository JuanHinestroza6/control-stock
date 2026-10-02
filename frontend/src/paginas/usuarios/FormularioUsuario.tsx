import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Modal } from '../../componentes/ui/Modal'
import { Selector } from '../../componentes/ui/Selector'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import { ETIQUETA_ROL } from '../../lib/roles'
import type { Rol } from '../../tipos/auth'
import type { UsuarioResponse } from '../../tipos/usuarios'
import {
  esquemaCrearUsuario,
  esquemaEditarPropio,
  esquemaEditarUsuario,
  type DatosFormularioUsuario,
} from './esquemaUsuario'

const OPCIONES_ROL = (Object.keys(ETIQUETA_ROL) as Rol[]).map((r) => ({ valor: r, etiqueta: ETIQUETA_ROL[r] }))

interface FormularioUsuarioProps {
  /** Usuario a editar; si es undefined el formulario crea uno nuevo. */
  usuario?: UsuarioResponse
  /** True si se edita al propio usuario: el backend rechaza cambiar el propio rol. */
  esPropio?: boolean
  guardando: boolean
  /** Error de la última petición (ProblemDetail del backend). */
  error: unknown
  onGuardar: (datos: DatosFormularioUsuario) => void
  onCancelar: () => void
}

export function FormularioUsuario({ usuario, esPropio = false, guardando, error, onGuardar, onCancelar }: FormularioUsuarioProps) {
  const editando = usuario !== undefined
  const rolFijo = editando && esPropio
  const esquema = !editando ? esquemaCrearUsuario : rolFijo ? esquemaEditarPropio : esquemaEditarUsuario
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<DatosFormularioUsuario>({
    // El esquema varía según el modo; la forma de salida coincide con DatosFormularioUsuario en cada caso.
    resolver: zodResolver(esquema) as never,
    defaultValues: {
      nombre: usuario?.nombre ?? '',
      email: usuario?.email ?? '',
      rol: usuario?.rol ?? 'VENDEDOR',
      passwordTemporal: '',
    },
  })

  const campos = error ? erroresPorCampo(error) : {}
  const hayErrorCampo = ['nombre', 'email', 'rol', 'passwordTemporal'].some((c) => campos[c])
  const banner = error && !hayErrorCampo ? mensajeError(error) : undefined

  const enviar = (datos: DatosFormularioUsuario) => onGuardar(rolFijo ? { ...datos, rol: usuario.rol } : datos)

  return (
    <Modal abierto titulo={editando ? 'Editar usuario' : 'Nuevo usuario'} onCerrar={onCancelar} bloqueado={guardando}>
      <form noValidate onSubmit={handleSubmit(enviar)} className="flex flex-col gap-4" aria-label="Formulario de usuario">
        {banner && (
          <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
            {banner}
          </p>
        )}
        <Campo etiqueta="Nombre" autoComplete="off" error={errors.nombre?.message ?? campos.nombre} {...register('nombre')} />
        <Campo etiqueta="Correo" type="email" autoComplete="off" error={errors.email?.message ?? campos.email} {...register('email')} />
        {rolFijo ? (
          <div className="flex flex-col gap-1">
            <Selector etiqueta="Rol" opciones={OPCIONES_ROL} value={usuario.rol} disabled onChange={() => {}} />
            <p className="text-xs text-oxford/70">No puedes cambiar tu propio rol.</p>
          </div>
        ) : (
          <Selector etiqueta="Rol" opciones={OPCIONES_ROL} error={errors.rol?.message ?? campos.rol} {...register('rol')} />
        )}
        {!editando && (
          <div className="flex flex-col gap-1">
            <Campo
              etiqueta="Contraseña temporal"
              type="password"
              autoComplete="new-password"
              error={errors.passwordTemporal?.message ?? campos.passwordTemporal}
              {...register('passwordTemporal')}
            />
            <p className="text-xs text-oxford/70">El usuario deberá cambiarla en su primer ingreso.</p>
          </div>
        )}
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
