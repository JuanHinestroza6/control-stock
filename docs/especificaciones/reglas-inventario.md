# Reglas de inventario

Invariantes y reglas de implementación del inventario de Control Stock: variantes, stock físico, reservado y
disponible, kardex, tipos de movimiento, reservas, devoluciones y alertas. La especificación funcional completa está en
`docs/dominio.md`; las decisiones, en ADR-006 (operación), ADR-007 (reservas y alertas), ADR-009 (SIN_ROTACION y
re-evaluación nocturna) y ADR-010 (posición de inventario).

## Invariantes

Para toda variante, en todo momento:

- `stockFisico ≥ 0`, `stockReservado ≥ 0`, `stockDisponible = stockFisico − stockReservado ≥ 0`.
- El stock de una variante es igual a la suma de sus movimientos en el kardex (prueba de consistencia).

## Único punto de escritura del stock

```java
// inventario/servicio/MovimientoService.java
@Transactional
public Movimiento registrar(SolicitudMovimiento s) {
    if (s.cantidad() <= 0) {
        throw new ExcepcionNegocio("La cantidad debe ser mayor que cero.");
    }
    Variante v = varianteRepository.findById(s.varianteId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Variante", s.varianteId()));
    EfectoMovimiento efecto = s.tipo().efecto(s.cantidad());   // deltas de físico y reservado
    validar(v, s);                                            // ExcepcionNegocio si rompe una regla

    int fisicoAntes = v.getStockFisico();
    int reservadoAntes = v.getStockReservado();
    v.aplicarMovimiento(efecto.deltaFisico(), efecto.deltaReservado());
    Movimiento m = Movimiento.de(v, s.tipo(), s.cantidad(), fisicoAntes, reservadoAntes, s.costoUnitario(),
            s.usuarioId(), LocalDateTime.now(clock), s.referenciaTipo(), s.referenciaId(), s.observacion());
    movimientoRepository.save(m);
    eventos.publishEvent(new StockCambiado(v.getId()));       // alertas AFTER_COMMIT
    return m;
}
```

- `Variante` no tiene setters de stock. Solo `aplicarMovimiento(int deltaFisico, int deltaReservado)`, público, que
  valida los invariantes (físico ≥ 0, reservado ≥ 0, reservado ≤ físico) y lanza `IllegalStateException` si se rompen.
  Es público porque `Variante` vive en `catalogo.modelo` y el servicio en `inventario.servicio`; la prueba ArchUnit
  `ReglasArquitecturaTest` falla si otra clase distinta de `inventario.servicio.MovimientoService` lo invoca (ADR-006).
- `@Version` en `Variante`: si dos ventas concurrentes chocan, una falla con `OptimisticLockingFailureException`; el
  manejador global responde 409 sin reintento en el servidor; el cliente conserva el formulario y el usuario vuelve a
  confirmar (ADR-006).
- Ventas, devoluciones, anulaciones, reservas, ajustes y recepción de compras llaman a `MovimientoService`; nunca tocan
  `Variante` directamente. La analítica escribe `puntoReorden` y `claseAbc` con un UPDATE dirigido que no toca el stock
  ni la versión.

## Efectos por tipo

| Tipo | Δ físico | Δ reservado | Requisito previo |
|---|---|---|---|
| ENTRADA | +q | 0 | costo unitario obligatorio |
| VENTA | −q | 0 | q ≤ disponible |
| VENTA_RESERVA | −q | −q | reserva ACTIVA con cantidad q |
| DEVOLUCION | +q | 0 | devuelto acumulado ≤ vendido; solo si es apta |
| AJUSTE_POSITIVO | +q | 0 | observación obligatoria |
| AJUSTE_NEGATIVO | −q | 0 | observación, q ≤ disponible |
| RESERVA | 0 | +q | q ≤ disponible |
| LIBERACION | 0 | −q | reserva ACTIVA |
| ANULACION_VENTA | +q | 0 | venta REGISTRADA sin devoluciones; solo ADMIN (ADR-006) |

Los efectos están en el enum `TipoMovimiento`, para que no haya `switch` repartidos por el código.

## Kardex

- `Movimiento` no tiene setters, el repositorio no expone métodos de borrado ni actualización y la tabla no se modifica
  en migraciones posteriores salvo para agregar columnas. Los errores se corrigen con un AJUSTE.
