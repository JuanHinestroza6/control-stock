import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/alertas'
import { renderizarApp } from '../../pruebas/render'
import type { AlertaResponse } from '../../tipos/alertas'
import type { Rol } from '../../tipos/auth'
import { AlertasPage } from './AlertasPage'

vi.mock('../../api/alertas')

function alerta(id: number, extra: Partial<AlertaResponse> = {}): AlertaResponse {
  return {
    id, tipo: 'AGOTADO', estado: 'ACTIVA', varianteId: 11, sku: 'LIV001-S-NEG', productoNombre: 'Camiseta Oversize', talla: 'S',
    color: 'NEGRO', stockDisponible: 0, umbral: 3, creadaEn: '2026-10-02T10:00:00', revisadaEn: '2026-10-02T10:00:00',
    atendidaEn: null, atendidaPor: null, ...extra,
  }
}
function pagina(content: AlertaResponse[]) {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } }
}
const ultimos = () => vi.mocked(api.listarAlertas).mock.calls.at(-1)?.[0]

function montar(rol: Rol, lista: AlertaResponse[] = [alerta(1)]) {
  vi.mocked(api.listarAlertas).mockResolvedValue(pagina(lista))
  return renderizarApp(<AlertasPage />, { rol })
}

beforeEach(() => vi.resetAllMocks())

describe('AlertasPage', () => {
  it('lista las activas por defecto', async () => {
    montar('ADMIN')
    expect(await screen.findByText('LIV001-S-NEG · S · NEGRO')).toBeInTheDocument()
    expect(ultimos()).toMatchObject({ estado: 'ACTIVA', page: 0 })
  })

  it('muestra error con reintento', async () => {
    vi.mocked(api.listarAlertas).mockRejectedValueOnce(new Error('x')).mockResolvedValue(pagina([alerta(1)]))
    renderizarApp(<AlertasPage />, { rol: 'ADMIN' })
    await userEvent.click(await screen.findByRole('button', { name: /reintentar/i }))
    expect(await screen.findByText('LIV001-S-NEG · S · NEGRO')).toBeInTheDocument()
  })

  it('vacío', async () => {
    montar('ADMIN', [])
    expect(await screen.findByText(/No hay alertas activas/)).toBeInTheDocument()
  })

  it('ADMIN e INVENTARIO atienden; VENDEDOR no', async () => {
    vi.mocked(api.atenderAlerta).mockResolvedValue(alerta(1, { estado: 'ATENDIDA' }))
    const { unmount } = montar('INVENTARIO')
    await userEvent.click(await screen.findByRole('button', { name: 'Atender LIV001-S-NEG' }))
    await waitFor(() => expect(vi.mocked(api.atenderAlerta).mock.calls[0][0]).toBe(1))
    unmount()
    montar('VENDEDOR')
    await screen.findByText('LIV001-S-NEG · S · NEGRO')
    expect(screen.queryByRole('button', { name: /Atender/ })).not.toBeInTheDocument()
  })

  it('kardex solo para roles con acceso', async () => {
    const { unmount } = montar('ADMIN')
    expect(await screen.findByRole('link', { name: 'Kardex de LIV001-S-NEG' })).toHaveAttribute('href', '/inventario/kardex?varianteId=11')
    unmount()
    montar('VENDEDOR')
    await screen.findByText('LIV001-S-NEG · S · NEGRO')
    expect(screen.queryByRole('link', { name: /Kardex/ })).not.toBeInTheDocument()
  })

  it('filtra por tipo y estado', async () => {
    montar('ADMIN')
    await screen.findByText('LIV001-S-NEG · S · NEGRO')
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'STOCK_BAJO')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'ATENDIDA')
    await waitFor(() => expect(ultimos()).toMatchObject({ tipo: 'STOCK_BAJO', estado: 'ATENDIDA' }))
  })

  it('muestra quién atendió o Automático', async () => {
    montar('ADMIN', [
      alerta(1, { estado: 'ATENDIDA', atendidaEn: '2026-10-03T09:00:00', atendidaPor: null }),
      alerta(2, { estado: 'ATENDIDA', atendidaEn: '2026-10-03T09:00:00', atendidaPor: 'Ana', varianteId: 12, sku: 'X' }),
    ])
    expect(await screen.findByText('Automático')).toBeInTheDocument()
    expect(screen.getByText('Ana')).toBeInTheDocument()
  })
})
