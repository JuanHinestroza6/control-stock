import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import App from './App'
import { EVENTO_SESION_EXPIRADA, cliente } from './api/cliente'
import type { Rol } from './tipos/auth'
import { soloLogin } from './pruebas/http'
import { crearSesion, renderizarApp } from './pruebas/render'

const adaptadorOriginal = cliente.defaults.adapter

function loginResponde(rol: Rol) {
  cliente.defaults.adapter = soloLogin((config: InternalAxiosRequestConfig) =>
    Promise.resolve({ status: 200, data: { ...crearSesion(rol), tipo: 'Bearer' }, statusText: '', headers: {}, config }),
  )
}

async function iniciarSesion() {
  await userEvent.type(screen.getByLabelText('Correo electrónico'), 'ana@livorno.co')
  await userEvent.type(screen.getByLabelText('Contraseña'), 'secreto')
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}

describe('destino tras iniciar sesión', () => {
  afterEach(() => {
    cliente.defaults.adapter = adaptadorOriginal
  })

  it('tras Salir (cierre voluntario) el siguiente login va a /', async () => {
    loginResponde('VENDEDOR')
    renderizarApp(<App />, { ruta: '/analitica', rol: 'VENDEDOR' })
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
    await iniciarSesion()
    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Ana Pérez' })).toBeInTheDocument()
  })

  it('el acceso directo sin sesión conserva el destino', async () => {
    loginResponde('ADMIN')
    renderizarApp(<App />, { ruta: '/compras' })
    await iniciarSesion()
    expect(await screen.findByRole('heading', { level: 1, name: 'Compras' })).toBeInTheDocument()
  })

  it('si el destino no está permitido para el rol que entra, va a / y no a acceso denegado', async () => {
    loginResponde('VENDEDOR')
    renderizarApp(<App />, { ruta: '/usuarios' })
    await iniciarSesion()
    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Ana Pérez' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Acceso denegado' })).not.toBeInTheDocument()
  })

  it('tras expirar la sesión en una ruta, el nuevo login vuelve a esa ruta', async () => {
    loginResponde('ADMIN')
    renderizarApp(<App />, { ruta: '/compras', rol: 'ADMIN' })
    act(() => {
      window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA))
    })
    await iniciarSesion()
    expect(await screen.findByRole('heading', { level: 1, name: 'Compras' })).toBeInTheDocument()
  })

  it('al expirar la sesión se limpia la caché de consultas', () => {
    const { queryClient } = renderizarApp(<App />, { ruta: '/', rol: 'ADMIN' })
    queryClient.setQueryData(['dato'], 1)
    act(() => {
      window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA))
    })
    expect(queryClient.getQueryData(['dato'])).toBeUndefined()
  })
})
