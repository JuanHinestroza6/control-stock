import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/usuarios'
import { renderizarApp } from '../../pruebas/render'
import type { PaginaUsuarios, UsuarioResponse } from '../../tipos/usuarios'
import { UsuariosPage } from './UsuariosPage'

vi.mock('../../api/usuarios')

function usuario(id: number, extra: Partial<UsuarioResponse> = {}): UsuarioResponse {
  return {
    id,
    nombre: `Usuario ${id}`,
    email: `u${id}@livorno.co`,
    rol: 'VENDEDOR',
    activo: true,
    debeCambiarPassword: false,
    creadoEn: '2026-10-01T10:00:00',
    actualizadoEn: '2026-10-01T10:00:00',
    ...extra,
  }
}

function pagina(content: UsuarioResponse[]): PaginaUsuarios {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } }
}

function errorHttp(status: number, detail: string, errores?: { campo: string; mensaje: string }[]) {
  return new AxiosError('fallo', 'ERR', undefined, undefined, {
    status,
    statusText: '',
    headers: {},
    config: {} as never,
    data: { detail, errores },
  })
}

const PASSWORD = 'TemporalSegura-2026'

beforeEach(() => {
  vi.resetAllMocks()
  // renderizarApp crea la sesión con id 1 (Ana Pérez)
  vi.mocked(api.listarUsuarios).mockResolvedValue(
    pagina([usuario(1, { nombre: 'Ana Pérez', rol: 'ADMIN' }), usuario(2, { debeCambiarPassword: true }), usuario(3, { activo: false })]),
  )
})

