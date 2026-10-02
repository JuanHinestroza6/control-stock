import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { renderizarApp } from './pruebas/render'
import type { Rol } from './tipos/auth'

vi.mock('./paginas/inventario/InventarioPage', () => ({ InventarioPage: () => <p>pagina inventario</p> }))
vi.mock('./paginas/inventario/KardexPage', () => ({ KardexPage: () => <p>pagina kardex</p> }))
vi.mock('./paginas/inventario/EntradaNuevaPage', () => ({ EntradaNuevaPage: () => <p>pagina entrada</p> }))
vi.mock('./paginas/ventas/VentasPage', () => ({ VentasPage: () => <p>pagina ventas</p> }))
vi.mock('./paginas/ventas/VentaNuevaPage', () => ({ VentaNuevaPage: () => <p>pagina venta nueva</p> }))
vi.mock('./paginas/ventas/VentaDetallePage', () => ({ VentaDetallePage: () => <p>pagina venta detalle</p> }))

const ROLES: Rol[] = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD']

/** Rutas de Fase 2 y roles con acceso (docs/dominio.md y plan de Fase 2). */
const RUTAS: [string, string, Rol[]][] = [
  ['/inventario', 'pagina inventario', ['ADMIN', 'INVENTARIO', 'CONTABILIDAD']],
  ['/inventario/kardex', 'pagina kardex', ['ADMIN', 'INVENTARIO', 'CONTABILIDAD']],
  ['/inventario/entradas/nueva', 'pagina entrada', ['ADMIN', 'INVENTARIO']],
  ['/ventas', 'pagina ventas', ['ADMIN', 'VENDEDOR', 'CONTABILIDAD']],
  ['/ventas/7', 'pagina venta detalle', ['ADMIN', 'VENDEDOR', 'CONTABILIDAD']],
  ['/ventas/nueva', 'pagina venta nueva', ['ADMIN', 'VENDEDOR']],
]

describe('rutas de inventario y ventas por rol', () => {
  for (const [ruta, texto, permitidos] of RUTAS) {
    for (const rol of ROLES) {
      const permitido = permitidos.includes(rol)
      it(`${ruta} ${permitido ? 'se muestra' : 'niega el acceso'} a ${rol}`, () => {
        renderizarApp(<App />, { ruta, rol })
        if (permitido) {
          expect(screen.getByText(texto)).toBeInTheDocument()
        } else {
          expect(screen.getByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
          expect(screen.queryByText(texto)).not.toBeInTheDocument()
        }
      })
    }
  }
})
