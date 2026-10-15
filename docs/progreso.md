# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 3
- Fase en curso: 5 — Analítica base terminada (rama `fase-5-analitica` publicada, PR abierto); siguiente: Fase 6
  (predicción y compras)
- Última actualización: 2026-10-15

## Hecho

- [x] Fase 0 — Setup: monorepo, infra, CI, auth JWT, frontend base; PPI REQ-06/REQ-12, convención RNF (PR #1–#3)
- [x] Fase 1 — Núcleo: catálogo y usuarios (ADR-004, ADR-005); PPI REQ-11; T3 sin errores (PR #4)
- [x] Fase 2 — Operación: kardex, MovimientoService, ventas, anulación, devoluciones (ADR-006); PPI REQ-01/02/08 (PR #5)
- [x] Fase 3 — Apartados: reservas, conversión en venta, alertas por evento, anonimización Ley 1581 (ADR-007); PPI REQ-04
      y REQ-13 (nuevo, NEC-11, CU-14); T3 sin errores (PR #6)
- [x] Fase 4 — Datos semilla (ADR-008): 30 productos / 299 variantes, 24 meses; T1 real correcta; PPI "Datos de
      demostración" (PR #7)
- [x] Fase 5 — plan (docs/fases/fase-5-plan.md) y ADR-009 con D1 (a) ABC por variante 12 meses, D2 (b) ROP normal/Poisson
      sin piso, D3 (a) SIN_ROTACION por producto excluyente, D4 (a) meta de alertas medida ahora y cumplida en la fase 6
- [x] Fase 5 — backend B1–B8: V14 (ejecucion_analitica, analitica_variante), escritura dirigida de ROP/clase sin
      `version`, ABC/ROP/Poisson, orquestador con tarea nocturna 02:00 y recálculo manual (ADMIN), SIN_ROTACION con
      prioridad y episodio, cierre de alertas al desactivar (bug fase 3), más vendidos, endpoints, semilla cierra con la
      analítica. Algoritmos A1–A2 con prueba Monte Carlo del nivel de servicio
- [x] Fase 5 — frontend F1–F6: página Analítica (pestañas por rol, panel de ejecución, Más vendidos, ABC, Reorden),
      Clase y Umbral en inventario, SIN_ROTACION en alertas y campana
- [x] Fase 5 — T1 flujo de punta a punta y matriz de roles; revisión final sin críticos: 2 importantes de concurrencia
      (SKIP LOCKED en el cálculo, cierre de SIN_ROTACION por ids) y 7 menores corregidos
- [x] Fase 5 — T2 real (2026-10-15): la primera corrida no mostró SIN_ROTACION (los estancados vendían por reservas
      convertidas); se ajustó la semilla (estancados sin ventas ni reservas en los últimos 90 días) y se repitió. Segunda
      corrida: carga 2 min 10 s; 12.227 ventas (91 anuladas); analítica SEMILLA EXITOSA en 4,95 s; ABC A 79 / B 86 /
      C 126 (79,9 / 15,1 / 5,0 % del ingreso); ROP NORMAL 26, POISSON 201, SIN_DEMANDA 64; alertas AGOTADO 27, STOCK_BAJO
      73, SIN_ROTACION 18 (LIV004, LIV026, LIV030); **34,4 % de variantes en alerta de stock** (fase 4: 47 %); Recalcular
      4,8 s (primera corrida). Revisión por los 4 roles correcta; la segunda ejecución no creó nada
- [x] Fase 5 — PPI: fichas REQ-05 (Vendedor, NEC-05, CU-08, Media) y REQ-14 nuevo (Asistente de gerencia, NEC-10, CU-15
      "Clasificar productos (ABC)" nuevo, Media), REQ-04 (fase 5) y "Datos de demostración" con T2. Backend 855/855,
      frontend 425/425

## En curso

- [ ] PR de `fase-5-analitica` hacia main (revisar CI y hacer merge)

## Próximos pasos

1. Merge del PR de la fase 5; luego `git switch main && git pull`.
2. Iniciar `/fase 6`: pronóstico (SES/Holt-Winters con backtesting), tendencias (REQ-09) y plan de compras (REQ-10).
3. En la fase 6, STOCK_BAJO por posición de inventario (disponible + pedidos aprobados en camino) para llegar a la meta de
   ~10–15 % de variantes en alerta (D4); hoy 34,4 %.
4. Diagramas UML de la analítica (casos de uso CU-08/CU-15 y clases de `analitica`): aún no existen; crearlos con
   `documentador-ppi` cuando se arme el documento PPI (fase 7) o antes si se piden.

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

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT).
- Docker Desktop debe estar arriba: sin él se omiten las pruebas con Testcontainers (no hacer commit en ese caso).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`).
- Las pruebas que borran variantes deben borrar antes `alerta` y `reserva`; las de semilla usan `LimpiezaSemilla`.
- Las reservas ACTIVAS de la semilla vencen a los pocos días: regenerar la víspera de una demo.
- CI fijado en `ubuntu-24.04`; el limitador de login es en memoria (`getRemoteAddr()`).
- Problema conocido: `ClassFormatError: Invalid method Code length 0` al arrancar (clase corrupta en `target/`, p. ej.
  `AnaliticaVariante`) se resuelve con `./mvnw clean` (o `.\mvnw.cmd clean`). El usuario desactivó el Auto Build de la
  extensión Java de VS Code, que compilaba en `target/` a la vez que Maven.
- Para demos: regenerar la semilla la víspera (`docker compose down -v` y carga `dev,seed`) para que las reservas ACTIVAS
  y la ventana de SIN_ROTACION queden alineadas con la fecha.
