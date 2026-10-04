---
name: decisiones-fase3
description: APROBADAS Fase 3 (2026-10-02) - reservas con bloqueo de fila, conversión en venta (módulo venta), vencimiento por transacción, alertas por evento, V11-V12, ArchUnit de módulos
metadata:
  type: project
---

Plan de Fase 3 APROBADO el 2026-10-02 (fuente: docs/fases/fase-3-plan.md y ADR-007 una vez copiados). El usuario eligió: varias reservas del mismo cliente → una venta; alertas STOCK_BAJO/AGOTADO por evento en Fase 3 (SIN_ROTACION + nocturna en Fase 5), excluyentes (STOCK_BAJO solo si 0 < disponible ≤ umbral); vigencia elegible 1–168 h y extender con tope 7 días.
- Dependencias entre módulos hoy limpias: catalogo no importa inventario/venta; inventario no importa venta. Propuse fijarlo con ArchUnit. Por eso: la conversión reserva→venta vive en `venta` (`POST /api/ventas/desde-reservas`), `Reserva` guarda `ventaId` como Long (sin relación a Venta), y la desactivación reforzada usa una interfaz en catalogo implementada en inventario.
- Concurrencia de reservas: bloqueo PESSIMISTIC_WRITE de la fila reserva + verificar ACTIVA (orden reserva → variante); la tarea de vencimiento procesa cada reserva en su transacción con `vencer(id, ahora)` (ahora explícito = testeable). "El estado manda": ACTIVA vencida por tiempo aún se puede convertir hasta que corra la tarea.
- Convertir requiere ADMIN/VENDEDOR aunque INVENTARIO acceda a Reservas (matriz: INVENTARIO no vende).
- Alertas: listener AFTER_COMMIT + REQUIRES_NEW obligatorio (Spring 6.1+ rechaza otro propagation) y try/catch (excepción after-commit = 500 con datos ya confirmados). Unicidad de ACTIVA por columna generada `CASE WHEN estado='ACTIVA' THEN variante_id END` + UNIQUE. STOCK_BAJO/AGOTADO excluyentes como estadoStock(). SIN_ROTACION a Fase 5.
- No había @EnableScheduling en el proyecto: propuse ConfiguracionTareas condicional, deshabilitada en test y seed.

**Why:** Fases 4 (seed genera reservas vía servicio con Clock), 5 (alertas nocturnas, demanda = fecha de la venta convertida) dependen de esto.
**How to apply:** al planear Fase 4/5 partir de lo aprobado; ver [[decisiones-fase2]], [[decisiones-fase1]].
