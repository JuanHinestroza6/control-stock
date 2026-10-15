import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiInv from '../../api/inventario'
import * as api from '../../api/reservas'
import { renderizarApp } from '../../pruebas/render'
import type { InventarioItemResponse } from '../../tipos/inventario'
import { ReservaNuevaPage } from './ReservaNuevaPage'

vi.mock('../../api/inventario')
vi.mock('../../api/reservas')

const item: InventarioItemResponse = {
  varianteId: 11, sku: 'LIV001-S-NEG', productoId: 1, productoCodigo: 'LIV001', productoNombre: 'Camiseta Oversize',
  categoria: { id: 1, nombre: 'Camisetas' }, talla: 'S', color: 'NEGRO', stockFisico: 10, stockReservado: 0, stockDisponible: 3,
  stockMinimo: 3, puntoReorden: null, claseAbc: null, estadoStock: 'OK', precioVenta: 50000, activo: true,
}

function pantalla() {
  return renderizarApp(
    <Routes>
      <Route path="/reservas/nueva" element={<ReservaNuevaPage />} />
      <Route path="/reservas" element={<p>Listado de apartados</p>} />
    </Routes>,
    { rol: 'VENDEDOR', ruta: '/reservas/nueva' },
  )
}

async function agregar() {
  await userEvent.type(screen.getByLabelText(/Agregar variante/), 'liv')
  await userEvent.click(await screen.findByRole('button', { name: /^LIV001-S-NEG/ }))
}

async function datosCliente() {
  await userEvent.type(screen.getByLabelText('Nombre del cliente'), '  Carla   Ruiz ')
  await userEvent.type(screen.getByLabelText(/Contacto/), '3001234567')
}

function error(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiInv.listarInventario).mockResolvedValue({ content: [item], page: { size: 8, number: 0, totalElements: 1, totalPages: 1 } })
  vi.mocked(api.crearReserva).mockResolvedValue([])
})

describe('ReservaNuevaPage', () => {
  it('valida cliente y líneas vacías sin llamar a la API', async () => {
    pantalla()
    await userEvent.click(screen.getByRole('button', { name: 'Crear apartado' }))
    expect(screen.getByText('Entre 2 y 120 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Entre 3 y 120 caracteres')).toBeInTheDocument()
    expect(screen.getByText(/al menos una variante/)).toBeInTheDocument()
    expect(api.crearReserva).not.toHaveBeenCalled()
  })

  it('rechaza cantidad mayor al disponible y vigencia fuera de rango', async () => {
    pantalla()
    await datosCliente()
    await agregar()
    await userEvent.clear(screen.getByLabelText('Cantidad LIV001-S-NEG'))
    await userEvent.type(screen.getByLabelText('Cantidad LIV001-S-NEG'), '4')
    await userEvent.type(screen.getByLabelText(/Vigencia/), '200')
    await userEvent.click(screen.getByRole('button', { name: 'Crear apartado' }))
    expect(screen.getByText('Solo hay 3 disponibles')).toBeInTheDocument()
    expect(screen.getByText('Entero entre 1 y 168 horas')).toBeInTheDocument()
    expect(api.crearReserva).not.toHaveBeenCalled()
  })

  it('envía la solicitud normalizada y vuelve al listado', async () => {
    pantalla()
    await datosCliente()
    await agregar()
    await userEvent.type(screen.getByLabelText(/Vigencia/), '24')
    await userEvent.click(screen.getByRole('button', { name: 'Crear apartado' }))
    await waitFor(() =>
      expect(api.crearReserva).toHaveBeenCalledWith({
        clienteNombre: 'Carla Ruiz', clienteContacto: '3001234567', vigenciaHoras: 24, lineas: [{ varianteId: 11, cantidad: 1 }],
      }),
    )
    expect(await screen.findByText('Listado de apartados')).toBeInTheDocument()
  })

  it('un 409 conserva el formulario y permite volver a confirmar', async () => {
    vi.mocked(api.crearReserva).mockRejectedValueOnce(error(409, 'Conflicto de concurrencia')).mockResolvedValue([])
    pantalla()
    await datosCliente()
    await agregar()
    await userEvent.click(screen.getByRole('button', { name: 'Crear apartado' }))
    expect(await screen.findByText('Conflicto de concurrencia')).toBeInTheDocument()
    expect(screen.getByLabelText('Nombre del cliente')).toHaveValue('  Carla   Ruiz ')
    expect(screen.getByLabelText('Cantidad LIV001-S-NEG')).toHaveValue('1')
    await userEvent.click(screen.getByRole('button', { name: 'Volver a confirmar' }))
    expect(await screen.findByText('Listado de apartados')).toBeInTheDocument()
    expect(api.crearReserva).toHaveBeenCalledTimes(2)
  })

  it('un 422 conserva el formulario sin ofrecer reintento', async () => {
    vi.mocked(api.crearReserva).mockRejectedValue(error(422, 'Stock insuficiente'))
    pantalla()
    await datosCliente()
    await agregar()
    await userEvent.click(screen.getByRole('button', { name: 'Crear apartado' }))
    expect(await screen.findByText('Stock insuficiente')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Volver a confirmar' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad LIV001-S-NEG')).toBeInTheDocument()
  })
})
