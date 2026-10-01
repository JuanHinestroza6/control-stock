# REQ-06 — Controlar el Acceso y Roles de Usuario (FO-ERS)

> Estado a la fecha: control de acceso por rol implementado en la Fase 0 (backend y frontend); asignación de roles desde la
> interfaz **pendiente — Fase 1** (ver REQ-12).

| Campo | Contenido |
|---|---|
| **Código** | REQ-06 |
| **Nombre** | Controlar el Acceso y Roles de Usuario |
| **Tipo** | Funcional |
| **Stakeholder** | Gerente |
| **Necesidad** | NEC-06 |
| **Caso de uso** | CU-02 "Gestión de usuarios y roles" |
| **Relacionados** | CU-13 "Iniciar sesión" (requisito no funcional REQ-02 "Seguridad y control", NEC-06); REQ-12 |
| **Prioridad** | Alta |
| **Módulos** | `auth`, `usuario`, `comun` (seguridad); frontend: `componentes/layout`, `hooks` |

## Descripción

El sistema debe restringir menús, rutas y endpoints según el rol del usuario (ADMIN, INVENTARIO, VENDEDOR, CONTABILIDAD),
de acuerdo con la matriz de permisos de `docs/dominio.md`, y aplicar siempre el rol y el estado vigentes del usuario.

## Alcance implementado (Fase 0)

- Cuatro roles definidos (`Rol`: ADMIN, INVENTARIO, VENDEDOR, CONTABILIDAD) con restricción `CHECK` en la tabla `usuario`.
- Seguridad por método con `@PreAuthorize`; todo endpoint exige autenticación salvo el inicio de sesión y `/actuator/health`.
  Respuestas 401/403 en formato `ProblemDetail`. CORS restringido a orígenes configurados.
- En cada petición se recarga el usuario por id: inactivo o inexistente produce 401; se aplica el rol vigente en base de datos
  (un cambio de rol o una desactivación surten efecto en la siguiente petición).
- Frontend: `RutaProtegida` (sin sesión redirige a `/login` conservando el destino; rol no permitido muestra acceso denegado)
  y `Sidebar` filtrado por rol según `modulos.ts`.
- Visibilidad del menú: Inicio, Productos y Analítica para todos; Inventario: ADMIN, INVENTARIO, CONTABILIDAD; Ventas: ADMIN,
  VENDEDOR, CONTABILIDAD; Reservas y Alertas: ADMIN, INVENTARIO, VENDEDOR; Compras: ADMIN, INVENTARIO, CONTABILIDAD;
  Reportes: ADMIN, CONTABILIDAD; Usuarios: ADMIN.

### Autenticación que soporta este requisito (CU-13 "Iniciar sesión", REQ-02 "Seguridad y control")

- `POST /api/auth/login` (público): correo normalizado (sin espacios, en minúsculas). Responde
  `LoginResponse{token, tipo "Bearer", expiraEn, usuario{id, nombre, email, rol}}`. Credenciales inválidas, usuario inexistente
  y usuario inactivo producen el mismo 401 "Credenciales inválidas" (sin enumeración de usuarios; siempre se ejecuta BCrypt).
- Token JWT HS256 firmado con `JWT_SECRET` (mínimo 32 bytes; la aplicación no arranca si falta o es de ejemplo), emisor
  `control-stock`, reclamos `sub` (id), `rol`, `nombre`, `exp`; vigencia de 8 horas, sin refresh token (ADR-003).
- `GET /api/auth/yo` (autenticado) devuelve el usuario de la sesión. No hay endpoint de cierre de sesión: el cliente descarta el token.
- Limitador de intentos en memoria por correo + IP: tras 5 fallos en 15 minutos responde 429 con `Retry-After`.
- Frontend: página de login con validación; sesión en `localStorage` con verificación de expiración, limpieza ante 401 y entre pestañas.

## Pendiente — Fase 1

- Pendiente — Fase 1: aplicar `@PreAuthorize` por rol a los endpoints de cada módulo conforme se creen (hoy solo existen los
  de `auth`; la prueba de 403 usa un endpoint de prueba).
- Pendiente — Fase 1: asignación y cambio de rol desde la interfaz por el ADMIN (CU-02; la administración de cuentas es REQ-12).

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Petición sin token a un endpoint protegido devuelve 401 `ProblemDetail`; login y `/actuator/health` son públicos | Cumplido | `ConfiguracionSeguridadTest`, `AuthIntegracionTest` |
| CA-2 | Un rol insuficiente recibe 403 y el rol aplicado es el vigente en base de datos | Cumplido | `ConfiguracionSeguridadTest`, `ConvertidorUsuarioJwtTest`, `AuthIntegracionTest` |
| CA-3 | Un cambio de rol o una desactivación en base de datos se refleja en la siguiente petición (inactivo → 401) | Cumplido | `ConvertidorUsuarioJwtTest`, `AuthIntegracionTest` |
| CA-4 | Solo existen los roles ADMIN, INVENTARIO, VENDEDOR y CONTABILIDAD | Cumplido | `UsuarioAdminInicialTest` |
| CA-5 | CORS acepta solo orígenes configurados | Cumplido | `ConfiguracionSeguridadTest` |
| CA-6 | Sin sesión se redirige a `/login` conservando el destino; con rol no permitido se muestra acceso denegado | Cumplido | `RutaProtegida.test.tsx`, `flujoSesion.test.tsx`, `modulos.test.ts` |
| CA-7 | El menú muestra solo los módulos permitidos para cada rol | Cumplido | `Sidebar.test.tsx`, `modulos.test.ts`, `Topbar.test.tsx` |
| CA-8 | El ADMIN asigna y cambia roles desde la interfaz | Pendiente — Fase 1 | Por definir |

### Criterios de CU-13 "Iniciar sesión" (REQ-02)

| # | Criterio | Pruebas |
|---|---|---|
| CA-13.1 | Login correcto devuelve 200 con token, expiración y datos del usuario, sin exponer el hash | `AuthIntegracionTest`, `AuthControllerTest`, `AuthServiceTest` |
| CA-13.2 | Contraseña incorrecta, correo inexistente o usuario inactivo devuelven 401 con el mismo mensaje | `AuthIntegracionTest`, `AuthServiceTest` |
| CA-13.3 | Correo o contraseña vacíos devuelven 400 con errores por campo | `AuthIntegracionTest`, `LoginPage.test.tsx` |
| CA-13.4 | Token vencido (8 h), malformado, con otro emisor o firmado con otro secreto devuelve 401 | `TokenServiceTest`, `ConfiguracionSeguridadTest`, `AuthIntegracionTest`, `PropiedadesJwtTest` |
| CA-13.5 | Tras 5 fallos el sexto intento devuelve 429 aunque la clave sea correcta; aplica igual a correos inexistentes | `AuthIntegracionTest`, `LimitadorIntentosLoginTest`, `AuthServiceTest` |
| CA-13.6 | La sesión se limpia al expirar, al salir y al cerrar sesión en otra pestaña | `SesionProvider.test.tsx`, `lib/auth.test.ts`, `flujoSesion.test.tsx` |

## Observaciones

- CU-13 "Iniciar sesión" no existe en el documento Word: se crea en esta fase y se liga al requisito no funcional REQ-02.
- `docs/dominio.md` usa REQ-02 para "Registrar operaciones de stock" (funcional); en el Word, REQ-02 "Seguridad y control" es
  no funcional. Confirmar si el Word numera aparte los no funcionales y ajustar la trazabilidad de `dominio.md`.
- El limitador de intentos es en memoria y usa `getRemoteAddr()`: detrás de un proxy inverso deberá revisarse.
