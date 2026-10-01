import { Link } from 'react-router'
import { Tarjeta } from '../../componentes/ui/Tarjeta'

export function AccesoDenegado() {
  return (
    <Tarjeta role="alert" className="mx-auto max-w-lg text-center">
      <h1>Acceso denegado</h1>
      <p className="mt-2 text-oxford/70">Tu rol no tiene permiso para ver esta sección.</p>
      <Link to="/" className="mt-4 inline-block font-medium text-acento hover:underline">
        Volver al inicio
      </Link>
    </Tarjeta>
  )
}
