import { useState, type ReactNode } from 'react'
import { Campo } from '../../componentes/ui/Campo'
import { Selector } from '../../componentes/ui/Selector'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useCategorias } from '../../hooks/useCategorias'
import { hoyIso, validarRango } from '../../lib/rangoFechas'

interface Props {
  /** Rango efectivo (el que devolvió el servidor o el elegido). Omitir en reportes sin fechas. */
  rango?: { desde: string; hasta: string; onCambio: (desde: string, hasta: string) => void }
  /** Id de categoría elegido ('' = todas). Omitir si el reporte no filtra por categoría. */
  categoria?: { valor: string; onCambio: (valor: string) => void }
  /** Selectores propios del reporte. */
  children?: ReactNode
}

/**
 * Filtros comunes de reportes. Las fechas se validan en cliente (desde ≤ hasta, máx. 731 días) y solo se aplican
 * cuando son válidas; mientras el usuario no las toque se muestran las que eligió el servidor.
 */
export function FiltrosReporte({ rango, categoria, children }: Props) {
  const categorias = useCategorias()
  const [borrador, setBorrador] = useState<{ desde?: string; hasta?: string }>({})
  const desde = borrador.desde ?? rango?.desde ?? ''
  const hasta = borrador.hasta ?? rango?.hasta ?? ''
  const editado = borrador.desde !== undefined || borrador.hasta !== undefined
  const error = rango && editado ? validarRango(desde, hasta) : null

  const cambiar = (nuevo: { desde?: string; hasta?: string }) => {
    if (!rango) return
    const d = nuevo.desde ?? desde
    const h = nuevo.hasta ?? hasta
    setBorrador({ desde: d, hasta: h })
    if (validarRango(d, h) === null) rango.onCambio(d, h)
  }

  return (
    <Tarjeta className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-4">
      {rango && (
        <>
          <Campo
            etiqueta="Desde"
            type="date"
            value={desde}
            max={hoyIso()}
            onChange={(e) => cambiar({ desde: e.target.value })}
            error={error ?? undefined}
          />
          <Campo etiqueta="Hasta" type="date" value={hasta} max={hoyIso()} onChange={(e) => cambiar({ hasta: e.target.value })} />
        </>
      )}
      {categoria && (
        <Selector
          etiqueta="Categoría"
          placeholder="Todas"
          opciones={(Array.isArray(categorias.data) ? categorias.data : []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
          value={categoria.valor}
          onChange={(e) => categoria.onCambio(e.target.value)}
        />
      )}
      {children}
    </Tarjeta>
  )
}
