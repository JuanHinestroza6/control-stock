import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/inventario'
import * as apiProductos from '../../api/productos'
import * as apiVariantes from '../../api/variantes'
import { renderizarApp } from '../../pruebas/render'
import type { MatrizResponse, ProductoDetalleResponse } from '../../tipos/catalogo'
import type { InventarioItemResponse } from '../../tipos/inventario'
import { construirEntrada } from './entrada'
import { EntradaNuevaPage } from './EntradaNuevaPage'

vi.mock('../../api/inventario')
vi.mock('../../api/productos')
vi.mock('../../api/variantes')

const producto: ProductoDetalleResponse = {
  id: 1,
  codigo: 'LIV001',
  nombre: 'Camiseta Oversize',
  categoria: { id: 1, nombre: 'Camisetas' },
  precioCompra: 25000,
  precioVenta: 59900,
  leadTimeDias: 15,
  loteMinimo: 1,
  activo: true,
  totalVariantes: 3,
  creadoEn: '2026-10-01T10:00:00',
  actualizadoEn: '2026-10-01T10:00:00',
}

const matriz: MatrizResponse = {
  productoId: 1,
  tallas: ['S', 'M'],
  colores: ['NEGRO'],
  celdas: [
    { talla: 'S', color: 'NEGRO', varianteId: 11, sku: 'LIV001-S-NEG', stockFisico: 0, stockReservado: 0, stockDisponible: 0, estadoStock: 'AGOTADO', activo: true },
    { talla: 'M', color: 'NEGRO', varianteId: 12, sku: 'LIV001-M-NEG', stockFisico: 4, stockReservado: 0, stockDisponible: 4, estadoStock: 'OK', activo: true },
  ],
}

function item(): InventarioItemResponse {
  return {
    varianteId: 11, sku: 'LIV001-S-NEG', productoId: 1, productoCodigo: 'LIV001', productoNombre: 'Camiseta Oversize',
    categoria: { id: 1, nombre: 'Camisetas' }, talla: 'S', color: 'NEGRO', stockFisico: 0, stockReservado: 0, stockDisponible: 0,
    stockMinimo: 3, puntoReorden: null, estadoStock: 'AGOTADO', precioVenta: 59900, activo: true,
  }
}

function pantalla(ruta = '/inventario/entradas/nueva?productoId=1') {
  return renderizarApp(
    <Routes>
      <Route path="/inventario/entradas/nueva" element={<EntradaNuevaPage />} />
      <Route path="/inventario" element={<p>Listado de inventario</p>} />
    </Routes>,
    { rol: 'INVENTARIO', ruta },
  )
}

function error(status: number, detail: string) {
  return new AxiosError('x', 'E', undefined, undefined, { status, statusText: '', headers: {}, config: {} as never, data: { detail } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiProductos.obtenerProducto).mockResolvedValue(producto)
  vi.mocked(apiVariantes.obtenerMatriz).mockResolvedValue(matriz)
  vi.mocked(api.listarInventario).mockResolvedValue({ content: [item()], page: { size: 8, number: 0, totalElements: 1, totalPages: 1 } })
  vi.mocked(api.registrarEntrada).mockResolvedValue([])
})

describe('construirEntrada', () => {
  it('aplica un solo costo por producto, permite 0 y rechaza costos inválidos o sin cantidades', () => {
    const ok = construirEntrada([1], { 1: { 11: '3', 12: '' } }, { 1: '0' }, ' recibido ')
    expect(ok.solicitud).toEqual({ observacion: 'recibido', lineas: [{ varianteId: 11, cantidad: 3, costoUnitario: 0 }] })
    expect(construirEntrada([1], { 1: { 11: '3' } }, { 1: '1.234' }, '').erroresCosto[1]).toBeDefined()
    expect(construirEntrada([1], { 1: { 11: '0' } }, { 1: '10' }, '').general).toMatch(/al menos una cantidad/)
    expect(construirEntrada([1], { 1: { 11: '1.5' } }, { 1: '10' }, '').erroresCantidad[11]).toBeDefined()
  })
})

describe('EntradaNuevaPage', () => {
  it('prellena el costo con precioCompra y envía las líneas con ese costo', async () => {
    pantalla()
    expect(await screen.findByLabelText('Costo unitario de LIV001')).toHaveValue('25000')
    await userEvent.type(await screen.findByLabelText('Cantidad LIV001-S-NEG'), '5')
    await userEvent.type(screen.getByLabelText('Cantidad LIV001-M-NEG'), '2')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar entrada' }))
    await waitFor(() =>
      expect(api.registrarEntrada).toHaveBeenCalledWith({
        lineas: [
          { varianteId: 11, cantidad: 5, costoUnitario: 25000 },
          { varianteId: 12, cantidad: 2, costoUnitario: 25000 },
        ],
      }),
    )
    expect(await screen.findByText('Listado de inventario')).toBeInTheDocument()
  })

  it('exige al menos una cantidad y avisa del costo 0', async () => {
    pantalla()
    const costo = await screen.findByLabelText('Costo unitario de LIV001')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar entrada' }))
    expect(await screen.findByText('Escribe al menos una cantidad mayor que 0.')).toBeInTheDocument()
    expect(api.registrarEntrada).not.toHaveBeenCalled()
    await userEvent.clear(costo)
    await userEvent.type(costo, '0')
    expect(screen.getByText(/Costo 0:/)).toBeInTheDocument()
  })

  it('valida el costo en el cliente', async () => {
    pantalla()
    const costo = await screen.findByLabelText('Costo unitario de LIV001')
    await userEvent.clear(costo)
    await userEvent.type(costo, 'abc')
    await userEvent.type(await screen.findByLabelText('Cantidad LIV001-S-NEG'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar entrada' }))
    expect(await screen.findByText(/Costo inválido/)).toBeInTheDocument()
    expect(api.registrarEntrada).not.toHaveBeenCalled()
  })

  it('muestra el 422 del backend y conserva el formulario', async () => {
    vi.mocked(api.registrarEntrada).mockRejectedValue(error(422, 'La variante LIV001-S-NEG está inactiva.'))
    pantalla()
    await userEvent.type(await screen.findByLabelText('Cantidad LIV001-S-NEG'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar entrada' }))
    expect(await screen.findByText('La variante LIV001-S-NEG está inactiva.')).toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad LIV001-S-NEG')).toHaveValue(5)
    expect(screen.queryByRole('button', { name: 'Volver a confirmar' })).not.toBeInTheDocument()
  })

  it('ante 409 no limpia el formulario y permite volver a confirmar', async () => {
    vi.mocked(api.registrarEntrada).mockRejectedValueOnce(error(409, 'El stock cambió mientras registrabas la operación. Revisa y vuelve a confirmar.'))
    pantalla()
    await userEvent.type(await screen.findByLabelText('Cantidad LIV001-S-NEG'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar entrada' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Volver a confirmar' }))
    await waitFor(() => expect(api.registrarEntrada).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Listado de inventario')).toBeInTheDocument()
  })

  it('sin producto inicial agrega uno desde el buscador y puede quitarlo', async () => {
    pantalla('/inventario/entradas/nueva')
    expect(screen.getByText(/Busca una variante/)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/Agregar producto/), 'liv')
    await userEvent.click(await screen.findByRole('button', { name: /LIV001-S-NEG/ }))
    expect(await screen.findByLabelText('Cantidad LIV001-M-NEG')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Quitar LIV001' }))
    expect(screen.queryByLabelText('Cantidad LIV001-M-NEG')).not.toBeInTheDocument()
  })
})
