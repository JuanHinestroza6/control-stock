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

  it('Reportes muestra sus pestañas a ADMIN y CONTABILIDAD', () => {
    for (const rol of ['ADMIN', 'CONTABILIDAD'] as const) {
      const { unmount } = renderizarApp(<App />, { ruta: '/reportes', rol })
      expect(screen.getByRole('heading', { level: 1, name: 'Reportes' })).toBeInTheDocument()
      expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Rentabilidad', 'Libro de ventas', 'Valorización'])
      expect(screen.queryByRole('heading', { name: 'Próximamente' })).not.toBeInTheDocument()
      unmount()
    }
  })

  it('Reportes niega el acceso a INVENTARIO y VENDEDOR', () => {
    for (const rol of ['INVENTARIO', 'VENDEDOR'] as const) {
      const { unmount } = renderizarApp(<App />, { ruta: '/reportes', rol })
      expect(screen.getByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
      unmount()
    }
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
