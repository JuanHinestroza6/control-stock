---
name: project-fase0-pendientes
description: Hallazgos de la revisión de Fase 0 (2026-10-01) que conviene re-verificar cuando aparezca el código Java (admin inicial, zona horaria, compose)
metadata:
  type: project
---

Revisión parcial Fase 0 (2026-10-01, frontend + config, sin código Java aún): sin críticos.

Puntos a re-verificar en la siguiente revisión:
- ADR-003 dice que el ADMIN inicial se crea con migración Java Flyway `V2__UsuarioAdminInicial` leyendo **placeholders de Flyway**, pero application.yml solo define `app.admin.email/password` (sin `spring.flyway.placeholders.*`). Comprobar qué mecanismo usa V2 y que sea coherente.
- La JVM solo se fija en America/Bogota para pruebas (surefire argLine); en runtime falta `TimeZone.setDefault` o `connectionTimeZone` en la URL JDBC (regla 5).
- docker-compose publica MySQL en 0.0.0.0 y el healthcheck pasa la contraseña root por línea de comandos.
- Frontend guarda el JWT en localStorage (decisión ADR-003): vigilar que nunca aparezca `dangerouslySetInnerHTML` ni HTML sin escapar.

**Why:** son decisiones de Fase 0 que afectan seguridad y la regla de fechas; el código que las materializa aún no existía.
**How to apply:** en la próxima revisión del backend (auth/usuario, Flyway V2, clase main), revisar estos puntos primero.

Recurrente en el equipo (verificado bien en Fase 0, mantener): matriz de roles en `frontend/src/componentes/layout/modulos.ts` coincide con docs/dominio.md; las rutas usan `RutaProtegida roles=...` por módulo.
