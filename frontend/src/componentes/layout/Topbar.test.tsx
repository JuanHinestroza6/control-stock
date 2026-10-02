import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import { Topbar } from './Topbar'

function montar(rol: Rol) {
  renderizarApp(<Topbar onAbrirMenu={() => {}} menuAbierto={false} idMenu="menu" />, { rol })
}

describe('Topbar', () => {
  it.each<Rol>(['ADMIN', 'INVENTARIO', 'VENDEDOR'])('%s ve la campana de alertas', (rol) => {
    montar(rol)
    expect(screen.getByRole('button', { name: /Alertas/ })).toBeInTheDocument()
  })

  it('CONTABILIDAD no ve la campana de alertas', () => {
    montar('CONTABILIDAD')
    expect(screen.queryByRole('button', { name: /Alertas/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salir' })).toBeInTheDocument()
  })

  it('muestra el enlace Mi cuenta hacia /cuenta', () => {
    montar('VENDEDOR')
    expect(screen.getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute('href', '/cuenta')
  })
})
