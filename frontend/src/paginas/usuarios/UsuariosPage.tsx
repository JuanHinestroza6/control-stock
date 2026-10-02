import { useEffect, useState } from 'react'
import { Badge } from '../../componentes/ui/Badge'
import { Boton } from '../../componentes/ui/Boton'
import { Campo } from '../../componentes/ui/Campo'
import { EstadoCarga, EstadoError, EstadoVacio } from '../../componentes/ui/Estados'
import { Paginacion } from '../../componentes/ui/Paginacion'
import { Selector } from '../../componentes/ui/Selector'
import { Tabla, type Columna } from '../../componentes/ui/Tabla'
import {
  useActualizarUsuario,
  useCambiarEstadoUsuario,
  useCrearUsuario,
  useRestablecerPassword,
  useUsuarios,
} from '../../hooks/useUsuarios'
import { DialogoConfirmacion } from '../../componentes/ui/DialogoConfirmacion'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { ETIQUETA_ROL } from '../../lib/roles'
import type { Rol } from '../../tipos/auth'
import type { UsuarioResponse } from '../../tipos/usuarios'
import { FormularioUsuario } from './FormularioUsuario'
import { ModalPassword } from './ModalPassword'

const TAMANO_PAGINA = 20
const RETARDO_BUSQUEDA_MS = 300

const ORDENES = [
  { valor: 'nombre,asc', etiqueta: 'Nombre (A-Z)' },
  { valor: 'nombre,desc', etiqueta: 'Nombre (Z-A)' },
  { valor: 'email,asc', etiqueta: 'Correo' },
  { valor: 'rol,asc', etiqueta: 'Rol' },
  { valor: 'creadoEn,desc', etiqueta: 'Más recientes' },
]

const ESTADOS = [
  { valor: 'true', etiqueta: 'Activos' },
  { valor: 'false', etiqueta: 'Inactivos' },
]

const OPCIONES_ROL = (Object.keys(ETIQUETA_ROL) as Rol[]).map((r) => ({ valor: r, etiqueta: ETIQUETA_ROL[r] }))

const clasesLink = 'font-medium text-acento hover:underline'

