import { act, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from '../App'
import { EVENTO_SESION_EXPIRADA } from '../api/cliente'
import { CLAVE_SESION } from '../lib/auth'
import { renderizarApp } from '../pruebas/render'

describe('SesionProvider', () => {
  it('ante el evento de sesión expirada vuelve a /login', () => {
    renderizarApp(<App />, { ruta: '/compras', rol: 'ADMIN' })
    expect(screen.getByRole('heading', { name: 'Próximamente' })).toBeInTheDocument()
    act(() => {
      window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA))
    })
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
  })

  it('Salir borra la sesión y lleva a /login', async () => {
    const { default: userEvent } = await import('@testing-library/user-event')
    renderizarApp(<App />, { rol: 'ADMIN' })
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
    expect(localStorage.getItem('control-stock.sesion')).toBeNull()
  })

  it('cerrar sesión en otra pestaña (evento storage) cierra esta sesión', () => {
    renderizarApp(<App />, { ruta: '/compras', rol: 'ADMIN' })
    expect(screen.getByRole('heading', { name: 'Próximamente' })).toBeInTheDocument()
    act(() => {
      localStorage.removeItem(CLAVE_SESION)
      window.dispatchEvent(new StorageEvent('storage', { key: CLAVE_SESION, newValue: null }))
    })
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
  })

  it('ignora eventos storage de otras claves', () => {
    renderizarApp(<App />, { ruta: '/compras', rol: 'ADMIN' })
    act(() => {
      window.dispatchEvent(new StorageEvent('storage', { key: 'otra-clave', newValue: null }))
    })
    expect(screen.getByRole('heading', { name: 'Próximamente' })).toBeInTheDocument()
  })
})
