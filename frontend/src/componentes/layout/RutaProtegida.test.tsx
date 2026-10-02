import { screen } from '@testing-library/react'
import { Route, Routes, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { renderizarApp } from '../../pruebas/render'
import type { EstadoLogin } from '../../tipos/navegacion'
import { RutaProtegida } from './RutaProtegida'

function Login() {
  const estado = useLocation().state as EstadoLogin | null
  return <p>Pantalla de login (desde: {estado?.desde ?? 'ninguno'})</p>
}

function rutas() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/usuarios"
        element={
          <RutaProtegida roles={['ADMIN']}>
            <p>Gestión de usuarios</p>
          </RutaProtegida>
        }
      />
      <Route path="/cuenta" element={<RutaProtegida><p>Mi cuenta</p></RutaProtegida>} />
      <Route element={<RutaProtegida />}>
        <Route path="/" element={<p>Inicio privado</p>} />
      </Route>
    </Routes>
  )
}

describe('RutaProtegida', () => {
  it('sin sesión redirige a /login conservando el destino', () => {
    renderizarApp(rutas(), { ruta: '/usuarios?x=1' })
    expect(screen.getByText('Pantalla de login (desde: /usuarios?x=1)')).toBeInTheDocument()
  })

  it('con sesión y sin restricción de rol muestra el contenido (Outlet)', () => {
    renderizarApp(rutas(), { ruta: '/', rol: 'VENDEDOR' })
    expect(screen.getByText('Inicio privado')).toBeInTheDocument()
  })

  it('con un rol no permitido muestra acceso denegado', () => {
    renderizarApp(rutas(), { ruta: '/usuarios', rol: 'VENDEDOR' })
    expect(screen.getByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
    expect(screen.queryByText('Gestión de usuarios')).not.toBeInTheDocument()
  })

  it('con un rol permitido muestra el contenido', () => {
    renderizarApp(rutas(), { ruta: '/usuarios', rol: 'ADMIN' })
    expect(screen.getByText('Gestión de usuarios')).toBeInTheDocument()
  })

  it('con contraseña temporal redirige cualquier ruta a /cuenta', () => {
    renderizarApp(rutas(), { ruta: '/', rol: 'ADMIN', debeCambiarPassword: true })
    expect(screen.getByText('Mi cuenta')).toBeInTheDocument()
    expect(screen.queryByText('Inicio privado')).not.toBeInTheDocument()
  })

  it('con contraseña temporal permite quedarse en /cuenta', () => {
    renderizarApp(rutas(), { ruta: '/cuenta', rol: 'VENDEDOR', debeCambiarPassword: true })
    expect(screen.getByText('Mi cuenta')).toBeInTheDocument()
  })
})
