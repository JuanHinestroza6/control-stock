import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/inventario'
import { renderizarApp } from '../../pruebas/render'
import type { InventarioItemResponse } from '../../tipos/inventario'
import { ModalAjuste } from './ModalAjuste'

vi.mock('../../api/inventario')

const variante: InventarioItemResponse = {
  varianteId: 7,
  sku: 'LIV001-M-NEG',
  productoId: 1,
  productoCodigo: 'LIV001',
  productoNombre: 'Camiseta Oversize',
  categoria: { id: 1, nombre: 'Camisetas' },
  talla: 'M',
  color: 'NEGRO',
  stockFisico: 10,
  stockReservado: 2,
  stockDisponible: 8,
  stockMinimo: 3,
  puntoReorden: null,
  estadoStock: 'OK',
  precioVenta: 59900,
  activo: true,
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('ModalAjuste', () => {
  it('exige cantidad y observación de 5 a 255 caracteres', async () => {
    renderizarApp(<ModalAjuste variante={variante} onCerrar={vi.fn()} />, { rol: 'INVENTARIO' })
    await userEvent.click(screen.getByRole('button', { name: 'Registrar ajuste' }))
    expect(screen.getByText(/entero entre 1 y 100000/)).toBeInTheDocument()
    expect(screen.getByText(/observación es obligatoria/)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Cantidad'), '2')
    await userEvent.type(screen.getByLabelText('Observación'), 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar ajuste' }))
    expect(screen.getByText(/observación es obligatoria/)).toBeInTheDocument()
    expect(api.registrarAjuste).not.toHaveBeenCalled()
  })

  it('un ajuste negativo no puede superar el disponible', async () => {
    renderizarApp(<ModalAjuste variante={variante} onCerrar={vi.fn()} />, { rol: 'ADMIN' })
    await userEvent.selectOptions(screen.getByLabelText('Tipo de ajuste'), 'Restar unidades (negativo)')
    await userEvent.type(screen.getByLabelText('Cantidad'), '9')
    await userEvent.type(screen.getByLabelText('Observación'), 'merma en bodega')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar ajuste' }))
    expect(screen.getByText('No puedes restar más que el disponible (8)')).toBeInTheDocument()
    expect(api.registrarAjuste).not.toHaveBeenCalled()
  })

  it('envía el ajuste y cierra', async () => {
    vi.mocked(api.registrarAjuste).mockResolvedValue({} as never)
    const onCerrar = vi.fn()
    renderizarApp(<ModalAjuste variante={variante} onCerrar={onCerrar} />, { rol: 'INVENTARIO' })
    await userEvent.selectOptions(screen.getByLabelText('Tipo de ajuste'), 'Restar unidades (negativo)')
    await userEvent.type(screen.getByLabelText('Cantidad'), '3')
    await userEvent.type(screen.getByLabelText('Observación'), '  merma en bodega ')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar ajuste' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(api.registrarAjuste).toHaveBeenCalledWith({ varianteId: 7, tipo: 'AJUSTE_NEGATIVO', cantidad: 3, observacion: 'merma en bodega' })
  })

  it('muestra el error del backend y conserva lo escrito', async () => {
    vi.mocked(api.registrarAjuste).mockRejectedValue(
      new AxiosError('x', 'E', undefined, undefined, {
        status: 409,
        statusText: '',
        headers: {},
        config: {} as never,
        data: { detail: 'El stock cambió mientras registrabas la operación. Revisa y vuelve a confirmar.' },
      }),
    )
    const onCerrar = vi.fn()
    renderizarApp(<ModalAjuste variante={variante} onCerrar={onCerrar} />, { rol: 'ADMIN' })
    await userEvent.type(screen.getByLabelText('Cantidad'), '3')
    await userEvent.type(screen.getByLabelText('Observación'), 'conteo físico')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar ajuste' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El stock cambió')
    expect(screen.getByLabelText('Cantidad')).toHaveValue('3')
    expect(onCerrar).not.toHaveBeenCalled()
  })
})
