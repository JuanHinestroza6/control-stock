---
name: entorno-y-versiones
description: Entorno de desarrollo (JDK 21 y Docker ya instalados desde fin de Fase 0), Maven offline en sandbox, versiones decididas (Boot 4.1.1, MySQL 8.4) y particularidades de Boot 4
metadata:
  type: project
---

Al planear Fase 1 (2026-10-01): la máquina Windows 11 YA tiene JDK 21.0.12 y Docker 29.8.1 (en Fase 0 no los tenía), Node 24.
- ADR-002 aceptada: Spring Boot 4.1.1 + MySQL 8.4 (`mysql:8.4`). Boot 4: @MockitoBean, Jackson 3 (tools.jackson), starters modulares, Testcontainers 2.
- Maven en el sandbox de Claude solo funciona offline (`./mvnw -o`): no se pueden agregar dependencias (ArchUnit, MapStruct, etc.) sin que el usuario las descargue desde su terminal → marcar [DECIDIR] si alguna es imprescindible.
- React fijado 18.3.1, React Router 7, Tailwind v4 (@theme), oxlint, zod 4, react-hook-form. Sin Recharts ni MSW aún.

**Why:** determina qué se puede verificar localmente y qué dependencias son viables.
**How to apply:** antes de planear, verificar `java -version` / `docker --version`; no proponer dependencias nuevas sin [DECIDIR]. Ver [[decisiones-fase0]], [[decisiones-fase1]].
