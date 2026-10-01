import { Link } from 'react-router'
import { modulosPermitidos } from '../../componentes/layout/modulos'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useSesion } from '../../hooks/useSesion'
import { etiquetaRol } from '../../lib/roles'

export function InicioPage() {
  const { usuario } = useSesion()
  if (!usuario) return null
  const accesos = modulosPermitidos(usuario.rol).filter((m) => m.ruta !== '/')

  return (
    <section>
      <h1>Hola, {usuario.nombre}</h1>
      <p className="mt-1 text-oxford/70">
        Bienvenido a Control Stock. Tu rol es <strong>{etiquetaRol(usuario.rol)}</strong>.
      </p>
      <ul className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {accesos.map((m) => (
          <li key={m.clave}>
            <Link to={m.ruta} className="block rounded-lg focus-visible:outline-2 focus-visible:outline-acento">
              <Tarjeta className="h-full transition-shadow hover:shadow-md">
                <h2 className="text-acento">{m.etiqueta}</h2>
                <p className="mt-1 text-oxford/70">{m.descripcion}</p>
              </Tarjeta>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
