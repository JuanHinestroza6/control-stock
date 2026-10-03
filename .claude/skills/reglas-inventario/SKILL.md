---
name: reglas-inventario
description: Reglas de negocio e invariantes del inventario de Control Stock (variantes, stock físico/reservado/disponible, kardex, tipos de movimiento, reservas, devoluciones, alertas). Úsalo SIEMPRE que el código toque stock, variantes, movimientos, ventas, devoluciones, reservas, alertas o planes de compra, aunque la tarea parezca pequeña.
---

# Reglas de inventario

La especificación completa está en `docs/dominio.md`. Este skill resume lo que nunca debe romperse y cómo implementarlo.

## Invariantes

Para toda variante, en todo momento:
- `stockFisico ≥ 0`, `stockReservado ≥ 0`, `stockDisponible = stockFisico − stockReservado ≥ 0`.
- El stock de una variante es igual a la suma de sus movimientos en el kardex (prueba de consistencia).

## Único punto de escritura del stock

```java
// inventario/servicio/MovimientoService.java
@Transactional
public Movimiento registrar(SolicitudMovimiento solicitud) {
    Variante v = varianteRepository.findById(solicitud.varianteId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Variante", solicitud.varianteId()));

    int fisicoAntes = v.getStockFisico();
    int reservadoAntes = v.getStockReservado();

    EfectoMovimiento efecto = solicitud.tipo().efecto(solicitud.cantidad()); // deltas físico/reservado
    validar(v, solicitud, efecto);   // lanza ExcepcionNegocio si rompe un invariante o permiso

    // Único método de la entidad que modifica stock. Es público (Variante está en catalogo.modelo y este servicio en
    // inventario.servicio); una regla ArchUnit garantiza que solo MovimientoService lo invoca (ADR-006).
    v.aplicarMovimiento(efecto.deltaFisico(), efecto.deltaReservado());
    // usuarioId llega en la solicitud (null = proceso del sistema); la fecha sale del Clock inyectado.
    Movimiento m = Movimiento.de(v, solicitud, fisicoAntes, reservadoAntes, LocalDateTime.now(clock));
    movimientoRepository.save(m);

    eventos.publishEvent(new StockCambiado(v.getId()));  // alertas AFTER_COMMIT
    return m;
}
```

- `Variante` NO tiene setters de stock. Solo `aplicarMovimiento(int deltaFisico, int deltaReservado)`, público, que
  valida los invariantes (físico ≥ 0, reservado ≥ 0, reservado ≤ físico) y lanza `IllegalStateException` si se rompen.
  La prueba ArchUnit `ReglasArquitecturaTest` falla si alguna clase distinta de `inventario.servicio.MovimientoService`
  lo invoca (ADR-006).
- `@Version` en `Variante`: si dos ventas concurrentes chocan, una falla con `OptimisticLockingFailureException`; el manejador global responde 409 sin reintento en el servidor; el cliente conserva el formulario y el usuario vuelve a confirmar (ADR-006).
- Ventas, devoluciones, reservas y recepción de compras llaman a `MovimientoService`; nunca tocan `Variante` directamente.

## Efectos por tipo

| Tipo | Δ físico | Δ reservado | Requisito previo |
|---|---|---|---|
| ENTRADA | +q | 0 | — |
| VENTA | −q | 0 | q ≤ disponible |
| VENTA_RESERVA | −q | −q | reserva ACTIVA con cantidad q |
| DEVOLUCION | +q | 0 | devuelto acumulado ≤ vendido |
| AJUSTE_POSITIVO | +q | 0 | observación obligatoria |
| AJUSTE_NEGATIVO | −q | 0 | observación, q ≤ disponible |
| RESERVA | 0 | +q | q ≤ disponible |
| LIBERACION | 0 | −q | reserva ACTIVA |
| ANULACION_VENTA | +q | 0 | venta REGISTRADA sin devoluciones; solo ADMIN (ADR-006) |

Implementa los efectos en el enum `TipoMovimiento` para que no haya `switch` repartidos por el código.

## Kardex

- `Movimiento` no tiene `@Setter`, el repositorio no expone métodos de borrado/actualización y la tabla no se modifica en migraciones posteriores salvo para agregar columnas.
- Cada movimiento guarda `referenciaTipo` + `referenciaId` (VENTA/42, RESERVA/7, PLAN_COMPRA/3) para trazar su origen.

## Reservas (ADR-007)

- Una variante por reserva. RESERVA y LIBERACION llevan referencia `RESERVA/{id}`; VENTA_RESERVA, `VENTA/{id}`.
- Toda transición (convertir, cancelar, extender, vencer) bloquea la fila de la reserva (`PESSIMISTIC_WRITE`) y verifica
  que siga ACTIVA (si no, 409). Orden de bloqueo: reserva → variante. El estado manda: ACTIVA con `venceEn` pasado aún se
  puede convertir hasta que la tarea la venza.
- Vigencia 1–168 h (48 por defecto); extender hasta 7 días desde `creadaEn`.
- Tarea `@Scheduled` cada 15 min (apagada en pruebas y `seed`): consulta ids ACTIVAS con `venceEn ≤ ahora` y llama a
  `vencer(id, ahora)` **en una transacción por reserva** → LIBERACION (`usuarioId` null) → VENCIDA. Idempotente.
- Convertir: varias reservas ACTIVAS del mismo cliente → una venta (líneas agrupadas por variante, precio de lista al
  convertir). Vive en el módulo `venta`; `Reserva.ventaId` es un `Long`, no una relación (inventario no depende de venta).
- Anular una venta que vino de reservas solo genera ANULACION_VENTA; las reservas siguen CONVERTIDAS.

## Devoluciones

- Validar contra `DetalleVenta`: suma de devoluciones previas + nueva ≤ cantidad vendida.
- `aptaParaVenta=false` → se registra la devolución sin movimiento de stock.

## Alertas

- Listener `@TransactionalEventListener(phase = AFTER_COMMIT)` sobre `StockCambiado` (y `UmbralCambiado`) evalúa
  STOCK_BAJO y AGOTADO con `@Transactional(propagation = REQUIRES_NEW)` (Spring 6.1+ rechaza otra propagación) y
  try/catch con log: una excepción aquí llegaría al cliente como 500 con la operación ya confirmada.
- Excluyentes, igual que `estadoStock()`: AGOTADO si disponible = 0; STOCK_BAJO si 0 < disponible ≤ umbral.
- Umbral: `puntoReorden` si existe, si no `stockMinimo`.
- No duplicar: si hay alerta ACTIVA del mismo tipo para la variante, solo actualiza `revisadaEn` (además, índice único).
- Si la condición deja de cumplirse, la alerta ACTIVA pasa a ATENDIDA automáticamente (`atendidaPor` null).
- SIN_ROTACION y la re-evaluación nocturna: Fase 5.

## Pruebas obligatorias para cualquier cambio en esta zona

- Cada tipo de movimiento: efecto correcto y snapshot antes/después.
- Rechazo cuando la cantidad deja stock negativo o supera el disponible.
- Consistencia: tras N movimientos aleatorios, el stock de la variante coincide con la suma del kardex.
- Concurrencia: dos ventas simultáneas del último ítem → exactamente una falla.
- Reservas: suma de reservas ACTIVAS de la variante = `stockReservado`; vencer y convertir la misma reserva a la vez →
  exactamente una transición.
