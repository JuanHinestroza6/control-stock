---
name: decisiones-fase6
description: APROBADAS Fase 6 predicción y compras (2026-10-15) - D1..D8 recomendados, puertos compra/analitica/inventario, V15-V16; hallazgos no obvios (23 meses completos, semilla no pide si llega tras fin)
metadata:
  type: project
---

Plan de Fase 6 APROBADO el 2026-10-15 (docs/fases/fase-6-plan.md y ADR-010, escritos por mí en el scratchpad y copiados
por el coordinador). El usuario eligió D1–D8 recomendadas (D3 b) con cambios: Holt-Winters MULTIPLICATIVO (índices
como razones de media 1; producto con algún mes en 0 → SES, motivo MES_EN_CERO); punto de corte (núcleo A1–A5, B1–B11,
F1–F6, T1 → reportar; luego B12 semilla 36 m, F7, T2, docs); PDF de tendencias (CU-05) a fase 7; Recharts lo instala el
usuario. Lo aprobado:
- D1 (a) serie mensual por producto con demanda de inventario, corregida por disponibilidad (observada / max(disp, 0,5)).
- D2 (a) semilla 36 meses + backtest con origen móvil (n−6, n−3; WAPE sobre 6 puntos); HW si n ≥ 30.
- D3 (b) ROP con μL pronosticado (s observada 90 d), fallback histórico; columna fuente_demanda.
- D4 (a) STOCK_BAJO por posición (disponible + en camino de planes APROBADO); AGOTADO y estadoStock siguen sobre disponible.
- D5 (a) + estado CANCELADO, recepción única con cantidades recibidas; recepción permitida sobre variantes inactivas.
- D6 (a) política (s,S): candidatas posición ≤ umbral; S = umbral + demanda de R=30 días tras L; lote; omite variantes en otro BORRADOR.
- D7 (a) recibir ADMIN+INVENTARIO, aprobar solo ADMIN; tendencias/pronóstico AN (VENDEDOR 403).
- D8 (a) semilla repone con planes reales, permite pedidos que llegan después de fin (quedan en camino).
Resueltas por mí: puertos `inventario.servicio.ConsultaPedidosEnCamino` (impl compra) y `compra.servicio.FuenteSugerenciaCompra`
(impl analitica) para no romper "nadie depende de analitica"; HW con init desestacionalizada por tendencia (nivel_0 = ȳ1 − 6,5·tend_0);
inserciones de `pronostico` en orden de variante_id (FK toma S en variante); V15 pronóstico, V16 compra.
Hallazgos: semilla de 24 meses empieza a mitad de mes → solo 23 meses completos (HW nunca elegible con el skill literal);
`SimuladorDiario.reponer()` no pide si la llegada cae después de `fin` (por eso el final queda en alerta); AGOTADO ya es ~9 %
(27/299) → meta 10–15 % estimada 11–17 %; no existe módulo `compra` (solo la ruta /compras "Próximamente" en frontend);
Recharts aún no instalado.

**Why:** fase 7 (reportes, dashboard) y la sustentación dependen de estas tablas y de la métrica final de alertas.
**How to apply:** verificar en docs/decisiones/ADR-010 qué eligió el usuario antes de planear la fase 7. Ver [[decisiones-fase5]], [[decisiones-fase4]].
