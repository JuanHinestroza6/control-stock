# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 4
- Fase en curso: 6 — Predicción y compras, **núcleo terminado** (rama `fase-6-prediccion-compras`, sin push); en pausa
  en el punto de corte para que el usuario decida si sigue con B12, F7, T2 y documentación
- Última actualización: 2026-10-20

## Hecho

- [x] Fase 0 — Setup: monorepo, infra, CI, auth JWT, frontend base; PPI REQ-06/REQ-12, convención RNF (PR #1–#3)
- [x] Fase 1 — Núcleo: catálogo y usuarios (ADR-004, ADR-005); PPI REQ-11; T3 sin errores (PR #4)
- [x] Fase 2 — Operación: kardex, MovimientoService, ventas, anulación, devoluciones (ADR-006); PPI REQ-01/02/08 (PR #5)
- [x] Fase 3 — Apartados: reservas, conversión en venta, alertas por evento, anonimización Ley 1581 (ADR-007); PPI REQ-04
      y REQ-13 (nuevo, NEC-11, CU-14); T3 sin errores (PR #6)
- [x] Fase 4 — Datos semilla (ADR-008): 30 productos / 299 variantes, 24 meses; T1 real correcta; PPI "Datos de
      demostración" (PR #7)
- [x] Fase 5 — Analítica base (ADR-009): ABC por variante, ROP normal/Poisson, SIN_ROTACION, más vendidos, ejecución
      nocturna y manual; T2 real con 34,4 % de variantes en alerta de stock (fase 4: 47 %); PPI REQ-05, REQ-14 (CU-15),
      REQ-04; diagramas CU-08, CU-15 y clases de `analitica` (PR #8, #9)
- [x] Fase 6 — plan (docs/fases/fase-6-plan.md) y ADR-010 con D1–D8 (D3 en b), **Holt-Winters multiplicativo** (mes en 0
      → SES), punto de corte (núcleo primero) y PDF de tendencias (CU-05) movido a la fase 7; dominio y skill de algoritmos
- [x] Fase 6 — algoritmos A1–A5: serie mensual y corrección por agotados, SES y HW multiplicativo, selección por
      backtesting con origen móvil (serie sintética de LIV001: WAPE SES 12,1 % vs HW 5,2 %), desagregación y ROP con
      pronóstico, sugerencia (s, S)
- [x] Fase 6 — backend B1–B11: V15 (pronóstico e instantáneas), consultas de demanda mensual y disponibilidad,
      PronosticoService, ejecución con pronóstico previo (fallo → FALLIDA con ROP histórico), V16 y módulo `compra` con
      puertos, crear/editar/cancelar/aprobar/recibir (entradas `PLAN_COMPRA/{id}` por MovimientoService), STOCK_BAJO por
      posición y métricas de tránsito, sugerencia con omitidas, endpoints de tendencias, pronósticos y planes
- [x] Fase 6 — frontend F1–F6: Recharts, pestañas Tendencias y Pronóstico, página Compras (listado, sugerir, plan manual),
      detalle del plan (editar, aprobar, cancelar) y recepción con costo por producto
- [x] Fase 6 — T1: flujo de compra de punta a punta por HTTP y matriz de roles de 60 filas. Backend 1247/1247,
      frontend 561/561

## En curso

- [ ] Fase 6 — punto de corte: reporte al usuario antes de seguir

## Próximos pasos

1. Después del corte (si el usuario lo aprueba): B12 semilla de 36 meses con reposición por planes (D8) y skill
   `datos-semilla`; F7 "en camino" y posición en Inventario, Reorden, Alertas y panel de ejecución (alinear el filtro
   "Solo en alerta" de Reorden con la posición); T2 real; fichas REQ-09, REQ-10, REQ-04 y diagramas.
2. Revisión final de la fase con `revisor` (ver pendientes de revisión abajo) y PR de la rama.
3. Fase 7: dashboard, reportes PDF/Excel (incluye el PDF de tendencias, CU-05), rendimiento y documento PPI.

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409.
- Semilla: la analítica (ABC, ROP y alertas) corre tras el commit de la carga; si falla, la carga queda confirmada y se
  recupera con "Recalcular" como ADMIN (una nueva ejecución de la semilla dice "ya hay datos").
- Umbral de alertas: fase 4 con `stockMinimo` 47 %; fase 5 con ROP 34,4 % (T2). La meta de ~10–15 % llega en la fase 6
  con la posición de inventario (D4, ADR-009).
- Riesgos residuales de concurrencia de la analítica aceptados y documentados en ADR-009 (variante ocupada conserva ROP
  hasta la siguiente ejecución; ventana de milisegundos en SIN_ROTACION).
- Para fases futuras: ventas ANULADAS fuera del pronóstico y la rentabilidad (fases 6–7); la demanda observada está
  censurada por las ventas perdidas (2,5–3 %): la corrige el pronóstico de la fase 6. El ROP con revisión diaria o ventas
  de 2+ unidades queda unos puntos bajo el nivel de servicio (ver riesgos del plan de la fase 5).

- Pendientes para la revisión final de la fase 6: (1) al guardar un borrador tras un 409 y "Cargar versión vigente",
  las líneas cambiadas por otro usuario se sobrescriben; (2) no se puede vaciar la observación de un borrador (el PUT
  omite el campo vacío); (3) el filtro "Solo en alerta" de Reorden aún mira el disponible, no la posición (F7); (4) con
  más de 300 candidatas la sugerencia toma las primeras 300 por id, no por prioridad.
- Antes de B12 la demo usa la semilla de 24 meses (23 completos): todos los productos salen SES con POCA_HISTORIA y la
  métrica de alertas no cambia hasta que se aprueben planes (HW se valida con pruebas).

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT).
- Docker Desktop debe estar arriba: sin él se omiten las pruebas con Testcontainers (no hacer commit en ese caso).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`).
- Las pruebas que borran variantes deben borrar antes `alerta` y `reserva`; las de semilla usan `LimpiezaSemilla`.
- Las reservas ACTIVAS de la semilla vencen a los pocos días: regenerar la víspera de una demo.
- CI fijado en `ubuntu-24.04`; el limitador de login es en memoria (`getRemoteAddr()`).
- Frontend: en dos corridas apareció 1 fallo intermitente que no se reprodujo en 7 corridas seguidas; probablemente
  carga de la máquina con Maven en paralelo. Si reaparece, guardar la salida de `npx vitest run --reporter=verbose`.
- Fase 6: el IDE volvió a compilar en `target/` ("Unresolved compilation problems"); revisar que Java › Autobuild siga
  desactivado. Nunca correr dos Maven a la vez (corrompe `target/` y choca con Testcontainers).
- Problema conocido: `ClassFormatError: Invalid method Code length 0` al arrancar (clase corrupta en `target/`, p. ej.
  `AnaliticaVariante`) se resuelve con `./mvnw clean` (o `.\mvnw.cmd clean`). El usuario desactivó el Auto Build de la
  extensión Java de VS Code, que compilaba en `target/` a la vez que Maven.
- Para demos: regenerar la semilla la víspera (`docker compose down -v` y carga `dev,seed`) para que las reservas ACTIVAS
  y la ventana de SIN_ROTACION queden alineadas con la fecha.
