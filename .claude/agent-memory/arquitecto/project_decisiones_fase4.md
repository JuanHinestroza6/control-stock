---
name: decisiones-fase4
description: APROBADAS Fase 4 datos semilla (2026-10-04) - módulo semilla, RelojSimulado @Primary en perfil seed, una transacción con flush/clear por día, alertas al final, salir al terminar; hallazgos no obvios del código
metadata:
  type: project
---

Plan de Fase 4 APROBADO el 2026-10-04 (fuente: docs/fases/fase-4-plan.md y ADR-008 una vez copiados). El usuario eligió tx única, alertas al final, salir al terminar y módulo `semilla`. Añadí al Paso 0: `fecha-fin` debe ser anterior a hoy y las reservas ACTIVAS se crean en las últimas 48 h de la historia (si no, el reloj monótono chocaría con fechas futuras):
- Paquete `co.livorno.controlstock.semilla` (no `comun/semilla` como dice el skill: comun no depende de módulos). ArchUnit: nadie depende de semilla.
- `RelojSimulado extends Clock` @Primary @Profile("seed"), monótono, vuelve al reloj del sistema al terminar. Todos los servicios (y el DateTimeProvider de auditoría) toman el Clock primario, así que fechar en el pasado no toca MovimientoService.
- Servicios leen `UsuarioActual` del SecurityContext (no tienen @PreAuthorize): el generador fija un `UsuarioAutenticado` por operación (try/finally).
- Una sola transacción con flush+clear por día simulado (todo o nada): una ExcepcionNegocio dentro de un @Transactional anidado marca rollback-only, así que el generador debe pre-validar stock.
- Alertas: `AlertaListener` condicional `app.alertas.por-evento` (false en seed) y evaluación de todas las variantes al final con el reloj real.
- Idempotencia: si hay ventas, no hace nada; si hay productos/movimientos sin ventas, aborta. Reset = `docker compose down -v` (ArchUnit prohíbe EntityManager.remove).
- Usuarios demo por repositorio + PasswordEncoder (UsuarioService.crear marca contraseña temporal), SEED_PASSWORD obligatoria.
- Hallazgo: todas las entidades usan IDENTITY → Hibernate no agrupa INSERT; `batch_size` solo ayuda en UPDATE.

**Why:** Fase 5–6 (ABC, ROP, Holt-Winters) y la sustentación dependen de estos datos.
**How to apply:** al planear Fase 5+, asumir que la historia existe vía servicios reales y alertas evaluadas al final; ver [[decisiones-fase3]], [[decisiones-fase2]].
