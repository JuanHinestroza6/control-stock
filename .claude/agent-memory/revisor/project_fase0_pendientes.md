---
name: project-fase0-pendientes
description: Estado de los hallazgos de seguridad/config de Fase 0 (auth JWT, admin inicial, .env.example) y qué vigilar en revisiones siguientes
metadata:
  type: project
---

Revisión final backend Fase 0 (2026-10-01): sin críticos. Resueltos respecto a la revisión parcial: V2 usa
`spring.flyway.placeholders.admin-*`, TZ fijada en `ControlStockApplication.main` + `hibernate.jdbc.time_zone`,
compose publica MySQL solo en 127.0.0.1.

Abiertos al cierre de Fase 0 (re-verificar):
- `.env.example` trae JWT_SECRET de ejemplo de >=32 bytes: si se copia sin cambiar, la app arranca con secreto público.
- Login sin limitación de intentos (no documentado en ADR-003).
- `toLowerCase()` sin `Locale.ROOT` en normalización de email (AuthService, Usuario, V2).
- Login sin `@PreAuthorize("permitAll()")` explícito (regla 8 dice "cada endpoint").
- Falta prueba de que solo /actuator/health está expuesto/público.

**Why:** son puntos de seguridad que no rompen el build y tienden a quedarse olvidados.
**How to apply:** en la próxima revisión que toque auth/usuario o config, comprobar si se corrigieron.

Recurrente en el equipo (verificado bien, mantener): matriz de roles del frontend coincide con docs/dominio.md;
pruebas de integración con Testcontainers sí corren (revisar surefire-reports `skipped="0"` antes de fiarse del conteo).
