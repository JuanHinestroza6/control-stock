import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { BadgeStock } from '../../componentes/dominio/BadgeStock'
import { GeneradorVariantes } from '../../componentes/dominio/GeneradorVariantes'
import { MatrizTallaColor } from '../../componentes/dominio/MatrizTallaColor'
import { Badge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { DialogoConfirmacion } from '../../componentes/ui/DialogoConfirmacion'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useProducto } from '../../hooks/useProductos'
import { useSesion } from '../../hooks/useSesion'
import {
  useActualizarVariante,
  useCambiarEstadoProducto,
  useCambiarEstadoVariante,
  useMatriz,
  useVariantes,
} from '../../hooks/useVariantes'
import { mensajeError } from '../../lib/errores'
import { formatearFecha, formatearMoneda } from '../../lib/formato'
import { puedeEditarCatalogo } from '../../lib/permisos'
import type { ProductoDetalleResponse, VarianteResponse } from '../../tipos/catalogo'
import { ModalStockMinimo } from './ModalStockMinimo'
import { textoEstadoProducto, textoEstadoVariante } from './textosEstado'

type Confirmacion = { tipo: 'producto'; activar: boolean } | { tipo: 'variante'; variante: VarianteResponse; activar: boolean }

function Dato({ etiqueta, valor }: { etiqueta: string; valor: string | number }) {
  return (
    <div>
      <dt className="text-xs font-medium text-oxford/70">{etiqueta}</dt>
      <dd className="text-sm font-medium text-oxford">{valor}</dd>
    </div>
  )
}