/** Solo ADMIN (la ruta ya está protegida; el backend tiene la última palabra). */
export function UsuariosPage() {
  const { usuario: yo } = useSesion()
  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const [rol, setRol] = useState('')
  const [activo, setActivo] = useState('')
  const [sort, setSort] = useState('nombre,asc')
  const [pagina, setPagina] = useState(0)
  const [edicion, setEdicion] = useState<'nuevo' | UsuarioResponse | null>(null)

  const [aCambiarEstado, setACambiarEstado] = useState<UsuarioResponse | null>(null)
  const [aRestablecer, setARestablecer] = useState<UsuarioResponse | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const cambiarEstado = useCambiarEstadoUsuario()
  const restablecer = useRestablecerPassword()
  const crear = useCrearUsuario()
  const actualizar = useActualizarUsuario()
  const mutacionForm = edicion === 'nuevo' ? crear : actualizar

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(texto.trim())
      setPagina(0)
    }, RETARDO_BUSQUEDA_MS)
    return () => clearTimeout(id)
  }, [texto])

  const consulta = useUsuarios({
    q: q || undefined,
    rol: rol ? (rol as Rol) : undefined,
    activo: activo === '' ? undefined : activo === 'true',
    page: pagina,
    size: TAMANO_PAGINA,
    sort,
  })

  const hayFiltros = q !== '' || rol !== '' || activo !== ''

  const cerrarFormulario = () => {
    setEdicion(null)
    crear.reset()
    actualizar.reset()
  }

  const columnas: Columna<UsuarioResponse>[] = [
    { clave: 'nombre', encabezado: 'Nombre', celda: (u) => <span className="font-medium">{u.nombre}</span> },
    { clave: 'email', encabezado: 'Correo', celda: (u) => u.email, ocultarEnMovil: true },
    { clave: 'rol', encabezado: 'Rol', celda: (u) => <Badge tono="acento">{ETIQUETA_ROL[u.rol] ?? u.rol}</Badge> },
    {
      clave: 'estado',
      encabezado: 'Estado',
      celda: (u) => (
        <div className="flex flex-wrap gap-1">
          <Badge tono={u.activo ? 'ok' : 'neutro'}>{u.activo ? 'Activo' : 'Inactivo'}</Badge>
          {u.debeCambiarPassword && <Badge tono="alerta">Contraseña temporal</Badge>}
        </div>
      ),
    },
    {
      clave: 'acciones',
      encabezado: 'Acciones',
      className: 'text-right',
      celda: (u) => (
        <div className="flex flex-wrap justify-end gap-2">
          <Boton variante="secundario" aria-label={`Editar ${u.nombre}`} onClick={() => setEdicion(u)}>
            Editar
          </Boton>
          {u.id === yo?.id ? (
            <span className="self-center text-xs font-medium text-oxford/60">Tu cuenta</span>
          ) : (
            <>
              <Boton
                variante={u.activo ? 'peligro' : 'secundario'}
                aria-label={`${u.activo ? 'Desactivar' : 'Activar'} ${u.nombre}`}
                onClick={() => setACambiarEstado(u)}
              >
                {u.activo ? 'Desactivar' : 'Activar'}
              </Boton>
              <Boton variante="secundario" aria-label={`Restablecer contraseña de ${u.nombre}`} onClick={() => setARestablecer(u)}>
                Restablecer contraseña
              </Boton>
            </>
          )}
        </div>
      ),
    },
  ]

  const limpiar = () => {
    setTexto('')
    setQ('')
    setRol('')
    setActivo('')
    setPagina(0)
  }

  let contenido
  if (consulta.isPending) {
    contenido = <EstadoCarga />
  } else if (consulta.isError) {
    contenido = <EstadoError mensaje={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
  } else if (consulta.data.content.length === 0) {
    contenido = hayFiltros ? (
      <EstadoVacio
        mensaje="Ningún usuario coincide con los filtros."
        accion={
          <button type="button" onClick={limpiar} className={clasesLink}>
            Limpiar filtros
          </button>
        }
      />
    ) : (
      <EstadoVacio mensaje="Aún no hay usuarios." accion={<Boton onClick={() => setEdicion('nuevo')}>Crear el primer usuario</Boton>} />
    )
  } else {
    contenido = (
      <>
        <Tabla columnas={columnas} filas={consulta.data.content} claveFila={(u) => u.id} descripcion="Listado de usuarios" />
        <Paginacion pagina={consulta.data.page} onCambiar={setPagina} />
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1>Usuarios</h1>
        <Boton onClick={() => setEdicion('nuevo')}>Nuevo usuario</Boton>
      </div>

      <form
        role="search"
        aria-label="Filtros de usuarios"
        onSubmit={(e) => e.preventDefault()}
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4"
      >
        <Campo
          etiqueta="Buscar"
          type="search"
          maxLength={60}
          placeholder="Nombre o correo"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
        />
        <Selector
          etiqueta="Rol"
          placeholder="Todos"
          opciones={OPCIONES_ROL}
          value={rol}
          onChange={(e) => {
            setRol(e.target.value)
            setPagina(0)
          }}
        />
        <Selector
          etiqueta="Estado"
          placeholder="Todos"
          opciones={ESTADOS}
          value={activo}
          onChange={(e) => {
            setActivo(e.target.value)
            setPagina(0)
          }}
        />
        <Selector
          etiqueta="Ordenar por"
          opciones={ORDENES}
          value={sort}
          onChange={(e) => {
            setSort(e.target.value)
            setPagina(0)
          }}
        />
      </form>

      {aviso && (
        <p role="status" className="rounded-lg bg-ok/10 px-3 py-2 text-sm font-medium text-oxford">
          {aviso}
        </p>
      )}
      {contenido}

      {edicion !== null && (
        <FormularioUsuario
          key={edicion === 'nuevo' ? 'nuevo' : edicion.id}
          usuario={edicion === 'nuevo' ? undefined : edicion}
          esPropio={edicion !== 'nuevo' && edicion.id === yo?.id}
          guardando={mutacionForm.isPending}
          error={mutacionForm.error}
          onCancelar={cerrarFormulario}
          onGuardar={(datos) => {
            if (edicion === 'nuevo') {
              crear.mutate(
                { nombre: datos.nombre, email: datos.email, rol: datos.rol, passwordTemporal: datos.passwordTemporal },
                { onSuccess: cerrarFormulario },
              )
            } else {
              actualizar.mutate(
                { id: edicion.id, datos: { nombre: datos.nombre, email: datos.email, rol: datos.rol } },
                { onSuccess: cerrarFormulario },
              )
            }
          }}
        />
      )}

      <DialogoConfirmacion
        abierto={aCambiarEstado !== null}
        titulo={aCambiarEstado?.activo ? 'Desactivar usuario' : 'Activar usuario'}
        mensaje={
          aCambiarEstado?.activo
            ? `¿Desactivar a ${aCambiarEstado.nombre}? No podrá iniciar sesión. Siempre debe quedar al menos un administrador activo.`
            : `¿Activar a ${aCambiarEstado?.nombre ?? ''}?`
        }
        textoConfirmar={aCambiarEstado?.activo ? 'Desactivar' : 'Activar'}
        peligro={aCambiarEstado?.activo}
        cargando={cambiarEstado.isPending}
        error={cambiarEstado.isError ? mensajeError(cambiarEstado.error) : undefined}
        onCancelar={() => {
          setACambiarEstado(null)
          cambiarEstado.reset()
        }}
        onConfirmar={() =>
          aCambiarEstado &&
          cambiarEstado.mutate(
            { id: aCambiarEstado.id, activo: !aCambiarEstado.activo },
            {
              onSuccess: () => {
                setACambiarEstado(null)
                cambiarEstado.reset()
              },
            },
          )
        }
      />

      {aRestablecer !== null && (
        <ModalPassword
          key={aRestablecer.id}
          usuario={aRestablecer}
          guardando={restablecer.isPending}
          error={restablecer.error}
          onCancelar={() => {
            setARestablecer(null)
            restablecer.reset()
          }}
          onGuardar={(passwordTemporal) =>
            restablecer.mutate(
              { id: aRestablecer.id, passwordTemporal },
              {
                onSuccess: () => {
                  setAviso(`Contraseña temporal fijada para ${aRestablecer.nombre}.`)
                  setARestablecer(null)
                  restablecer.reset()
                },
              },
            )
          }
        />
      )}
    </section>
  )
}
