# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 1
- Fase en curso: 0 — Setup (código completo, revisado y con CI en verde; falta merge del PR #1 y documentación PPI)
- Última actualización: 2026-10-01

## Hecho

- [x] Configuración de agentes y skills; dominio y plan (docs/dominio.md, docs/plan.md)
- [x] Plan de Fase 0 aprobado: docs/fases/fase-0-plan.md. ADR-002 (Boot 4.1.1 + MySQL 8.4) y ADR-003 (autenticación)
- [x] Infra: docker-compose.yml (mysql:8.4, solo 127.0.0.1), .env.example, .gitattributes, CI GitHub Actions
- [x] Frontend base: login, sesión JWT, layout con sidebar por rol, rutas protegidas. 89/89 pruebas, lint y build OK
- [x] Backend: comun (ProblemDetail, auditoría con Clock), usuario (V1 + V2 ADMIN desde entorno), auth (JWT HS256 con
      issuer, convertidor que recarga usuario, CORS, login/yo, limitador de intentos 429). 78/78 pruebas con Testcontainers
- [x] Revisión final de fase 0: sin críticos; importantes y menores corregidos
- [x] Rama `fase-0-setup` publicada y PR #1 hacia main abierto; CI en verde (backend y frontend) en el primer run

## En curso

- [ ] PR #1 (https://github.com/JuanHinestroza6/control-stock/pull/1): pendiente de revisión del equipo y merge

## Próximos pasos

1. Merge del PR #1; luego `git switch main && git pull` (si se usa squash, alinear el main local con origin/main).
2. Agente `documentador-ppi`: fichas REQ-06/REQ-12 y diagramas de la fase 0 (clases auth/usuario, secuencia de login).
3. Iniciar `/fase 1`: CRUD de categorías, productos y variantes; matriz talla × color; CRUD de usuarios.

## Bloqueos y decisiones pendientes

- (ninguno)

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  desde una terminal propia (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT por la inspección TLS del equipo).
- Slices `@WebMvcTest`: el convertidor JWT ya no es bean `Converter`, no hace falta mockear `UsuarioRepository`.
- El limitador de login es en memoria y usa `getRemoteAddr()`; detrás de un proxy inverso habrá que revisarlo.
- Rama local `appmod/java-upgrade-20261001225110` creada por una extensión del IDE (ya deshabilitada): borrar si no se usa.
- El main local tiene 12 commits sin push (los mismos de `fase-0-setup`); no hacer push directo a main, entrar por el PR.
- CI fijado en `ubuntu-24.04` (ubuntu-latest pasa a Ubuntu 26 el 2026-10-19); migrar a propósito cuando convenga.
