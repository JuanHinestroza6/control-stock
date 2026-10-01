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

    v.aplicar(efecto);               // único método de la entidad que modifica stock (paquete-privado)
    Movimiento m = Movimiento.de(v, solicitud, fisicoAntes, reservadoAntes, usuarioActual());
    movimientoRepository.save(m);

    eventos.publishEvent(new StockCambiado(v.getId()));  // alertas AFTER_COMMIT
    return m;
}
```

- `Variante` NO tiene setters públicos de stock. Solo `aplicar(EfectoMovimiento)`.
- `@Version` en `Variante`: si dos ventas concurrentes chocan, una falla con `OptimisticLockingFailureException`; el manejador global responde 409 y el cliente reintenta.
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

Implementa los efectos en el enum `TipoMovimiento` para que no haya `switch` repartidos por el código.

## Kardex

- `Movimiento` no tiene `@Setter`, el repositorio no expone métodos de borrado/actualización y la tabla no se modifica en migraciones posteriores salvo para agregar columnas.
- Cada movimiento guarda `referenciaTipo` + `referenciaId` (VENTA/42, RESERVA/7, PLAN_COMPRA/3) para trazar su origen.

## Reservas

- Vencen a las 48 h por defecto. Tarea `@Scheduled(fixedDelay = 15 min)` → busca ACTIVAS vencidas → movimiento LIBERACION → estado VENCIDA.
- Convertir en venta: crea la venta con movimiento VENTA_RESERVA y marca la reserva CONVERTIDA con `ventaId`.

## Devoluciones

- Validar contra `DetalleVenta`: suma de devoluciones previas + nueva ≤ cantidad vendida.
- `aptaParaVenta=false` → se registra la devolución sin movimiento de stock.

## Alertas

- Listener `@TransactionalEventListener(phase = AFTER_COMMIT)` sobre `StockCambiado` evalúa STOCK_BAJO y AGOTADO.
- Umbral: `puntoReorden` si existe, si no `stockMinimo`.
- No duplicar: si hay alerta ACTIVA del mismo tipo para la variante, solo actualiza `revisadaEn`.
- Si el stock se recupera por encima del umbral, la alerta ACTIVA pasa a ATENDIDA automáticamente.

## Pruebas obligatorias para cualquier cambio en esta zona

- Cada tipo de movimiento: efecto correcto y snapshot antes/después.
- Rechazo cuando la cantidad deja stock negativo o supera el disponible.
- Consistencia: tras N movimientos aleatorios, el stock de la variante coincide con la suma del kardex.
- Concurrencia: dos ventas simultáneas del último ítem → exactamente una falla.
