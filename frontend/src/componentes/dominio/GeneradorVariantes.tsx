import { useId, useState, type KeyboardEvent } from 'react'
import { useGenerarVariantes } from '../../hooks/useVariantes'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import { agregarColores, alternarTalla, combinaciones, MAX_COLORES, TALLAS } from '../../lib/variantes'
import type { GenerarVariantesResponse, Talla } from '../../tipos/catalogo'
import { Boton } from '../ui/Boton'
import { Campo } from '../ui/Campo'
import { Tarjeta } from '../ui/Tarjeta'
import { Badge } from '../ui/Badge'

const MOTIVO: Record<'EXISTE' | 'EXISTE_INACTIVA', string> = {
  EXISTE: 'Ya existe',
  EXISTE_INACTIVA: 'Existe inactiva: actívala desde la tabla de variantes',
}

interface GeneradorVariantesProps {
  productoId: number
  /** Un producto inactivo no admite variantes nuevas (422 en el backend). */
  productoActivo: boolean
}

function Resultado({ resultado }: { resultado: GenerarVariantesResponse }) {
  return (
    <div role="status" className="flex flex-col gap-3 rounded-lg bg-fondo-suave p-4">
      <p className="text-sm font-medium text-oxford">
        {resultado.creadas.length} variante(s) creada(s), {resultado.omitidas.length} omitida(s).
      </p>
      {resultado.creadas.length > 0 && (
        <div>
          <h3 className="text-xs font-medium text-oxford/70">Creadas</h3>
          <ul className="mt-1 flex flex-wrap gap-2">
            {resultado.creadas.map((v) => (
              <li key={v.id}>
                <Badge tono="ok">{v.sku}</Badge>
              </li>
            ))}
          </ul>
        </div>
      )}
      {resultado.omitidas.length > 0 && (
        <div>
          <h3 className="text-xs font-medium text-oxford/70">Omitidas</h3>
          <ul className="mt-1 flex flex-col gap-1 text-sm text-oxford">
            {resultado.omitidas.map((o) => (
              <li key={o.sku}>
                <span className="font-medium">{o.sku}</span> ({o.talla} / {o.color}): {MOTIVO[o.motivo]}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

export function GeneradorVariantes({ productoId, productoActivo }: GeneradorVariantesProps) {
  const idTallas = useId()
  const generar = useGenerarVariantes(productoId)
  const [tallas, setTallas] = useState<Talla[]>([])
  const [colores, setColores] = useState<string[]>([])
  const [entrada, setEntrada] = useState('')
  const [errorEntrada, setErrorEntrada] = useState<string>()
  const [stockMinimo, setStockMinimo] = useState('0')
  const [errorStock, setErrorStock] = useState<string>()
  const [resultado, setResultado] = useState<GenerarVariantesResponse | null>(null)

  const vista = combinaciones(tallas, colores)
  const campos = generar.error ? erroresPorCampo(generar.error) : {}
  const hayErrorCampo = Object.keys(campos).length > 0

  const agregar = () => {
    const { colores: nuevos, error } = agregarColores(colores, entrada)
    setColores(nuevos)
    setErrorEntrada(error)
    if (!error) setEntrada('')
  }

  const alTeclear = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      agregar()
    }
  }

  const enviar = () => {
    const minimo = stockMinimo.trim()
    if (!/^\d{1,6}$/.test(minimo) || Number(minimo) > 100000) {
      setErrorStock('Ingresa un entero entre 0 y 100000')
      return
    }
    setErrorStock(undefined)
    generar.mutate(
      { tallas, colores, stockMinimo: Number(minimo) },
      {
        onSuccess: (r) => {
          setResultado(r)
          setTallas([])
          setColores([])
        },
      },
    )
  }

  return (
    <Tarjeta className="flex flex-col gap-4">
      <h2>Generar variantes</h2>
      {!productoActivo && (
        <p role="alert" className="rounded-lg bg-alerta/15 px-3 py-2 text-sm font-medium text-oxford">
          Activa el producto para poder generar variantes.
        </p>
      )}
      {generar.isError && !hayErrorCampo && (
        <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
          {mensajeError(generar.error)}
        </p>
      )}
      {hayErrorCampo && (
        <ul role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
          {Object.entries(campos).map(([campo, mensaje]) => (
            <li key={campo}>{mensaje}</li>
          ))}
        </ul>
      )}

      <fieldset disabled={!productoActivo} className="flex flex-col gap-4">
        <div>
          <legend id={idTallas} className="text-xs font-medium text-oxford">
            Tallas (UNICA no se combina con otras)
          </legend>
          <div role="group" aria-labelledby={idTallas} className="mt-2 flex flex-wrap gap-3">
            {TALLAS.map((t) => (
              <label key={t} className="flex min-h-10 items-center gap-2 text-sm text-oxford">
                <input type="checkbox" checked={tallas.includes(t)} onChange={() => setTallas(alternarTalla(tallas, t))} />
                {t}
              </label>
            ))}
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <Campo
                etiqueta="Colores (separados por coma o con Enter)"
                value={entrada}
                onChange={(e) => setEntrada(e.target.value)}
                onKeyDown={alTeclear}
                error={errorEntrada}
                maxLength={200}
              />
            </div>
            <Boton variante="secundario" onClick={agregar}>
              Agregar color
            </Boton>
          </div>
          {colores.length > 0 && (
            <ul aria-label="Colores elegidos" className="flex flex-wrap gap-2">
              {colores.map((c) => (
                <li key={c}>
                  <Badge tono="acento">
                    {c}
                    <button
                      type="button"
                      aria-label={`Quitar ${c}`}
                      className="ml-1 rounded-full px-1 hover:bg-acento/20"
                      onClick={() => setColores(colores.filter((x) => x !== c))}
                    >
                      ×
                    </button>
                  </Badge>
                </li>
              ))}
            </ul>
          )}
          <p className="text-xs text-oxford/70">
            {colores.length} de {MAX_COLORES} colores
          </p>
        </div>

        <div className="max-w-xs">
          <Campo
            etiqueta="Stock mínimo de las variantes nuevas"
            inputMode="numeric"
            value={stockMinimo}
            onChange={(e) => setStockMinimo(e.target.value)}
            error={errorStock ?? campos.stockMinimo}
          />
        </div>

        <div aria-live="polite">
          <h3 className="text-xs font-medium text-oxford/70">Vista previa</h3>
          {vista.length === 0 ? (
            <p className="mt-1 text-sm text-oxford/70">Elige al menos una talla y un color.</p>
          ) : (
            <>
              <p className="mt-1 text-sm font-medium text-oxford">Se intentarán crear {vista.length} variante(s).</p>
              <ul aria-label="Combinaciones" className="mt-2 flex max-h-40 flex-wrap gap-2 overflow-y-auto">
                {vista.map((c) => (
                  <li key={`${c.talla}|${c.color}`}>
                    <Badge>
                      {c.talla} · {c.color}
                    </Badge>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>

        <div>
          <Boton cargando={generar.isPending} disabled={vista.length === 0} onClick={enviar}>
            Generar variantes
          </Boton>
        </div>
      </fieldset>

      {resultado && <Resultado resultado={resultado} />}
    </Tarjeta>
  )
}
