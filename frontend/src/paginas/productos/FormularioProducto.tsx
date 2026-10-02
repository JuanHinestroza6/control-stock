import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { Selector } from '../../componentes/ui/Selector'
import { erroresPorCampo, mensajeError } from '../../lib/errores'
import type {
  ActualizarProductoRequest,
  CategoriaResponse,
  CrearProductoRequest,
  ProductoDetalleResponse,
} from '../../tipos/catalogo'
import {
  aActualizarRequest,
  aCrearRequest,
  crearEsquemaProducto,
  ventaMenorQueCosto,
  type DatosFormularioProducto,
} from './esquemaProducto'

interface FormularioProductoBase {
  categorias: CategoriaResponse[]
  guardando: boolean
  /** Error de la última petición (ProblemDetail del backend). */
  error: unknown
  cancelarA: string
}

type FormularioProductoProps = FormularioProductoBase &
  (
    | { producto?: undefined; onGuardar: (datos: CrearProductoRequest) => void }
    | { producto: ProductoDetalleResponse; onGuardar: (datos: ActualizarProductoRequest) => void }
  )

const CAMPOS: (keyof DatosFormularioProducto)[] = ['codigo', 'nombre', 'categoriaId', 'precioCompra', 'precioVenta', 'leadTimeDias', 'loteMinimo']
// El backend nombra el campo de categoría `categoriaId`, igual que el formulario.

export function FormularioProducto(props: FormularioProductoProps) {
  const { categorias, guardando, error, cancelarA, producto } = props
  const edicion = producto !== undefined

  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<DatosFormularioProducto>({
    resolver: zodResolver(crearEsquemaProducto(edicion)),
    defaultValues: {
      codigo: producto?.codigo ?? '',
      nombre: producto?.nombre ?? '',
      categoriaId: producto ? String(producto.categoria.id) : '',
      // precioCompra llega null para VENDEDOR; este formulario solo lo usan roles con costo.
      precioCompra: producto?.precioCompra != null ? String(producto.precioCompra) : '',
      precioVenta: producto ? String(producto.precioVenta) : '',
      leadTimeDias: producto ? String(producto.leadTimeDias) : '',
      loteMinimo: producto ? String(producto.loteMinimo) : '',
    },
  })

  const camposBackend = error ? erroresPorCampo(error) : {}
  useEffect(() => {
    if (!error) return
    const deCampo = erroresPorCampo(error)
    for (const campo of CAMPOS) {
      const mensaje = deCampo[campo]
      if (mensaje) setError(campo, { type: 'server', message: mensaje })
    }
  }, [error, setError])

  const hayErrorDeCampo = CAMPOS.some((c) => camposBackend[c])
  const banner = error && !hayErrorDeCampo ? mensajeError(error) : undefined

  const [compra, venta] = useWatch({ control, name: ['precioCompra', 'precioVenta'] })
  const advertirPrecio = ventaMenorQueCosto(compra, venta)

  const alEnviar = (datos: DatosFormularioProducto) => {
    if (props.producto) props.onGuardar(aActualizarRequest(datos))
    else props.onGuardar(aCrearRequest(datos))
  }

  return (
    <form noValidate onSubmit={handleSubmit(alEnviar)} aria-label="Formulario de producto" className="flex flex-col gap-4">
      {banner && (
        <p role="alert" className="rounded-lg bg-critico/10 px-3 py-2 text-sm font-medium text-critico">
          {banner}
        </p>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Campo
          etiqueta="Código"
          autoComplete="off"
          maxLength={20}
          readOnly={edicion}
          error={errors.codigo?.message}
          {...register('codigo')}
        />
        <Campo etiqueta="Nombre" autoComplete="off" maxLength={120} error={errors.nombre?.message} {...register('nombre')} />
        <Selector
          etiqueta="Categoría"
          placeholder="Selecciona…"
          opciones={categorias.map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
          error={errors.categoriaId?.message}
          {...register('categoriaId')}
        />
        <Campo
          etiqueta="Precio de compra (COP)"
          inputMode="decimal"
          error={errors.precioCompra?.message}
          {...register('precioCompra')}
        />
        <Campo
          etiqueta="Precio de venta (COP)"
          inputMode="decimal"
          error={errors.precioVenta?.message}
          {...register('precioVenta')}
        />
        <Campo
          etiqueta={edicion ? 'Tiempo de reposición (días)' : 'Tiempo de reposición (días, por defecto 15)'}
          inputMode="numeric"
          error={errors.leadTimeDias?.message}
          {...register('leadTimeDias')}
        />
        <Campo
          etiqueta={edicion ? 'Lote mínimo (unidades)' : 'Lote mínimo (unidades, por defecto 1)'}
          inputMode="numeric"
          error={errors.loteMinimo?.message}
          {...register('loteMinimo')}
        />
      </div>
      {advertirPrecio && (
        <p role="status" className="rounded-lg bg-alerta/15 px-3 py-2 text-sm font-medium text-oxford">
          Advertencia: el precio de venta es menor que el precio de compra; cada venta tendrá pérdida.
        </p>
      )}
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Link
          to={cancelarA}
          className="inline-flex min-h-10 items-center justify-center rounded-lg px-4 py-2 text-sm font-medium text-acento hover:underline"
        >
          Cancelar
        </Link>
        <Boton type="submit" cargando={guardando}>
          Guardar
        </Boton>
      </div>
    </form>
  )
}
