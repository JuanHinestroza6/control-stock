# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 1
- Fase en curso: 0 — Setup terminada (PR #1 fusionado); documentación PPI en rama `docs/ppi-fase-0`, PR pendiente
- Última actualización: 2026-10-01

## Hecho

- [x] Configuración de agentes y skills; dominio y plan (docs/dominio.md, docs/plan.md)
- [x] Plan de Fase 0 aprobado: docs/fases/fase-0-plan.md. ADR-002 (Boot 4.1.1 + MySQL 8.4) y ADR-003 (autenticación)
- [x] Infra: docker-compose.yml (mysql:8.4, solo 127.0.0.1), .env.example, .gitattributes, CI GitHub Actions
- [x] Frontend base: login, sesión JWT, layout con sidebar por rol, rutas protegidas. 89/89 pruebas, lint y build OK
- [x] Backend: comun (ProblemDetail, auditoría con Clock), usuario (V1 + V2 ADMIN desde entorno), auth (JWT HS256 con
      issuer, convertidor que recarga usuario, CORS, login/yo, limitador de intentos 429). 78/78 pruebas con Testcontainers
- [x] Revisión final de fase 0: sin críticos; importantes y menores corregidos
- [x] PR #1 (fase-0-setup) fusionado en main con CI en verde
- [x] Documentación PPI fase 0 (rama `docs/ppi-fase-0`): fichas REQ-06 (CU-02) y REQ-12 (CU-12) con códigos del Word,
      CU-13 "Iniciar sesión" nuevo ligado a REQ-02 (no funcional); diagramas de clases, secuencia del login y casos de uso

## En curso

- [ ] Rama `docs/ppi-fase-0` sin push: el usuario abre el PR. Diagramas sin renderizar (PlantUML no instalado)

## Próximos pasos

1. Revisar los 3 diagramas con Alt+D en VS Code; push de `docs/ppi-fase-0` y PR (lo hace el usuario).
2. Resolver la numeración REQ-02: en `dominio.md` es "Registrar operaciones de stock"; en el Word es el no funcional
   "Seguridad y control". Ajustar la trazabilidad de `dominio.md`.
3. Iniciar `/fase 1`: CRUD de categorías, productos y variantes; matriz talla × color; CRUD de usuarios.

## Bloqueos y decisiones pendientes

- (ninguno)

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  desde una terminal propia (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT por la inspección TLS del equipo).
- Slices `@WebMvcTest`: el convertidor JWT ya no es bean `Converter`, no hace falta mockear `UsuarioRepository`.
- El limitador de login es en memoria y usa `getRemoteAddr()`; detrás de un proxy inverso habrá que revisarlo.
- Rama local `appmod/java-upgrade-20261001225110` creada por una extensión del IDE (ya deshabilitada): borrar si no se usa.
- CI fijado en `ubuntu-24.04` (ubuntu-latest pasa a Ubuntu 26 el 2026-10-19); migrar a propósito cuando convenga.
