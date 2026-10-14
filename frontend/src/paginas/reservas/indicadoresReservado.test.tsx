import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiCategorias from '../../api/categorias'
import * as apiInv from '../../api/inventario'
import * as apiVentas from '../../api/ventas'
import { BuscadorVariante } from '../../componentes/dominio/BuscadorVariante'
import { MatrizTallaColor } from '../../componentes/dominio/MatrizTallaColor'
import { renderizarApp } from '../../pruebas/render'
import type { CeldaMatriz } from '../../tipos/catalogo'
import type { InventarioItemResponse } from '../../tipos/inventario'
import type { VentaDetalleResponse } from '../../tipos/ventas'
import { InventarioPage } from '../inventario/InventarioPage'
import { VentaDetallePage } from '../ventas/VentaDetallePage'

vi.mock('../../api/categorias')
vi.mock('../../api/inventario')
vi.mock('../../api/ventas')

const item: InventarioItemResponse = {
  varianteId: 11, sku: 'LIV001-S-NEG', productoId: 1, productoCodigo: 'LIV001', productoNombre: 'Camiseta Oversize',
  categoria: { id: 1, nombre: 'Camisetas' }, talla: 'S', color: 'NEGRO', stockFisico: 10, stockReservado: 3, stockDisponible: 7,
  stockMinimo: 3, puntoReorden: null, claseAbc: null, estadoStock: 'OK', precioVenta: 50000, activo: true,
}
const pagina = { content: [item], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(apiCategorias.listarCategorias).mockResolvedValue([])
  vi.mocked(apiInv.listarInventario).mockResolvedValue(pagina)
})

describe('indicadores de reservado', () => {
  it('inventario enlaza el reservado a /reservas?varianteId= para quien puede reservar', async () => {
    renderizarApp(<InventarioPage />, { rol: 'VENDEDOR' })
    const enlace = await screen.findByRole('link', { name: 'Ver apartados de LIV001-S-NEG' })
    expect(enlace).toHaveAttribute('href', '/reservas?varianteId=11')
  })

  it('CONTABILIDAD ve el reservado sin enlace', async () => {
    renderizarApp(<InventarioPage />, { rol: 'CONTABILIDAD' })
    await screen.findByText('LIV001-S-NEG')
    expect(screen.queryByRole('link', { name: /Ver apartados/ })).not.toBeInTheDocument()
  })

  it('la matriz muestra R n solo con reservado', () => {
    const celda = (talla: CeldaMatriz['talla'], reservado: number): CeldaMatriz => ({
      talla, color: 'NEGRO', varianteId: 1, sku: `SKU-${talla}`, stockFisico: 5, stockReservado: reservado,
      stockDisponible: 5 - reservado, estadoStock: 'OK', activo: true,
    })
    render(<MatrizTallaColor matriz={{ productoId: 1, tallas: ['S', 'M'], colores: ['NEGRO'], celdas: [celda('S', 2), celda('M', 0)] }} />)
    expect(screen.getByText('R 2')).toBeInTheDocument()
    expect(screen.queryByText('R 0')).not.toBeInTheDocument()
  })

  it('el buscador muestra el reservado', async () => {
    renderizarApp(<BuscadorVariante onSeleccionar={vi.fn()} />, { rol: 'VENDEDOR' })
    await userEvent.type(screen.getByRole('searchbox'), 'liv')
    expect(await screen.findByText('Reservado: 3')).toBeInTheDocument()
  })

  it('el detalle de venta muestra las reservas de origen', async () => {
    vi.mocked(apiVentas.obtenerVenta).mockResolvedValue({
      id: 5, numero: 'V-000005', fecha: '2026-10-02T10:30:00', vendedor: 'Ana', estado: 'REGISTRADA', total: 0, descuentoTotal: 0,
      anuladaEn: null, anuladaPor: null, motivoAnulacion: null, lineas: [], reservas: ['R-000001', 'R-000002'],
    } as VentaDetalleResponse)
    renderizarApp(
      <Routes>
        <Route path="/ventas/:id" element={<VentaDetallePage />} />
      </Routes>,
      { rol: 'VENDEDOR', ruta: '/ventas/5' },
    )
    expect(await screen.findByText(/apartados R-000001, R-000002/)).toBeInTheDocument()
  })
})
