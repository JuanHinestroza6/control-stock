---
name: project-patrones-recurrentes
description: Errores que el equipo repite (collation ai_ci vs Java, IllegalArgumentException=500, catch DIVE genérico, ADR/contrato que no coincide con el código, pruebas de concurrencia, reglas ArchUnit vacuas, cast en JPQL que anula índices, @Size antes de strip)
metadata:
  type: project
---

Patrones vistos en la revisión final de Fase 1 (2026-10-01, rama fase-1-nucleo):

1. Las tablas usan `utf8mb4_0900_ai_ci` pero el servicio compara/deduplica con `String` de Java (tildes) -> la BD
   rechaza con DataIntegrityViolation y se muestra un 409 engañoso.
2. Validaciones de entidad lanzan `IllegalArgumentException`/`IllegalStateException`, que el manejador global no mapea
   -> 500. En Fase 2 el equipo ya las deja inalcanzables validando antes en el servicio (verificar que siga así).
3. `catch (DataIntegrityViolationException)` traducido siempre al mismo mensaje aunque la causa sea otra restricción.
4. ADR / plan / contrato describe algo que el código no hace. Fase 1: token sin iat. Fase 2 (2026-10-02): ADR-006 dice
   "varias líneas ordenadas por id" pero las entradas no ordenan; el detail del 409 difiere del contrato del plan.
5. Garantías de concurrencia sin prueba con dos hilos reales. En Fase 2 SÍ las hay (venta del último ítem, 10 hilos,
   dos devoluciones simultáneas); falta solo anular vs devolver.

Nuevos en Fase 2 (rama fase-2-operacion, 2026-10-02):
6. La regla ArchUnit de la regla de oro 1 usa `callMethod(Variante.class, "aplicarMovimiento", int, int)`: queda vacua si
   se renombra, no cubre sobrecargas, otros métodos de Variante que escriban stock, referencias a método ni el paquete
   `db.migration`. Pedir regla sobre escritura de campos + aserción positiva.
7. `cast(m.tipo as string) = :tipo` en JPQL (truco para enum nulo) impide usar el índice (tipo, fecha).
8. `@Size(min=5)` en el DTO y `.strip()` después en el servicio: pasa "a    " y se guarda "a".

**Why:** pasan el build (430/430 y 304/304 en F2) y solo aparecen con datos reales, refactors o concurrencia.
**How to apply:** en cada revisión, cruzar unicidad con la collation, buscar excepciones de Java alcanzables desde un
endpoint, leer ADR/plan línea por línea contra el código, pedir prueba concurrente con @Lock/@Version y revisar que las
reglas ArchUnit no puedan quedar vacuas. Relacionado: [[project-fase0-pendientes]].

Fase 3 (rama fase-3-apartados, 2026-10-02), sin críticos:
- Se repitió el 8: `CrearReservaRequest` con `@Size(min=2/3)` y `normalizar()` (strip) en el servicio. El 6 empeoró: la
  regla "@Modifying prohibido" se estrechó a MovimientoRepository para permitir AlertaRepository; nada impide un
  UPDATE masivo de `variante.stock_*` en otro repositorio.
9. Listeners AFTER_COMMIT + REQUIRES_NEW que leen el agregado sin bloquearlo (AlertaService.evaluar): dos evaluaciones
   concurrentes de la misma variante pueden dejar una alerta obsoleta o AGOTADO y STOCK_BAJO activas a la vez. Pedir
   bloqueo de la fila de la variante al evaluar y prueba con dos hilos.
10. Datos personales (Ley 1581) copiados a tablas inmutables (motivo de cancelación en la observación del kardex) y sin
    plan de supresión/anonimización de `reserva.cliente_*` (columnas updatable=false).
