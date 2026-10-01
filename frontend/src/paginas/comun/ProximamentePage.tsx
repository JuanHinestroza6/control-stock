import { Tarjeta } from '../../componentes/ui/Tarjeta'

interface ProximamentePageProps {
  titulo: string
  descripcion?: string
}

/** Marcador de posición para módulos aún no construidos. */
export function ProximamentePage({ titulo, descripcion }: ProximamentePageProps) {
  return (
    <section>
      <h1>{titulo}</h1>
      <Tarjeta className="mt-4">
        <h2 className="text-acento">Próximamente</h2>
        <p className="mt-2 text-oxford/70">
          {descripcion ? `${descripcion}. ` : ''}Este módulo estará disponible en una próxima fase.
        </p>
      </Tarjeta>
    </section>
  )
}
