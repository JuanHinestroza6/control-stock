# REQ-06 — Gestión de usuarios y roles (FO-ERS)

> Estado a la fecha: Fase 0 completada (modelo, roles y usuario inicial); administración desde la interfaz **pendiente — Fase 1**.
> Nota: `docs/dominio.md` agrupa REQ-06 y REQ-12 como "Usuarios y roles". El reparto aquí propuesto (REQ-06 = administración de cuentas y roles; REQ-12 = autenticación y control de acceso) debe contrastarse con los nombres exactos del documento Word.

| Campo | Contenido |
|---|---|
| **Código** | REQ-06 |
| **Nombre** | Gestión de usuarios y roles |
| **Tipo** | Funcional |
| **Stakeholder** | Administrador (dueño de Livorno Clothing) |
| **Necesidad** | NEC-xx (verificar en el Word la necesidad de control de personal y permisos; no se asigna número sin ver la tabla de necesidades) |
| **Prioridad** | Alta |
| **Módulos** | `usuario`, `auth` |

## Descripción

El sistema debe mantener cuentas de usuario con nombre, correo electrónico único, contraseña almacenada como hash BCrypt, rol y estado activo/inactivo, y permitir al rol ADMIN administrarlas. Los roles definidos son ADMIN, INVENTARIO, VENDEDOR y CONTABILIDAD, con los permisos de la matriz de `docs/dominio.md`.

## Alcance implementado (Fase 0)

- Entidad `Usuario` (`id, nombre, email único, passwordHash, rol, activo, creadoEn, actualizadoEn`) y enumeración `Rol` con los cuatro roles; tabla `usuario` creada por la migración `V1__crear_tabla_usuario.sql` con restricción `CHECK` sobre el rol y correo único.
- Creación del primer ADMIN mediante la migración Java `V2__UsuarioAdminInicial`, que lee `ADMIN_EMAIL` y `ADMIN_PASSWORD` del entorno, exige contraseña de 12 o más caracteres, rechaza valores de ejemplo (`cambiar...`), guarda el hash BCrypt y solo inserta si no existe un ADMIN (ADR-003).
- Un usuario desactivado o con rol modificado en la base de datos pierde acceso o adopta el nuevo rol en la siguiente petición (el usuario se recarga en cada solicitud).
- El menú del frontend incluye la entrada "Usuarios", visible únicamente para ADMIN (`modulos.ts`); el módulo en sí no está implementado.

## Pendiente — Fase 1

- Pendiente — Fase 1: listado, alta, edición y desactivación de usuarios por el ADMIN.
- Pendiente — Fase 1: asignación y cambio de rol desde la interfaz.
- Pendiente — Fase 1: cambio y restablecimiento de contraseña.
- Pendiente — Fase 1: pantalla del módulo "Usuarios" y endpoints REST de administración (aún no existen; los detalles se definirán en el plan de la fase).

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Al migrar una base vacía con credenciales válidas se crea exactamente un ADMIN con hash BCrypt verificable | Cumplido | `UsuarioAdminInicialTest`, `V2__UsuarioAdminInicialTest` |
| CA-2 | Repetir la migración no duplica al ADMIN | Cumplido | `UsuarioAdminInicialTest`, `V2__UsuarioAdminInicialTest` |
| CA-3 | La migración falla con mensaje claro si no hay credenciales, la contraseña tiene menos de 12 caracteres o es de ejemplo | Cumplido | `V2__UsuarioAdminInicialTest` |
| CA-4 | El rol se persiste como texto y los campos de auditoría se llenan | Cumplido | `UsuarioAdminInicialTest` |
| CA-5 | Solo existen los roles ADMIN, INVENTARIO, VENDEDOR y CONTABILIDAD (restricción `CHECK` de V1 y enumeración `Rol`) | Cumplido | `UsuarioAdminInicialTest` |
| CA-6 | Un cambio de rol o desactivación en base de datos se refleja en la siguiente petición | Cumplido | `ConvertidorUsuarioJwtTest`, `AuthIntegracionTest` |
| CA-7 | El ADMIN crea, edita y desactiva usuarios y cambia roles desde la interfaz | Pendiente — Fase 1 | Por definir |

## Inconsistencias detectadas

- `docs/dominio.md` unifica REQ-06 y REQ-12 bajo "Usuarios y roles"; confirmar contra el Word cómo se dividen.
