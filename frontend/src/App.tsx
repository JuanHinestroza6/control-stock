import { Route, Routes } from 'react-router'
import { LayoutPrincipal } from './componentes/layout/LayoutPrincipal'
import { MODULOS } from './componentes/layout/modulos'
import { CuentaPage } from './paginas/cuenta/CuentaPage'
import { RutaProtegida } from './componentes/layout/RutaProtegida'
import { LoginPage } from './paginas/auth/LoginPage'
import { NoEncontradaPage } from './paginas/comun/NoEncontradaPage'
import { ProximamentePage } from './paginas/comun/ProximamentePage'
import { InicioPage } from './paginas/inicio/InicioPage'
import { CategoriasPage } from './paginas/productos/CategoriasPage'
import { ProductoDetallePage } from './paginas/productos/ProductoDetallePage'
import { ProductoFormPage } from './paginas/productos/ProductoFormPage'
import { ProductosPage } from './paginas/productos/ProductosPage'

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
          {MODULOS.filter((m) => m.ruta !== '/' && m.clave !== 'productos').map((m) => (
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