- Cada movimiento guarda `referenciaTipo` + `referenciaId` (VENTA/42, RESERVA/7, PLAN_COMPRA/3) para trazar su origen.
- Es la fuente del reporte «Movimientos del periodo» (ADR-011): saldo final = físico actual − neto posterior al rango;
  RESERVA y LIBERACION no cambian el físico.

## Reservas (ADR-007)

- Una variante por reserva. RESERVA y LIBERACION llevan referencia `RESERVA/{id}`; VENTA_RESERVA, `VENTA/{id}`.
- Toda transición (convertir, cancelar, extender, vencer) bloquea la fila de la reserva (`PESSIMISTIC_WRITE`) y verifica
  que siga ACTIVA (si no, 409). Orden de bloqueo: reserva → variante. El estado manda: una ACTIVA con `venceEn` pasado
  aún se puede convertir hasta que la tarea la venza.
- Vigencia de 1 a 168 h (48 por defecto); se puede extender hasta 7 días desde `creadaEn`.
- Tarea programada cada 15 min (apagada en pruebas y en `seed`): consulta las ACTIVAS con `venceEn ≤ ahora` y llama a
  `vencer(id, ahora)` en una transacción por reserva → LIBERACION (`usuarioId` null) → VENCIDA. Idempotente.
- Convertir: varias reservas ACTIVAS del mismo cliente → una venta (líneas agrupadas por variante, precio de lista al
  convertir). Vive en el módulo `venta`; `Reserva.ventaId` es un `Long`, no una relación (inventario no depende de
  venta).
- Anular una venta que vino de reservas solo genera ANULACION_VENTA; las reservas siguen CONVERTIDAS.
- Ley 1581: una tarea nocturna (02:30) anonimiza el nombre y el contacto del cliente en las reservas cerradas hace 90 días
  o más; las ACTIVAS nunca se anonimizan.

## Devoluciones

- Se validan contra `DetalleVenta`: suma de devoluciones previas + nueva ≤ cantidad vendida.
- `aptaParaVenta = false` → se registra la devolución sin movimiento de stock (su costo es merma no apta en los reportes).

## Alertas

- Un `@TransactionalEventListener(phase = AFTER_COMMIT)` sobre `StockCambiado` y `UmbralCambiado` evalúa las alertas de
  la variante con `@Transactional(propagation = REQUIRES_NEW)` (Spring rechaza otra propagación en AFTER_COMMIT) y
  try/catch con log: una excepción ahí llegaría al cliente como 500 con la operación ya confirmada. La aprobación o
  cancelación de un plan de compra publica `PosicionCambiada` con el mismo tratamiento.
- Tipos excluyentes, con prioridad AGOTADO > SIN_ROTACION > STOCK_BAJO:
  - **AGOTADO**: disponible = 0 (lo que viene en camino no se puede vender).
  - **SIN_ROTACION** (ADR-009): el producto no registra ventas no anuladas en 60 días (`app.alertas.sin-rotacion-dias`);
    se abre en cada variante con disponible > 0; atendida a mano no se reabre dentro del mismo episodio.
  - **STOCK_BAJO** (ADR-010): 0 < disponible y **posición** (disponible + en camino de planes APROBADO) ≤ umbral.
- Umbral: `puntoReorden` si existe; si no, `stockMinimo`.
- No se duplican: si hay una alerta ACTIVA del mismo tipo para la variante, solo se actualiza `revisadaEn` (además, hay un
  índice único en la base).
- Si la condición deja de cumplirse, la alerta ACTIVA pasa a ATENDIDA automáticamente (`atendidaPor` null).
- La ejecución analítica nocturna re-evalúa todas las variantes, una transacción por variante.
- Panel de inicio: `GET /api/alertas/urgentes` lista las AGOTADO por mayor umbral y antigüedad y luego las STOCK_BAJO por
  menor cobertura de la posición ((disponible + en camino) / umbral).

## Pruebas obligatorias para cualquier cambio en esta zona

- Cada tipo de movimiento: efecto correcto y saldos antes y después.
- Rechazo cuando la cantidad deja stock negativo o supera el disponible.
- Consistencia: tras N movimientos aleatorios, el stock de la variante coincide con la suma del kardex.
- Concurrencia: dos ventas simultáneas del último ítem → exactamente una falla.
- Reservas: suma de reservas ACTIVAS de la variante = `stockReservado`; vencer y convertir la misma reserva a la vez →
  exactamente una transición.
