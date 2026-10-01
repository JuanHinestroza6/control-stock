---
name: entorno-y-versiones
description: Restricciones del entorno de desarrollo (sin JDK ni Docker en Windows) y hallazgos de versiones (Spring Boot 3.x fuera de Initializr, MySQL 8.0 EOL) detectados al planear Fase 0 el 2026-10-01
metadata:
  type: project
---

Al 2026-10-01 la máquina de desarrollo principal (Windows 11) NO tiene JDK, Maven ni Docker; sí Node 24 / npm 11 y curl.
- start.spring.io ya no ofrece Spring Boot 3.x (solo 4.0.x, 4.1.x; default 4.1.1). Última 3.5 es 3.5.16 y su soporte OSS terminó el 2026-06-30. ADR-001 dice "Spring Boot 3.x" → se propuso ADR-002 para decidir 3.5.16 vs 4.1.x (pendiente de confirmación del usuario).
- MySQL 8.0 llegó a EOL el 2026-04-30 → se propuso imagen `mysql:8.4` (LTS hasta 2032) para compose y Testcontainers.
- jjwt última release 0.13.0; se recomendó en su lugar spring-boot-starter-oauth2-resource-server (Nimbus, HS256) para no escribir filtro JWT propio.
- create-vite trae React 19 por defecto: hay que fijar react@18.3.1. Tailwind actual es v4 (tokens con @theme en CSS, no tailwind.config).

**Why:** sin JDK no se puede compilar ni correr pruebas backend localmente; sin Docker no hay MySQL ni Testcontainers. Regla de oro 10 impide cerrar tareas sin pruebas.
**How to apply:** antes de planear tareas backend, verificar `java -version` / `docker --version`; proponer CI (GitHub Actions) como vía de verificación y `@Testcontainers(disabledWithoutDocker = true)`. Revisar si ADR-002 ya se decidió en docs/decisiones/. Ver [[decisiones-fase0]].
