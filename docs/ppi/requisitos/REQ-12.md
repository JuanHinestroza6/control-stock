# REQ-12 — Autenticación y control de acceso por rol (FO-ERS)

> Estado a la fecha: implementado en la Fase 0 (backend y frontend). Ver nota de nomenclatura en `REQ-06.md`.

| Campo | Contenido |
|---|---|
| **Código** | REQ-12 |
| **Nombre** | Autenticación y control de acceso por rol |
| **Tipo** | Funcional / seguridad |
| **Stakeholder** | Administrador; todos los usuarios del sistema |
| **Necesidad** | NEC-xx (verificar en el Word; no se asigna número sin ver la tabla de necesidades) |
| **Prioridad** | Alta |
| **Módulos** | `auth`, `usuario`, `comun` (seguridad); frontend: `api/auth`, `hooks`, `componentes/layout` |

## Descripción

El sistema debe permitir iniciar sesión con correo y contraseña, mantener una sesión sin estado mediante token JWT y restringir menús, rutas y endpoints según el rol del usuario (ADMIN, INVENTARIO, VENDEDOR, CONTABILIDAD).

## Alcance implementado (Fase 0)

- `POST /api/auth/login` (público): recibe correo y contraseña; el correo se normaliza (sin espacios, en minúsculas). Responde `LoginResponse{token, tipo "Bearer", expiraEn, usuario{id, nombre, email, rol}}`. Credenciales inválidas, usuario inexistente y usuario inactivo producen el mismo 401 "Credenciales inválidas" (sin enumeración de usuarios; siempre se ejecuta BCrypt).
- Token JWT HS256 firmado con `JWT_SECRET` (mínimo 32 bytes; la aplicación no arranca si falta), emisor `control-stock`, reclamos `sub` (id), `rol`, `nombre`, `exp`; vigencia de 8 horas, sin refresh token (ADR-003).
- `GET /api/auth/yo` (autenticado): devuelve el usuario de la sesión. No existe endpoint de cierre de sesión: el cliente descarta el token.
- En cada petición se recarga el usuario por id: inactivo o inexistente produce 401; se aplica el rol vigente en base de datos.
- Limitador de intentos en memoria por correo + IP: tras 5 fallos en 15 minutos responde 429 con `Retry-After`; un login exitoso reinicia el contador.
- Seguridad por método con `@PreAuthorize`; todo endpoint exige autenticación salvo login y `/actuator/health`. Respuestas 401/403 en formato `ProblemDetail`. CORS restringido a orígenes configurados.
- Frontend: página de login con validación; sesión en `localStorage` con verificación de expiración; limpieza de sesión ante 401 y entre pestañas; `RutaProtegida` (sin sesión redirige a `/login` conservando el destino; rol no permitido muestra acceso denegado); `Sidebar` filtrado por rol según `modulos.ts`.
- Visibilidad del menú implementada: Inicio, Productos y Analítica para todos; Inventario: ADMIN, INVENTARIO, CONTABILIDAD; Ventas: ADMIN, VENDEDOR, CONTABILIDAD; Reservas y Alertas: ADMIN, INVENTARIO, VENDEDOR; Compras: ADMIN, INVENTARIO, CONTABILIDAD; Reportes: ADMIN, CONTABILIDAD; Usuarios: ADMIN.

## Pendiente — Fase 1 en adelante

- Pendiente — Fase 1: aplicar `@PreAuthorize` por rol a los endpoints de cada módulo conforme se creen (hoy solo existen los de `auth`; la prueba de 403 usa un endpoint de prueba).
- Pendiente — Fase 1: gestión de contraseñas desde la interfaz (ver REQ-06).

## Criterios de aceptación

| # | Criterio | Pruebas |
|---|---|---|
| CA-1 | Login correcto devuelve 200 con token, expiración y datos del usuario, sin exponer el hash | `AuthIntegracionTest`, `AuthControllerTest`, `AuthServiceTest` |
| CA-2 | Contraseña incorrecta, correo inexistente o usuario inactivo devuelven 401 con el mismo mensaje | `AuthIntegracionTest`, `AuthServiceTest` |
| CA-3 | Correo o contraseña vacíos devuelven 400 con errores por campo | `AuthIntegracionTest`, `LoginPage.test.tsx` |
| CA-4 | El token vence a las 8 h; token vencido, malformado, con otro emisor o firmado con otro secreto devuelve 401 | `TokenServiceTest`, `ConfiguracionSeguridadTest`, `AuthIntegracionTest`, `PropiedadesJwtTest` |
| CA-5 | Petición sin token a un endpoint protegido devuelve 401 `ProblemDetail`; login y `/actuator/health` son públicos | `ConfiguracionSeguridadTest`, `AuthIntegracionTest` |
| CA-6 | Usuario desactivado tras obtener el token recibe 401 en `/api/auth/yo` | `AuthIntegracionTest`, `ConvertidorUsuarioJwtTest` |
| CA-7 | Un rol insuficiente recibe 403 y el rol aplicado es el vigente en base de datos | `ConfiguracionSeguridadTest`, `ConvertidorUsuarioJwtTest`, `AuthIntegracionTest` |
| CA-8 | Tras 5 fallos seguidos el sexto intento devuelve 429 aunque la clave sea correcta; aplica igual a correos inexistentes; el éxito reinicia el contador; la ventana expira a los 15 min | `AuthIntegracionTest`, `LimitadorIntentosLoginTest`, `AuthServiceTest` |
| CA-9 | CORS acepta solo orígenes configurados | `ConfiguracionSeguridadTest` |
| CA-10 | Sin sesión se redirige a `/login` conservando el destino; con rol no permitido se muestra acceso denegado | `RutaProtegida.test.tsx`, `flujoSesion.test.tsx`, `modulos.test.ts` |
| CA-11 | El menú muestra solo los módulos permitidos para cada rol (ADMIN ve 10; VENDEDOR sin Usuarios, Compras, Reportes ni Inventario) | `Sidebar.test.tsx`, `modulos.test.ts`, `Topbar.test.tsx` |
| CA-12 | La sesión se limpia al expirar, al salir y al cerrar sesión en otra pestaña; se descarta una sesión vencida o corrupta | `SesionProvider.test.tsx`, `lib/auth.test.ts`, `flujoSesion.test.tsx` |

## Observaciones

- Inconsistencias del documento, ajenas a este requisito: REQ-08/REQ-09 con nombre y descripción cruzados; NEC-010 debería ser NEC-10.
- El limitador de intentos es en memoria y usa `getRemoteAddr()`: detrás de un proxy inverso deberá revisarse (registrado en `progreso.md`).
