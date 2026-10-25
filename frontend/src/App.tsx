import { Route, Routes } from 'react-router'
import { LayoutPrincipal } from './componentes/layout/LayoutPrincipal'
import { MODULOS } from './componentes/layout/modulos'
import { CuentaPage } from './paginas/cuenta/CuentaPage'
import { RutaProtegida } from './componentes/layout/RutaProtegida'
import { LoginPage } from './paginas/auth/LoginPage'
import { NoEncontradaPage } from './paginas/comun/NoEncontradaPage'
import { ProximamentePage } from './paginas/comun/ProximamentePage'
import { EntradaNuevaPage } from './paginas/inventario/EntradaNuevaPage'
import { InicioPage } from './paginas/inicio/InicioPage'
import { InventarioPage } from './paginas/inventario/InventarioPage'
import { KardexPage } from './paginas/inventario/KardexPage'
import { AlertasPage } from './paginas/alertas/AlertasPage'
import { AnaliticaPage } from './paginas/analitica/AnaliticaPage'
import { ComprasPage } from './paginas/compras/ComprasPage'
import { PlanDetallePage } from './paginas/compras/PlanDetallePage'
import { PlanNuevoPage } from './paginas/compras/PlanNuevoPage'
import { ReportesPage } from './paginas/reportes/ReportesPage'
import { ReservaNuevaPage } from './paginas/reservas/ReservaNuevaPage'
import { ReservasPage } from './paginas/reservas/ReservasPage'
import { CategoriasPage } from './paginas/productos/CategoriasPage'
import { ProductoDetallePage } from './paginas/productos/ProductoDetallePage'
import { ProductoFormPage } from './paginas/productos/ProductoFormPage'
import { ProductosPage } from './paginas/productos/ProductosPage'
import { UsuariosPage } from './paginas/usuarios/UsuariosPage'
import { VentaDetallePage } from './paginas/ventas/VentaDetallePage'
import { VentaNuevaPage } from './paginas/ventas/VentaNuevaPage'
import { VentasPage } from './paginas/ventas/VentasPage'

const CON_PAGINA = ['productos', 'inventario', 'ventas', 'reservas', 'alertas', 'analitica', 'compras', 'reportes', 'usuarios']

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RutaProtegida />}>
        <Route element={<LayoutPrincipal />}>
          <Route index element={<InicioPage />} />
          <Route path="/cuenta" element={<CuentaPage />} />
          <Route path="/productos" element={<ProductosPage />} />
          <Route path="/productos/:id" element={<ProductoDetallePage />} />
          <Route path="/productos/categorias" element={<CategoriasPage />} />
          <Route element={<RutaProtegida roles={['ADMIN', 'INVENTARIO']} />}>
            <Route path="/productos/nuevo" element={<ProductoFormPage />} />
            <Route path="/productos/:id/editar" element={<ProductoFormPage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN', 'INVENTARIO', 'CONTABILIDAD']} />}>
            <Route path="/inventario" element={<InventarioPage />} />
            <Route path="/inventario/kardex" element={<KardexPage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN', 'INVENTARIO']} />}>
            <Route path="/inventario/entradas/nueva" element={<EntradaNuevaPage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN', 'VENDEDOR', 'CONTABILIDAD']} />}>
            <Route path="/ventas" element={<VentasPage />} />
            <Route path="/ventas/:id" element={<VentaDetallePage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN', 'VENDEDOR']} />}>
            <Route path="/ventas/nueva" element={<VentaNuevaPage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN', 'INVENTARIO', 'VENDEDOR']} />}>
            <Route path="/reservas" element={<ReservasPage />} />
            <Route path="/reservas/nueva" element={<ReservaNuevaPage />} />
            <Route path="/alertas" element={<AlertasPage />} />
          </Route>
          <Route path="/analitica" element={<AnaliticaPage />} />
          <Route element={<RutaProtegida roles={['ADMIN', 'INVENTARIO', 'CONTABILIDAD']} />}>
            <Route path="/compras" element={<ComprasPage />} />
            <Route path="/compras/:id" element={<PlanDetallePage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN', 'INVENTARIO']} />}>
            <Route path="/compras/nueva" element={<PlanNuevoPage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN', 'CONTABILIDAD']} />}>
            <Route path="/reportes" element={<ReportesPage />} />
          </Route>
          <Route element={<RutaProtegida roles={['ADMIN']} />}>
            <Route path="/usuarios" element={<UsuariosPage />} />
          </Route>
          {MODULOS.filter((m) => m.ruta !== '/' && !CON_PAGINA.includes(m.clave)).map((m) => (
            <Route key={m.clave} element={<RutaProtegida roles={m.roles} />}>
              <Route path={m.ruta} element={<ProximamentePage titulo={m.etiqueta} descripcion={m.descripcion} />} />
            </Route>
          ))}
          <Route path="*" element={<NoEncontradaPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
