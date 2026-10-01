# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 1
- Fase en curso: 0 — Setup (frontend terminado; backend bloqueado por falta de JDK, ya en instalación)
- Última actualización: 2026-10-01

## Hecho

- [x] Configuración de agentes y skills de Claude Code
- [x] Modelo de dominio y plan definidos (docs/dominio.md, docs/plan.md)
- [x] Plan de Fase 0 aprobado: docs/fases/fase-0-plan.md
- [x] ADR-002 (Spring Boot 4.1.1 + MySQL 8.4) y ADR-003 (autenticación JWT); `actualizadoEn` en dominio.md
- [x] Infra: docker-compose.yml (mysql:8.4, solo 127.0.0.1), .env.example, .gitattributes, CI GitHub Actions (sin ejecutar aún)
- [x] Frontend base: Vite + React 18.3.1 + Tailwind 4 (tokens PPI), login, sesión JWT en localStorage, layout con
      sidebar por rol, rutas protegidas. 89/89 pruebas, lint (oxlint) y build en verde.

## En curso

- [ ] Fase 0 backend — tareas 4 a 9 del plan (excepciones/ProblemDetail, Clock + EntidadAuditable, V1 usuario + V2 admin,
      seguridad JWT, AuthService/AuthController, pruebas)

## Próximos pasos

1. Verificar `java -version` (21) y `docker --version`.
2. Compilar el esqueleto: `cd backend && ./mvnw verify`. Si compila, commit pendiente:
   `chore(backend): esqueleto Spring Boot 4.1.1 con Maven Wrapper`.
3. Continuar `/fase 0` desde la tarea 4 (backend-dev → tester tras cada tarea).
4. Al terminar: revisor final, documentador-ppi, cierre de la fase.

## Bloqueos y decisiones pendientes

- (ninguno, una vez instalado el JDK)

## Notas para la próxima sesión

- `backend/` está sin commit. `backend/mvnw` ya está en el índice con modo 100755; si se saca del índice, repetir
  `git update-index --chmod=+x backend/mvnw` antes de commitear.
- El pom de Initializr traía `4.1.1.RELEASE` (no existe en Maven Central); se corrigió a `4.1.1`.
- Contrato que el frontend ya consume: errores de validación `errores: [{campo, mensaje}]` en el ProblemDetail;
  login/yo según docs/fases/fase-0-plan.md.
- V2 del admin con placeholders de Flyway (`${ADMIN_EMAIL:}`/`${ADMIN_PASSWORD:}`), validados solo si va a insertar.
- Hallazgo del revisor ya descartado: la zona America/Bogota sí se fija en `ControlStockApplication.main`.
