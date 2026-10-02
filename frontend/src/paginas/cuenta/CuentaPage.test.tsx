import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/auth'
import { CLAVE_SESION } from '../../lib/auth'
import { renderizarApp } from '../../pruebas/render'
import type { LoginResponse } from '../../tipos/auth'
import { CuentaPage } from './CuentaPage'

vi.mock('../../api/auth')

const NUEVA = 'ClaveSegura2026!'

const RESPUESTA: LoginResponse = {
  token: 'token-nuevo',
  tipo: 'Bearer',
  expiraEn: new Date(Date.now() + 3_600_000).toISOString(),
  usuario: { id: 1, nombre: 'Ana Pérez', email: 'ana@livorno.co', rol: 'VENDEDOR', debeCambiarPassword: false },
}

function montar(debeCambiarPassword = false) {
  renderizarApp(
    <Routes>
      <Route path="/cuenta" element={<CuentaPage />} />
      <Route path="/" element={<p>Inicio</p>} />
    </Routes>,
    { ruta: '/cuenta', rol: 'VENDEDOR', debeCambiarPassword },
  )
}

async function llenar(actual: string, nueva: string, confirmacion: string) {
  await userEvent.type(screen.getByLabelText('Contraseña actual'), actual)
  await userEvent.type(screen.getByLabelText('Contraseña nueva'), nueva)
  await userEvent.type(screen.getByLabelText('Confirmar contraseña nueva'), confirmacion)
  await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }))
}

beforeEach(() => vi.resetAllMocks())

describe('CuentaPage', () => {
  it('valida en cliente: corta, sin coincidencia y empezando por "cambiar"', async () => {
    montar()
    await llenar('antigua-123', 'corta', 'otra')
    expect(await screen.findByText('Mínimo 12 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Las contraseñas no coinciden')).toBeInTheDocument()
    expect(api.cambiarPassword).not.toHaveBeenCalled()
  })

  it('rechaza una nueva que empieza por "cambiar"', async () => {
    montar()
    await llenar('antigua-123', 'CambiarYa-2026-x', 'CambiarYa-2026-x')
    expect(await screen.findByText('No puede empezar por "cambiar"')).toBeInTheDocument()
  })

  it('cambio voluntario: guarda la sesión nueva y muestra éxito', async () => {
    vi.mocked(api.cambiarPassword).mockResolvedValue(RESPUESTA)
    montar()
    await llenar('antigua-123', NUEVA, NUEVA)
    expect(await screen.findByRole('status')).toHaveTextContent('Contraseña actualizada')
    expect(api.cambiarPassword).toHaveBeenCalledWith({ actual: 'antigua-123', nueva: NUEVA })
    expect(JSON.parse(localStorage.getItem(CLAVE_SESION) ?? '{}').token).toBe('token-nuevo')
  })

  it('cambio obligatorio: avisa, limpia la marca y navega al inicio', async () => {
    vi.mocked(api.cambiarPassword).mockResolvedValue(RESPUESTA)
    montar(true)
    expect(screen.getByRole('alert')).toHaveTextContent('Debes cambiar tu contraseña temporal')
    await llenar('temporal-123456', NUEVA, NUEVA)
    expect(await screen.findByText('Inicio')).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(CLAVE_SESION) ?? '{}').usuario.debeCambiarPassword).toBe(false)
  })

  it('muestra el 422 del backend', async () => {
    vi.mocked(api.cambiarPassword).mockRejectedValue(
      new AxiosError('x', 'ERR', undefined, undefined, {
        status: 422, statusText: '', headers: {}, config: {} as never,
        data: { detail: 'La contraseña actual no es correcta.' },
      }),
    )
    montar()
    await llenar('mala-123456789', NUEVA, NUEVA)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('La contraseña actual no es correcta.'))
  })
})
