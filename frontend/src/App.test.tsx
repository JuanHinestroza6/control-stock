import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'
import { renderizarApp } from './pruebas/render'

describe('App', () => {
  it('sin sesión lleva al formulario de login', () => {
    renderizarApp(<App />, { ruta: '/ventas' })
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
  })

  it('con sesión muestra la bienvenida con nombre y rol dentro del layout', () => {
    renderizarApp(<App />, { rol: 'ADMIN' })
    expect(screen.getByRole('heading', { level: 1, name: 'Hola, Ana Pérez' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Módulos' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salir' })).toBeInTheDocument()
  })

  it('un módulo sin construir muestra Próximamente', () => {
    renderizarApp(<App />, { ruta: '/analitica', rol: 'VENDEDOR' })
    expect(screen.getByRole('heading', { name: 'Próximamente' })).toBeInTheDocument()
  })

  it('un módulo fuera del rol muestra acceso denegado', () => {
    renderizarApp(<App />, { ruta: '/usuarios', rol: 'VENDEDOR' })
    expect(screen.getByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
  })

  it('una ruta inexistente muestra 404', () => {
    renderizarApp(<App />, { ruta: '/no-existe', rol: 'ADMIN' })
    expect(screen.getByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument()
  })
})
