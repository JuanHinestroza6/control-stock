import type { CeldaMatriz, MatrizResponse } from '../../tipos/catalogo'
import { Badge } from '../ui/Badge'
import { BadgeStock } from './BadgeStock'

/** Índice talla|color -> celda; las combinaciones ausentes no se crearon. */
function indexarCeldas(celdas: CeldaMatriz[]): Map<string, CeldaMatriz> {
  return new Map(celdas.map((c) => [`${c.talla}|${c.color}`, c]))
}

function Celda({ celda }: { celda: CeldaMatriz | undefined }) {
  if (!celda) {
    return (
      <span aria-label="Sin variante" className="text-oxford/30">
        —
      </span>
    )
  }
  if (!celda.activo) {
    return (
      <div className="flex flex-col items-center gap-1 opacity-60">
        <Badge>Inactiva</Badge>
        <span className="text-xs text-oxford/70">{celda.sku}</span>
      </div>
    )
  }
  return (
    <div className="flex flex-col items-center gap-1" title={`${celda.sku}: físico ${celda.stockFisico}, reservado ${celda.stockReservado}`}>
      <span className="text-base font-bold text-oxford">{celda.stockDisponible}</span>
      <BadgeStock estado={celda.estadoStock} />
      <span className="text-xs text-oxford/70">{celda.sku}</span>
    </div>
  )
}

/** Matriz talla (columnas) x color (filas) con el stock disponible por variante. */
export function MatrizTallaColor({ matriz }: { matriz: MatrizResponse }) {
  const celdas = indexarCeldas(matriz.celdas)
  return (
    <div className="overflow-x-auto rounded-lg border border-oxford/10 bg-fondo">
      <table className="w-full text-center text-sm">
        <caption className="sr-only">Stock disponible por talla y color</caption>
        <thead className="bg-oxford text-fondo">
          <tr>
            <th scope="col" className="px-4 py-3 text-left text-xs font-medium">
              Color
            </th>
            {matriz.tallas.map((t) => (
              <th key={t} scope="col" className="px-4 py-3 text-xs font-medium">
                {t}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {matriz.colores.map((color) => (
            <tr key={color} className="odd:bg-fondo even:bg-fondo-suave">
              <th scope="row" className="px-4 py-3 text-left text-xs font-medium text-oxford">
                {color}
              </th>
              {matriz.tallas.map((talla) => (
                <td key={talla} className="px-4 py-3 align-middle">
                  <Celda celda={celdas.get(`${talla}|${color}`)} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
