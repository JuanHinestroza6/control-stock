import { useState } from 'react'
import { Link } from 'react-router'
import { Boton } from '../../componentes/ui/Boton'
import { DialogoConfirmacion } from '../../componentes/ui/DialogoConfirmacion'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import { useActualizarCategoria, useCategorias, useCrearCategoria, useEliminarCategoria } from '../../hooks/useCategorias'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { puedeEditarCatalogo } from '../../lib/permisos'
import type { CategoriaResponse } from '../../tipos/catalogo'
import { FormularioCategoria } from './FormularioCategoria'

/** `null` = cerrado, `'nueva'` = crear, objeto = editar esa categoría. */
type Edicion = null | 'nueva' | CategoriaResponse

export function CategoriasPage() {
  const { usuario } = useSesion()
  const puedeEditar = puedeEditarCatalogo(usuario?.rol)
  const consulta = useCategorias()
  const crear = useCrearCategoria()
  const actualizar = useActualizarCategoria()
  const eliminar = useEliminarCategoria()
  const [edicion, setEdicion] = useState<Edicion>(null)
  const [aEliminar, setAEliminar] = useState<CategoriaResponse | null>(null)

  const editando = edicion !== null && edicion !== 'nueva'
  const mutacionForm = editando ? actualizar : crear

  const cerrarFormulario = () => {
    setEdicion(null)
    crear.reset()
    actualizar.reset()
  }

  const cerrarEliminar = () => {
    setAEliminar(null)
    eliminar.reset()
  }

  const columnas: Columna<CategoriaResponse>[] = [
    { clave: 'nombre', encabezado: 'Nombre', celda: (c) => <span className="font-medium">{c.nombre}</span> },
    { clave: 'productos', encabezado: 'Productos', celda: (c) => c.totalProductos, className: 'text-right' },
  ]
  if (puedeEditar) {
    columnas.push({
      clave: 'acciones',
      encabezado: 'Acciones',
      className: 'text-right',
      celda: (c) => (
        <div className="flex justify-end gap-2">
          <Boton variante="secundario" aria-label={`Editar ${c.nombre}`} onClick={() => setEdicion(c)}>
            Editar
          </Boton>
          <Boton
            variante="peligro"
            aria-label={`Eliminar ${c.nombre}`}
            disabled={c.totalProductos > 0}
            title={c.totalProductos > 0 ? 'La categoría tiene productos asociados' : undefined}
            onClick={() => setAEliminar(c)}
          >
            Eliminar
          </Boton>
        </div>
      ),
    })
  }

  const nuevaAccion = puedeEditar ? <Boton onClick={() => setEdicion('nueva')}>Nueva categoría</Boton> : undefined

  let contenido
  if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else if (consulta.data.length === 0) {
    contenido = <EstadoVacio mensaje="Aún no hay categorías." accion={nuevaAccion} />
  } else {
    contenido = <Tabla columnas={columnas} filas={consulta.data} claveFila={(c) => c.id} descripcion="Categorías de productos" />
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1>Categorías</h1>
          <Link to="/productos" className="text-sm font-medium text-acento hover:underline">
            Volver a productos
          </Link>
        </div>
        {consulta.data && consulta.data.length > 0 && nuevaAccion}
      </div>
      {contenido}

      {edicion !== null && (
        <FormularioCategoria
          key={edicion === 'nueva' ? 'nueva' : edicion.id}
          categoria={edicion === 'nueva' ? undefined : edicion}
          guardando={mutacionForm.isPending}
          error={mutacionForm.error}
          onCancelar={cerrarFormulario}
          onGuardar={(datos) => {
            if (edicion === 'nueva') crear.mutate(datos, { onSuccess: cerrarFormulario })
            else actualizar.mutate({ id: edicion.id, datos }, { onSuccess: cerrarFormulario })
          }}
        />
      )}

      <DialogoConfirmacion
        abierto={aEliminar !== null}
        titulo="Eliminar categoría"
        mensaje={`¿Eliminar la categoría "${aEliminar?.nombre ?? ''}"? Esta acción no se puede deshacer.`}
        textoConfirmar="Eliminar"
        peligro
        cargando={eliminar.isPending}
        error={eliminar.isError ? mensajeError(eliminar.error) : undefined}
        onCancelar={cerrarEliminar}
        onConfirmar={() => aEliminar && eliminar.mutate(aEliminar.id, { onSuccess: cerrarEliminar })}
      />
    </section>
  )
}
