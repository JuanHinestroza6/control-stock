import type { Key, ReactNode } from 'react'

export interface Columna<T> {
  clave: string
  encabezado: string
  celda: (fila: T) => ReactNode
  /** Clases extra para th y td (p. ej. alineación o ancho). */
  className?: string
  /** En móvil oculta la columna (se muestra desde `md`). */
  ocultarEnMovil?: boolean
}

interface TablaProps<T> {
  columnas: Columna<T>[]
  filas: T[]
  claveFila: (fila: T) => Key
  /** Texto accesible para lectores de pantalla. */
  descripcion: string
}

/** Tabla zebra con encabezado oxford y desplazamiento horizontal en pantallas estrechas. */
export function Tabla<T>({ columnas, filas, claveFila, descripcion }: TablaProps<T>) {
  const ocultar = (c: Columna<T>) => (c.ocultarEnMovil ? 'hidden md:table-cell' : '')
  return (
    <div className="overflow-x-auto rounded-lg border border-oxford/10 bg-fondo">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">{descripcion}</caption>
        <thead className="bg-oxford text-fondo">
          <tr>
            {columnas.map((c) => (
              <th key={c.clave} scope="col" className={`px-4 py-3 text-xs font-medium ${ocultar(c)} ${c.className ?? ''}`}>
                {c.encabezado}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((fila) => (
            <tr key={claveFila(fila)} className="odd:bg-fondo even:bg-fondo-suave">
              {columnas.map((c) => (
                <td key={c.clave} className={`px-4 py-3 align-middle ${ocultar(c)} ${c.className ?? ''}`}>
                  {c.celda(fila)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
