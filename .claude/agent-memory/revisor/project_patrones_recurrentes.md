---
name: project-patrones-recurrentes
description: Errores que el equipo repite (collation ai_ci vs comparaciones Java, IllegalArgumentException como 500, catch genérico de DataIntegrityViolation, ADR que no coincide con el código, falta de pruebas de concurrencia)
metadata:
  type: project
---

Patrones vistos en la revisión final de Fase 1 (2026-10-01, rama fase-1-nucleo):

1. Las tablas usan `utf8mb4_0900_ai_ci` (sin mayúsculas ni tildes) pero el servicio compara/deduplica con `String`
   de Java que conserva tildes (ej. color "AZUL" vs "AZÚL" en VarianteService.generar) -> la BD rechaza con
   DataIntegrityViolation y se muestra un 409 engañoso.
2. Validaciones de entidad o utilidades lanzan `IllegalArgumentException`, que el manejador global no mapea -> 500
   (ej. GeneradorSku.abreviarColor con colores de letras no latinas que sí pasan el @Pattern \p{L}).
3. `catch (DataIntegrityViolationException)` traducido siempre al mismo mensaje ("código duplicado") aunque la causa
   pueda ser una FK u otra restricción.
4. ADR describe un comportamiento que el código no implementa (ADR-005: token sin iat debe rechazarse; el convertidor lo acepta).
5. Garantías de concurrencia (bloqueo pesimista del último ADMIN) sin prueba con dos hilos reales sobre MySQL.

**Why:** son fallos que pasan el build (294/294 en verde) y solo aparecen con datos reales o concurrencia.
**How to apply:** en cada revisión, cruzar comparaciones de unicidad con la collation, buscar `IllegalArgumentException`
alcanzable desde un endpoint, leer los ADR nuevos línea por línea contra el código y pedir prueba concurrente cuando
haya @Lock / @Version. Relacionado: [[project-fase0-pendientes]].
