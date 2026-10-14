import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/alertas'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import { Topbar } from './Topbar'

vi.mock('../../api/alertas')

function montar(rol: Rol) {
  renderizarApp(<Topbar onAbrirMenu={() => {}} menuAbierto={false} idMenu="menu" />, { rol })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.obtenerResumenAlertas).mockResolvedValue({ activas: 3, agotado: 1, stockBajo: 2, sinRotacion: 0 })
})

describe('Topbar', () => {
  it.each<Rol>(['ADMIN', 'INVENTARIO', 'VENDEDOR'])('%s ve la campana con el contador y enlaza a /alertas', async (rol) => {
    montar(rol)
    const campana = await screen.findByRole('link', { name: 'Alertas: 3 sin atender' })
    expect(campana).toHaveAttribute('href', '/alertas')
  })

  it('CONTABILIDAD no ve la campana ni consulta el resumen', () => {
    montar('CONTABILIDAD')
    expect(screen.queryByRole('link', { name: /Alertas/ })).not.toBeInTheDocument()
    expect(api.obtenerResumenAlertas).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Salir' })).toBeInTheDocument()
  })

  it('muestra 0 mientras carga o si falla el resumen', async () => {
    vi.mocked(api.obtenerResumenAlertas).mockRejectedValue(new Error('x'))
    montar('ADMIN')
    expect(await screen.findByRole('link', { name: 'Alertas: 0 sin atender' })).toBeInTheDocument()
  })

  it('muestra el enlace Mi cuenta hacia /cuenta', () => {
    montar('VENDEDOR')
    expect(screen.getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute('href', '/cuenta')
  })
})
