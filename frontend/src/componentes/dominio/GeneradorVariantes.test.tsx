import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/variantes'
import { renderizarApp } from '../../pruebas/render'
import { GeneradorVariantes } from './GeneradorVariantes'

vi.mock('../../api/variantes')

beforeEach(() => {
  vi.resetAllMocks()
})

async function elegir(tallas: string[], colores: string) {
  for (const t of tallas) await userEvent.click(screen.getByRole('checkbox', { name: t }))
  await userEvent.type(screen.getByLabelText(/Colores/), colores)
  await userEvent.click(screen.getByRole('button', { name: 'Agregar color' }))
}

describe('GeneradorVariantes', () => {
  it('sin selección: vista previa vacía y botón deshabilitado', () => {
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo />)
    expect(screen.getByText('Elige al menos una talla y un color.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generar variantes' })).toBeDisabled()
  })

  it('vista previa con el producto talla x color y chips de color quitables', async () => {
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo />)
    await elegir(['S', 'M'], 'negro, blanco')
    expect(screen.getByText('Se intentarán crear 4 variante(s).')).toBeInTheDocument()
    expect(screen.getByText('S · NEGRO')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Quitar BLANCO' }))
    expect(screen.getByText('Se intentarán crear 2 variante(s).')).toBeInTheDocument()
  })

  it('UNICA desmarca las demás tallas', async () => {
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo />)
    await userEvent.click(screen.getByRole('checkbox', { name: 'S' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'UNICA' }))
    expect(screen.getByRole('checkbox', { name: 'S' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'UNICA' })).toBeChecked()
  })

  it('color inválido muestra error y no se agrega', async () => {
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo />)
    await userEvent.type(screen.getByLabelText(/Colores/), 'ro@jo')
    await userEvent.click(screen.getByRole('button', { name: 'Agregar color' }))
    expect(screen.getByText('El color solo admite letras, números y espacios')).toBeInTheDocument()
    expect(screen.queryByLabelText('Colores elegidos')).not.toBeInTheDocument()
  })

  it('envía tallas, colores y stock mínimo y muestra creadas y omitidas', async () => {
    vi.mocked(api.generarVariantes).mockResolvedValue({
      creadas: [{ id: 1, productoId: 5, sku: 'LIV005-S-NEG', talla: 'S', color: 'NEGRO', stockFisico: 0, stockReservado: 0, stockDisponible: 0, stockMinimo: 2, puntoReorden: null, claseAbc: null, estadoStock: 'AGOTADO', activo: true }],
      omitidas: [
        { talla: 'M', color: 'NEGRO', sku: 'LIV005-M-NEG', motivo: 'EXISTE' },
        { talla: 'L', color: 'NEGRO', sku: 'LIV005-L-NEG', motivo: 'EXISTE_INACTIVA' },
      ],
    })
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo />)
    await elegir(['S', 'M', 'L'], 'negro')
    const minimo = screen.getByLabelText('Stock mínimo de las variantes nuevas')
    await userEvent.clear(minimo)
    await userEvent.type(minimo, '2')
    await userEvent.click(screen.getByRole('button', { name: 'Generar variantes' }))
    await waitFor(() => expect(api.generarVariantes).toHaveBeenCalledWith(5, { tallas: ['S', 'M', 'L'], colores: ['NEGRO'], stockMinimo: 2 }))
    expect(await screen.findByText('1 variante(s) creada(s), 2 omitida(s).')).toBeInTheDocument()
    expect(screen.getByText('LIV005-S-NEG')).toBeInTheDocument()
    expect(screen.getByText(/Ya existe$/)).toBeInTheDocument()
    expect(screen.getByText(/Existe inactiva/)).toBeInTheDocument()
    // la selección se limpia tras generar
    expect(screen.getByText('Elige al menos una talla y un color.')).toBeInTheDocument()
  })

  it('stock mínimo inválido no llama al backend', async () => {
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo />)
    await elegir(['S'], 'negro')
    const minimo = screen.getByLabelText('Stock mínimo de las variantes nuevas')
    await userEvent.clear(minimo)
    await userEvent.type(minimo, '-1')
    await userEvent.click(screen.getByRole('button', { name: 'Generar variantes' }))
    expect(await screen.findByText('Ingresa un entero entre 0 y 100000')).toBeInTheDocument()
    expect(api.generarVariantes).not.toHaveBeenCalled()
  })

  it('muestra el 422 del backend (mezcla UNICA o producto inactivo)', async () => {
    vi.mocked(api.generarVariantes).mockRejectedValue(
      new AxiosError('x', 'E', undefined, undefined, {
        status: 422,
        statusText: '',
        headers: {},
        config: {} as never,
        data: { detail: 'UNICA no se puede combinar con otras tallas' },
      }),
    )
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo />)
    await elegir(['S'], 'negro')
    await userEvent.click(screen.getByRole('button', { name: 'Generar variantes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('UNICA no se puede combinar')
  })

  it('con producto inactivo avisa y deshabilita todo', () => {
    renderizarApp(<GeneradorVariantes productoId={5} productoActivo={false} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Activa el producto')
    expect(screen.getByRole('checkbox', { name: 'S' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Generar variantes' })).toBeDisabled()
  })
})
