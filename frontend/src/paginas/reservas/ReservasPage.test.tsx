import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/reservas'
import { vencimientoRelativo } from '../../lib/reservas'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { PaginaReservas, ReservaResponse } from '../../tipos/reservas'
import { ReservasPage } from './ReservasPage'

vi.mock('../../api/reservas')

function reserva(id: number, extra: Partial<ReservaResponse> = {}): ReservaResponse {
  return {
    id, numero: `R-00000${id}`, varianteId: 11, sku: 'LIV001-S-NEG', productoNombre: 'Camiseta Oversize', talla: 'S', color: 'NEGRO',
    cantidad: 2, precioVenta: 50000, clienteNombre: 'Carla Ruiz', clienteContacto: '3001234567', estado: 'ACTIVA',
    creadaEn: '2026-10-02T10:00:00', venceEn: '2099-10-04T10:00:00', creadaPor: 'Ana Pérez', cerradaEn: null, cerradaPor: null,
    motivoCancelacion: null, ventaId: null, ventaNumero: null, ...extra,
  }
}
function pagina(content: ReservaResponse[]): PaginaReservas {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } }
}
const ultimos = () => vi.mocked(api.listarReservas).mock.calls.at(-1)?.[0]

function montar(rol: Rol = 'VENDEDOR', ruta = '/reservas') {
  return renderizarApp(<ReservasPage />, { rol, ruta })
}

describe('ReservasPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('muestra la lista con ACTIVA por defecto', async () => {
    vi.mocked(api.listarReservas).mockResolvedValue(pagina([reserva(1)]))
    montar()
    expect(await screen.findByText('R-000001')).toBeInTheDocument()
    expect(screen.getByText('Carla Ruiz')).toBeInTheDocument()
    expect(ultimos()).toMatchObject({ estado: 'ACTIVA', sort: 'venceEn,asc' })
  })

  it('muestra error con reintento', async () => {
    vi.mocked(api.listarReservas).mockRejectedValueOnce(new Error('x')).mockResolvedValue(pagina([reserva(1)]))
    montar()
    await userEvent.click(await screen.findByRole('button', { name: /reintentar/i }))
    expect(await screen.findByText('R-000001')).toBeInTheDocument()
  })

  it('vacío sin filtros ofrece crear a quien puede reservar', async () => {
    vi.mocked(api.listarReservas).mockResolvedValue(pagina([]))
    montar('VENDEDOR')
    expect(await screen.findByText('No hay apartados activos.')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /apartado/i }).length).toBeGreaterThan(0)
  })

  it('CONTABILIDAD no ve el botón de nuevo apartado', async () => {
    vi.mocked(api.listarReservas).mockResolvedValue(pagina([]))
    montar('CONTABILIDAD')
    await screen.findByText('No hay apartados activos.')
    expect(screen.queryByRole('link', { name: /apartado/i })).not.toBeInTheDocument()
  })

  it('aplica filtros y lee varianteId de la URL', async () => {
    vi.mocked(api.listarReservas).mockResolvedValue(pagina([reserva(1)]))
    montar('ADMIN', '/reservas?varianteId=11')
    await screen.findByText('R-000001')
    expect(ultimos()).toMatchObject({ varianteId: 11 })
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'VENCIDA')
    await userEvent.click(screen.getByLabelText(/por vencer/i))
    await userEvent.type(screen.getByLabelText('Buscar'), 'carla')
    await waitFor(() => expect(ultimos()).toMatchObject({ estado: 'VENCIDA', porVencer: true, q: 'carla', varianteId: 11 }))
  })

  it('reserva convertida enlaza a su venta', async () => {
    vi.mocked(api.listarReservas).mockResolvedValue(
      pagina([reserva(2, { estado: 'CONVERTIDA', ventaId: 9, ventaNumero: 'V-000009', cerradaEn: '2026-10-03T09:00:00' })]),
    )
    montar()
    expect(await screen.findByRole('link', { name: 'V-000009' })).toHaveAttribute('href', '/ventas/9')
  })
})

describe('vencimientoRelativo', () => {
  const ahora = new Date('2026-10-02T10:00:00')
  it('tonos por cercanía', () => {
    expect(vencimientoRelativo('2026-10-05T10:00:00', ahora).tono).toBe('neutro')
    expect(vencimientoRelativo('2026-10-02T15:30:00', ahora)).toEqual({ texto: 'Vence en 5 h 30 min', tono: 'alerta' })
    expect(vencimientoRelativo('2026-10-02T08:00:00', ahora)).toEqual({ texto: 'Venció hace 2 h 0 min', tono: 'critico' })
  })
})
