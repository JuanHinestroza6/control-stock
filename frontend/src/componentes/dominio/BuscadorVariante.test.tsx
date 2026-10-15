import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../api/inventario'
import { renderizarApp } from '../../pruebas/render'
import type { InventarioItemResponse } from '../../tipos/inventario'
import { BuscadorVariante } from './BuscadorVariante'

vi.mock('../../api/inventario')

function item(id: number, extra: Partial<InventarioItemResponse> = {}): InventarioItemResponse {
  return {
    varianteId: id,
    sku: `LIV001-M-NEG${id}`,
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
    puntoReorden: null, claseAbc: null,
    estadoStock: 'OK',
    precioVenta: 59900,
    activo: true,
    ...extra,
  }
}

const pagina = (content: InventarioItemResponse[]) => ({ content, page: { size: 8, number: 0, totalElements: content.length, totalPages: 1 } })

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.listarInventario).mockResolvedValue(pagina([item(1), item(2)]))
})

describe('BuscadorVariante', () => {
  it('no consulta con menos de 2 caracteres', async () => {
    renderizarApp(<BuscadorVariante onSeleccionar={vi.fn()} />, { rol: 'ADMIN' })
    await userEvent.type(screen.getByLabelText('Buscar variante'), 'l')
    await new Promise((r) => setTimeout(r, 400))
    expect(api.listarInventario).not.toHaveBeenCalled()
  })

  it('consulta con debounce una sola vez, muestra SKU, disponible y precio, y notifica la elegida', async () => {
    const onSeleccionar = vi.fn()
    renderizarApp(<BuscadorVariante onSeleccionar={onSeleccionar} />, { rol: 'VENDEDOR' })
    await userEvent.type(screen.getByLabelText('Buscar variante'), 'liv')
    expect(await screen.findByText('LIV001-M-NEG1')).toBeInTheDocument()
    expect(api.listarInventario).toHaveBeenCalledTimes(1)
    expect(vi.mocked(api.listarInventario).mock.calls[0][0]).toMatchObject({ q: 'liv', incluirInactivas: false, size: 8 })
    expect(screen.getAllByText('Disponible: 8')).toHaveLength(2)
    await userEvent.click(screen.getByRole('button', { name: /LIV001-M-NEG2/ }))
    expect(onSeleccionar).toHaveBeenCalledWith(expect.objectContaining({ varianteId: 2 }))
    expect(screen.getByLabelText('Buscar variante')).toHaveValue('')
    await waitFor(() => expect(screen.queryByRole('list')).not.toBeInTheDocument())
  })

  it('oculta las variantes excluidas y avisa si no hay resultados', async () => {
    renderizarApp(<BuscadorVariante onSeleccionar={vi.fn()} excluirIds={[1, 2]} />, { rol: 'ADMIN' })
    await userEvent.type(screen.getByLabelText('Buscar variante'), 'liv')
    expect(await screen.findByText('Sin resultados para la búsqueda.')).toBeInTheDocument()
  })

  it('muestra el error del servidor', async () => {
    vi.mocked(api.listarInventario).mockRejectedValue(new Error('x'))
    renderizarApp(<BuscadorVariante onSeleccionar={vi.fn()} />, { rol: 'ADMIN' })
    await userEvent.type(screen.getByLabelText('Buscar variante'), 'liv')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
