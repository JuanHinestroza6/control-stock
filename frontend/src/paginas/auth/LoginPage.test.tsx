import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { Navigate, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { cliente } from '../../api/cliente'
import { soloLogin } from '../../pruebas/http'
import { crearSesion, renderizarApp } from '../../pruebas/render'
import { LoginPage } from './LoginPage'

const adaptadorOriginal = cliente.defaults.adapter

/** Simula la respuesta del backend a nivel de transporte HTTP (el cliente Axios real queda en uso). */
function responderCon(status: number, data: unknown): { cuerpos: unknown[] } {
  const cuerpos: unknown[] = []
  cliente.defaults.adapter = soloLogin((config: InternalAxiosRequestConfig) => {
    cuerpos.push(typeof config.data === 'string' ? JSON.parse(config.data) : config.data)
    const respuesta = { status, data, statusText: '', headers: {}, config }
    return status >= 400
      ? Promise.reject(new AxiosError('fallo', 'ERR_BAD_REQUEST', config, undefined, respuesta))
      : Promise.resolve(respuesta)
  })
  return { cuerpos }
}

function pagina() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<p>Pantalla de inicio</p>} />
    </Routes>
  )
}

async function enviar(correo: string, clave: string) {
  if (correo) await userEvent.type(screen.getByLabelText('Correo electrónico'), correo)
  if (clave) await userEvent.type(screen.getByLabelText('Contraseña'), clave)
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}

describe('LoginPage', () => {
  afterEach(() => {
    cliente.defaults.adapter = adaptadorOriginal
  })

  it('valida campos obligatorios y no llama al backend', async () => {
    const { cuerpos } = responderCon(200, {})
    renderizarApp(pagina(), { ruta: '/login' })
    await enviar('', '')
    expect(await screen.findByText('Ingresa tu correo')).toBeInTheDocument()
    expect(screen.getByText('Ingresa tu contraseña')).toBeInTheDocument()
    expect(cuerpos).toHaveLength(0)
  })

  it('valida el formato del correo', async () => {
    const { cuerpos } = responderCon(200, {})
    renderizarApp(pagina(), { ruta: '/login' })
    await enviar('no-es-correo', 'secreto')
    expect(await screen.findByText('Ingresa un correo válido')).toBeInTheDocument()
    expect(cuerpos).toHaveLength(0)
  })

  it('muestra el error del backend (401)', async () => {
    responderCon(401, { title: 'No autorizado', detail: 'Credenciales inválidas' })
    renderizarApp(pagina(), { ruta: '/login' })
    await enviar('ana@livorno.co', 'mala')
    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales inválidas')
  })

  it('asigna los errores por campo del backend (400) al campo', async () => {
    responderCon(400, { title: 'Solicitud inválida', errores: [{ campo: 'email', mensaje: 'Correo mal formado' }] })
    renderizarApp(pagina(), { ruta: '/login' })
    await enviar('ana@livorno.co', 'x')
    expect(await screen.findByText('Correo mal formado')).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toHaveAttribute('aria-invalid', 'true')
  })

  it('redirige a / tras iniciar sesión y guarda la sesión', async () => {
    const { cuerpos } = responderCon(200, { ...crearSesion('ADMIN'), tipo: 'Bearer' })
    renderizarApp(pagina(), { ruta: '/login' })
    await enviar('ana@livorno.co', 'secreto')
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument()
    expect(cuerpos[0]).toEqual({ email: 'ana@livorno.co', password: 'secreto' })
    await waitFor(() => expect(localStorage.getItem('control-stock.sesion')).toContain('token-de-prueba'))
  })

  it('con sesión activa redirige de inmediato fuera de /login', () => {
    renderizarApp(pagina(), { ruta: '/login', rol: 'VENDEDOR' })
    expect(screen.getByText('Pantalla de inicio')).toBeInTheDocument()
  })

  it('recorta los espacios del correo antes de enviarlo', async () => {
    const { cuerpos } = responderCon(200, { ...crearSesion('ADMIN'), tipo: 'Bearer' })
    renderizarApp(pagina(), { ruta: '/login' })
    await enviar('  ana@livorno.co  ', 'secreto')
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument()
    expect(cuerpos[0]).toEqual({ email: 'ana@livorno.co', password: 'secreto' })
  })

  it('un 401 deja el formulario usable y permite reintentar', async () => {
    responderCon(401, { detail: 'Credenciales inválidas' })
    renderizarApp(pagina(), { ruta: '/login' })
    await enviar('ana@livorno.co', 'mala')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled()
    expect(localStorage.getItem('control-stock.sesion')).toBeNull()
  })

  it.each(['//sitio-externo.com', 'https://sitio-externo.com', '/login'])(
    'ignora el destino no seguro "%s" y va a /',
    (desde) => {
      renderizarApp(
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/entrada" element={<Navigate to="/login" state={{ desde }} />} />
          <Route path="/" element={<p>Pantalla de inicio</p>} />
        </Routes>,
        { ruta: '/entrada', rol: 'ADMIN' },
      )
      expect(screen.getByText('Pantalla de inicio')).toBeInTheDocument()
    },
  )
})
