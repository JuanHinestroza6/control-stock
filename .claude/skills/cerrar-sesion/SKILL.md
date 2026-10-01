---
name: cerrar-sesion
description: Cierra la sesión de trabajo actualizando docs/progreso.md y proponiendo commits, para retomar mañana sin perder contexto.
disable-model-invocation: true
allowed-tools:
  - Bash(git status *)
  - Bash(git diff *)
  - Bash(git log *)
---

# Cerrar sesión

1. Revisa `git status`, `git diff --stat` y `git log --oneline -10`.
2. Actualiza `docs/progreso.md`:
   - Estado actual (semana, fase, fecha de hoy).
   - Mueve a "Hecho" lo terminado en esta sesión (con pruebas en verde).
   - "En curso": lo empezado y en qué punto exacto quedó (archivo, método, qué falta).
   - "Próximos pasos": 1 a 3 acciones concretas para la siguiente sesión.
   - "Bloqueos y decisiones pendientes".
   - Mantén el archivo por debajo de ~60 líneas: resume lo antiguo en una línea por fase terminada.
3. Si hubo decisiones de diseño nuevas sin ADR, créalo en `docs/decisiones/`.
4. Propón los commits agrupados por tema (no los ejecutes hasta que yo confirme) y recuérdame si hay cambios sin probar.