describe('UsuariosPage', () => {
  it('muestra carga y luego la tabla con estado y marca de contraseña temporal', async () => {
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument()
    expect(await screen.findByText('Usuario 2')).toBeInTheDocument()
    expect(screen.getByText('Contraseña temporal')).toBeInTheDocument()
    expect(screen.getByText('Inactivo')).toBeInTheDocument()
  })

  it('muestra error con reintento', async () => {
    vi.mocked(api.listarUsuarios).mockRejectedValueOnce(errorHttp(500, 'Servidor caído'))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Servidor caído')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Usuario 2')).toBeInTheDocument()
  })

  it('muestra estado vacío con acción', async () => {
    vi.mocked(api.listarUsuarios).mockResolvedValue(pagina([]))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    expect(await screen.findByText('Aún no hay usuarios.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear el primer usuario' })).toBeInTheDocument()
  })

  it('filtra por rol y estado enviando los parámetros', async () => {
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.selectOptions(screen.getByLabelText('Rol'), 'INVENTARIO')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'false')
    await vi.waitFor(() =>
      expect(vi.mocked(api.listarUsuarios).mock.calls.at(-1)?.[0]).toMatchObject({ rol: 'INVENTARIO', activo: false, page: 0 }),
    )
  })

  it('valida el formulario de creación en cliente', async () => {
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    const form = screen.getByRole('form', { name: 'Formulario de usuario' })
    await userEvent.type(within(form).getByLabelText('Correo'), 'no-es-correo')
    await userEvent.type(within(form).getByLabelText('Contraseña temporal'), 'corta')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))
    expect(await within(form).findByText('Ingresa el nombre')).toBeInTheDocument()
    expect(within(form).getByText('Correo no válido')).toBeInTheDocument()
    expect(within(form).getByText('Mínimo 12 caracteres')).toBeInTheDocument()
    expect(api.crearUsuario).not.toHaveBeenCalled()
  })

  it('crea un usuario con contraseña temporal y cierra el formulario', async () => {
    vi.mocked(api.crearUsuario).mockResolvedValue(usuario(9, { debeCambiarPassword: true }))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    const form = screen.getByRole('form', { name: 'Formulario de usuario' })
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Luis Gómez')
    await userEvent.type(within(form).getByLabelText('Correo'), 'luis@livorno.co')
    await userEvent.selectOptions(within(form).getByLabelText('Rol'), 'INVENTARIO')
    await userEvent.type(within(form).getByLabelText('Contraseña temporal'), PASSWORD)
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))
    await vi.waitFor(() =>
      expect(api.crearUsuario).toHaveBeenCalledWith({
        nombre: 'Luis Gómez',
        email: 'luis@livorno.co',
        rol: 'INVENTARIO',
        passwordTemporal: PASSWORD,
      }),
    )
    await vi.waitFor(() => expect(screen.queryByRole('form', { name: 'Formulario de usuario' })).not.toBeInTheDocument())
  })

  it('muestra el 409 de correo duplicado del backend', async () => {
    vi.mocked(api.crearUsuario).mockRejectedValue(errorHttp(409, 'Ya existe un usuario con ese correo.'))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    const form = screen.getByRole('form', { name: 'Formulario de usuario' })
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Luis')
    await userEvent.type(within(form).getByLabelText('Correo'), 'luis@livorno.co')
    await userEvent.type(within(form).getByLabelText('Contraseña temporal'), PASSWORD)
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))
    expect(await within(form).findByText('Ya existe un usuario con ese correo.')).toBeInTheDocument()
  })

  it('edita un usuario sin pedir contraseña', async () => {
    vi.mocked(api.actualizarUsuario).mockResolvedValue(usuario(2))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Editar Usuario 2' }))
    const form = screen.getByRole('form', { name: 'Formulario de usuario' })
    expect(within(form).queryByLabelText('Contraseña temporal')).not.toBeInTheDocument()
    await userEvent.selectOptions(within(form).getByLabelText('Rol'), 'CONTABILIDAD')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))
    await vi.waitFor(() =>
      expect(api.actualizarUsuario).toHaveBeenCalledWith(2, { nombre: 'Usuario 2', email: 'u2@livorno.co', rol: 'CONTABILIDAD' }),
    )
  })

  it('no ofrece desactivar ni restablecer sobre la propia cuenta', async () => {
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    expect(screen.queryByRole('button', { name: 'Desactivar Ana Pérez' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Restablecer contraseña de Ana Pérez' })).not.toBeInTheDocument()
    expect(screen.getByText('Tu cuenta')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Desactivar Usuario 2' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Activar Usuario 3' })).toBeInTheDocument()
  })

  it('desactiva con confirmación', async () => {
    vi.mocked(api.cambiarEstadoUsuario).mockResolvedValue(usuario(2, { activo: false }))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Desactivar Usuario 2' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desactivar' }))
    await vi.waitFor(() => expect(api.cambiarEstadoUsuario).toHaveBeenCalledWith(2, false))
  })

  it('muestra el 422 de último administrador dentro del diálogo', async () => {
    vi.mocked(api.cambiarEstadoUsuario).mockRejectedValue(errorHttp(422, 'Debe quedar al menos un administrador activo.'))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Desactivar Usuario 2' }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar' }))
    expect(await within(dialogo).findByText('Debe quedar al menos un administrador activo.')).toBeInTheDocument()
  })

  it('restablece la contraseña validando y confirma con aviso', async () => {
    vi.mocked(api.restablecerPassword).mockResolvedValue(undefined)
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Restablecer contraseña de Usuario 2' }))
    const form = screen.getByRole('form', { name: 'Restablecer contraseña' })
    await userEvent.type(within(form).getByLabelText('Contraseña temporal'), 'cambiar-esto-123')
    await userEvent.click(within(form).getByRole('button', { name: 'Restablecer' }))
    expect(await within(form).findByText('No puede empezar por "cambiar"')).toBeInTheDocument()
    await userEvent.clear(within(form).getByLabelText('Contraseña temporal'))
    await userEvent.type(within(form).getByLabelText('Contraseña temporal'), PASSWORD)
    await userEvent.click(within(form).getByRole('button', { name: 'Restablecer' }))
    await vi.waitFor(() => expect(api.restablecerPassword).toHaveBeenCalledWith(2, PASSWORD))
    expect(await screen.findByRole('status')).toHaveTextContent('Contraseña temporal fijada para Usuario 2.')
  })

  it('al editarse a sí mismo el rol está bloqueado y se conserva', async () => {
    vi.mocked(api.actualizarUsuario).mockResolvedValue(usuario(1))
    renderizarApp(<UsuariosPage />, { rol: 'ADMIN' })
    await screen.findByText('Usuario 2')
    await userEvent.click(screen.getByRole('button', { name: 'Editar Ana Pérez' }))
    const form = screen.getByRole('form', { name: 'Formulario de usuario' })
    expect(within(form).getByLabelText('Rol')).toBeDisabled()
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))
    await vi.waitFor(() =>
      expect(api.actualizarUsuario).toHaveBeenCalledWith(1, { nombre: 'Ana Pérez', email: 'u1@livorno.co', rol: 'ADMIN' }),
    )
  })
})
