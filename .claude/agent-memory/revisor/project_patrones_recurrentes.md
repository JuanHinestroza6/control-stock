---
name: project-patrones-recurrentes
description: Errores que el equipo repite (versión optimista tomada de la prop refrescada, tope limit(N) por id, collation, IAE=500, ADR≠código, concurrencia sin prueba, ArchUnit vacua, cast en JPQL, @Size antes de strip, listener sin bloqueo, locks implícitos de InnoDB, tipos TS no nulos, ventanas con dos relojes, IT sin cubrir ramas del CASE)
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
Fase 4 (rama fase-4-datos-semilla, 2026-10-04), sin críticos:
11. Guardas por nombre de perfil que no existe en el repo (`acceptsProfiles("prod")` sin application-prod.yml): protección
    vacua, misma familia que el 6. Pedir lista de permitidos (dev|test) en vez de lista de prohibidos.
12. Estado global mutable (`Clock` @Primary ajustable) cambiado dentro de un CommandLineRunner: Tomcat ya escucha
    durante la carga. Pedir `spring.main.web-application-type: none` en perfiles de tarea batch.
13. Records `@ConfigurationProperties` con un secreto (`password`) sin `toString()` propio. Sin ArchUnit que obligue
    `@Profile("seed")` en cada bean del módulo semilla.

10. Datos personales (Ley 1581) copiados a tablas inmutables (motivo de cancelación en la observación del kardex) y sin
    plan de supresión/anonimización de `reserva.cliente_*` (columnas updatable=false).

Fase 5 (rama fase-5-analitica, 2026-10-14), sin críticos; 847 pruebas, 0 skipped:
14. Bloqueos implícitos de InnoDB que el equipo no modela: (a) el INSERT de una fila hija toma S sobre el padre por la
    FK (movimiento→variante, analitica_variante→variante) y luego el UPDATE del padre pide X: deadlock S→X con otro
    escritor (ejemplo del manual de MySQL); (b) `UPDATE t ... WHERE col IN (SELECT ... FROM s)` pone S next-key en `s`;
    (c) UPDATE con `OR` entre columnas sin índice bloquea toda la tabla en REPEATABLE READ. Pedir: UPDATE del padre antes
    de insertar hijos, ids por SELECT no bloqueante y luego `IN (:ids)`, y prueba con dos hilos intercalados.
15. Tipos TS no nulos para campos que el backend devuelve null (stockSeguridad en SIN_HISTORIA) → Intl.format(null)="0".
Se repitieron el 6 (regla ArchUnit solo mira @Query de @Modifying; JdbcTemplate la evade), el 7 (cast(enum as string))
y el 9 (SIN_ROTACION cruza variantes del producto bajo el bloqueo de una sola variante).

Fase 6 (rama fase-6-prediccion-compras, 2026-10-21), sin críticos:
16. Formularios con `version` optimista que toman la versión de la consulta refrescada (prop) y no la versión sobre la
    que se editó: tras un 409 + "recargar" (o cualquier refetch) el guardado sobrescribe cambios ajenos sin 409. Pedir
    versión base en estado + fusión a 3 vías (base/mía/vigente) con conflictos marcados.
17. Topes de tamaño (`limit(300)`) aplicados sobre listas ordenadas por id en vez de por prioridad de negocio, y sin
    informar lo que quedó fuera.
Se repitió el 14a con otra forma: inserts analíticos con FK a `producto` (S) antes de las variantes vs la entrada que
actualiza `producto.precioCompra` (X) después de las variantes. También: un "bug conocido" del equipo (no se puede
vaciar la observación del plan) no se reproducía en el código; verificar antes de confirmar pendientes heredados.

Fase 7 después del corte (rama fase-7-cierre-entrega, 2026-10-24, portátil sin Docker), sin críticos:
18. Ventanas de tiempo derivadas de dos relojes distintos para compararse entre sí: la semilla cuenta "unidades perdidas
    comparables" con `fin + 1` y la analítica de cierre corre con `LocalDate.now(clock)` del sistema; con `fecha-fin`
    explícita de otro mes la razón sale desalineada sin aviso. Pedir que la ventana salga del mismo corte (o n/d).
19. SQL con clasificación por tipo (CASE m.tipo IN ...) y pruebas de integración que no se corren localmente (sin
    Docker): verificar que la IT ejercite cada rama (VENTA_RESERVA, LIBERACION, inactivas, otra categoría que el filtro
    debe excluir). El cuadre saldo = flujos solo detecta errores si el tipo aparece en los datos.
20. Helpers de prueba que hacen UPDATE al kardex (`DatosReporte.fecharMovimientos`) para fechar al pasado: aceptable como
    preparación, pero vigilar que no se copie a código de producción ni se use fuera de preparación.
Bien hecho (mantener): exportar() y consultar() comparten una transacción readOnly (snapshot REPEATABLE READ coherente
entre resumen, conteo y detalle); orden por prioridad de negocio antes del tope (el 17 no se repitió).
