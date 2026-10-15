import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiInv from '../../api/inventario'
import * as api from '../../api/ventas'
import { renderizarApp } from '../../pruebas/render'
import type { InventarioItemResponse } from '../../tipos/inventario'
import type { VentaDetalleResponse } from '../../tipos/ventas'
import { VentaNuevaPage } from './VentaNuevaPage'

vi.mock('../../api/inventario')
vi.mock('../../api/ventas')

const item: InventarioItemResponse = {
  varianteId: 11, sku: 'LIV001-S-NEG', productoId: 1, productoCodigo: 'LIV001', productoNombre: 'Camiseta Oversize',
  categoria: { id: 1, nombre: 'Camisetas' }, talla: 'S', color: 'NEGRO', stockFisico: 10, stockReservado: 0, stockDisponible: 10,
  stockMinimo: 3, puntoReorden: null, claseAbc: null, estadoStock: 'OK', precioVenta: 50000, activo: true,
}

function pantalla() {
  return renderizarApp(
    <Routes>
      <Route path="/ventas/nueva" element={<VentaNuevaPage />} />
      <Route path="/ventas/:id" element={<p>Detalle de venta</p>} />
    </Routes>,
    { rol: 'VENDEDOR', ruta: '/ventas/nueva' },
  )
}

async function agregar() {
  await userEvent.type(screen.getByLabelText(/Agregar variante/), 'liv')
  await userEvent.click(await screen.findByRole('button', { name: /^LIV001-S-NEG/ }))
}

function error(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiInv.listarInventario).mockResolvedValue({ content: [item], page: { size: 8, number: 0, totalElements: 1, totalPages: 1 } })
  vi.mocked(api.registrarVenta).mockResolvedValue({ id: 9 } as VentaDetalleResponse)
})

describe('VentaNuevaPage', () => {
  it('carrito vacío, agrega, suma al repetir y calcula el total', async () => {
    pantalla()
    expect(screen.getByText(/carrito está vacío/)).toBeInTheDocument()
    await agregar()
    expect(screen.getByLabelText('Cantidad LIV001-S-NEG')).toHaveValue('1')
    expect(screen.getByText(/Total:/)).toHaveTextContent('50.000')
    await agregar()
    expect(screen.getByLabelText('Cantidad LIV001-S-NEG')).toHaveValue('2')
    expect(screen.getByText(/Total:/)).toHaveTextContent('100.000')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar LIV001-S-NEG' }))
    expect(screen.getByText(/carrito está vacío/)).toBeInTheDocument()
  })

  it('no envía un precio mayor a la lista ni un carrito vacío', async () => {
    pantalla()
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }))
    expect(screen.getByText('Agrega al menos una variante a la venta.')).toBeInTheDocument()
    await agregar()
    await userEvent.type(screen.getByLabelText('Precio LIV001-S-NEG'), '60000')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }))
    expect(screen.getByText('No puede superar el precio de lista')).toBeInTheDocument()
    expect(api.registrarVenta).not.toHaveBeenCalled()
  })

  it('registra la venta (precio solo si difiere) y navega al detalle', async () => {
    pantalla()
    await agregar()
    await userEvent.clear(screen.getByLabelText('Cantidad LIV001-S-NEG'))
    await userEvent.type(screen.getByLabelText('Cantidad LIV001-S-NEG'), '3')
    await userEvent.type(screen.getByLabelText('Precio LIV001-S-NEG'), '45000')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }))
    await waitFor(() =>
      expect(api.registrarVenta).toHaveBeenCalledWith({ lineas: [{ varianteId: 11, cantidad: 3, precioUnitario: 45000 }] }),
    )
    expect(await screen.findByText('Detalle de venta')).toBeInTheDocument()
  })

  it('un 422 muestra el mensaje y conserva el carrito sin ofrecer reconfirmar', async () => {
    vi.mocked(api.registrarVenta).mockRejectedValue(error(422, 'Stock insuficiente para LIV001-S-NEG: disponible 0, solicitado 1.'))
    pantalla()
    await agregar()
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }))
    expect(await screen.findByText(/Stock insuficiente/)).toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad LIV001-S-NEG')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Volver a confirmar' })).not.toBeInTheDocument()
  })

  it('un 409 conserva el carrito y permite volver a confirmar', async () => {
    vi.mocked(api.registrarVenta).mockRejectedValueOnce(error(409, 'El stock cambió mientras registrabas la operación. Revisa y vuelve a confirmar.'))
    pantalla()
    await agregar()
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }))
    expect(await screen.findByText(/El stock cambió/)).toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad LIV001-S-NEG')).toHaveValue('1')
    await userEvent.click(screen.getByRole('button', { name: 'Volver a confirmar' }))
    await waitFor(() => expect(api.registrarVenta).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Detalle de venta')).toBeInTheDocument()
  })
})
