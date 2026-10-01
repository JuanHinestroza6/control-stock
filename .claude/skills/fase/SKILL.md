---
name: fase
description: Arranca o continúa una fase del plan de desarrollo coordinando a los agentes arquitecto, backend-dev, frontend-dev, analitica, tester y revisor.
disable-model-invocation: true
argument-hint: <número de fase 0-7>
---

# Ejecutar fase $ARGUMENTS

1. **Contexto**: lee `docs/plan.md` (sección de la fase $ARGUMENTS) y `docs/progreso.md`. Si la fase ya está empezada, continúa desde lo pendiente.
2. **Plan**: delega en el agente `arquitecto` el diseño de la fase completa. Muéstrame el plan resumido y ESPERA mi aprobación antes de escribir código.
3. **Tareas**: con el plan aprobado, crea una lista de tareas pequeñas (cada una verificable en menos de ~1 hora).
4. **Implementación**, tarea por tarea:
   - Backend → agente `backend-dev`. Algoritmos → agente `analitica`. Interfaz → agente `frontend-dev`.
   - Tareas independientes de backend y frontend pueden ir en paralelo; las que dependen de un endpoint esperan a que exista.
   - Tras cada tarea, agente `tester` para pruebas faltantes y ejecución. No avances con pruebas en rojo.
5. **Revisión**: al terminar la fase, agente `revisor`. Corrige los críticos antes de cerrar.
6. **Documentación**: agente `documentador-ppi` para las fichas y diagramas que la fase afecte.
7. **Cierre**: actualiza `docs/progreso.md` y propone los commits (no hagas push).

Mantén mi contexto limpio: de cada agente tráeme solo el resumen, no el detalle de archivos.
Si en cualquier punto surge una decisión de diseño no cubierta por el plan, pregúntame.
