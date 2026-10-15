# ADR-008: Generador de datos semilla

- Estado: aceptada
- Fecha: 2026-10-04
- Modificada por: ADR-009 — al cerrar, la semilla ejecuta la analítica completa (origen SEMILLA) en lugar de su bucle
  propio de alertas; si falla, la carga queda confirmada, termina con código 1 y se recupera con "Recalcular" (ADMIN).

## Contexto
La analítica de las Fases 5–6 (ABC, punto de reorden, Holt-Winters con m = 12) y la sustentación necesitan 24 meses de
historia creíble. Los datos deben respetar las reglas de oro: el stock solo cambia vía `MovimientoService` (ADR-006) y el
kardex es inmutable. Había seis cuestiones abiertas:
1. Cómo fechar operaciones en el pasado si los servicios toman la fecha del `Clock` inyectado.
2. Dónde vive el generador, si `comun` no puede depender de los módulos de negocio.
3. Qué pasa si se ejecuta dos veces o falla a mitad de camino, y cómo evitar que corra en producción.
4. Cómo cargar decenas de miles de movimientos en poco tiempo, con el listener de alertas reaccionando a cada uno.
5. Qué hace la aplicación cuando termina la carga, dado que el perfil `seed` apaga las tareas programadas.
6. Cómo crear usuarios de demostración sin secretos en el repositorio y sin contraseña temporal.

## Decisión
1. **Reloj simulado solo en `seed`**: el bean `RelojSimulado extends Clock`, `@Primary` y `@Profile("seed")`, reemplaza al
   reloj del sistema; servicios y auditoría fechan con él sin cambiar su código. Solo avanza (retroceder lanza excepción),
   así el kardex queda cronológico; las operaciones de cada día se ejecutan en orden de hora. Al terminar vuelve al reloj
   del sistema. El usuario que actúa se fija en el contexto de seguridad por operación.
2. **Módulo `semilla`** (`co.livorno.controlstock.semilla`): todo con `@Profile("seed")`; depende de los demás módulos y
   ninguno depende de él (regla ArchUnit). Usa solo los servicios reales: catálogo, entradas, ajustes, ventas,
   devoluciones, anulaciones y reservas; ningún insert directo de stock o kardex.
3. **Todo o nada e idempotencia**: una sola transacción para toda la carga, con `flush` + `clear` por día simulado. Un error
   revierte todo y la base queda como estaba. Si ya hay ventas, el generador no hace nada; si hay productos o movimientos
   sin ventas, aborta; no corre con un perfil `prod`. Para regenerar se borra la base (`docker compose down -v`). Al
   final verifica la consistencia (stock = suma del kardex, reservado = suma de reservas ACTIVAS, nada negativo) dentro de
   la misma transacción: si falla, se revierte.
4. **Alertas al final**: `AlertaListener` se vuelve condicional (`app.alertas.por-evento`, `true` por defecto, `false` solo
   en `seed`). Al terminar la simulación, con el reloj real, se evalúan las alertas de todas las variantes con
   `AlertaService.evaluar`.
5. **Salir al terminar**: la carga termina la aplicación (código 0 si todo salió bien, 1 si falló;
   `app.semilla.salir-al-terminar`, `false` en pruebas). Luego se arranca normal con `dev`.
6. **Usuarios demo** (`admin@`, `inventario@`, `ventas@`, `contabilidad@livorno.co`) creados con el repositorio y el
   `PasswordEncoder`, sin contraseña temporal, con la contraseña de `SEED_PASSWORD` (obligatoria, misma política).
   Determinismo con `Random(42)` y `app.semilla.fecha-fin` configurable.

## Alternativas consideradas
- Fecha explícita en cada solicitud de servicio — ensucia las firmas de producción solo para la carga.
- Inserts directos por SQL — rompen la regla de oro 1 y no prueban la coherencia kardex ↔ stock.
- Generador en `comun/semilla` (como decía el skill) — `comun` pasaría a depender de todos los módulos.
- Commit por día o por mes — menos memoria por transacción, pero un fallo deja una carga parcial que bloquea la
  re-ejecución (ya hay ventas).
- Borrar y regenerar desde el código — ArchUnit prohíbe `EntityManager.remove` y el kardex no se borra.
- Listener de alertas activo durante la carga — unas 30 mil evaluaciones con commit propio (1–2 min extra) y un historial
  de alertas fechado con el reloj simulado; la re-evaluación nocturna de la Fase 5 hace lo mismo que la evaluación final.
- Dejar la web corriendo con el perfil `seed` — quedaría sin tareas programadas ni alertas por evento.
- Crear los usuarios con `UsuarioService.crear` — los marcaría con contraseña temporal.

## Consecuencias
- Sin migraciones nuevas. Cambio de producción mínimo: `@ConditionalOnProperty` en `AlertaListener`.
- Las entidades usan `IDENTITY`: Hibernate no agrupa INSERT; el tiempo depende del volumen (meta < 2 min, se mide en T1).
- No hay historial de alertas anterior a la carga; solo quedan las ACTIVAS que correspondan al stock final.
- Las ventas que no se hacen por falta de stock se pierden (demanda censurada): el pronóstico de la Fase 6 lo tiene en
  cuenta.
- Las reservas ACTIVAS vencen pocos días después de la carga: regenerar la víspera de la sustentación con una fecha final
  fija.
- A los 90 días, la tarea de anonimización (perfil `dev`) suprime los datos de cliente de las reservas cerradas, como con
  datos reales.
