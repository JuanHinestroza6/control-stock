import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/reservas'
import { horasMaximasExtension } from '../../lib/reservas'
import { renderizarApp } from '../../pruebas/render'
import type { ReservaResponse } from '../../tipos/reservas'
import { ModalCancelarReserva } from './ModalCancelarReserva'
import { ModalExtenderReserva } from './ModalExtenderReserva'

vi.mock('../../api/reservas')

function reserva(extra: Partial<ReservaResponse> = {}): ReservaResponse {
  return {
    id: 7, numero: 'R-000007', varianteId: 11, sku: 'LIV001-S-NEG', productoNombre: 'Camiseta', talla: 'S', color: 'NEGRO', cantidad: 2,
    precioVenta: 50000, clienteNombre: 'Carla Ruiz', clienteContacto: '300', estado: 'ACTIVA', creadaEn: new Date().toISOString(),
    venceEn: new Date(Date.now() + 3_600_000).toISOString(), creadaPor: 'Ana', cerradaEn: null, cerradaPor: null, motivoCancelacion: null,
    ventaId: null, ventaNumero: null, ...extra,
  }
}
function error(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}

beforeEach(() => vi.resetAllMocks())

describe('ModalCancelarReserva', () => {
  it('exige motivo de 5 a 200 caracteres y envía el recortado', async () => {
    vi.mocked(api.cancelarReserva).mockResolvedValue(reserva({ estado: 'CANCELADA' }))
    const onCerrar = vi.fn()
    renderizarApp(<ModalCancelarReserva reserva={reserva()} onCerrar={onCerrar} />, { rol: 'VENDEDOR' })
    await userEvent.type(screen.getByLabelText('Motivo de la cancelación'), 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar apartado' }))
    expect(screen.getByText(/5 a 200 caracteres/)).toBeInTheDocument()
    expect(api.cancelarReserva).not.toHaveBeenCalled()
    await userEvent.type(screen.getByLabelText('Motivo de la cancelación'), 'de  ')
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar apartado' }))
    await vi.waitFor(() => expect(api.cancelarReserva).toHaveBeenCalledWith(7, { motivo: 'abcde' }))
    await vi.waitFor(() => expect(onCerrar).toHaveBeenCalled())
  })

  it('un 409 conserva el motivo y ofrece volver a confirmar', async () => {
    vi.mocked(api.cancelarReserva).mockRejectedValue(error(409, 'La reserva R-000007 ya está CONVERTIDA.'))
    const onCerrar = vi.fn()
    renderizarApp(<ModalCancelarReserva reserva={reserva()} onCerrar={onCerrar} />, { rol: 'VENDEDOR' })
    await userEvent.type(screen.getByLabelText('Motivo de la cancelación'), 'cliente desistió')
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar apartado' }))
    expect(await screen.findByText(/ya está CONVERTIDA/)).toBeInTheDocument()
    expect(screen.getByLabelText('Motivo de la cancelación')).toHaveValue('cliente desistió')
    expect(screen.getByRole('button', { name: 'Volver a confirmar' })).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
  })
})

describe('ModalExtenderReserva', () => {
  it('valida rango 1-168 y envía las horas', async () => {
    vi.mocked(api.extenderReserva).mockResolvedValue(reserva())
    const onCerrar = vi.fn()
    renderizarApp(<ModalExtenderReserva reserva={reserva()} onCerrar={onCerrar} />, { rol: 'INVENTARIO' })
    await userEvent.type(screen.getByLabelText(/Horas/), '0')
    await userEvent.click(screen.getByRole('button', { name: 'Extender' }))
    expect(screen.getByText('Entero entre 1 y 168 horas')).toBeInTheDocument()
    await userEvent.clear(screen.getByLabelText(/Horas/))
    await userEvent.type(screen.getByLabelText(/Horas/), '24')
    await userEvent.click(screen.getByRole('button', { name: 'Extender' }))
    await vi.waitFor(() => expect(api.extenderReserva).toHaveBeenCalledWith(7, { horas: 24 }))
    await vi.waitFor(() => expect(onCerrar).toHaveBeenCalled())
  })

  it('no permite pasar de 7 días desde la creación', async () => {
    const creada = new Date(Date.now() - 6.5 * 24 * 3_600_000).toISOString()
    renderizarApp(<ModalExtenderReserva reserva={reserva({ creadaEn: creada })} onCerrar={vi.fn()} />, { rol: 'ADMIN' })
    await userEvent.type(screen.getByLabelText(/Horas/), '24')
    await userEvent.click(screen.getByRole('button', { name: 'Extender' }))
    expect(screen.getByText('Una reserva no puede durar más de 7 días.')).toBeInTheDocument()
    expect(api.extenderReserva).not.toHaveBeenCalled()
  })

  it('horasMaximasExtension respeta el tope', () => {
    const ahora = new Date('2026-10-05T10:00:00')
    expect(horasMaximasExtension('2026-10-02T10:00:00', ahora)).toBe(96)
    expect(horasMaximasExtension('2026-10-05T10:00:00', ahora)).toBe(168)
    expect(horasMaximasExtension('2026-09-20T10:00:00', ahora)).toBe(0)
  })
})
