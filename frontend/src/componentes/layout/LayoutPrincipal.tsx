import { useCallback, useState } from 'react'
import { Outlet } from 'react-router'
import { useSesion } from '../../hooks/useSesion'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

const ID_MENU = 'menu-principal'

export function LayoutPrincipal() {
  const { usuario } = useSesion()
  const [menuAbierto, setMenuAbierto] = useState(false)
  const cerrarMenu = useCallback(() => setMenuAbierto(false), [])

  if (!usuario) return null

  return (
    <div className="flex h-screen overflow-hidden bg-fondo-suave">
      <Sidebar rol={usuario.rol} abierto={menuAbierto} onCerrar={cerrarMenu} id={ID_MENU} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onAbrirMenu={() => setMenuAbierto(true)} menuAbierto={menuAbierto} idMenu={ID_MENU} />
        <main className="flex-1 overflow-y-auto p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