function Detalle({ producto }: { producto: ProductoDetalleResponse }) {
  const { usuario } = useSesion()
  const puedeEditar = puedeEditarCatalogo(usuario?.rol)
  const [incluirInactivas, setIncluirInactivas] = useState(false)
  const variantes = useVariantes(producto.id, incluirInactivas)
  const matriz = useMatriz(producto.id, incluirInactivas)
  const estadoProducto = useCambiarEstadoProducto(producto.id)
  const estadoVariante = useCambiarEstadoVariante(producto.id)
  const actualizar = useActualizarVariante(producto.id)
  const [confirmacion, setConfirmacion] = useState<Confirmacion | null>(null)
  const [editando, setEditando] = useState<VarianteResponse | null>(null)

  const mutacionConfirmacion = confirmacion?.tipo === 'variante' ? estadoVariante : estadoProducto

  const cerrarConfirmacion = () => {
    setConfirmacion(null)
    estadoProducto.reset()
    estadoVariante.reset()
  }

  const confirmar = () => {
    if (!confirmacion) return
    if (confirmacion.tipo === 'producto') {
      estadoProducto.mutate(confirmacion.activar, { onSuccess: cerrarConfirmacion })
    } else {
      estadoVariante.mutate({ id: confirmacion.variante.id, activo: confirmacion.activar }, { onSuccess: cerrarConfirmacion })
    }
  }

  const texto = confirmacion
    ? confirmacion.tipo === 'producto'
      ? textoEstadoProducto(producto, confirmacion.activar)
      : textoEstadoVariante(confirmacion.variante, confirmacion.activar)
    : null

  const columnas: Columna<VarianteResponse>[] = [
    { clave: 'sku', encabezado: 'SKU', celda: (v) => <span className="font-medium">{v.sku}</span> },
    { clave: 'talla', encabezado: 'Talla', celda: (v) => v.talla },
    { clave: 'color', encabezado: 'Color', celda: (v) => v.color, ocultarEnMovil: true },
    { clave: 'fisico', encabezado: 'Físico', celda: (v) => v.stockFisico, className: 'text-right', ocultarEnMovil: true },
    { clave: 'reservado', encabezado: 'Reservado', celda: (v) => v.stockReservado, className: 'text-right', ocultarEnMovil: true },
    { clave: 'disponible', encabezado: 'Disponible', celda: (v) => v.stockDisponible, className: 'text-right' },
    { clave: 'minimo', encabezado: 'Mínimo', celda: (v) => v.stockMinimo, className: 'text-right', ocultarEnMovil: true },
    {
      clave: 'estado',
      encabezado: 'Estado',
      celda: (v) => (v.activo ? <BadgeStock estado={v.estadoStock} /> : <Badge>Inactiva</Badge>),
    },
  ]
  if (puedeEditar) {
    columnas.push({
      clave: 'acciones',
      encabezado: 'Acciones',
      className: 'text-right',
      celda: (v) => (
        <div className="flex justify-end gap-2">
          <Boton variante="secundario" aria-label={`Editar stock mínimo de ${v.sku}`} onClick={() => setEditando(v)}>
            Mínimo
          </Boton>
          <Boton
            variante={v.activo ? 'peligro' : 'primario'}
            aria-label={`${v.activo ? 'Desactivar' : 'Activar'} ${v.sku}`}
            disabled={!v.activo && !producto.activo}
            title={!v.activo && !producto.activo ? `Activa primero el producto ${producto.codigo}` : undefined}
            onClick={() => setConfirmacion({ tipo: 'variante', variante: v, activar: !v.activo })}
          >
            {v.activo ? 'Desactivar' : 'Activar'}
          </Boton>
        </div>
      ),
    })
  }

  let tablaVariantes
  if (variantes.isPending) {
    tablaVariantes = <EstadoCarga filas={3} />
  } else if (variantes.isError) {
    tablaVariantes = <EstadoError mensaje={mensajeError(variantes.error)} onReintentar={() => void variantes.refetch()} />
  } else if (variantes.data.length === 0) {
    tablaVariantes = <EstadoVacio mensaje="Este producto aún no tiene variantes." />
  } else {
    tablaVariantes = <Tabla columnas={columnas} filas={variantes.data} claveFila={(v) => v.id} descripcion="Variantes del producto" />
  }

  let contenidoMatriz
  if (matriz.isPending) {
    contenidoMatriz = <EstadoCarga filas={3} />
  } else if (matriz.isError) {
    contenidoMatriz = <EstadoError mensaje={mensajeError(matriz.error)} onReintentar={() => void matriz.refetch()} />
  } else if (matriz.data.celdas.length === 0) {
    contenidoMatriz = <EstadoVacio mensaje="Aún no hay variantes para mostrar la matriz." />
  } else {
    contenidoMatriz = <MatrizTallaColor matriz={matriz.data} />
  }

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <Link to="/productos" className="text-sm font-medium text-acento hover:underline">
            Volver a productos
          </Link>
          <h1 className="mt-1">{producto.nombre}</h1>
          <p className="mt-1 flex items-center gap-2 text-sm text-oxford/70">
            {producto.codigo}
            <Badge tono={producto.activo ? 'ok' : 'neutro'}>{producto.activo ? 'Activo' : 'Inactivo'}</Badge>
          </p>
        </div>
        {puedeEditar && (
          <div className="flex flex-wrap gap-2">
            <Link
              to={`/productos/${producto.id}/editar`}
              className="inline-flex min-h-10 items-center rounded-lg bg-acento px-4 py-2 text-sm font-medium text-fondo hover:bg-acento/90"
            >
              Editar
            </Link>
            <Boton
              variante={producto.activo ? 'peligro' : 'primario'}
              onClick={() => setConfirmacion({ tipo: 'producto', activar: !producto.activo })}
            >
              {producto.activo ? 'Desactivar producto' : 'Activar producto'}
            </Boton>
          </div>
        )}
      </div>

      <Tarjeta>
        <h2 className="mb-3">Datos del producto</h2>
        <dl className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <Dato etiqueta="Categoría" valor={producto.categoria.nombre} />
          {producto.precioCompra !== null && <Dato etiqueta="Precio de compra" valor={formatearMoneda(producto.precioCompra)} />}
          <Dato etiqueta="Precio de venta" valor={formatearMoneda(producto.precioVenta)} />
          <Dato etiqueta="Tiempo de reposición" valor={`${producto.leadTimeDias} días`} />
          <Dato etiqueta="Lote mínimo" valor={`${producto.loteMinimo} u.`} />
          <Dato etiqueta="Variantes" valor={producto.totalVariantes} />
          <Dato etiqueta="Creado" valor={formatearFecha(producto.creadoEn, true)} />
          <Dato etiqueta="Actualizado" valor={formatearFecha(producto.actualizadoEn, true)} />
        </dl>
      </Tarjeta>

      <div className="flex flex-col gap-3">
        <h2>Matriz talla x color</h2>
        {contenidoMatriz}
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <h2>Variantes</h2>
          <label className="flex items-center gap-2 text-sm text-oxford">
            <input type="checkbox" checked={incluirInactivas} onChange={(e) => setIncluirInactivas(e.target.checked)} />
            Mostrar inactivas
          </label>
        </div>
        {tablaVariantes}
      </div>

      {puedeEditar && <GeneradorVariantes productoId={producto.id} productoActivo={producto.activo} />}

      {editando && (
        <ModalStockMinimo
          key={editando.id}
          variante={editando}
          guardando={actualizar.isPending}
          error={actualizar.error}
          onCancelar={() => {
            setEditando(null)
            actualizar.reset()
          }}
          onGuardar={(stockMinimo) =>
            actualizar.mutate(
              { id: editando.id, stockMinimo },
              {
                onSuccess: () => {
                  setEditando(null)
                  actualizar.reset()
                },
              },
            )
          }
        />
      )}

      <DialogoConfirmacion
        abierto={confirmacion !== null}
        titulo={texto?.titulo ?? ''}
        mensaje={texto?.mensaje ?? ''}
        textoConfirmar={texto?.boton}
        peligro={confirmacion !== null && !confirmacion.activar}
        cargando={mutacionConfirmacion.isPending}
        error={mutacionConfirmacion.isError ? mensajeError(mutacionConfirmacion.error) : undefined}
        onCancelar={cerrarConfirmacion}
        onConfirmar={confirmar}
      />
    </section>
  )
}

/** Ruta /productos/:id. */
export function ProductoDetallePage() {
  const { id } = useParams()
  const numero = Number(id)
  const valido = Number.isInteger(numero) && numero > 0
  const producto = useProducto(valido ? numero : undefined)

  if (!valido) return <EstadoError mensaje="El identificador del producto no es válido." />
  if (producto.isPending) return <EstadoCarga filas={4} />
  if (producto.isError) return <EstadoError mensaje={mensajeError(producto.error)} onReintentar={() => void producto.refetch()} />
  return <Detalle producto={producto.data} />
}
