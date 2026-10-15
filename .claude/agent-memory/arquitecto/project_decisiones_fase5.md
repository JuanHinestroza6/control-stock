---
name: decisiones-fase5
description: APROBADAS Fase 5 analítica (2026-10-14) - ABC por variante 12 m, ROP híbrido normal/Poisson sin piso, escritura dirigida updatable=false sin @Version, SIN_ROTACION por producto excluyente, V14, meta de alertas a fase 6; hallazgos no obvios
metadata:
  type: project
---

Plan de Fase 5 APROBADO el 2026-10-14 (docs/fases/fase-5-plan.md, ADR-009 aceptado). El usuario eligió la opción recomendada
en las cuatro: D1 (a), D2 (b), D3 (a), D4 (a):
- D1 ABC por variante, ingreso neto, 12 meses (skill decía 90 días), cortes 80/95.
- D2 ROP: 90 días; normal si μL=d̄·L ≥ 5, cuantil Poisson si < 5; SIN piso stockMinimo (el skill decía max(ROP, stockMinimo), contradice dominio "respaldo"); historia mínima 30 d.
- D3 SIN_ROTACION por producto (por variante daría ~25 % de alertas falsas por demanda intermitente), excluyente AGOTADO > SIN_ROTACION > STOCK_BAJO, evaluada en AlertaService con el kardex (inventario no depende de venta), regla de episodio para no reabrir la atendida.
- D4 Meta 10–15 % de alertas: inalcanzable con alertas sobre disponible (fracción ≈ L/(Q/d), ~40 % en la semilla con cualquier ROP). Propuse medir línea base en fase 5 (métrica guardada en ejecucion_analitica) y cumplir la meta en fase 6 con posición de inventario (disponible + en tránsito de planes aprobados) y semilla reponiendo con planes.
Resueltas por mí: puntoReorden/claseAbc `updatable=false` + UPDATE nativo por variante en orden de id sin tocar version (evita 409 a ventas y lost update); instantánea analitica_variante + ejecucion_analitica (V14, + índice movimiento(variante_id,tipo,fecha)); orquestador no transaccional (ABC+ROP 1 tx, alertas 1 tx por variante), cron 02:00, guardia AtomicBoolean, recálculo manual síncrono ADMIN; analitica lee venta por SQL (modelo de lectura), nadie salvo semilla depende de analitica.
- Hallazgos: la semilla tiene ~240 de 299 variantes con < 1 u/mes (cola Zipf) y stockMinimo = 2; desactivar variante/producto NO cierra sus alertas ACTIVAS (quedan ocultas) — bug de fase 3 a corregir en B4. No pude consultar la base local (credenciales en .env, lectura denegada): las estimaciones son analíticas.

**Why:** fase 6 (pronóstico, plan de compras, en tránsito) y la sustentación dependen de estas fórmulas y de la métrica.
**How to apply:** al planear fase 6, partir de lo aprobado aquí; incluir en fase 6 STOCK_BAJO por posición de inventario (D4(a) aprobada). Ver [[decisiones-fase4]], [[decisiones-fase3]].
