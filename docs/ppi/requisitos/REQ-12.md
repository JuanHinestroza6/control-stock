# REQ-12 — Registrar y Gestionar Usuarios (FO-ERS)

> Estado a la fecha: Fase 0 (modelo de usuario y ADMIN inicial) y Fase 1 (administración de cuentas, contraseñas temporales,
> cambio de contraseña propia y revocación de tokens, ADR-005) completadas.

| Campo | Contenido |
|---|---|
| **Código** | REQ-12 |
| **Nombre** | Registrar y Gestionar Usuarios |
| **Tipo** | Funcional |
| **Stakeholder** | Gerente |
| **Necesidad** | NEC-06 |
| **Caso de uso** | CU-12 "Registrar y gestionar usuarios" |
| **Relacionados** | REQ-06 (roles y control de acceso, CU-02); CU-13 "Iniciar sesión" (RNF-02) |
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

## Alcance implementado (Fase 1)

- API REST solo ADMIN (`/api/usuarios`): listado paginado con filtros (`q`, `rol`, `activo`; orden `nombre, email, rol, creadoEn`),
  detalle, alta, edición, `PATCH /{id}/estado` y `PUT /{id}/password` (restablecer). La respuesta nunca incluye el hash.
- Desactivación lógica; el correo es único (409).
- Contraseñas (`@PasswordValida`): 12 a 72 caracteres, máximo 72 bytes UTF-8, no empieza por `cambiar`. La que define el ADMIN
  es temporal: marca `debeCambiarPassword` y fija `passwordCambiadoEn`, lo que revoca los tokens vigentes del usuario (ADR-005).
- Cambio de la contraseña propia: `PUT /api/auth/yo/password` (devuelve token nuevo) desde la página `/cuenta`.
- Reglas (422): nadie se desactiva, se cambia el rol ni restablece su propia contraseña desde `/usuarios` (usa "Mi cuenta");
  siempre debe quedar al menos un ADMIN activo (bloqueo pesimista ante concurrencia).
- Migración `V6__agregar_credenciales_usuario.sql` (`password_cambiado_en`, `debe_cambiar_password`).
- Frontend: página `/usuarios` (tabla, filtros, formulario, estado, rol, restablecimiento; acciones ocultas sobre la propia
  cuenta) y `/cuenta` (cambio voluntario u obligatorio).

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Al migrar una base vacía con credenciales válidas se crea exactamente un ADMIN con hash BCrypt verificable | Cumplido | `UsuarioAdminInicialTest`, `V2__UsuarioAdminInicialTest` |
| CA-2 | Repetir la migración no duplica al ADMIN | Cumplido | `UsuarioAdminInicialTest`, `V2__UsuarioAdminInicialTest` |
| CA-3 | La migración falla con mensaje claro si no hay credenciales, la contraseña tiene menos de 12 caracteres o es de ejemplo | Cumplido | `V2__UsuarioAdminInicialTest` |
| CA-4 | El rol se persiste como texto y los campos de auditoría se llenan | Cumplido | `UsuarioAdminInicialTest` |
| CA-5 | El ADMIN registra, edita y desactiva usuarios desde la interfaz; correo duplicado → 409; no se desactiva a sí mismo ni al último ADMIN activo (422) | Cumplido | `UsuarioServiceTest`, `UsuarioControllerTest`, `UsuarioRepositoryTest`, `UsuarioSeguridadIntegracionTest`, `UsuarioAdminConcurrenciaTest`, `UsuariosPage.test.tsx` |
| CA-6 | El ADMIN restablece la contraseña de otro usuario y el propio usuario cambia la suya; la temporal obliga a cambiarla y revoca sesiones | Cumplido | `UsuarioServiceTest`, `PasswordValidaTest`, `CambioPasswordObligatorioTest`, `ConvertidorUsuarioJwtRevocacionTest`, `UsuarioSeguridadIntegracionTest`, `CuentaPage.test.tsx`, `UsuariosPage.test.tsx` |
| CA-7 | La entidad aplica `asignarPasswordTemporal`, `cambiarPassword`, `actualizarDatos` y `cambiarRol` | Cumplido | `UsuarioTest` |
| CA-8 | Contraseñas inválidas (11 caracteres, más de 72 bytes, prefijo `cambiar`) devuelven 400 con error por campo | Cumplido | `PasswordValidaTest`, `UsuarioSeguridadIntegracionTest`, validación en `CuentaPage.test.tsx` |
