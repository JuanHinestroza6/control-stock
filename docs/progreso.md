# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 1
- Fase en curso: 1 — Núcleo terminada (rama `fase-1-nucleo` publicada); siguiente: Fase 2 (operación)
- Última actualización: 2026-10-02

## Hecho

- [x] Fase 0 — Setup: monorepo, infra, CI, auth JWT, frontend base; PPI REQ-06/REQ-12, convención RNF (PR #1–#3)
- [x] Fase 1 — plan (docs/fases/fase-1-plan.md), ADR-004 (catálogo) y ADR-005 (revocación y cambio de contraseña)
- [x] Fase 1 — backend: categorías (V3, 6 base), productos (V4), variantes (V5, SKU, matriz, cascada), usuarios (V6,
      reglas de último ADMIN, revocación por iat, cambio obligatorio, límite de intentos, V7 índice). 307/307 pruebas
- [x] Fase 1 — frontend: tabla/paginación/modal, categorías, productos, detalle con matriz y generador, usuarios, /cuenta.
      215/215 pruebas, lint y build OK
- [x] Fase 1 — revisión final: sin críticos; 2 importantes y 6 menores corregidos (commit 41a9424)
- [x] Fase 1 — PPI: ficha REQ-11 (Administrador, NEC-01, CU-11, prioridad Alta), CA de REQ-06/REQ-12 cumplidos,
      diagrama de clases del catálogo
- [x] Fase 1 — T3: prueba manual con el backend real completa, sin errores (2026-10-02)

## En curso

- [ ] PR de `fase-1-nucleo` hacia main (lo abre el usuario cuando lo decida)

## Próximos pasos

1. PR y merge de `fase-1-nucleo`; luego `git switch main && git pull`.
2. Revisar los diagramas con Alt+D (PlantUML no instalado).
3. Iniciar `/fase 2`: operación (entradas, ajustes, ventas, devoluciones, kardex).

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).

## Prueba manual T3 (PowerShell 5, desde la raíz del repo) — completada 2026-10-02

1. `Copy-Item .env.example .env` (solo la primera vez) y completa `.env`:
   - `JWT_SECRET`: genera uno con
     `$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`
   - `ADMIN_EMAIL` y `ADMIN_PASSWORD` (12+ caracteres, no puede empezar por "cambiar"): son las credenciales del ADMIN.
     Solo se usan la primera vez que migra una base vacía; cambiarlas después no tiene efecto.
   - `DB_PASSWORD` y `DB_ROOT_PASSWORD`; deja `DB_URL=jdbc:mysql://localhost:3306/control_stock`.
2. MySQL: `docker compose up -d mysql` y espera a que `docker compose ps` muestre `healthy`.
3. Backend (otra terminal): `cd backend` y `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`.
   No hace falta cargar `.env` a mano: Spring lo lee desde `..\.env`. Comprueba con
   `Invoke-RestMethod http://localhost:8080/actuator/health` (debe decir `UP`).
4. Frontend (otra terminal): `cd frontend`, `npm install` (primera vez) y `npm run dev`. Abre http://localhost:5173
5. Verificación:
   - [x] Entrar con `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
   - [x] Categorías: aparecen las 6 base; crear, editar y eliminar una sin productos; eliminar una con productos → aviso 409.
   - [x] Crear producto (p. ej. LIV001), editarlo; un precio de venta menor que el costo muestra aviso pero guarda.
   - [x] Detalle: generar tallas S, M, L × colores NEGRO, AZUL → 6 variantes; matriz con 6 celdas en 0 (AGOTADO);
         generar de nuevo → todas omitidas.
   - [x] Desactivar el producto → sus variantes quedan inactivas; reactivarlo → las variantes siguen inactivas.
   - [x] Usuarios: crear un VENDEDOR con contraseña temporal; en otra ventana privada entrar como él → lo lleva a
         /cuenta; tras cambiarla entra, ve productos sin costo y sin botones de edición, y no ve "Usuarios".
   - [x] Como ADMIN, restablecer la contraseña del VENDEDOR → su sesión abierta cae al login.
   - [x] En la tabla de usuarios, tu propia fila no ofrece desactivar ni cambiar el rol.


## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  desde una terminal propia (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT por la inspección TLS del equipo).
- Slices `@WebMvcTest`: el convertidor JWT ya no es bean `Converter`, no hace falta mockear `UsuarioRepository`.
- El limitador de login es en memoria y usa `getRemoteAddr()`; detrás de un proxy inverso habrá que revisarlo.
- CI fijado en `ubuntu-24.04` (ubuntu-latest pasa a Ubuntu 26 el 2026-10-19); migrar a propósito cuando convenga.
