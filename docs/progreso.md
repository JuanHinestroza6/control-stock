# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 4
- Fase en curso: 6 — Predicción y compras **terminada en código y documentación** (rama `fase-6-prediccion-compras`,
  PR abierto sin merge); falta la prueba T2 real del usuario (guía abajo)
- Última actualización: 2026-10-21

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
- [x] Fase 6 — T1: flujo de compra de punta a punta por HTTP y matriz de roles de 60 filas
- [x] Fase 6 — después del corte: B12 semilla de 36 meses con reposición por planes reales (pedidos en camino al cerrar,
      verificador de en camino); F7 en camino y posición en Inventario, Reorden, Alertas y panel; filtro "Solo en alerta"
      de Reorden por posición en el servidor
- [x] Fase 6 — revisión final sin críticos; corregidos los 2 importantes (fusión del borrador ante edición concurrente
      con conflictos a resolver; sugerencia priorizada por agotado, clase ABC y faltante con tope de 300 y aviso de
      excluidas) y los menores (orden producto → variante en entradas contra interbloqueo con el pronóstico, N+1 del
      listado de planes, en camino del detalle, observación vacía, respaldo 2 × stock mínimo y riesgo de sugerencias
      simultáneas documentados)
- [x] Fase 6 — PPI: fichas REQ-09 (CU-05) y REQ-10 (CU-16, CU-17, CU-18 nuevos), REQ-04 con posición, datos de
      demostración con 36 meses; diagramas de estados, clases de `compra` y secuencia sugerir → aprobar → recibir.
      Backend 1269/1269, frontend 592/592

## En curso

- [ ] Fase 6 — PR de `fase-6-prediccion-compras` hacia main (sin merge) y prueba T2 real del usuario

## Próximos pasos

1. El usuario corre la T2 (guía abajo); con sus cifras se completan los "pendiente de T2" de REQ-04, REQ-10 y
   `datos-demostracion.md` (`documentador-ppi`) y se hace el merge del PR.
2. Completar en REQ-09 y REQ-10 stakeholder, necesidad, prioridad y el título de CU-05 (quedaron "[confirmar con el
   Word]"); validar la sintaxis de los `.puml` nuevos (no hay `plantuml` local).
3. Fase 7: dashboard, reportes PDF/Excel (incluye el PDF de tendencias, CU-05), rendimiento y documento PPI.

## Prueba T2 — fase 6 (PowerShell 5, desde la raíz del repo) — pendiente

**Atención: el paso 1 BORRA todos los datos de tu base de desarrollo local.** Contraseñas: los 4 usuarios demo usan la de
`SEED_PASSWORD` del `.env`; el ADMIN inicial (`ADMIN_EMAIL`) usa la de `ADMIN_PASSWORD`. Nunca se escriben en el repo.

1. Base vacía: `docker compose down -v` y `docker compose up -d mysql` (esperar `healthy` con `docker compose ps`).
2. Carga: `cd backend`, `.\mvnw.cmd clean` y `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,seed"`
   (`fecha-fin` vacía = ayer; ahora son **36 meses**, se esperan ~3,5–4 min). Anota del resumen: duración total, ventas
   (y anuladas), unidades perdidas, **planes creados / recibidos / en camino** (y unidades en camino), y de la analítica
   de cierre: productos con **Holt-Winters** y con SES, **WAPE global**, % de variantes en alerta **con y sin tránsito**
   y variantes con pedido en camino.
