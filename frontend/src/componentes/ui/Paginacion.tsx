import type { MetaPagina } from '../../tipos/paginacion'
import { rangoVisible } from '../../lib/paginacion'
import { Boton } from './Boton'

interface PaginacionProps {
  pagina: MetaPagina
  /** Recibe el número de página destino (desde 0). */
  onCambiar: (pagina: number) => void
}

export function Paginacion({ pagina, onCambiar }: PaginacionProps) {
  const { desde, hasta } = rangoVisible(pagina)
  const totalPaginas = Math.max(pagina.totalPages, 1)
  const hayAnterior = pagina.number > 0
  const haySiguiente = pagina.number + 1 < pagina.totalPages

  return (
    <nav aria-label="Paginación" className="flex flex-col items-center justify-between gap-2 sm:flex-row">
      <p className="text-xs font-medium text-oxford/70" aria-live="polite">
        {desde}–{hasta} de {pagina.totalElements}
      </p>
      <div className="flex items-center gap-2">
        <Boton variante="secundario" disabled={!hayAnterior} onClick={() => onCambiar(pagina.number - 1)}>
          Anterior
        </Boton>
        <span className="text-xs font-medium text-oxford">
          Página {pagina.number + 1} de {totalPaginas}
        </span>
        <Boton variante="secundario" disabled={!haySiguiente} onClick={() => onCambiar(pagina.number + 1)}>
          Siguiente
        </Boton>
      </div>
    </nav>
  )
}
