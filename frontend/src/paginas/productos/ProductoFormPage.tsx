import type { ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { useCategorias } from '../../hooks/useCategorias'
import { useActualizarProducto, useCrearProducto, useProducto } from '../../hooks/useProductos'
import { mensajeError } from '../../lib/errores'
import type { CategoriaResponse, ProductoDetalleResponse } from '../../tipos/catalogo'
import { FormularioProducto } from './FormularioProducto'

function Contenedor({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4">
      <h1>{titulo}</h1>
      <Tarjeta className="max-w-3xl p-6">{children}</Tarjeta>
    </section>
  )
}

function CrearProducto({ categorias }: { categorias: CategoriaResponse[] }) {
  const navegar = useNavigate()
  const crear = useCrearProducto()
  return (
    <Contenedor titulo="Nuevo producto">
      <FormularioProducto
        categorias={categorias}
        guardando={crear.isPending}
        error={crear.error}
        cancelarA="/productos"
        onGuardar={(datos) => crear.mutate(datos, { onSuccess: (p) => navegar(`/productos/${p.id}`) })}
      />
    </Contenedor>
  )
}

function EditarProducto({ producto, categorias }: { producto: ProductoDetalleResponse; categorias: CategoriaResponse[] }) {
  const navegar = useNavigate()
  const actualizar = useActualizarProducto(producto.id)
  return (
    <Contenedor titulo={`Editar ${producto.nombre}`}>
      <FormularioProducto
        producto={producto}
        categorias={categorias}
        guardando={actualizar.isPending}
        error={actualizar.error}
        cancelarA={`/productos/${producto.id}`}
        onGuardar={(datos) => actualizar.mutate(datos, { onSuccess: () => navegar(`/productos/${producto.id}`) })}
      />
    </Contenedor>
  )
}

/** Carga el producto (si se edita) y las categorías; delega en el formulario cuando ambos están listos. */
function Cargador({ id }: { id?: number }) {
  const categorias = useCategorias()
  const producto = useProducto(id)
  const editando = id !== undefined

  if (categorias.isPending || (editando && producto.isPending)) {
    return (
      <Contenedor titulo={editando ? 'Editar producto' : 'Nuevo producto'}>
        <EstadoCarga filas={4} />
      </Contenedor>
    )
  }
  const fallo = categorias.isError ? categorias : editando && producto.isError ? producto : null
  if (fallo) {
    return (
      <Contenedor titulo={editando ? 'Editar producto' : 'Nuevo producto'}>
        <EstadoError
          mensaje={mensajeError(fallo.error)}
          onReintentar={() => {
            void categorias.refetch()
            if (editando) void producto.refetch()
          }}
        />
      </Contenedor>
    )
  }
  if (editando && producto.data) return <EditarProducto producto={producto.data} categorias={categorias.data ?? []} />
  return <CrearProducto categorias={categorias.data ?? []} />
}

/** Rutas /productos/nuevo y /productos/:id/editar. */
export function ProductoFormPage() {
  const { id } = useParams()
  const numero = id === undefined ? undefined : Number(id)
  if (numero !== undefined && !Number.isInteger(numero)) {
    return (
      <Contenedor titulo="Editar producto">
        <EstadoError mensaje="El identificador del producto no es válido." />
      </Contenedor>
    )
  }
  return <Cargador id={numero} />
}
