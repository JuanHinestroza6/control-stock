# ADR-007: Apartados (reservas) y alertas por evento

- Estado: aceptada
- Fecha: 2026-10-02
- Modificada por: ADR-009 — SIN_ROTACION por producto, las tres alertas excluyentes (AGOTADO > SIN_ROTACION >
  STOCK_BAJO), re-evaluación nocturna y cierre de alertas al desactivar.

## Contexto
La Fase 3 implementa los apartados que los clientes hacen por Instagram, TikTok y WhatsApp: crear, cancelar, convertir en
venta y vencer automáticamente. ADR-006 dejó publicado `StockCambiado` sin listener y la Fase 3 debía evaluar las
alertas. Había siete cuestiones abiertas:
1. Cómo convertir reservas en venta (cuántas reservas por venta, qué precio).
2. Cómo evitar que el vencimiento automático y una acción manual (convertir, cancelar) cierren la misma reserva dos veces.
3. Si una reserva se puede extender.
4. Quién puede convertir, cuando INVENTARIO gestiona reservas pero no vende.
5. Cómo reforzar "reservas activas bloquean la desactivación" sin que catalogo dependa de inventario.
6. Dónde vive la conversión, si inventario no debe depender de venta (venta ya depende de inventario).
7. Qué alertas entran en esta fase y si STOCK_BAJO y AGOTADO pueden coexistir.

## Decisión
1. **Conversión**: varias reservas ACTIVAS del mismo cliente (contacto normalizado) → una venta; líneas agrupadas por
   variante; un VENTA_RESERVA por línea con referencia `VENTA/{id}`. Precio de lista **al convertir**, `precioUnitario`
   opcional ≤ lista, costo = `precioCompra` al convertir. Anular la venta no reactiva las reservas.
2. **Bloqueo de la fila de la reserva** (`PESSIMISTIC_WRITE`) en toda transición, verificando `estado = ACTIVA` (si no,
   409); orden reserva → variante. "El estado manda": una ACTIVA con `venceEn` pasado aún se puede convertir hasta que la
   tarea la venza. La tarea (`@Scheduled` cada 15 min, apagable por propiedad) procesa **cada reserva en su propia
   transacción** con `vencer(id, ahora)`, idempotente; un fallo se reintenta en el ciclo siguiente.
3. **Vigencia elegible** al crear (1–168 h, 48 por defecto) y **extensión** (`venceEn = ahora + horas`) con tope de 7 días
   desde la creación.
4. **Roles**: crear, cancelar y extender ADMIN, INVENTARIO y VENDEDOR (cualquiera; se registra `cerradaPor`). Convertir:
   ADMIN y VENDEDOR. `Reserva` agrega `usuarioId`, `cerradaEn`, `cerradaPor` (null = sistema) y `motivoCancelacion`.
5. **Desactivación reforzada** con la interfaz `catalogo.servicio.ConsultaReservasActivas`, implementada en inventario
   (inversión de dependencias); se conserva `stockReservado > 0` como defensa.
6. **Dirección de módulos fijada con ArchUnit**: catalogo no depende de inventario ni de venta; inventario no depende de
   venta. La conversión está en `venta` (`POST /api/ventas/desde-reservas`) y `Reserva.ventaId` es un `Long`.
7. **Alertas por evento en la Fase 3** (STOCK_BAJO y AGOTADO, listado, atender y campana): listener
   `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`, con try/catch y log. **Excluyentes** como
   `estadoStock()`: AGOTADO si disponible = 0, STOCK_BAJO si 0 < disponible ≤ umbral; al cambiar de una a otra se cierra
   la anterior (ATENDIDA con `atendidaPor` null). Una ACTIVA por variante y tipo garantizada por un índice único sobre una
   columna generada. SIN_ROTACION y la re-evaluación nocturna pasan a la Fase 5.
8. **Supresión de datos del cliente (Ley 1581)**: retención de 90 días configurable
   (`app.reservas.anonimizacion.dias`). Una tarea nocturna (`app.reservas.anonimizacion.cron`, 02:30 America/Bogota,
   apagada con `app.tareas.habilitadas=false`) reemplaza `clienteNombre` y `clienteContacto` por `[anonimizado]` en las
   reservas cerradas (CANCELADA, VENCIDA, CONVERTIDA) con `cerradaEn ≤ ahora − días`, en lotes de 200 y una transacción
   por lote; el log solo registra la cantidad. Es idempotente, no genera movimientos ni toca stock. El kardex no contiene
   datos del cliente, así que no requiere cambios. Migración V13: índice `(estado, cerrada_en)`.

## Alternativas consideradas
- Una reserva → una venta — más simple, pero un cliente con tres prendas genera tres ventas y tres números.
- Congelar el precio al reservar — exige otra columna; el precio opcional ≤ lista ya permite respetar lo cotizado.
- Tratar como vencida una ACTIVA con `venceEn` pasado — el cliente que paga a las 48 h 05 min no podría comprar y el stock
  seguiría reservado hasta que corra la tarea.
- `@Version` en `Reserva` — daría 409 al perdedor, pero el bloqueo de fila serializa sin error al usuario en la tarea y
  hace el vencimiento idempotente.
- Una sola transacción para todo el lote de vencimiento — un choque con una venta revertiría todas las liberaciones.
- ShedLock para varias instancias — fuera de alcance; el bloqueo de fila basta para que dos ejecuciones no dupliquen.
- Consultar la tabla `reserva` desde catalogo con JPQL — acoplamiento oculto entre módulos.
- Alertas a la Fase 5 — la Fase 5 ya es la más cargada de la semana 3; ADR-006 las comprometió para la Fase 3.
- STOCK_BAJO y AGOTADO simultáneas — duplican el aviso de la misma variante.
- Listener `@Async` — complica las pruebas y no hace falta con el volumen esperado.

## Consecuencias
- Migraciones V11 (`reserva`) y V12 (`alerta`); `@EnableScheduling` en `comun.config.ConfiguracionTareas` con
  `app.tareas.habilitadas` (false en pruebas y en el perfil `seed`).
- Hasta 15 minutos de stock reservado de más tras el vencimiento; aceptado.
- Un AJUSTE_NEGATIVO no consume unidades reservadas: primero se cancela la reserva.
- Atender a mano una alerta vigente la reabre el siguiente movimiento (regla literal del dominio).
- Crear reservas puede abrir alertas, porque baja el disponible.
- Fase 4: el generador semilla crea reservas a través de `ReservaService` con el `Clock` y la tarea apagada.
  Fase 5: la demanda de una venta convertida usa la fecha de la venta; agrega SIN_ROTACION y la re-evaluación nocturna.
- Datos de cliente mínimos (Ley 1581): sin exportación y sin acceso para CONTABILIDAD.
