# REQ-12 — Registrar y Gestionar Usuarios (FO-ERS)

> Estado a la fecha: Fase 0 completada (modelo de usuario y ADMIN inicial); administración de cuentas desde la interfaz
> **pendiente — Fase 1**.

| Campo | Contenido |
|---|---|
| **Código** | REQ-12 |
| **Nombre** | Registrar y Gestionar Usuarios |
| **Tipo** | Funcional |
| **Stakeholder** | Gerente |
| **Necesidad** | NEC-06 |
| **Caso de uso** | CU-12 "Registrar y gestionar usuarios" |
| **Relacionados** | REQ-06 (roles y control de acceso, CU-02); CU-13 "Iniciar sesión" (REQ-02) |
| **Prioridad** | Alta |
| **Módulos** | `usuario`, `auth` |

## Descripción

El sistema debe mantener cuentas de usuario con nombre, correo electrónico único, contraseña almacenada como hash BCrypt,
rol y estado activo/inactivo, y permitir al rol ADMIN registrarlas y administrarlas.

## Alcance implementado (Fase 0)

- Entidad `Usuario` (`id, nombre, email único, passwordHash, rol, activo, creadoEn, actualizadoEn`) y enumeración `Rol`;
  tabla `usuario` creada por la migración `V1__crear_tabla_usuario.sql` con correo único y restricción `CHECK` sobre el rol.
- Registro del primer ADMIN mediante la migración Java `V2__UsuarioAdminInicial`, que lee `ADMIN_EMAIL` y `ADMIN_PASSWORD`
  del entorno, exige contraseña de 12 o más caracteres, rechaza valores de ejemplo (`cambiar...`), guarda el hash BCrypt y
  solo inserta si no existe un ADMIN (ADR-003).
- El estado `activo` ya se respeta: un usuario desactivado no puede iniciar sesión ni usar un token vigente (ver REQ-06).
- El menú del frontend incluye la entrada "Usuarios", visible únicamente para ADMIN (`modulos.ts`); el módulo en sí no está implementado.

## Pendiente — Fase 1

- Pendiente — Fase 1: listado, alta, edición y desactivación de usuarios por el ADMIN (CU-12).
- Pendiente — Fase 1: cambio y restablecimiento de contraseña.
- Pendiente — Fase 1: pantalla del módulo "Usuarios" y endpoints REST de administración (aún no existen; los detalles se
  definirán en el plan de la fase).

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Al migrar una base vacía con credenciales válidas se crea exactamente un ADMIN con hash BCrypt verificable | Cumplido | `UsuarioAdminInicialTest`, `V2__UsuarioAdminInicialTest` |
| CA-2 | Repetir la migración no duplica al ADMIN | Cumplido | `UsuarioAdminInicialTest`, `V2__UsuarioAdminInicialTest` |
| CA-3 | La migración falla con mensaje claro si no hay credenciales, la contraseña tiene menos de 12 caracteres o es de ejemplo | Cumplido | `V2__UsuarioAdminInicialTest` |
| CA-4 | El rol se persiste como texto y los campos de auditoría se llenan | Cumplido | `UsuarioAdminInicialTest` |
| CA-5 | El ADMIN registra, edita y desactiva usuarios desde la interfaz | Pendiente — Fase 1 | Por definir |
| CA-6 | El ADMIN cambia o restablece la contraseña de un usuario | Pendiente — Fase 1 | Por definir |
