---
name: decisiones-fase0
description: Decisiones de diseño propuestas en el plan de Fase 0 (auth JWT, admin inicial, auditoría, zona horaria, CORS, token en frontend, ubicación de paquetes de seguridad) y cuáles quedaron como [DECIDIR]
metadata:
  type: project
---

Plan de Fase 0 entregado el 2026-10-01. Propuestas (verificar en docs/decisiones/ si se aprobaron o cambiaron):
- Seguridad: oauth2-resource-server con HS256 (JWT_SECRET >= 32 bytes, fallo al arrancar si no), access token 8 h, sin refresh token. Converter de JWT carga Usuario por id para verificar `activo` y rol vigente. Config de seguridad en módulo `auth`; `comun.seguridad` solo tiene el principal `UsuarioAutenticado` y `UsuarioActual` (comun no depende de módulos).
- AccessDeniedException de @PreAuthorize debe manejarse en ManejadorGlobalExcepciones (si no, cae en el 500 genérico). 401/403 de filtro vía AuthenticationEntryPoint/AccessDeniedHandler que escriben ProblemDetail.
- Admin inicial [DECIDIR]: migración Java Flyway V2 que lee placeholders ADMIN_EMAIL/ADMIN_PASSWORD desde env y hashea BCrypt (alternativa: ApplicationRunner).
- Auditoría [DECIDIR]: @MappedSuperclass EntidadAuditable (creadoEn, actualizadoEn) con @EnableJpaAuditing + DateTimeProvider basado en bean Clock America/Bogota. Implica agregar actualizadoEn a dominio.md. Movimiento NO hereda (inmutable, usa `fecha`).
- Zona: JVM default America/Bogota (main + surefire argLine), columnas DATETIME(6), Colombia sin DST.
- CORS: proxy de Vite en dev (/api → 8080) + bean CORS con origenes por propiedad.
- Frontend: token en localStorage [DECIDIR], React 18.3.1 fijado, Tailwind v4 con @theme, fuentes vía @fontsource. Contrato login fijado en el plan para que frontend avance en paralelo.
- .env en raíz importado por Spring con `spring.config.import=optional:file:../.env[.properties]`.
- ADRs propuestos: ADR-002 versión Spring Boot, ADR-003 autenticación.

**Why:** coherencia entre fases; Fase 1 (CRUD usuarios) depende de estas decisiones.
**How to apply:** al planear Fase 1+, partir de estas decisiones salvo que un ADR posterior las cambie. Ver [[entorno-y-versiones]].
