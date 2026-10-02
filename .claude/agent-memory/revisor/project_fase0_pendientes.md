---
name: project-fase0-pendientes
description: Estado de los hallazgos de seguridad/config de Fase 0 (auth JWT, admin inicial, .env.example); casi todos cerrados en la revisión de Fase 1
metadata:
  type: project
---

Revisión final backend Fase 0 (2026-10-01): sin críticos. Re-verificado en la revisión final de Fase 1 (2026-10-01):
- CERRADO: `.env.example` ya trae `JWT_SECRET=` vacío.
- CERRADO: login con limitador de intentos (429, en memoria, usa getRemoteAddr()).
- CERRADO: `Locale.ROOT` en normalización de email (Usuario, UsuarioService).
- CERRADO: login con `@PreAuthorize("permitAll()")`.
- Sin verificar: prueba de que solo /actuator/health está expuesto/público.

Nota: V2 es migración Java (`backend/src/main/java/db/migration/V2__UsuarioAdminInicial.java`), no SQL; no la busques en resources.

**Why:** son puntos de seguridad que no rompen el build y tienden a quedarse olvidados.
**How to apply:** en revisiones que toquen auth/config, solo queda pendiente la prueba de actuator.

Recurrente en el equipo (verificado bien, mantener): matriz de roles del frontend coincide con docs/dominio.md;
pruebas de integración con Testcontainers sí corren (revisar surefire-reports `skipped="0"` antes de fiarse del conteo).
