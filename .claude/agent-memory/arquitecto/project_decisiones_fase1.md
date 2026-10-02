---
name: decisiones-fase1
description: Decisiones APROBADAS de Fase 1 (catálogo, SKU, cascada al desactivar, revocación de tokens, contraseña temporal obligatoria) del 2026-10-01 y hallazgos técnicos no obvios
metadata:
  type: project
---

Plan de Fase 1 aprobado 2026-10-01 (fuente: docs/fases/fase-1-plan.md, ADR-004, ADR-005, dominio.md secciones Catálogo y Usuarios):
- Migraciones V3 categoria (+6 categorías base del skill datos-semilla), V4 producto, V5 variante, V6 columnas usuario (password_cambiado_en, debe_cambiar_password).
- El usuario CAMBIÓ mi propuesta: desactivar producto desactiva en cascada sus variantes; reactivar NO las reactiva. Reservas activas bloquean desactivar (409), en Fase 1 vía stockReservado > 0; reforzar con tabla reserva en Fase 3.
- El usuario AGREGÓ: revocación de tokens (iat < passwordCambiadoEn → 401, truncado a segundos) y contraseña temporal obligatoria (debeCambiarPassword → solo GET /auth/yo y PUT /auth/yo/password; resto 403 con codigo CAMBIO_PASSWORD_REQUERIDO). Prefiere más seguridad aunque sume tareas.
- Hallazgos: sort inválido → PropertyReferenceException → 500 (hay que mapear a 400); Page con VIA_DTO; cambio de propia contraseña 422 (no 401, dispararía logout del frontend); `Variante.aplicar()` no puede ser paquete-privado (decidir en Fase 2).
- Mi rol (arquitecto) no escribe en el repo: los textos del Paso 0 quedaron en el scratchpad para que otro agente los copie.

**Why:** Fase 2+ (MovimientoService, reservas, seed) dependen de este modelo; el usuario tiende a pedir el rigor de seguridad extra.
**How to apply:** al planear Fase 2/3 partir de esto; el generador semilla reutiliza categorías de V3. Ver [[decisiones-fase0]], [[entorno-y-versiones]].
