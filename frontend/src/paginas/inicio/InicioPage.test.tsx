import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiAlertas from '../../api/alertas'
import * as apiAnalitica from '../../api/analitica'
import * as apiPanel from '../../api/panel'
import { renderizarApp } from '../../pruebas/render'
import type { Rol } from '../../tipos/auth'
import type { PanelResponse } from '../../tipos/panel'
import { InicioPage } from './InicioPage'

vi.mock('../../api/panel')
vi.mock('../../api/alertas')
vi.mock('../../api/analitica')

const dias = Array.from({ length: 7 }, (_, i) => ({ fecha: `2026-10-${15 + i}`, total: 100_000 * (i + 1), numeroVentas: i + 1 }))

function panel(rol: Rol): PanelResponse {
  const costos = rol === 'ADMIN' || rol === 'CONTABILIDAD'
  return {
    generadoEn: '2026-10-21T15:30:00-05:00',
    ventas:
      rol === 'INVENTARIO'
        ? null
        : {
            ventasHoy: 1_500_000, numeroVentasHoy: 7, ventasMes: 20_000_000, numeroVentasMes: 90,
            margenMes: costos ? 8_000_000 : null, margenMesPorcentaje: costos ? 40 : null, ultimos7Dias: dias,
          },
    inventario: {
      variantesActivas: 299, unidadesFisicas: 5000, unidadesDisponibles: 4800, alertasAgotado: 26, alertasStockBajo: 16,
      alertasSinRotacion: 18, valorCosto: costos ? 90_000_000 : null, valorVenta: costos ? 150_000_000 : null,
    },
    reservas: rol === 'CONTABILIDAD' ? null : { activas: 4, unidadesReservadas: 9, porVencer24h: 2 },
    compras:
      rol === 'VENDEDOR'
        ? null
        : { planesBorrador: 3, planesEnCamino: 5, unidadesEnCamino: 120, planesAtrasados: 1, valorEnCamino: costos ? 7_000_000 : null },
  }
}

function simular(rol: Rol) {
  vi.mocked(apiPanel.obtenerPanel).mockResolvedValue(panel(rol))
  vi.mocked(apiAlertas.obtenerResumenAlertas).mockResolvedValue({ activas: 60, agotado: 26, stockBajo: 16, sinRotacion: 18 })
  vi.mocked(apiAnalitica.obtenerMasVendidos).mockResolvedValue({
    desde: '2026-09-22', hasta: '2026-10-21', criterio: 'UNIDADES', agrupacion: 'PRODUCTO', categoriaId: null,
    totalUnidades: 10, totalIngreso: 500_000,
    items: [{ posicion: 1, productoId: 1, productoCodigo: 'LIV001', productoNombre: 'Camiseta Oversize', categoria: { id: 2, nombre: 'Camisetas' }, unidades: 10, ingreso: 500_000, participacion: 100, activo: true }],
  })
  vi.mocked(apiAnalitica.obtenerUltimaEjecucion).mockResolvedValue({
    id: 1, origen: 'NOCTURNA', usuario: null, iniciadaEn: '2026-10-21T02:00:00-05:00', terminadaEn: null, estado: 'EXITOSA',
    fechaCorte: '2026-10-21', duracionMs: 6000, porcentajeAlertaStock: 14.4,
  } as Awaited<ReturnType<typeof apiAnalitica.obtenerUltimaEjecucion>>)
}

function montar(rol: Rol) {
  simular(rol)
  return renderizarApp(<InicioPage />, { rol })
}

beforeEach(() => vi.resetAllMocks())

describe('InicioPage (panel)', () => {
  it('ADMIN ve todas las secciones, el gráfico y los bloques', async () => {
    montar('ADMIN')
    expect(await screen.findByText('Ventas de hoy')).toBeInTheDocument()
    expect(screen.getByText('Margen del mes')).toBeInTheDocument()
    expect(screen.getByText('Valor a costo')).toBeInTheDocument()
    expect(screen.getByText('Valor a precio de venta')).toBeInTheDocument()
    expect(screen.getByText('Apartados activos')).toBeInTheDocument()
    expect(screen.getByText('Planes en camino')).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: /últimos 7 días/ })).toBeInTheDocument()
    expect(await screen.findByText('Alertas activas')).toBeInTheDocument()
    expect(await screen.findByText(/Camiseta Oversize/)).toBeInTheDocument()
    expect(await screen.findByText('Última ejecución analítica')).toBeInTheDocument()
    expect(screen.getByText(/Generado a las 15:30/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Ventas de hoy/ })).toHaveAttribute('href', '/ventas')
    expect(screen.getByRole('link', { name: /Margen del mes/ })).toHaveAttribute('href', '/reportes')
  })

  it('INVENTARIO no ve ventas, margen ni valores a costo', async () => {
    montar('INVENTARIO')
    expect(await screen.findByText('Variantes activas')).toBeInTheDocument()
    expect(screen.queryByText('Ventas de hoy')).not.toBeInTheDocument()
    expect(screen.queryByText('Margen del mes')).not.toBeInTheDocument()
    expect(screen.queryByText('Valor a costo')).not.toBeInTheDocument()
    expect(screen.queryByText('Valor a precio de venta')).not.toBeInTheDocument()
    expect(screen.getByText('Apartados activos')).toBeInTheDocument()
    expect(screen.getByText('Planes en borrador')).toBeInTheDocument()
    expect(screen.getByText(/120 unidades$/)).toBeInTheDocument()
  })

  it('VENDEDOR ve ventas pero no margen, valores a costo ni compras', async () => {
    montar('VENDEDOR')
    expect(await screen.findByText('Ventas de hoy')).toBeInTheDocument()
    expect(screen.queryByText('Margen del mes')).not.toBeInTheDocument()
    expect(screen.queryByText('Valor a costo')).not.toBeInTheDocument()
    expect(screen.queryByText('Planes en borrador')).not.toBeInTheDocument()
    expect(screen.getByText('Apartados activos')).toBeInTheDocument()
    expect(screen.queryByText('Última ejecución analítica')).not.toBeInTheDocument()
    expect(apiAnalitica.obtenerUltimaEjecucion).not.toHaveBeenCalled()
  })

  it('CONTABILIDAD no ve apartados pero sí margen y valores', async () => {
    montar('CONTABILIDAD')
    expect(await screen.findByText('Margen del mes')).toBeInTheDocument()
    expect(screen.getByText('Valor a costo')).toBeInTheDocument()
    expect(screen.queryByText('Apartados activos')).not.toBeInTheDocument()
    expect(screen.queryByText('Alertas activas')).not.toBeInTheDocument()
    expect(apiAlertas.obtenerResumenAlertas).not.toHaveBeenCalled()
  })

  it('muestra el estado de carga', () => {
    vi.mocked(apiPanel.obtenerPanel).mockReturnValue(new Promise(() => {}))
    vi.mocked(apiAnalitica.obtenerMasVendidos).mockReturnValue(new Promise(() => {}))
    renderizarApp(<InicioPage />, { rol: 'VENDEDOR' })
    expect(screen.getAllByRole('status', { name: 'Cargando' }).length).toBeGreaterThan(0)
  })

  it('muestra el error y permite reintentar', async () => {
    simular('ADMIN')
    vi.mocked(apiPanel.obtenerPanel).mockRejectedValueOnce(new Error('falló'))
    renderizarApp(<InicioPage />, { rol: 'ADMIN' })
    const reintentar = await screen.findByRole('button', { name: 'Reintentar' })
    await userEvent.click(reintentar)
    expect(await screen.findByText('Ventas de hoy')).toBeInTheDocument()
  })
})
