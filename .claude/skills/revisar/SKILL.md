---
name: revisar
description: Ejecuta pruebas de backend y frontend y una revisión de código de los cambios pendientes antes de hacer commit.
disable-model-invocation: true
allowed-tools:
  - Bash(./mvnw *)
  - Bash(mvnw.cmd *)
  - Bash(npm run *)
  - Bash(git diff *)
  - Bash(git status *)
---

# Revisión previa a commit

1. `git status` para ver qué cambió (backend, frontend o ambos).
2. Si hay cambios en `backend/`: delega en el agente `tester` la ejecución de `./mvnw verify` (o `mvnw.cmd verify` en Windows) y que reporte solo fallos.
3. Si hay cambios en `frontend/`: ejecuta `npm run lint`, `npm run test -- --run` y `npm run build` dentro de `frontend/`.
4. Delega en el agente `revisor` la revisión del diff.
5. Entrega un veredicto:
   - ✅ Listo para commit — y propone el mensaje en Conventional Commits en español.
   - ❌ No listo — lista de críticos y pruebas fallidas, en orden de prioridad.
