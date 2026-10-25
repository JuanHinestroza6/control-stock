# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 5
- Fase en curso: 7 — Cierre de entrega: en `main` (PR #11) y T2 por API hecha (2026-10-25); falta la revisión visual del
  usuario y el ensayo. Rama de esta actualización: `docs/cifras-t2-fase-7`. Plan: docs/fases/fase-7-plan.md; ADR-011
- Última actualización: 2026-10-25 (portátil: Windows 10 LTSC, sin Docker, MySQL 9.7 nativo, perfil `dev,local`)

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
- [x] Fase 6 — T2 real (2026-10-21, por API y base de datos; la revisión visual la hace el usuario después): carga de 36
      meses en 4 min 26 s; 20.281 ventas (139 anuladas), 2.817 unidades perdidas; planes 3.673 creados / 3.575 recibidos
      / 98 en camino (1.060 u.); analítica SEMILLA EXITOSA en 6,5 s; ABC A 78 / B 82 / C 131; ROP NORMAL 42, POISSON 222,
      SIN_DEMANDA 27, fuente PRONOSTICO en las 291; pronóstico HW 7 (LIV001, 002, 009, 014, 018, 020, 027) / SES 22
      (9 por MES_EN_CERO), WAPE global 18,3 % (HW medio 17,1 %, SES 42,0 %); alertas AGOTADO 26, STOCK_BAJO 16,
      SIN_ROTACION 18; **14,4 % de variantes en alerta de stock** (meta 10–15 % cumplida; sin tránsito 38,8 %; fase 5
      34,4 %; fase 4 47 %); 101 variantes con pedido en camino. Recalcular 6,46 s (meta < 8 s). Matriz de roles por API
      correcta; ciclo sugerir (PC-003674, 20 líneas) → aprobar (ADMIN; INVENTARIO 403) → recibir: 20 ENTRADAS
      `PLAN_COMPRA/3674`, stock y en camino coherentes (LIV002-L-BLA: físico 10 → 25, en camino 0 → 15 → 0); STOCK_BAJO
      16 → 0. Segunda carga sin borrar: no hizo nada. Observación: la corrección por agotados recupera ≈ 0,3× de las
      unidades perdidas (esperado 0,5×–2×)

- [x] Fase 7 — plan y ADR-011 (D1–D6 confirmadas: 3 reportes + movimientos después del corte, devoluciones en fecha
      mixta, merma aparte, costos del panel solo ADMIN/CONTABILIDAD, corrección por agotados sin cambio, sin Dockerfile);
      textos del Word de REQ-03, REQ-07, RNF-03, REQ-09 y REQ-10 en el anexo del plan
- [x] Fase 7 — núcleo: POI 5.4.1 y OpenPDF 2.0.3; `comun.exportacion` (Excel y PDF); módulo `reporte` con rentabilidad,
      libro de ventas, valorización, `/exportar` y panel por rol; exportar tendencias a PDF (con gráfico y resumen) y
      Excel; frontend con panel de inicio, página Reportes (3 pestañas, gráfico de margen, respaldo de formato) y exportar
      en Tendencias; T1 rendimiento con 12.150 ventas: todo < 0,5 s (peor: libro XLSX 24 meses 430 ms), EXPLAIN sin
      recorrido completo, sin índices nuevos; T2 flujo por HTTP (conciliación, Ley 1581 en bytes, matriz de roles).
      Diagramas de paquetes, componentes, despliegue y casos de uso general. Backend 1380/1380, frontend 635/635

- [x] Fase 7 — después del corte (2026-10-24, en el portátil sin Docker; las pruebas con Testcontainers las corre la CI):
      panel sin duplicación (agotadas y bajo stock solo en las tarjetas de Inventario; bloque "Alertas más urgentes" con
      `GET /api/alertas/urgentes`: AGOTADO por umbral y antigüedad, STOCK_BAJO por cobertura); esperas de Testing Library
      a 3 s (fallos intermitentes por carga); B9 + F7 movimientos del periodo (saldo inicial, entradas y su valor,
      ventas, anulaciones, devoluciones, ajustes, saldo final; detalle del kardex solo en Excel; cuadre verificado en
      vivo contra el kardex: 1.321 → 1.496); B10 unidades perdidas comparables y razón de la corrección en el resumen de
      la semilla; D1 fichas REQ-03, REQ-07, RNF-03 nuevas y REQ-01, REQ-09, REQ-10 completas (sin "[confirmar con el
      Word]"), títulos del Word de CU-03, CU-04, CU-09 y CU-10; D2 casos de uso general corregido (sin "CU-06 extiende
      CU-05"; CU-01 extiende CU-10; "Comparar periodos" extiende CU-05), clases general y secuencia de exportar; D3
      `docs/ppi/trazabilidad.md` y README del PPI. 20 `.puml` validan con PlantUML 1.2024.7 (JAR de `~/.m2`)

- [x] Fase 7 — revisión (rol `revisor`; esta sesión no cargó los agentes del proyecto y se usó un agente general con
      `.claude/agents/revisor.md`): sin críticos. Corregidas las 2 advertencias: B10 usa la ventana de la `fechaCorte`
      real de la analítica (pérdidas por mes en la simulación; antes podía desalinearse con `fecha-fin` explícita) y la
      prueba de integración de movimientos cubre VENTA_RESERVA, LIBERACION, variante inactiva y otra categoría. De las
      sugerencias: urgentes STOCK_BAJO por posición (con lo en camino), fórmula completa y aviso del tope del Excel en
      la pestaña Movimientos, sesgo de B10 documentado, ajustes menores de documentación. Quedan como están: carga de
      todas las alertas activas para elegir 5 (con ~300 variantes no pesa) y el UPDATE de fecha del kardex solo en la
      preparación de la prueba. Backend 905 + 493 omitidas (Testcontainers), frontend 642

- [x] Fase 7 — T3: guía T2 adaptada al portátil (vaciar la base con el cliente nativo, carga `dev,local,seed`,
      verificación por rol, cifras contra SQL, `Measure-Command`) y guion de sustentación de 15 min para 3 expositores
      (Juan Francisco Hinestroza, Juan David Quinto y Santiago Granada Baena; `docs/ppi/guion-sustentacion.md`).
      Comprobado que `dev,local,seed` arranca y, con datos, no hace nada

- [x] Fase 7 — PR #11 en `main` (CI: backend 1.403/1.403 con integración, frontend en verde; movimientos 121–725 ms)
- [x] Fase 7 — T2 real (2026-10-25) **por API (curl) y base de datos en el portátil** (MySQL nativo, `dev,local,seed`);
      la revisión visual por la interfaz la hace el usuario. Carga 3 min 01 s (generación 141,6 s; `clean` omitido por
      falta de red); 20.337 ventas (139 anuladas), 2.788 unidades perdidas; planes 3.723 / 3.648 / 75 en camino (705 u.);
      ABC 78 / 81 / 132; Holt-Winters 11, SES 18, WAPE global 17,95 %; alertas AGOTADO 30, STOCK_BAJO 18, SIN_ROTACION
      18; **16,5 % en alerta de stock** (35,1 % sin lo en camino; cerca de la meta de 10–15 %, varía con la fecha de corte
      de la semilla: fase 6 14,4 % con la misma lógica; no se ajustaron parámetros); **B10: +284,13 unidades de la
      corrección frente a 270 perdidas comparables = 1,05×** (esperado 0,5×–2×). Login de los 4 demo; panel por rol
      correcto; matriz de los 4 reportes 32/32; 14 exportaciones 200 con content-type y tamaño correctos; todo < 0,9 s
      (más pesada: libro 24 meses a Excel 852 ms); recalcular 3,39 s; valorización = SQL; movimientos cuadran con el
      físico; libro y rentabilidad concilian con los bordes del rango calculados por SQL. Cifras en
      `docs/ppi/datos-demostracion.md`, RNF-03 y REQ-07

## En curso

- [ ] Fase 7 — revisión visual del usuario (paso 4 de la guía T2) y ensayo cronometrado con el guion

## Próximos pasos

1. Revisión visual por la interfaz con los 4 roles (paso 4 de la guía T2); la base de demo quedó cargada.
2. Ensayo cronometrado con `docs/ppi/guion-sustentacion.md` (ya tiene las cifras de esta T2).
3. La víspera de la demo, regenerar la semilla (pasos 1–2 de la guía) para alinear reservas y SIN_ROTACION con la fecha.

## Prueba T2 — fase 7 en el portátil (PowerShell 5, MySQL nativo, perfiles `dev,local` y `dev,local,seed`)

Sin Docker: la base es el MySQL 9.7 nativo y la configuración sale de `backend/config/application-local.properties`
(fuera de Git). Cliente de MySQL: `C:\tools\mysql\current\bin\mysql.exe`.

**Atención: el paso 1 BORRA todos los datos de la base local `control_stock`.** Credenciales (nunca en el repo): el
ADMIN inicial es el `spring.flyway.placeholders.admin-email` de `application-local.properties` con su `admin-password`;
los 4 usuarios demo (`admin@livorno.co`, `inventario@livorno.co`, `ventas@livorno.co`, `contabilidad@livorno.co`) usan
`app.semilla.password`. La clave de MySQL es `spring.datasource.password`. **Ojo en el portátil:** el `admin-email` local
no es `admin@livorno.co`, así que la semilla crea `admin@livorno.co` como usuario demo aparte, con `app.semilla.password`
(no con `admin-password`, que es solo del ADMIN inicial); en el PC de mesa, con `ADMIN_EMAIL=admin@livorno.co`, la
semilla reutiliza ese usuario y entra con `ADMIN_PASSWORD`. `.\mvnw.cmd clean` necesita red la primera vez (descarga
`maven-clean-plugin`); sin red se puede omitir: `spring-boot:run` compila lo que cambió.

1. Base vacía (el usuario `control_stock` tiene todos los permisos sobre su base; pide la clave):
   `& 'C:\tools\mysql\current\bin\mysql.exe' -u control_stock -p -e "DROP DATABASE control_stock; CREATE DATABASE control_stock CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"`
2. Carga (Flyway crea V1–V16 y la semilla corre unos 4–5 min y termina): `cd backend`, `.\mvnw.cmd clean` y
   `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,local,seed"`. Anota del resumen: duración, ventas (y
   anuladas), unidades perdidas, planes creados / recibidos / en camino, y de la analítica: Holt-Winters y SES, WAPE
   global, % en alerta con y sin tránsito y, **nuevo (B10)**, la línea «corrección por agotados (12 meses antes de
   AAAA-MM): +X unidades frente a Y perdidas comparables = Z×» (esperado 0,5×–2×; en la fase 6 daba ≈ 0,3× contra todas
   las perdidas de 36 meses).
3. Arranque normal: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,local"`; en otra terminal `cd frontend`
   y `npm run dev`. Abre http://localhost:5173.
4. Verificación por rol:
   - [ ] `admin@livorno.co`, **Inicio**: secciones Ventas (con margen y gráfico de 7 días), Inventario (Agotadas, Bajo
         stock y Sin rotación solo como tarjetas; valores a costo y a venta), Apartados y Compras. El bloque «Alertas más
         urgentes» lista 5 (primero agotadas de mayor umbral, luego bajo stock con menor posición / umbral), sin repetir
         los conteos, y «Ver todas las alertas» abre Alertas.
   - [ ] `admin@livorno.co`, **Reportes** (4 pestañas): Rentabilidad del mes anterior completo por producto (gráfico del
         top 10); Libro de ventas por mes de los últimos 12 meses (meses con devoluciones de otros meses pueden ir en
         negativo; sección de anuladas); Valorización por categoría; **Movimientos** del mes anterior por categoría (la
         fila Total cuadra: saldo inicial + entradas − ventas + anulaciones + devoluciones + ajustes + − ajustes − =
         saldo final) y por variante. Exporta PDF y Excel de los cuatro y ábrelos (el Excel del libro y el de
         movimientos traen hoja de detalle; el PDF no).
   - [ ] `contabilidad@livorno.co`: Inicio con margen y valores a costo, sin Apartados ni bloque de alertas; Reportes
         igual que el ADMIN.
   - [ ] `inventario@livorno.co`: Inicio sin ventas ni valores a costo; `/reportes` muestra «Acceso denegado»;
         Analítica → Tendencias → «Exportar PDF»: tabla, gráfico (año anterior punteado) y párrafo de resumen.
   - [ ] `ventas@livorno.co`: Inicio con ventas sin margen ni Compras y con el bloque de alertas; `/reportes` denegado;
         en Analítica solo «Más vendidos» (sin exportar).
   - [ ] Pendiente visual de la fase 6 (rápido): Compras → «Generar sugerencia» como INVENTARIO, aprobar como ADMIN y
         recibir como INVENTARIO un plan en camino; el kardex muestra las ENTRADAS `PLAN_COMPRA/…`.
5. Cifras contra SQL (pide la clave de MySQL):
   `& 'C:\tools\mysql\current\bin\mysql.exe' -u control_stock -p control_stock -e "select sum(va.stock_fisico * p.precio_compra) valor_costo, sum(va.stock_fisico) fisico from variante va join producto p on p.id = va.producto_id;"`
   debe coincidir con el Total de Valorización (con inactivas) y con el saldo final de Movimientos hasta hoy.
   Opcional: `select sum(unidades_observadas_12m), sum(unidades_corregidas_12m) from analitica_producto;` (la diferencia
   entre ambas es el +X del paso 2).
6. Tiempos (RNF-03, < 3 s) con el backend arriba; pide el usuario y la clave de un ADMIN demo:
   ```
   $c = Get-Credential admin@livorno.co
   $b = @{ email = $c.UserName; password = $c.GetNetworkCredential().Password } | ConvertTo-Json
   $h = @{ Authorization = "Bearer $((Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login -ContentType 'application/json' -Body $b).token)" }
   $hasta = Get-Date -Format yyyy-MM-dd; $desde = (Get-Date).AddDays(-730).ToString('yyyy-MM-dd'); $r = "desde=$desde&hasta=$hasta"
   foreach ($u in "/api/panel", "/api/reportes/rentabilidad?$r&agrupacion=VARIANTE", "/api/reportes/libro-ventas?$r&agrupacion=MES", "/api/reportes/valorizacion?agrupacion=VARIANTE", "/api/reportes/movimientos?$r&agrupacion=VARIANTE", "/api/reportes/libro-ventas/exportar?$r&formato=XLSX", "/api/reportes/movimientos/exportar?desde=$((Get-Date).AddDays(-365).ToString('yyyy-MM-dd'))&hasta=$hasta&formato=XLSX", "/api/analitica/tendencias/exportar?formato=PDF") {
     $ms = (Measure-Command { Invoke-WebRequest "http://localhost:8080$u" -Headers $h -UseBasicParsing -OutFile "$env:TEMP\t2.bin" }).TotalMilliseconds
     "{0,8:N0} ms  {1}" -f $ms, $u
   }
   ```
   Si el Excel de movimientos de 12 meses responde 422 (más de 50.000 movimientos), acota a 6 meses y anótalo.
7. Volver a ejecutar el paso 2 sin borrar: debe decir «ya hay ventas en la base, no se hace nada».
8. Ensayo cronometrado de la sustentación (unos 15 min) con `docs/ppi/guion-sustentacion.md`. La víspera de la demo,
   repetir los pasos 1–2 para que las reservas ACTIVAS y SIN_ROTACION queden alineadas con la fecha.

Anota los resultados en "Hecho" (y en REQ-10, RNF-03 y `docs/ppi/datos-demostracion.md`).

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409.
- Semilla: la analítica (ABC, ROP y alertas) corre tras el commit de la carga; si falla, la carga queda confirmada y se
  recupera con "Recalcular" como ADMIN (una nueva ejecución de la semilla dice "ya hay datos").
- Umbral de alertas: fase 4 con `stockMinimo` 47 %; fase 5 con ROP 34,4 % (T2); fase 6 con posición de inventario
  14,4 % (T2; meta ~10–15 % cumplida; 38,8 % si no se contara lo en camino).
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
- PC de mesa: Docker Desktop debe estar arriba (sin él se omiten las pruebas con Testcontainers). **Portátil** (sin
  Docker, MySQL 9.7 nativo, perfil `dev,local` con `backend/config/application-local.properties`): se omiten las pruebas
  con Testcontainers; un commit requiere en verde el resto del backend y el frontend completo, y las de integración las
  valida la CI del PR. `gh` no está en el PATH (`& "C:\Program Files\GitHub CLI\gh.exe"`); no hay Python; PlantUML se
  valida con el JAR de `~/.m2` (ver `docs/ppi/README.md`). En la sesión del 2026-10-24 Claude Code no cargó los agentes
  de `.claude/agents/` (se usó un agente general con las instrucciones de `revisor.md`).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`).
- Las pruebas que borran variantes deben borrar antes `alerta` y `reserva`; las de semilla usan `LimpiezaSemilla`.
- Las reservas ACTIVAS de la semilla vencen a los pocos días: regenerar la víspera de una demo.
- CI fijado en `ubuntu-24.04`; el limitador de login es en memoria (`getRemoteAddr()`).
- CI de la fase 6 (2026-10-21): el job Backend se cancelaba a los ~11 min por falta de memoria del runner: cada contexto
  de prueba con Testcontainers mantiene un MySQL propio y la caché de contextos de Spring (32) llegaba a ~20 MySQL vivos.
  Se fijó `spring.test.context.cache.maxSize=8` (`backend/src/test/resources/spring.properties`). Antes: hasta 20
  contenedores (CI), suite 10 min 35 s en CI y ~14 min en local; después: máximo 8 contenedores vivos y 13 min 08 s en
  local (1269/1269). Si se agregan pruebas con `@MockitoBean`/`@MockitoSpyBean` (contexto nuevo), vigilar ese número.
- Frontend: las pruebas que simulan el login con un adaptador de Axios deben usar `soloLogin` (`src/pruebas/http.ts`);
  un falso que responde lo mismo a todo rompe la página destino según el tiempo (falló en CI con `/compras`).
- Frontend: los fallos intermitentes por carga (una prueba distinta en cada corrida completa) se resolvieron subiendo
  `asyncUtilTimeout` a 3 s (`src/pruebas/setup.ts`, fase 7). Antes: en dos corridas apareció 1 fallo intermitente;
  carga de la máquina con Maven en paralelo. Si reaparece, guardar la salida de `npx vitest run --reporter=verbose`.
- Fase 6: el IDE volvió a compilar en `target/` ("Unresolved compilation problems"); revisar que Java › Autobuild siga
  desactivado. Nunca correr dos Maven a la vez (corrompe `target/` y choca con Testcontainers).
- Problema conocido: `ClassFormatError: Invalid method Code length 0` al arrancar (clase corrupta en `target/`, p. ej.
  `AnaliticaVariante`) se resuelve con `./mvnw clean` (o `.\mvnw.cmd clean`). El usuario desactivó el Auto Build de la
  extensión Java de VS Code, que compilaba en `target/` a la vez que Maven.
- Para demos: regenerar la semilla la víspera (PC de mesa: `docker compose down -v` y carga `dev,seed`; portátil: pasos
  1–2 de la guía T2 con `dev,local,seed`) para que las reservas ACTIVAS
  y la ventana de SIN_ROTACION queden alineadas con la fecha.
