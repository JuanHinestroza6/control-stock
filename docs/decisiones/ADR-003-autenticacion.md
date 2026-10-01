# ADR-003: Autenticación y sesión

- Estado: aceptada
- Fecha: 2026-10-01

## Contexto
ADR-001 definió Spring Security con JWT stateless. Faltaba decidir librería, duración de la sesión, almacenamiento del token
en el navegador y cómo crear el primer ADMIN sin dejar credenciales en el repositorio (regla de oro 9).

## Decisión
- Tokens con `spring-boot-starter-oauth2-resource-server` (Nimbus), firma HS256 con `JWT_SECRET` (≥ 32 bytes; la app no arranca si falta o es corto).
  Se usa el filtro estándar de Bearer token de Spring Security; no hay filtro JWT propio.
- Claims: `sub` = id de usuario, `rol`, `nombre`, `exp`. Expiración 8 h (una jornada), sin refresh token: al vencer, se vuelve a iniciar sesión.
- Un convertidor de JWT recarga el `Usuario` por id en cada petición: si está inactivo responde 401 y el rol aplicado es el vigente
  en base de datos (desactivar o cambiar de rol surte efecto de inmediato).
- Frontend guarda el token en `localStorage`; al cargar verifica la expiración y el interceptor de Axios limpia la sesión ante un 401.
- ADMIN inicial con migración Java de Flyway `V2__UsuarioAdminInicial` que lee `ADMIN_EMAIL` y `ADMIN_PASSWORD` del entorno
  (placeholders de Flyway) y guarda el hash BCrypt. Solo inserta si no existe un ADMIN.

## Alternativas consideradas
- jjwt + `OncePerRequestFilter` propio — más código de seguridad escrito a mano y más superficie de error.
- Refresh tokens — más complejidad (rotación, revocación) sin necesidad para el alcance.
- Cookie httpOnly — mitiga XSS pero obliga a manejar CSRF; `sessionStorage` — pierde la sesión entre pestañas.
- Admin con hash BCrypt fijo en una migración SQL — contraseña conocida en el repo. `ApplicationRunner` — válido, pero el plan pide "por migración".

## Consecuencias
- Sin secretos en el repo; `.env.example` documenta `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.
- Una consulta por PK extra por petición (costo despreciable).
- `localStorage` es accesible a scripts: prohibido `dangerouslySetInnerHTML` y HTML no escapado.
- Cambiar `ADMIN_PASSWORD` después de aplicada V2 no tiene efecto (la migración corre una sola vez).
- No hay endpoint de logout: el cliente descarta el token.
