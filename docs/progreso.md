# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 3
- Fase en curso: 5 — Analítica base (rama `fase-5-analitica`, sin push): implementación y revisión terminadas; falta la
  prueba T2 del usuario y la documentación PPI con sus números
- Última actualización: 2026-10-14

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
      (SKIP LOCKED en el cálculo, cierre de SIN_ROTACION por ids) y 7 menores corregidos. Backend 853/853, frontend 425/425

## En curso

- [ ] Fase 5 — T2 (usuario): corrida real de la semilla y medición (ver sección "Prueba T2" abajo)

## Próximos pasos

1. T2 del usuario; pasarme el resumen de la carga, el % en alerta, la duración del recálculo y lo observado por rol.
2. Documentación PPI con los números de T2: fichas REQ-05 y REQ-14 (stakeholder, NEC, CU y prioridad "Por asignar" hasta
   que el usuario los aporte del Word), REQ-04 (ROP, SIN_ROTACION, métrica) y "Datos de demostración".
3. Cerrar la fase en `progreso.md`; push y PR de `fase-5-analitica` cuando el usuario lo pida.
4. Fase 6: STOCK_BAJO por posición de inventario (disponible + pedidos aprobados en camino) para llegar a la meta de
   ~10–15 % de variantes en alerta (D4).

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409.
- Semilla: la analítica (ABC, ROP y alertas) corre tras el commit de la carga; si falla, la carga queda confirmada y se
  recupera con "Recalcular" como ADMIN (una nueva ejecución de la semilla dice "ya hay datos").
- Umbral de alertas: línea base de la fase 4 con `stockMinimo`, 141 de 299 (≈ 47 %). Con el ROP de la fase 5 se mide en T2
  (prueba de integración de 3 meses: ≈ 27–32 %). La meta de ~10–15 % llega en la fase 6 con la posición de inventario
  (D4, ADR-009).
- Riesgos residuales de concurrencia de la analítica aceptados y documentados en ADR-009 (variante ocupada conserva ROP
  hasta la siguiente ejecución; ventana de milisegundos en SIN_ROTACION).
- Para fases futuras: ventas ANULADAS fuera del pronóstico y la rentabilidad (fases 6–7); la demanda observada está
  censurada por las ventas perdidas (2,5–3 %): la corrige el pronóstico de la fase 6. El ROP con revisión diaria o ventas
  de 2+ unidades queda unos puntos bajo el nivel de servicio (ver riesgos del plan de la fase 5).

## Prueba T2 — fase 5 (PowerShell 5, desde la raíz del repo) — pendiente

**Atención: el paso 1 BORRA todos los datos de tu base de desarrollo local.** Contraseñas: los 4 usuarios demo usan la de
`SEED_PASSWORD` del `.env`; el ADMIN inicial (`ADMIN_EMAIL`) usa la de `ADMIN_PASSWORD`. Nunca se escriben en el repo.

1. Base vacía: `docker compose down -v` y `docker compose up -d mysql` (esperar `healthy` con `docker compose ps`).
2. Carga: `cd backend` y `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,seed"` (deja `fecha-fin` vacía =
   ayer). Al final imprime el resumen con una línea nueva de analítica: clases A/B/C, ROP por método, % de variantes en
   alerta de stock, AGOTADO, STOCK_BAJO y SIN_ROTACION, y la duración. Anota todo el resumen y el tiempo total.
3. Arranque normal: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` y en otra terminal `cd frontend`,
   `npm run dev`. Abre http://localhost:5173.
4. Verificación por rol:
   - [ ] `admin@livorno.co`: Analítica → panel "Última ejecución" (origen Datos semilla, EXITOSA, % en alerta). Pulsa
         "Recalcular", confirma y anota la duración que muestra (meta < 5 s; aceptable < 15 s). Revisa las pestañas
         Más vendidos (cambia periodo, criterio y agrupación), ABC (tarjetas y filtros) y Reorden (filtro "Solo en
         alerta", ayuda de la fórmula).
   - [ ] `inventario@livorno.co`: ve las tres pestañas y el panel, pero no el botón "Recalcular". Inventario muestra
         las columnas Clase y Umbral (ROP o stock mín.). Alertas: aparecen SIN_ROTACION en productos estancados
         (LIV004, LIV026, LIV030 según la semilla) y se pueden atender.
   - [ ] `ventas@livorno.co`: en Analítica solo ve "Más vendidos" (sin costos); no ve el panel. La campana muestra el
         desglose (agotado, stock bajo, sin rotación).
   - [ ] `contabilidad@livorno.co`: ve ABC, Reorden y el panel; no ve "Recalcular".
5. Opcional (desglose para el PPI), desde la raíz: `docker compose exec mysql mysql -u root -p control_stock` (pide la
   contraseña `DB_ROOT_PASSWORD` del `.env`) y ejecuta:
   `select a.clase_abc, a.metodo_reorden, count(*) variantes, sum(al.id is not null) en_alerta from analitica_variante a
   left join alerta al on al.variante_id = a.variante_id and al.estado = 'ACTIVA' and al.tipo in ('AGOTADO','STOCK_BAJO')
   group by a.clase_abc, a.metodo_reorden order by 1, 2;`
6. Volver a ejecutar el paso 2 sin borrar: debe decir que ya hay datos y no crear nada (tampoco otra ejecución analítica).

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT).
- Docker Desktop debe estar arriba: sin él se omiten las pruebas con Testcontainers (no hacer commit en ese caso).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`).
- Las pruebas que borran variantes deben borrar antes `alerta` y `reserva`; las de semilla usan `LimpiezaSemilla`.
- Las reservas ACTIVAS de la semilla vencen a los pocos días: regenerar la víspera de una demo.
- CI fijado en `ubuntu-24.04`; el limitador de login es en memoria (`getRemoteAddr()`).
