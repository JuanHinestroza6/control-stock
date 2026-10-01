# Plan APROBADO — Fase 0 (Setup)

## Decisiones confirmadas por el usuario
- Spring Boot 4.1.1 (Spring Security 7, Jackson 3, Testcontainers 2, starters separados p. ej. spring-boot-starter-flyway). Los skills están escritos para Boot 3: usar APIs de Boot 4 (p. ej. @MockitoBean, no @MockBean).
- MySQL 8.4 (docker compose y Testcontainers: `mysql:8.4`).
- JWT con spring-boot-starter-oauth2-resource-server, HS256. JWT_SECRET >= 32 bytes o la app no arranca. Claims: sub=id usuario, rol, nombre, exp. Convertidor JWT carga el usuario por id en cada request: inactivo → 401; aplica rol vigente (ROLE_x).
- Token 8 h, sin refresh. Frontend guarda la sesión en localStorage, valida expiración al cargar; interceptor 401 limpia sesión y redirige a /login.
- ADMIN inicial: migración Flyway Java `V2__UsuarioAdminInicial.java` lee ADMIN_EMAIL / ADMIN_PASSWORD del entorno (o propiedades), guarda BCrypt, inserta solo si no existe ningún ADMIN; falla si faltan. application-test.yml trae credenciales de prueba.
- Auditoría: `EntidadAuditable` (@MappedSuperclass) con creadoEn + actualizadoEn vía @EnableJpaAuditing y DateTimeProvider basado en bean `Clock` (America/Bogota). Movimiento NO la hereda. Actualizar docs/dominio.md.
- Zona: JVM America/Bogota (en main y en -Duser.timezone de Surefire), columnas DATETIME(6), contenedor con TZ.
- CORS: proxy de Vite /api → localhost:8080; además bean CORS con orígenes configurables (default http://localhost:5173). CSRF off (stateless). Actuator solo health.
- .env en la raíz; Spring lo importa con `spring.config.import: optional:file:../.env[.properties]`.
- CI GitHub Actions: backend `./mvnw verify` (Docker del runner → Testcontainers corre), frontend `npm ci && npm run lint && npm run test -- --run && npm run build`.
- ADR-002 (Boot 4.1.1 + MySQL 8.4, corrige ADR-001) y ADR-003 (autenticación).

## Estructura
Raíz: docker-compose.yml (mysql:8.4, healthcheck, volumen nombrado, utf8mb4, TZ), .env.example, .gitattributes (mvnw LF, *.cmd CRLF), .gitignore (+ `.env.*` excepto `!.env.example`, frontend/coverage/, *.tsbuildinfo, HELP.md).

backend/ paquete co.livorno.controlstock:
- comun/excepcion: ExcepcionNegocio, RecursoNoEncontradoException, ConflictoException, ManejadorGlobalExcepciones
- comun/config: ConfiguracionTiempo (Clock + DateTimeProvider)
- comun/modelo: EntidadAuditable
- comun/seguridad: UsuarioAutenticado, UsuarioActual (comun no depende de otros módulos)
- usuario/modelo: Usuario, Rol ; usuario/repositorio: UsuarioRepository
- auth/config: ConfiguracionSeguridad, PropiedadesJwt
- auth/seguridad: convertidor JWT, manejadores 401/403 con ProblemDetail
- auth/servicio: AuthService, TokenService ; auth/web: AuthController ; auth/dto
- resources: application.yml, application-dev.yml, application-test.yml

frontend/src:
- api/cliente.ts, api/auth.ts ; tipos/auth.ts, tipos/problema.ts
- lib/auth.ts (sesión, tieneRol), lib/errores.ts (mensajeError), lib/formato.ts
- hooks/useLogin, hooks/useSesion (contexto)
- componentes/ui: Boton, Campo, Tarjeta
- componentes/layout: LayoutPrincipal, Sidebar (menú filtrado por rol según matriz de docs/dominio.md), Topbar (buscador deshabilitado, campana 0, usuario+rol, salir), RutaProtegida
- paginas/auth/LoginPage, paginas/inicio/InicioPage, placeholders "Próximamente" por módulo
- Sidebar como drawer en móvil
- React 18.3.1 fijado (create-vite trae 19), Tailwind 4 con tokens @theme del PPI en index.css, fuentes Montserrat/Inter vía @fontsource, React Router, TanStack Query v5, Axios, react-hook-form + zod, Vitest + Testing Library. Sin Recharts aún.

## Migraciones
- V1__crear_tabla_usuario.sql: usuario(id BIGINT AI, nombre VARCHAR(100), email VARCHAR(150) UNIQUE (collation utf8mb4_0900_ai_ci), password_hash VARCHAR(100), rol VARCHAR(20) CHECK IN (ADMIN, INVENTARIO, VENDEDOR, CONTABILIDAD), activo BOOLEAN DEFAULT TRUE, creado_en DATETIME(6), actualizado_en DATETIME(6))
- V2__UsuarioAdminInicial.java

## Contrato de endpoints
- POST /api/auth/login (público): LoginRequest{email, password} → 200 LoginResponse{token, tipo:"Bearer", expiraEn, usuario: UsuarioSesionResponse{id, nombre, email, rol}}. 400 con errores[] por validación; 401 "Credenciales inválidas" (también si inactivo, sin distinguir). Email trim + lowercase.
- GET /api/auth/yo (isAuthenticated()) → UsuarioSesionResponse
- GET /actuator/health público sin detalles
- Sin logout.
- V2 del admin (hallazgo del revisor; alinear con ADR-003): pasar ADMIN_EMAIL/ADMIN_PASSWORD como placeholders de Flyway
  (`spring.flyway.placeholders.admin-email: ${ADMIN_EMAIL:}` y `admin-password: ${ADMIN_PASSWORD:}`, default vacío) y leerlos con
  `context.getConfiguration().getPlaceholders()`. V2 valida que no estén vacíos SOLO si va a insertar (no existe ningún ADMIN);
  así la app no exige la contraseña admin en cada arranque. Quitar `app.admin.*` de application.yml si queda sin uso.
- Forma FIJA de errores de validación (el frontend ya la consume): ProblemDetail con propiedad `errores: [{campo: string, mensaje: string}]`.
- Prefijos de configuración del backend: app.jwt.secret, app.jwt.expiracion (default 8h), app.cors.origenes, app.admin.email / app.admin.password.
- Frontend: api/cliente.ts emite evento `control-stock:sesion-expirada` ante 401 (salvo en /auth/login); suscribirse con `alExpirarSesion(cb)`.
- Errores ProblemDetail: token vencido/firma inválida → 401; AccessDeniedException → 403 (manejar explícitamente); OptimisticLockingFailure / DataIntegrityViolation → 409; ExcepcionNegocio → 422; 500 sin stack trace.

## Pruebas
- Sin Docker: TokenService con Clock fijo (emite, rechaza vencido y firma ajena); manejador con controller de prueba (400 errores[], 404, 409, 422, 403, 500); AuthController (ok, 401, 400); seguridad (sin token 401, VENDEDOR → endpoint solo ADMIN 403).
- Con Docker: @Testcontainers(disabledWithoutDocker = true) + @ServiceConnection mysql:8.4: Flyway migra + ddl-auto=validate; admin existe con BCrypt; login → /api/auth/yo 200; inactivo → 401.
- Frontend Vitest: LoginPage (validación, error backend, redirect), RutaProtegida (sin sesión → login; rol no permitido → denegado), Sidebar filtra por rol, mensajeError, interceptor 401 limpia sesión.

## Entorno (2026-10-01)
- Windows 11, Git Bash disponible. Node 24 instalado. JDK 21 y Docker se instalaron al cierre de la sesión 1; verificar con `java -version` y `docker --version`.
