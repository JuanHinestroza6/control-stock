import type { CeldaMatriz, MatrizResponse } from '../../tipos/catalogo'
import { Badge } from '../ui/Badge'

interface MatrizCantidadesProps {
  matriz: MatrizResponse
  /** Cantidad escrita (texto) por varianteId; ausente = vacío. */
  valores: Record<number, string>
  onCambiar: (varianteId: number, valor: string) => void
  /** Mensaje de error por varianteId. */
  errores?: Record<number, string>
}

/** Matriz talla (columnas) x color (filas) con un campo de cantidad por variante activa. */
export function MatrizCantidades({ matriz, valores, onCambiar, errores = {} }: MatrizCantidadesProps) {
  const celdas = new Map<string, CeldaMatriz>(matriz.celdas.map((c) => [`${c.talla}|${c.color}`, c]))
  return (
    <div className="overflow-x-auto rounded-lg border border-oxford/10 bg-fondo">
      <table className="w-full text-center text-sm">
        <caption className="sr-only">Cantidades a ingresar por talla y color</caption>
        <thead className="bg-oxford text-fondo">
          <tr>
            <th scope="col" className="px-3 py-3 text-left text-xs font-medium">
              Color
            </th>
            {matriz.tallas.map((t) => (
              <th key={t} scope="col" className="px-3 py-3 text-xs font-medium">
                {t}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {matriz.colores.map((color) => (
            <tr key={color} className="odd:bg-fondo even:bg-fondo-suave">
              <th scope="row" className="px-3 py-2 text-left text-xs font-medium text-oxford">
                {color}
              </th>
              {matriz.tallas.map((talla) => {
                const celda = celdas.get(`${talla}|${color}`)
                return (
                  <td key={talla} className="px-2 py-2 align-middle">
                    {!celda ? (
                      <span aria-label="Sin variante" className="text-oxford/30">
                        —
                      </span>
                    ) : !celda.activo ? (
                      <Badge>Inactiva</Badge>
                    ) : (
                      <div className="flex flex-col items-center gap-1">
                        <input
                          type="number"
                          inputMode="numeric"
                          min={0}
                          max={100000}
                          step={1}
                          aria-label={`Cantidad ${celda.sku}`}
                          aria-invalid={errores[celda.varianteId] ? true : undefined}
                          value={valores[celda.varianteId] ?? ''}
                          onChange={(e) => onCambiar(celda.varianteId, e.target.value)}
                          className={`min-h-10 w-20 rounded-lg border bg-fondo px-2 py-1 text-center text-sm outline-none focus:border-acento focus:ring-1 focus:ring-acento ${errores[celda.varianteId] ? 'border-critico' : 'border-oxford/20'}`}
                        />
                        <span className="text-xs text-oxford/70">Disp. {celda.stockDisponible}</span>
                        {errores[celda.varianteId] && (
                          <span role="alert" className="text-xs font-medium text-critico">
                            {errores[celda.varianteId]}
                          </span>
                        )}
                      </div>
                    )}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
