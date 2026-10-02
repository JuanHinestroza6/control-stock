---
name: decisiones-fase2
description: Decisiones APROBADAS de Fase 2 (2026-10-02) - aplicarMovimiento público + ArchUnit, ANULACION_VENTA, 409 sin reintento, costo en entradas que actualiza precioCompra, precioLista en DetalleVenta, V8-V10
metadata:
  type: project
---

Plan de Fase 2 aprobado el 2026-10-02 (fuente: docs/fases/fase-2-plan.md, ADR-006, dominio.md "Entradas, ventas y costos"):
- `Variante.aplicarMovimiento(deltaFisico, deltaReservado)` público. El usuario CAMBIÓ mi propuesta (prueba que escanea fuentes) por **ArchUnit** (`com.tngtech.archunit:archunit-junit5` 1.5.1, test): paso manual del usuario `.\mvnw.cmd test-compile` porque el sandbox va offline. Maven Central sí respondió vía curl desde el sandbox (sirve para verificar versiones).
- ANULACION_VENTA aprobado. El usuario AGREGÓ: ventas ANULADAS excluidas de demanda/ABC/pronóstico/reportes (Fases 5–7); anular venta de reserva no reactiva la reserva (Fase 3).
- El usuario AGREGÓ `DetalleVenta.precioLista` (reportar descuentos) y entradas con `costoUnitario` por línea (columna `movimiento.costo_unitario`, obligatoria solo en ENTRADA) que actualiza `Producto.precioCompra` (último costo). Yo elegí: mismo costo por producto dentro de una entrada (422).
- 409 sin reintento (`@Retryable` de Spring 7 existe en spring-context 7.0.9 si se reconsidera). Kardex @Immutable sin triggers. Solo se publica StockCambiado; listener de alertas en Fase 3.
- MovimientoService recibe usuarioId explícito (null = sistema) y usa Clock (requisito del skill datos-semilla).
- Anular/devolver bloquean la fila de venta (PESSIMISTIC_WRITE): la devolución no apta no toca Variante.

**Why:** Fase 3 (reservas, alertas), 4 (seed) y 5–7 (analítica, reportes) dependen de estas firmas y reglas.
**How to apply:** al planear Fase 3+, partir de esto; el usuario tiende a pedir más rigor (ArchUnit, auditoría de descuentos). Ver [[decisiones-fase1]], [[entorno-y-versiones]].
