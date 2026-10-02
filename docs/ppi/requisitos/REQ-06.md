# REQ-06 — Controlar el Acceso y Roles de Usuario (FO-ERS)

> Estado a la fecha: control de acceso por rol implementado en la Fase 0; en la Fase 1 se añadieron la asignación de roles
> desde la interfaz (CU-02, REQ-12), la revocación de tokens por cambio de contraseña y el cambio obligatorio de la contraseña
> temporal (ADR-005).

| Campo | Contenido |
|---|---|
| **Código** | REQ-06 |
| **Nombre** | Controlar el Acceso y Roles de Usuario |
| **Tipo** | Funcional |
| **Stakeholder** | Gerente |
| **Necesidad** | NEC-06 |
| **Caso de uso** | CU-02 "Gestión de usuarios y roles" |
| **Relacionados** | CU-13 "Iniciar sesión" (requisito no funcional RNF-02 "Seguridad y control", NEC-06); REQ-12 |
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

### Autenticación que soporta este requisito (CU-13 "Iniciar sesión", RNF-02 "Seguridad y control")

- `POST /api/auth/login` (público): correo normalizado (sin espacios, en minúsculas). Responde
  `LoginResponse{token, tipo "Bearer", expiraEn, usuario{id, nombre, email, rol}}`. Credenciales inválidas, usuario inexistente
  y usuario inactivo producen el mismo 401 "Credenciales inválidas" (sin enumeración de usuarios; siempre se ejecuta BCrypt).
- Token JWT HS256 firmado con `JWT_SECRET` (mínimo 32 bytes; la aplicación no arranca si falta o es de ejemplo), emisor
  `control-stock`, reclamos `sub` (id), `rol`, `nombre`, `exp`; vigencia de 8 horas, sin refresh token (ADR-003).
- `GET /api/auth/yo` (autenticado) devuelve el usuario de la sesión. No hay endpoint de cierre de sesión: el cliente descarta el token.
- Limitador de intentos en memoria por correo + IP: tras 5 fallos en 15 minutos responde 429 con `Retry-After`.
- Frontend: página de login con validación; sesión en `localStorage` con verificación de expiración, limpieza ante 401 y entre pestañas.

## Alcance implementado (Fase 1)

- Asignación y cambio de rol por el ADMIN desde la pantalla "Usuarios" (`PUT /api/usuarios/{id}`, solo ADMIN, CU-02). Reglas:
  nadie cambia su propio rol (422) y siempre debe quedar al menos un ADMIN activo (422, con bloqueo pesimista sobre los ADMIN activos).
- `@PreAuthorize` aplicado en los endpoints de `catalogo` (lectura autenticada; escritura ADMIN/INVENTARIO) y `usuario` (solo ADMIN).
- Revocación de tokens (ADR-005): `usuario.password_cambiado_en` (migración `V6`); el convertidor JWT rechaza con 401 un token
  con `iat` anterior a ese instante (comparación en segundos; ventana aceptada de 1 s).
- Cambio obligatorio: la contraseña que asigna el ADMIN es temporal (`debeCambiarPassword`). Mientras esté pendiente, solo se permiten
  `GET /api/auth/yo` y `PUT /api/auth/yo/password`; el resto responde 403 con `codigo: "CAMBIO_PASSWORD_REQUERIDO"`. El frontend
  redirige a `/cuenta` (`RutaProtegida` e interceptor de Axios).
- `PUT /api/auth/yo/password` (autenticado): cambia la contraseña propia y devuelve un token nuevo; el anterior queda revocado.
  Contraseña actual incorrecta o nueva igual a la actual produce 422 (no 401, para no cerrar la sesión).

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
| CA-8 | El ADMIN asigna y cambia roles desde la interfaz; no puede cambiar su propio rol ni dejar el sistema sin ADMIN activo | Cumplido | `UsuarioServiceTest`, `UsuarioControllerTest`, `UsuarioSeguridadIntegracionTest`, `UsuarioAdminConcurrenciaTest`, `UsuariosPage.test.tsx` |
| CA-9 | Los endpoints de `usuarios` responden 403 a todo rol distinto de ADMIN y los de catálogo respetan la matriz por rol | Cumplido | `UsuarioSeguridadIntegracionTest`, `CatalogoIntegracionTest`, `UsuarioControllerTest`, `ProductoControllerTest` |
| CA-10 | Un token anterior al cambio o restablecimiento de contraseña recibe 401; uno del mismo segundo o posterior es válido; sin `passwordCambiadoEn` es válido | Cumplido | `ConvertidorUsuarioJwtRevocacionTest`, `UsuarioSeguridadIntegracionTest` |
| CA-11 | Con `debeCambiarPassword` todo endpoint responde 403 con `codigo` `CAMBIO_PASSWORD_REQUERIDO`, salvo `/api/auth/yo` y `/api/auth/yo/password`; el frontend redirige a `/cuenta` | Cumplido | `CambioPasswordObligatorioTest`, `RutaProtegida.test.tsx`, `cliente.test.ts`, `CuentaPage.test.tsx` |
| CA-12 | `PUT /api/auth/yo/password` devuelve token nuevo y el anterior queda revocado; contraseña actual incorrecta → 422 | Cumplido | `AuthControllerTest`, `AuthServiceTest`, `UsuarioSeguridadIntegracionTest`, `CuentaPage.test.tsx` |

### Criterios de CU-13 "Iniciar sesión" (RNF-02)

| # | Criterio | Pruebas |
|---|---|---|
| CA-13.1 | Login correcto devuelve 200 con token, expiración y datos del usuario, sin exponer el hash | `AuthIntegracionTest`, `AuthControllerTest`, `AuthServiceTest` |
| CA-13.2 | Contraseña incorrecta, correo inexistente o usuario inactivo devuelven 401 con el mismo mensaje | `AuthIntegracionTest`, `AuthServiceTest` |
| CA-13.3 | Correo o contraseña vacíos devuelven 400 con errores por campo | `AuthIntegracionTest`, `LoginPage.test.tsx` |
| CA-13.4 | Token vencido (8 h), malformado, con otro emisor o firmado con otro secreto devuelve 401 | `TokenServiceTest`, `ConfiguracionSeguridadTest`, `AuthIntegracionTest`, `PropiedadesJwtTest` |
| CA-13.5 | Tras 5 fallos el sexto intento devuelve 429 aunque la clave sea correcta; aplica igual a correos inexistentes | `AuthIntegracionTest`, `LimitadorIntentosLoginTest`, `AuthServiceTest` |
| CA-13.6 | La sesión se limpia al expirar, al salir y al cerrar sesión en otra pestaña | `SesionProvider.test.tsx`, `lib/auth.test.ts`, `flujoSesion.test.tsx` |

## Observaciones

- CU-13 "Iniciar sesión" no existe en el documento Word: se crea en esta fase y se liga al requisito no funcional RNF-02.
- Convención de códigos (ver `docs/dominio.md`): REQ-xx para requisitos funcionales y RNF-xx para los no funcionales del
  Word. El antiguo REQ-02 "Seguridad y control" del Word es RNF-02; REQ-02 sigue siendo "Registrar operaciones de stock".
- El limitador de intentos es en memoria y usa `getRemoteAddr()`: detrás de un proxy inverso deberá revisarse.
