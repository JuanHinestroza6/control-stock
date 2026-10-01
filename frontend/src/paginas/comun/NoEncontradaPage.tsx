import { Link } from 'react-router'
import { Tarjeta } from '../../componentes/ui/Tarjeta'

export function NoEncontradaPage() {
  return (
    <Tarjeta className="mx-auto max-w-md text-center">
      <p className="font-titulo text-5xl font-bold text-acento">404</p>
      <h1 className="mt-2">Página no encontrada</h1>
      <p className="mt-2 text-oxford/70">La dirección que buscas no existe.</p>
      <Link to="/" className="mt-4 inline-block font-medium text-acento hover:underline">
        Ir al inicio
      </Link>
    </Tarjeta>
  )
}