3. Arranque normal: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` y en otra terminal `cd frontend`,
   `npm run dev`. Abre http://localhost:5173.
4. Verificación por rol:
   - [ ] `admin@livorno.co`: Analítica → panel "Última ejecución" (origen Datos semilla, EXITOSA, pronóstico HW/SES,
         WAPE global, % en alerta con y sin tránsito). Pulsa "Recalcular" y anota la duración (meta < 8 s; aceptable
         < 15 s). **Tendencias**: cambia periodo 12/24/36, métrica, categoría y producto (año anterior punteado).
         **Pronóstico**: listado con método y semáforo de WAPE; abre LIV001 (producto de cabeza: gráfico con backtest y
         reparto por variante) y LIV004 (estancado: SES con motivo "meses sin demanda"). **Reorden**: columnas En camino,
         Posición y Fuente; "Solo en alerta" no muestra variantes cuya posición supera el umbral.
   - [ ] `inventario@livorno.co`: Compras → "Generar sugerencia" (anota líneas, omitidas y si aparece el aviso de
         excluidas por tope); en el borrador cambia una cantidad y guarda. Abre un plan **APROBADO** en camino de la
         semilla y **recíbelo** (cambia una cantidad recibida; un solo costo por producto); en el kardex deben aparecer
         las ENTRADAS con referencia `PLAN_COMPRA/…` y el plan queda RECIBIDO. No ve "Aprobar"; puede descartar un
         BORRADOR pero no cancelar un APROBADO. Inventario muestra "N en camino".
   - [ ] `admin@livorno.co` otra vez: aprueba el borrador sugerido (diálogo con total estimado), pulsa "Recalcular" y
         anota la nueva métrica con y sin tránsito. Opcional (concurrencia): abre el mismo borrador en dos navegadores,
         cambia líneas distintas en cada uno y guarda ambos: el segundo recibe 409 y "Cargar versión vigente" fusiona
         sin perder los cambios del otro; si tocaron la misma línea aparece el conflicto a resolver.
   - [ ] `ventas@livorno.co`: no ve Compras ni las pestañas Tendencias y Pronóstico (solo "Más vendidos").
   - [ ] `contabilidad@livorno.co`: ve Compras con costos en solo lectura (sin botones), Tendencias y Pronóstico.
5. Opcional (cifras para el PPI), desde la raíz: `docker compose exec mysql mysql -u root -p control_stock` (pide
   `DB_ROOT_PASSWORD` del `.env`) y ejecuta:
   `select metodo, count(*), avg(error_wape) from analitica_producto group by metodo;`
   `select motivo_sin_hw, count(*) from analitica_producto group by motivo_sin_hw;`
   `select al.tipo, a.clase_abc, count(*) from alerta al join analitica_variante a on a.variante_id = al.variante_id
   where al.estado = 'ACTIVA' group by al.tipo, a.clase_abc order by 1, 2;`
   `select p.estado, count(distinct p.id) planes, sum(d.cantidad_aprobada) unidades from plan_compra p
   join detalle_plan d on d.plan_id = p.id group by p.estado;`
   `select sum(unidades_observadas_12m), sum(unidades_corregidas_12m) from analitica_producto;` (lo que agrega la
   corrección por agotados frente a las unidades perdidas del resumen: se espera el mismo orden de magnitud, 0,5×–2×).
6. Volver a ejecutar el paso 2 sin borrar: debe decir que ya hay datos y no crear nada.

Referencia: % de variantes en alerta de stock fase 4 47 %, fase 5 34,4 %; meta ~10–15 % (estimado 11–17 %, ADR-010).

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409.
- Semilla: la analítica (ABC, ROP y alertas) corre tras el commit de la carga; si falla, la carga queda confirmada y se
  recupera con "Recalcular" como ADMIN (una nueva ejecución de la semilla dice "ya hay datos").
- Umbral de alertas: fase 4 con `stockMinimo` 47 %; fase 5 con ROP 34,4 % (T2); fase 6 con posición de inventario:
  pendiente de T2 (meta ~10–15 %, estimado 11–17 %; AGOTADO solo ya pesa ~9 %, ADR-010).
- Riesgos residuales de concurrencia de la analítica aceptados y documentados en ADR-009 (variante ocupada conserva ROP
  hasta la siguiente ejecución; ventana de milisegundos en SIN_ROTACION).
- Para fases futuras: ventas ANULADAS fuera del pronóstico y la rentabilidad (fases 6–7); la demanda observada está
  censurada por las ventas perdidas (2,5–3 %): la corrige el pronóstico de la fase 6. El ROP con revisión diaria o ventas
  de 2+ unidades queda unos puntos bajo el nivel de servicio (ver riesgos del plan de la fase 5).

- Fase 6, riesgos aceptados (ADR-010): dos sugerencias simultáneas pueden meter la misma variante en dos borradores; el
  backtest no cubre noviembre–diciembre; en la fusión del borrador un cambio de costo y otro de cantidad en la misma
  línea se tratan como conflicto (se resuelve a mano).

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
