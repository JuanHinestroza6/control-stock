# Plan APROBADO — Fase 6 (Predicción y compras)

Rama `fase-6-prediccion-compras`. Requisitos: REQ-10 (planeación de compras), REQ-09 (tendencias históricas), REQ-04
(cierre de la meta de alertas con la posición de inventario, D4 de ADR-009). Reglas: `docs/dominio.md` (secciones
"Analítica", "Alertas", "Datos semilla", "Máquinas de estado", matriz de roles), ADR-006, ADR-007, ADR-008 y ADR-009.
Algoritmos: `docs/especificaciones/algoritmos-analitica.md` §4 y §5 (se actualiza en el Paso 0 según las decisiones).

**Regla de oro 1:** el pronóstico y la sugerencia nunca tocan stock. La recepción de un plan genera ENTRADAS por
`OperacionInventarioService` → `MovimientoService` (referencia `PLAN_COMPRA/{id}`). El ROP y la clase se siguen
escribiendo solo con el UPDATE dirigido de ADR-009.

## Objetivo
Pronosticar cada noche la demanda mensual de cada producto activo (SES u Holt-Winters elegido por backtesting, con la
demanda corregida por agotados), desagregarla a variantes, usarla en el punto de reorden y en una sugerencia de compra
que el equipo edita, aprueba y recibe; mostrar las tendencias históricas con gráficos y llevar el porcentaje de variantes
en alerta de stock a la meta de ~10–15 % midiendo la posición de inventario (disponible + pedidos aprobados en camino).

## Contradicciones y hallazgos detectados antes de planear
1. **Holt-Winters nunca sería elegible con la semilla actual.** La semilla tiene 24 meses que empiezan a mitad de mes
   (`inicio = fin − 24 meses + 1 día`): al pronosticar el 15 de octubre quedan **23 meses completos** (noviembre a
   septiembre). `docs/especificaciones/algoritmos-analitica.md` exigía 24 meses para HW y reservaba 3 de prueba:
   harían falta 27. Con los datos actuales todo saldría SES y el diferenciador "Holt-Winters" no se vería. Ver D2.
2. **Plan de compras sin cancelación.** El dominio define `BORRADOR → APROBADO → RECIBIDO`. Un pedido aprobado que el
   proveedor nunca entrega quedaría "en camino" para siempre y taparía la alerta de su variante. Ver D5.
3. **Matriz de roles:** INVENTARIO solo "crea borrador" en el plan de compras, pero es el rol que registra las entradas en
   bodega. ¿Quién recibe un pedido? Ver D7.
4. **`docs/especificaciones/algoritmos-analitica.md` §5 (sugerencia):** la fórmula "pedir hasta cubrir L + R"
   aplicada a todas las variantes es una política de revisión periódica (cada plan rellena todo el catálogo, con
   cientos de líneas de 1 unidad), mientras que las alertas disparan por ROP (revisión continua). Ver D6.
5. **Estado de stock vs. alerta:** el dominio dice que `Variante.estadoStock()` usa "el mismo umbral que las alertas". Con
   STOCK_BAJO por posición, una variante con pedido en camino puede verse BAJO sin alerta. Ver D4.
6. **Entradas sobre variantes inactivas → 422** (dominio). Si una variante se desactiva después de aprobar su pedido, la
   mercancía llega igual y no se podría recibir. Ver D5.
7. **Demanda del pronóstico sin definir** (comercial o de inventario) y la entidad `Pronostico` no guarda los
   parámetros ni el backtest que permiten explicar el resultado. Se resuelve en R2/R3.
8. **ArchUnit (ADR-009): "ningún módulo salvo semilla depende de analitica".** El módulo `compra` necesita la sugerencia
   (que vive en analitica) e `inventario` necesita los pedidos en camino (que viven en compra, que a su vez depende de
   inventario para recibir). Se resuelve con dos puertos (R1), igual que `ConsultaReservasActivas`.

**Alcance:** `docs/plan.md` pide pronóstico, tendencias y plan de compras. Este plan agrega cuatro cosas que no están
literalmente allí pero se prometieron en la fase 5 (`progreso.md` y riesgos de ADR-009): la corrección por agotados (D1),
el ROP con la demanda pronosticada (D3), STOCK_BAJO por posición (D4) y la semilla de 36 meses que repone con planes
(D2, D8). Cada una tiene una versión mínima en "Recorte si falta tiempo".

## Fuera de alcance de esta fase
- Exportación PDF de tendencias (CU-05): va con los reportes de la fase 7 (OpenPDF / Apache POI), junto con REQ-03 y
  REQ-07. La pestaña Tendencias de esta fase solo muestra el gráfico y la tabla.
- Proveedores, recepciones parciales múltiples, historial de pronósticos anteriores (solo la última ejecución) y
  ejecución en varias instancias (ShedLock).

## Decisiones confirmadas por el usuario (2026-10-15)

Se eligió la opción recomendada en las ocho: D1 (a), D2 (a), D3 (b), D4 (a), D5 (a), D6 (a), D7 (a), D8 (a), con estos
cambios:
1. **Holt-Winters multiplicativo** en lugar de aditivo (la moda y la semilla tienen estacionalidad multiplicativa: el pico
   de diciembre crece con la empresa). Un producto con algún mes en 0 en su serie usa SES (HW no compite). Ver
   "Algoritmos".
2. **Orden de trabajo con punto de corte** (ver "Tareas"): núcleo primero (A1–A5, B1–B11, F1–F6, T1); al terminarlo se
   para y se reporta al usuario; después B12 (semilla de 36 meses y D8), F7, T2 y documentación.
3. **Exportación PDF de tendencias (CU-05) a la fase 7** (ver "Fuera de alcance").
4. **Recharts lo instala el usuario** (`npm install recharts` en `frontend/`, desde su terminal); F1 empieza pidiéndoselo.

### D1 [ELEGIDA (a)] Demanda que se pronostica y corrección por agotados (censura)
- **(a) Elegida:** serie **mensual por producto** con la *demanda de inventario* de ADR-009 (vendidas − devueltas
  aptas, ventas REGISTRADA, ANULADAS fuera, recorte a 0 por mes), solo **meses completos**, **corregida por
  disponibilidad**: `corregida = observada / max(disponibilidad, 0,5)`, donde `disponibilidad` es la fracción de días
  con stock de las variantes del producto ponderada por su participación (cálculo abajo). Se guardan las dos series.
- (b) Igual sin corrección: se documenta que el pronóstico hereda el sesgo (~2,5–3 % de ventas perdidas).
- (c) Corrección por variante y día: imputar a cada día sin stock la demanda media de la variante.
- Porqué (a): es la corrección clásica de demanda censurada ("ventas / fracción del periodo con stock"), cabe en una
  función pura con ejemplo a mano y deja evidencia medible: la semilla sabe cuántas unidades se perdieron y T2 las
  compara con las que agrega la corrección. (c) imputa ruido día a día en variantes de 0,02 u/día. El piso 0,5 evita
  inflar ×10 un mes casi sin stock. La demanda de inventario (no la comercial) es la que mueve el stock y la que usa el ROP.

### D2 [ELEGIDA (a)] Historia mínima, backtesting y meses de la semilla
- **(a) Elegida:** semilla de **36 meses** (`app.semilla.meses: 36`, ya admitido por `PropiedadesSemilla`) →
  35 meses completos. **Backtesting con origen móvil**: dos cortes (n − 6 y n − 3), cada uno pronostica 3 meses → WAPE
  sobre 6 puntos. HW (multiplicativo) es elegible si el entrenamiento más corto tiene ≥ 24 meses (n ≥ 30) y ningún mes
  de la serie es 0. Hasta B12 (después del corte) la semilla sigue en 24 meses: en la demo todo sale SES y HW se valida
  solo con pruebas unitarias (ver "Tareas").
- (b) Especificación literal (últimos 3 meses de prueba) con semilla de 30 meses: HW elegible (26 de entrenamiento), pero el WAPE
  sale de 3 puntos (julio–septiembre, los meses más planos): una sola racha de ruido decide el método.
- (c) Semilla de 24 meses y HW con inicialización de una sola temporada: sin tendencia inicial confiable y sin backtest
  real para HW.
- Porqué (a): 3 temporadas permiten inicializar HW con dos años y aun así evaluarlo fuera de muestra en 6 meses que
  incluyen el pico de junio (prima). Costo: la carga pasa de ~2 min 10 s a ~3,5–4 min (se ejecuta una vez, la víspera de
  la demo). Con 24 meses todo saldría SES (hallazgo 1).

### D3 [ELEGIDA (b)] ¿El punto de reorden usa la demanda pronosticada?
- (a) No: el ROP sigue con la demanda observada de 90 días (fase 5); el pronóstico solo alimenta la sugerencia de compra.
- **(b) Elegida:** `μL` = demanda **pronosticada** de la variante para los próximos `L` días (tasa diaria del mes de
  cada día); `s` sigue saliendo de la serie diaria observada de 90 días; misma regla NORMAL/POISSON, mismos niveles de
  servicio. Sin pronóstico del producto (nuevo, inactivo o fallo) → fórmula de la fase 5. Se guarda la fuente
  (`PRONOSTICO` | `HISTORICA`).
- (c) `μL = max(observada, pronosticada)`.
- Porqué (b): cierra los dos riesgos que la fase 5 dejó explícitamente para esta fase: el ROP rezagado al entrar a
  noviembre/diciembre y la demanda censurada. Es la historia de la sustentación: "el sistema sube el punto de reorden
  antes del Black Friday". (c) solo sube y nunca baja tras la temporada. Recorte posible: volver a (a) sin tocar el resto.

### D4 [ELEGIDA (a)] Alerta STOCK_BAJO por posición de inventario y métrica
- **(a) Elegida:** `posición = disponible + en camino` (en camino = Σ `cantidadAprobada` de planes APROBADO).
  STOCK_BAJO si `0 < disponible` y `posición ≤ umbral`. **AGOTADO sigue sobre el disponible** (`disponible == 0`, aunque
  haya pedido en camino: no se puede vender). `Variante.estadoStock()` sigue sobre el disponible (hecho físico); la
  interfaz muestra "N en camino" junto al estado. La métrica de ADR-009 no cambia de definición y se agrega la
  contrafactual "en alerta si no se contara lo que viene en camino" para mostrar el efecto.
- (b) (a) y además AGOTADO se omite si hay pedido en camino (pasaría a un aviso informativo).
- (c) Nuevo tipo de alerta PEDIDO_EN_CAMINO en lugar de cerrar STOCK_BAJO.
- Porqué (a): es la definición de libro (se compara la posición, no el físico, con el ROP) y ataca la causa del 34,4 %
  (alertas que siguen activas todo el lead time aunque ya se pidió). (b) maquilla el número escondiendo agotados reales;
  (c) agrega un tipo y no baja la carga de avisos. **Estimación honesta:** AGOTADO ya es ~9 % (27 de 299: ~15 variantes
  que la semilla deja agotar a propósito + quiebres reales), así que la meta queda en ~11–17 %; T2 lo mide y, si pasa de
  15 %, se explica con el desglose sin cambiar la definición.

### D5 [ELEGIDA (a)] Ciclo de vida del plan y recepción
- **(a) Elegida:** `BORRADOR → APROBADO → RECIBIDO`, más **CANCELADO** desde BORRADOR (descartar) o APROBADO (el
  proveedor no entregó; motivo obligatorio). **Recepción única**: por línea se indica la cantidad recibida (0 a
  100.000, por defecto la aprobada) y el costo; lo no recibido deja de estar en camino (si llega después, otro plan o una
  entrada). La recepción **sí se permite sobre variantes inactivas** (unidades físicas que ya se pidieron, igual que
  ajustes y devoluciones); aprobar exige variantes y productos activos.
- (b) Recepciones parciales (estado RECIBIDO_PARCIAL, pendiente por línea, varias recepciones).
- (c) Dominio literal, sin cancelación ni cantidades recibidas distintas.
- Porqué (a): sin CANCELADO el "en camino" puede quedar inflado para siempre y ocultar alertas (rompe D4). La recepción
  única cubre el caso común (llega menos de lo pedido) sin otra máquina de estados.

### D6 [ELEGIDA (a)] Política de la sugerencia de compra
- **(a) Elegida (s, S):** candidatas = variantes activas de productos activos con `posición ≤ umbral` (exactamente
  las que tendrían AGOTADO o STOCK_BAJO). Cantidad = hasta `S = umbral + demanda pronosticada de R días después del lead
  time` (R = 30, configurable), redondeada hacia arriba al múltiplo de `loteMinimo`; las de cantidad 0 no entran. Se
  omiten (y se informan) las variantes que ya están en otro BORRADOR. Justificación legible por línea.
- (b) Especificación literal: toda variante con `cobertura(L + R) + SS − disponible − en camino > 0` (revisión periódica).
- (c) (a) solo para clases A y B.
- Porqué (a): "el plan resuelve las alertas": aprobarlo cierra los STOCK_BAJO de sus variantes y la posición sube por
  encima del ROP. Con ROP = `ceil(μL + SS)`, `S` coincide con la fórmula de la especificación (`μ(L+R) + SS`), solo cambia qué
  variantes entran. (b) genera planes de cientos de líneas de 1 unidad para la cola.

### D7 [ELEGIDA (a)] Roles del plan de compras
- **(a) Elegida:** crear (sugerido o manual) y editar BORRADOR: ADMIN e INVENTARIO. Aprobar: solo ADMIN. Cancelar:
  ADMIN (cualquier estado abierto); INVENTARIO solo descarta BORRADOR. **Recibir: ADMIN e INVENTARIO** (bodega). Ver:
  ADMIN, INVENTARIO y CONTABILIDAD (con costos). VENDEDOR: 403. Tendencias y pronóstico: ADMIN, INVENTARIO y
  CONTABILIDAD (VENDEDOR sigue solo con "Más vendidos").
- (b) Matriz literal: INVENTARIO solo crea borradores; editar, aprobar, cancelar y recibir solo ADMIN.
- Porqué (a): INVENTARIO ya registra entradas (matriz "Entradas y ajustes: Sí"); recibir un pedido es una entrada con
  referencia. Mantiene la segregación clave: quien pide no aprueba (aprobar compromete dinero).

### D8 [ELEGIDA (a)] Semilla: reposición mediante planes de compra
- **(a) Elegida:** cada reposición de la simulación pasa por el plan de compras real: INVENTARIO crea el plan (un
  plan por producto y día de pedido, con el costo de la llegada), ADMIN lo aprueba a la misma hora e INVENTARIO lo recibe
  al llegar. **Se permiten pedidos cuya llegada cae después de `fin`**: quedan APROBADOS (en camino) al terminar. La
  política de pedido de la semilla no cambia (su propio punto de pedido; no usa el ROP del sistema, que no existe hasta
  el cierre).
- (b) La historia sigue con entradas directas y solo los pedidos abiertos al final se crean como planes.
- (c) En las últimas semanas la semilla sigue la sugerencia del sistema (exigiría correr la analítica dentro de la
  simulación).
- Porqué (a): la demo arranca con historial real de planes (RECIBIDOS, algunos en camino para mostrar "Recibir") y la
  posición de inventario del cierre es coherente con la simulación (hoy los pedidos que llegarían después de `fin` ni
  siquiera se hacen: por eso las variantes del final quedan en alerta). Verificable: en camino = el arreglo `enCamino`
  del simulador.

## Decisiones técnicas resueltas por el equipo (se documentan en ADR-010)
- R1 **Módulo `compra` y puertos.** `compra` depende de `catalogo`, `inventario` y `usuario`. Dos puertos con el patrón de
  `ConsultaReservasActivas`:
  - `inventario.servicio.ConsultaPedidosEnCamino` (lo implementa `compra`): `enCamino(varianteId)` y
    `enCaminoPorVariante()`. Así `AlertaService` usa la posición sin depender de `compra`.
  - `compra.servicio.FuenteSugerenciaCompra` (lo implementa `analitica`): devuelve las líneas sugeridas. `compra` no
    depende de `analitica`; la regla ArchUnit de ADR-009 queda intacta.
  ArchUnit nuevo: `catalogo`, `inventario`, `venta` y `usuario` no dependen de `compra`; `compra` no depende de `venta`
  ni de `analitica`.
- R2 **Instantáneas explicables** (mismo principio que R2 de ADR-009, "lo mostrado = lo aplicado"):
  `analitica_producto` (método, parámetros α/β/γ, WAPE de cada método, motivo por el que HW no compitió, meses de
  historia, unidades observadas y corregidas) y `analitica_producto_mes` (serie mensual observada, disponibilidad, corregida y los pronósticos del
  backtest por mes evaluado). Se reemplazan en cada ejecución. `pronostico` guarda los 3 meses futuros por producto
  (`variante_id` null) y por variante.
- R3 **Orquestación** (`EjecucionAnaliticaService`): registrar → **pronóstico** (transacción propia, todo o nada) →
  ABC + ROP (usa el pronóstico en memoria; si falló, ROP histórico) → alertas (con posición) → registro con métricas
  nuevas. Si el pronóstico falla la ejecución queda FALLIDA con mensaje "El pronóstico falló; el ROP usó la demanda
  histórica" (el ABC/ROP sí se aplicó). Mismo cron, misma guardia, mismo recálculo manual.
- R4 **Corte mensual:** se pronostica con los meses completos anteriores al mes en curso (`[mesInicio, primer día del
  mes actual)`); los 3 periodos pronosticados son el mes actual y los dos siguientes. El resultado es el mismo todos los
  días del mes salvo correcciones de ventas pasadas.
- R5 **Concurrencia de las escrituras del pronóstico:** `pronostico` tiene FK a `variante`; sus INSERT toman S sobre las
  variantes. Se insertan **en orden ascendente de `variante_id`** (el mismo orden en que las ventas bloquean), así no hay
  ciclo de espera con una venta de varias líneas (razonamiento en ADR-010). Las otras tablas nuevas no referencian
  `variante`.
- R6 **Plan de compras:**
  - Número visible `PC-000012`. `@Version` en `PlanCompra`: editar o aprobar con una versión vieja → 409 (dos personas
    editando el mismo borrador no se pisan).
  - Toda transición bloquea la fila del plan (`PESSIMISTIC_WRITE`) y verifica el estado (409 "El plan PC-000012 ya está
    APROBADO."). Orden de bloqueo: plan → variantes (estas en orden de id, como las ventas).
  - Recepción: una ENTRADA por línea con cantidad > 0 vía `OperacionInventarioService` (nuevo método interno con
    referencia `PLAN_COMPRA/{id}` y permiso de variantes inactivas), mismas reglas: mismo costo por producto (422),
    `precioCompra` = último costo > 0. Todas las líneas en 0 → 422 ("use Cancelar").
  - Aprobar y cancelar un APROBADO publican `PosicionCambiada(varianteId)` por línea (`inventario.evento`); el
    `AlertaListener` las re-evalúa AFTER_COMMIT. Recibir ya publica `StockCambiado`.
  - Sin borrado físico; sin proveedor (fuera de alcance; un plan puede mezclar productos).
  - Fecha esperada de llegada (calculada, no guardada) = `aprobadoEn + max(leadTimeDias)` de sus productos; la interfaz
    marca "atrasado" si ya pasó.
- R7 **Tendencias (REQ-09)** usan la *demanda comercial* (unidades netas e ingreso neto de ADR-009, igual que "Más
  vendidos") por mes, con el mismo mes del año anterior y la variación. El pronóstico usa la de inventario (D1); la
  interfaz rotula cada gráfico ("ventas netas" vs. "demanda").
- R8 **Recharts** llega en esta fase (ADR-001), cargado de forma diferida (`React.lazy`) solo en las pestañas con
  gráficos. **Lo instala el usuario** desde su terminal (`npm install recharts` dentro de `frontend/`; requiere red y
  una versión compatible con React 18); F1 empieza pidiéndoselo y no se instala sin su confirmación.
- R9 **Rendimiento:** la disponibilidad diaria se reconstruye en memoria desde el kardex (una consulta ordenada por
  `idx_movimiento_variante_fecha`, ~60–80 mil filas con 36 meses). HW por rejilla: 125 combinaciones × 3 ajustes × 30
  productos × 35 meses, despreciable. Meta del recálculo manual con pronóstico: < 8 s (límite 15 s, R7 de ADR-009).

## Algoritmos (paquete `analitica.calculo`, funciones puras)

### Serie mensual y disponibilidad (`SerieMensual`, `CorrectorCensura`)
```
mesCorte = primer día del mes actual (Clock). Meses de la serie: desde el primer mes completo después de
  producto.creadoEn (si se creó el día 1 a las 00:00, ese mes cuenta) hasta mesCorte − 1, como máximo 36.
y_m = Σ vendidas (fecha de la venta, REGISTRADA) − Σ devueltas aptas (fecha de la devolución), recorte a 0 por mes.

Disponibilidad diaria de la variante v (desde el kardex, orden fecha, id):
  disponible al abrir el día d = disponible tras el último movimiento anterior a d (0 antes del primero).
  d es "sin stock" si al abrir es 0 y ningún movimiento del día lo deja > 0. Días antes de creadoEn: no cuentan.
  frac_{v,m} = días con stock / días existentes de v en el mes m.
Pesos: w_v = participación de v en la demanda observada del producto en toda la serie (total 0 → pesos iguales).
  Solo variantes activas hoy (las inactivas aportan su demanda a y_m pero no a la disponibilidad).
disp_m = Σ_v w_v × frac_{v,m} / Σ_{v existentes en m} w_v
corregida_m = y_m = 0 ? 0 : y_m / max(disp_m, 0,5)            (piso app.pronostico.disponibilidad-minima)
```
Ejemplo a mano: dos variantes con w = 0,75 / 0,25; la segunda sin stock 15 de 30 días → disp = 0,75 + 0,25 × 0,5 =
0,875; observada 70 → corregida 80. Mes sin stock en todas → disp 0 → piso 0,5 → ×2.

### Suavizado exponencial simple (`SuavizadoExponencial`)
```
nivel_1 = y_1 ; nivel_t = α·y_t + (1 − α)·nivel_{t−1} ; pronóstico_{t+h} = nivel_n
α ∈ {0,1 … 0,9}: menor SSE = Σ_{t=2..n} (y_t − nivel_{t−1})² ; empate → menor α.
```
Ejemplo: α = 0,5 sobre 10, 12, 14 → niveles 10, 11, 12,5 → pronóstico 12,5.

### Holt-Winters multiplicativo, m = 12 (`HoltWintersMultiplicativo`)
Decisión del usuario: la estacionalidad de la moda (y de la semilla: `λ = base × factorMes × 1,015^mes`) es
**multiplicativa**: el pico de diciembre (×1,6) crece con la empresa. El modelo aditivo supondría un pico de amplitud fija
en unidades y lo subestimaría a medida que la tendencia sube.

Requisitos (si no se cumplen, HW no compite y el producto usa SES; el motivo se guarda en `analitica_producto`):
- n ≥ 24 meses en el entrenamiento (`POCA_HISTORIA`);
- **ningún mes de la serie usada en 0** (`MES_EN_CERO`): un índice estacional 0 haría el modelo inestable (dividir por
  s_{t−12}) y "cero por ciento de lo normal" no es estacionalidad sino falta de demanda. Se evalúa sobre la serie
  corregida completa (los entrenamientos del backtest son prefijos de ella);
- nivel inicial positivo en los 12 meses del primer año (`NIVEL_NO_POSITIVO`, posible con tendencia muy negativa).

Inicialización con los dos primeros años; índices como **razones** respecto de la tendencia, normalizados a media 1:
```
ȳ1, ȳ2 = promedios del año 1 y 2 ; tend_0 = (ȳ2 − ȳ1) / 12 ; nivel_0 = ȳ1 − 6,5 · tend_0
r_i = y_i / (nivel_0 + i · tend_0), i = 1..12
s_i = r_i × 12 / Σ r_j                                  (media 1)
Para t = 1..n (s_{t−12} = s_i inicial cuando t ≤ 12):
  ŷ_t     = (nivel_{t−1} + tend_{t−1}) × s_{t−12}        (error de un paso, entra en el SSE)
  nivel_t = α(y_t / s_{t−12}) + (1 − α)(nivel_{t−1} + tend_{t−1})
  tend_t  = β(nivel_t − nivel_{t−1}) + (1 − β)·tend_{t−1}
  s_t     = γ(y_t / nivel_t) + (1 − γ)·s_{t−12}
pronóstico_{n+h} = max(0, (nivel_n + h·tend_n) × s_{n−12+((h−1) mod 12)+1})
(α, β, γ) ∈ {0,1; 0,3; 0,5; 0,7; 0,9}³ por menor SSE; empate → orden lexicográfico ascendente.
Una combinación cuyo nivel_t llega a ≤ 0 durante el ajuste se descarta; si se descartan todas → SES
(NIVEL_NO_POSITIVO).
```
Pruebas a mano:
- `y_t = 100 × S_t` (sin tendencia, `S` de media 1, p. ej. los factores de la semilla normalizados) → ȳ1 = 100,
  tend_0 = 0, s_i = S_i y pronóstico **exacto** (error < 1e-9) para cualquier (α, β, γ).
- Ejemplo de inicialización a mano con 24 meses: año 1 = 12 meses de 10 salvo diciembre 16 (ȳ1 = 10,5), año 2 = 12 meses
  de 11 salvo diciembre 17,6 (ȳ2 = 11,55) → tend_0 = 0,0875; nivel_0 = 9,93125; se verifican r_1, r_12 y la media 1 de s.
- `y_t = (100 + 2t) × S_t`: el pronóstico no es exacto con esta inicialización (la media del año mezcla tendencia y
  estación); se exige WAPE < 2 % a 3 meses.
- Serie con un mes en 0 → HW no elegible (MES_EN_CERO); n = 23 → POCA_HISTORIA; tendencia que lleva el nivel a ≤ 0 →
  combinación descartada.

### Selección por backtesting (`SelectorPronostico`)
```
n = meses de la serie corregida.
n < 3                → SIN_HISTORIA (sin pronóstico; ROP y sugerencia usan la demanda histórica)
3 ≤ n < 9            → SES sin backtest (WAPE null)
9 ≤ n < 30           → solo SES, WAPE del backtest (motivo POCA_HISTORIA)
n ≥ 30               → SES vs HW multiplicativo, salvo MES_EN_CERO o NIVEL_NO_POSITIVO (solo SES)
Backtest: orígenes o ∈ {n − 6, n − 3}; con y_1..y_o se ajusta (rejilla incluida) y se pronostican o+1..o+3.
WAPE = Σ |y − ŷ| / Σ y sobre los 6 puntos (Σ y = 0 → WAPE null → SES).
Elegido = menor WAPE; diferencia < 0,001 → SES (parsimonia). Se reajusta el elegido con toda la serie y se
pronostican h = 1..3 (mes actual y dos siguientes). Cantidades en BigDecimal escala 2 HALF_UP.
WAPE global de la ejecución = Σ|e| / Σ y de todos los productos con backtest.
```
Ejemplo WAPE: reales 10, 20, 30; pronóstico 12, 18, 33 → 7 / 60 = 0,1167.

### Desagregación a variantes (`Desagregador`)
```
Ventana 180 días [corte − 180, corte). part_v = demanda_v / max(fracConStock_v, 0,5), normalizada entre las
variantes activas del producto (corrige que una talla agotada parezca impopular). Total 0 → todas 0.
pronóstico_v,mes = pronóstico_producto,mes × part_v (escala 2).
```

### ROP con demanda pronosticada (D3 b; `CalculadoraReorden` sobrecargada)
```
tasa_v(día) = pronóstico_v(mes del día) / días del mes ; más allá del horizonte se usa la tasa del último mes.
μL = Σ_{i=0..L−1} tasa_v(corte + i) ;  s = desviación de la serie diaria observada de 90 días (fase 5).
n < 30 → SIN_HISTORIA (igual) ; μL = 0 → SIN_DEMANDA ; μL ≥ 5 → NORMAL ceil(μL + z·s·√L) ; μL < 5 → POISSON(μL).
Producto sin pronóstico → fórmula de la fase 5 (fuente HISTORICA).
```
Ejemplo a mano: pronóstico de la variante octubre 31 (1/día), noviembre 60 (2/día); corte 25-oct, L = 15 → 7 × 1 + 8 × 2
= μL 23; s = 1,2, clase A → SS = 1,65 × 1,2 × √15 = 7,67 → ROP = ceil(30,67) = 31.

### Sugerencia de compra (D6 a; `CalculadoraSugerenciaCompra`)
```
umbral = puntoReorden ?? stockMinimo ; posición = disponible + enCamino
Candidata si variante y producto activos, posición ≤ umbral y no está en otro BORRADOR.
demandaR = Σ tasa_v en los R días que siguen al lead time [corte + L, corte + L + R)   (R = 30)
  (sin pronóstico: d̄ observada × R)
S = umbral + demandaR ; sin pronóstico ni demanda y ROP null: S = 2 × stockMinimo
q = ceil(S − posición) ; q ≤ 0 → no entra ; q = ceil(q / loteMinimo) × loteMinimo
justificación = "ROP 31 + demanda 30 d 68 − disponible 10 − en camino 0 = 89 → lote 6: 90"
```
Ejemplo (continúa el anterior): diciembre 93 (3/día); demanda del 9-nov al 8-dic = 22 × 2 + 8 × 3 = 68; S = 99; posición
10 → 89 → lote 6 → 90. Variante SIN_DEMANDA agotada: S = 0 → no entra (no se compra lo que no se vende).

## Cambios en base de datos
`V15__crear_tablas_pronostico.sql` (InnoDB, utf8mb4, `utf8mb4_0900_ai_ci`, DATETIME(6)):
- `pronostico(id BIGINT AI PK, producto_id BIGINT NOT NULL FK, variante_id BIGINT NULL FK, periodo DATE NOT NULL
  (día 1), cantidad_proyectada DECIMAL(10,2) NOT NULL, metodo VARCHAR(12) NOT NULL, error_wape DECIMAL(7,4) NULL,
  calculado_en DATETIME(6) NOT NULL, ejecucion_id BIGINT NOT NULL FK, variante_clave BIGINT AS (COALESCE(variante_id, 0))
  STORED)`. CHECK: `metodo IN ('SES','HOLT_WINTERS')`, `cantidad_proyectada >= 0`, `DAY(periodo) = 1`. UNIQUE
  `(producto_id, variante_clave, periodo)`; índices `(variante_id, periodo)`, `(ejecucion_id)`.
- `analitica_producto(producto_id BIGINT PK FK, ejecucion_id BIGINT NOT NULL FK, meses_historia INT NOT NULL, metodo
  VARCHAR(12) NOT NULL, alfa DECIMAL(3,2) NULL, beta DECIMAL(3,2) NULL, gamma DECIMAL(3,2) NULL, wape_ses DECIMAL(7,4)
  NULL, wape_hw DECIMAL(7,4) NULL, error_wape DECIMAL(7,4) NULL, motivo_sin_hw VARCHAR(20) NULL,
  unidades_observadas_12m INT NOT NULL, unidades_corregidas_12m DECIMAL(10,2) NOT NULL, calculado_en DATETIME(6) NOT
  NULL)`. CHECK: `metodo IN ('SES','HOLT_WINTERS','SIN_HISTORIA')`, `(metodo = 'HOLT_WINTERS') = (beta IS NOT NULL)`,
  `motivo_sin_hw IN ('POCA_HISTORIA','MES_EN_CERO','NIVEL_NO_POSITIVO')`, `metodo <> 'HOLT_WINTERS' OR motivo_sin_hw IS
  NULL`. (HOLT_WINTERS significa siempre el multiplicativo.)
- `analitica_producto_mes(producto_id BIGINT FK, periodo DATE, ejecucion_id BIGINT NOT NULL FK, unidades_observadas INT
  NOT NULL, disponibilidad DECIMAL(5,4) NOT NULL, unidades_corregidas DECIMAL(10,2) NOT NULL, backtest_ses DECIMAL(10,2)
  NULL, backtest_hw DECIMAL(10,2) NULL, PK (producto_id, periodo))`. CHECK `disponibilidad BETWEEN 0 AND 1`.
- `ALTER TABLE analitica_variante ADD fuente_demanda VARCHAR(10) NOT NULL DEFAULT 'HISTORICA', ADD demanda_lead_time
  DECIMAL(10,2) NULL, ADD tasa_pronosticada DECIMAL(10,4) NULL` + CHECK `fuente_demanda IN ('PRONOSTICO','HISTORICA')`.
- `ALTER TABLE ejecucion_analitica ADD productos_pronosticados INT NULL, ADD con_holt_winters INT NULL, ADD con_ses INT
  NULL, ADD wape_global DECIMAL(7,4) NULL, ADD en_alerta_sin_transito INT NULL, ADD con_pedido_en_camino INT NULL`.

`V16__crear_tablas_compra.sql`:
- `plan_compra(id BIGINT AI PK, estado VARCHAR(10) NOT NULL, origen VARCHAR(10) NOT NULL, observacion VARCHAR(255) NULL,
  creado_en DATETIME(6) NOT NULL, creado_por BIGINT NOT NULL FK usuario, aprobado_en DATETIME(6) NULL, aprobado_por
  BIGINT NULL FK, recibido_en DATETIME(6) NULL, recibido_por BIGINT NULL FK, cancelado_en DATETIME(6) NULL, cancelado_por
  BIGINT NULL FK, motivo_cancelacion VARCHAR(200) NULL, ejecucion_id BIGINT NULL FK ejecucion_analitica, version INT NOT
  NULL, actualizado_en DATETIME(6) NOT NULL)`. CHECK: `estado IN ('BORRADOR','APROBADO','RECIBIDO','CANCELADO')`,
  `origen IN ('SUGERIDO','MANUAL')`, `(estado = 'RECIBIDO') = (recibido_en IS NOT NULL)`, `(estado = 'CANCELADO') =
  (cancelado_en IS NOT NULL AND motivo_cancelacion IS NOT NULL)`, `estado NOT IN ('APROBADO','RECIBIDO') OR aprobado_en
  IS NOT NULL`. Índices `(estado, creado_en)` y uno por FK.
- `detalle_plan(id BIGINT AI PK, plan_id BIGINT NOT NULL FK, variante_id BIGINT NOT NULL FK, cantidad_sugerida INT NULL,
  cantidad_aprobada INT NOT NULL, costo_estimado DECIMAL(12,2) NOT NULL, justificacion VARCHAR(255) NULL,
  cantidad_recibida INT NULL, costo_recibido DECIMAL(12,2) NULL)`. CHECK: `cantidad_aprobada BETWEEN 1 AND 100000`,
  `costo_estimado >= 0`, `cantidad_recibida IS NULL OR cantidad_recibida >= 0`, `(cantidad_recibida IS NULL) =
  (costo_recibido IS NULL)`. UNIQUE `(plan_id, variante_id)`; índice `(variante_id, plan_id)` (consulta de en camino).
- Sin cambios en `movimiento`: `referencia_tipo = 'PLAN_COMPRA'` ya cabe (VARCHAR(20), sin CHECK de valores).

## Archivos a crear o modificar (orden de dependencia)
Migraciones: `V15__crear_tablas_pronostico.sql`, `V16__crear_tablas_compra.sql`.

Modelo
- `analitica/modelo/Pronostico`, `AnaliticaProducto`, `AnaliticaProductoMes`, `MetodoPronostico`, `FuenteDemanda`;
  `AnaliticaVariante` y `EjecucionAnalitica` (+ columnas; `ResultadoEjecucion` ampliado).
- `compra/modelo/PlanCompra` (`@Version`, fábricas `sugerido`/`manual`, `reemplazarLineas`, `aprobar`, `cancelar`,
  `recibir`; sin setters), `DetallePlan`, `EstadoPlan`, `OrigenPlan`.

Cálculo (puro)
- `analitica/calculo/SerieMensual`, `CorrectorCensura`, `SuavizadoExponencial`, `HoltWintersMultiplicativo`,
  `SelectorPronostico` (+ `ResultadoPronostico`), `Desagregador`, `CalculadoraSugerenciaCompra`;
  `CalculadoraReorden` (sobrecarga con `μL` externo).

Configuración
- `analitica/config/PropiedadesPronostico` (`app.pronostico`: meses-historia 36, disponibilidad-minima 0.5, ventana-mezcla
  180, horizonte 3, meses-backtest 6) y `compra/config/PropiedadesCompra` (`app.compras.periodo-revision-dias` 30,
  `max-lineas` 300); `application.yml`.

Repositorio
- `analitica/repositorio/ConsultasDemandaMensual` (demanda mensual por producto, demanda 180 d por variante),
  `ConsultasDisponibilidad` (kardex ordenado por variante para los productos activos), `PronosticoRepository`,
  `AnaliticaProductoRepository`, `AnaliticaProductoMesRepository`; `ConsultasEstadoAlertas` (+ métricas nuevas).
- `compra/repositorio/PlanCompraRepository` (`bloquear`, `buscar` paginado), `DetallePlanRepository` (en camino por
  variante, variantes en BORRADOR).
- `inventario/servicio/ConsultaPedidosEnCamino` (puerto).

Servicio
- `analitica/servicio/PronosticoService` (`calcularYGuardar(ejecucionId)`, `@Transactional`), `EjecucionAnaliticaService`
  y `CalculoAnaliticoService` (pronóstico en el ROP), `ConsultaPronosticoService`, `TendenciasService`,
  `SugerenciaCompraAnalitica` (implementa `FuenteSugerenciaCompra`).
- `compra/servicio/PlanCompraService`, `FuenteSugerenciaCompra` (puerto), `PedidosEnCaminoJpa` (implementa el puerto de
  inventario).
- `inventario/servicio/OperacionInventarioService` (+ `registrarEntradaDePlan`), `AlertaService` (posición),
  `AlertaListener` (+ `PosicionCambiada`), `InventarioService` (+ `enCamino`); `inventario/evento/PosicionCambiada`.

DTO
- `analitica/dto/`: `FiltroTendencias`, `TendenciaResponse`, `PuntoTendencia`, `PronosticoProductoResponse`,
  `PronosticoDetalleResponse`, `PuntoSerieMensual`, `PronosticoVarianteResponse`; `EjecucionAnaliticaResponse` y
  `ReordenVarianteResponse` ampliados.
- `compra/dto/`: `SugerirPlanRequest`, `CrearPlanRequest`, `LineaPlanRequest`, `ActualizarPlanRequest`,
  `AprobarPlanRequest`, `CancelarPlanRequest`, `RecibirPlanRequest`, `LineaRecepcionRequest`, `PlanCompraResumenResponse`,
  `PlanCompraDetalleResponse`, `LineaPlanResponse`, `FiltroPlanes`.
- `inventario/dto/InventarioItemResponse` (+ `enCamino`).

Web
- `analitica/web/AnaliticaController` (tendencias, pronósticos); `compra/web/PlanCompraController`.

Semilla
- `PropiedadesSemilla`/`application-seed.yml` (36 meses), `SimuladorDiario` (reposición por planes, pedidos que llegan
  después de `fin`), `VerificadorConsistencia` (en camino = simulador), `ResumenSemilla` (planes por estado, pronóstico,
  % en alerta con y sin tránsito).

Arquitectura (pruebas): `DependenciasEntreModulosTest` (reglas de R1).

Frontend
- `tipos/compras.ts`, `api/compras.ts`, `hooks/useCompras.ts`; `tipos/analitica.ts`, `api/analitica.ts`,
  `hooks/useAnalitica.ts` (tendencias, pronósticos); `lib/permisos.ts` (`puedeGestionarPlanes`, `puedeAprobarPlan`,
  `puedeRecibirPlan`, `puedeVerCompras`); `tipos/inventario.ts` (+ `enCamino`).
- `componentes/graficos/GraficoSerieMensual.tsx` (Recharts, carga diferida); `componentes/dominio/BadgeEstadoPlan.tsx`,
  `BadgeMetodoPronostico.tsx`.
- `paginas/analitica/PestanaTendencias.tsx`, `PestanaPronostico.tsx`, `DetallePronostico.tsx`; `PanelEjecucion.tsx`
  (métricas nuevas); `PestanaReorden.tsx` (en camino, posición, fuente de demanda).
- `paginas/compras/ComprasPage.tsx`, `PlanCompraDetallePage.tsx`, `PlanNuevoPage.tsx`, `ModalRecibirPlan.tsx`,
  `ModalCancelarPlan.tsx`; `App.tsx` (rutas reales `/compras…`).
- `paginas/inventario/InventarioPage.tsx`, `paginas/alertas/AlertasPage.tsx` (en camino; texto de STOCK_BAJO).

## Endpoints (contrato fijo: backend y frontend en paralelo)
Roles: **AN** `hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')`; **GI** `hasAnyRole('ADMIN','INVENTARIO')`; **AD**
`hasRole('ADMIN')`. Página VIA_DTO, `ProblemDetail`, periodos `YYYY-MM`, fechas ISO locales.

| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/analitica/tendencias?desde&hasta&categoriaId&productoId` | AN | — | 200 `TendenciaResponse`; 400 |
| `GET /api/analitica/pronosticos?categoriaId&q&metodo&page&size&sort` | AN | — | 200 `Page<PronosticoProductoResponse>` |
| `GET /api/analitica/pronosticos/{productoId}` | AN | — | 200 `PronosticoDetalleResponse`; 404 |
| `GET /api/analitica/reorden` (cambio) | AN | — | ítems `+ enCamino, posicion, fuenteDemanda, demandaLeadTime, tasaPronosticada` |
| `GET /api/analitica/ejecuciones/ultima`, `POST …/recalcular` (cambio) | sin cambio | — | `+ productosPronosticados, conHoltWinters, conSes, wapeGlobal, enAlertaSinTransito, conPedidoEnCamino` |
| `GET /api/inventario` (cambio) | sin cambio | — | ítems `+ enCamino` |
| `GET /api/compras/planes?estado&page&size&sort` | AN | — | 200 `Page<PlanCompraResumenResponse>` |
| `GET /api/compras/planes/{id}` | AN | — | 200 `PlanCompraDetalleResponse`; 404 |
| `POST /api/compras/planes/sugerido` | GI | `SugerirPlanRequest` | 201 `PlanCompraDetalleResponse`; 422 sin variantes por reponer |
| `POST /api/compras/planes` | GI | `CrearPlanRequest` | 201 `PlanCompraDetalleResponse`; 400; 422 |
| `PUT /api/compras/planes/{id}` | GI | `ActualizarPlanRequest` | 200; 409 versión/estado; 400; 422 |
| `POST /api/compras/planes/{id}/aprobar` | AD | `AprobarPlanRequest` | 200; 409; 422 |
| `POST /api/compras/planes/{id}/cancelar` | GI (INVENTARIO solo BORRADOR → 403) | `CancelarPlanRequest` | 200; 409 |
| `POST /api/compras/planes/{id}/recibir` | GI | `RecibirPlanRequest` | 200; 400; 409; 422 |

- `TendenciaResponse { desde, hasta, categoriaId, productoId, puntos: [{ periodo, unidades, ingreso, unidadesAnioAnterior,
  ingresoAnioAnterior, variacionUnidades, variacionIngreso (%, escala 1, null si el año anterior es 0) }], totalUnidades,
  totalIngreso }`. Por defecto los últimos 24 meses completos; máximo 36; `desde ≤ hasta` y `hasta` ≤ mes actual (400).
- `PronosticoProductoResponse { productoId, codigo, nombre, categoria, metodo, errorWape, wapeSes, wapeHw, motivoSinHw
  (texto amable en la interfaz: "menos de 30 meses", "tiene meses sin demanda"), mesesHistoria,
  unidadesUltimos12m, unidadesCorregidas12m, proximos: [{ periodo, cantidad }] }`. Orden: `codigo` (defecto),
  `errorWape`.
- `PronosticoDetalleResponse { …resumen, parametros: { alfa, beta, gamma }, serie: [{ periodo, observada, disponibilidad,
  corregida, backtestSes, backtestHw }], pronostico: [{ periodo, cantidad }], variantes: [{ varianteId, sku, talla, color,
  participacion, pronostico: [{ periodo, cantidad }] }], calculadoEn }`. Producto sin cálculo → 200 con `metodo` null y
  listas vacías.
- `PlanCompraResumenResponse { id, numero, estado, origen, creadoEn, creadoPor, aprobadoEn, aprobadoPor, recibidoEn,
  lineas, unidades, costoEstimadoTotal, llegadaEsperada, atrasado }`.
- `PlanCompraDetalleResponse { …resumen, version, observacion, motivoCancelacion, canceladoEn, canceladoPor, omitidas:
  [sku] (solo al sugerir), lineas: [{ id, varianteId, sku, productoId, productoCodigo, productoNombre, talla, color,
  claseAbc, cantidadSugerida, cantidadAprobada, costoEstimado, subtotal, justificacion, cantidadRecibida, costoRecibido,
  disponibleActual, enCaminoActual, varianteActiva }] }`.
- `SugerirPlanRequest { categoriaId?, observacion? (≤255) }`. `CrearPlanRequest { observacion?, lineas: [{ varianteId,
  cantidad 1–100000, costoEstimado? (≥ 0; por defecto precioCompra) }] (1–300, sin repetir → 400) }`.
  `ActualizarPlanRequest { version, observacion?, lineas (igual; reemplaza; conserva sugerida y justificación de las
  variantes que siguen) }`. `AprobarPlanRequest { version }`. `CancelarPlanRequest { motivo 5–200 }`.
  `RecibirPlanRequest { observacion?, lineas: [{ detalleId, cantidadRecibida 0–100000, costoUnitario ≥ 0 }] }`: todas
  las líneas del plan exactamente una vez (400).

## Reglas de negocio y casos borde
- Ventas ANULADAS fuera del pronóstico, las tendencias y la desagregación; devoluciones aptas restan en la demanda de
  inventario, todas en la comercial (tendencias).
- Producto con < 3 meses completos → SIN_HISTORIA: sin filas en `pronostico`; ROP y sugerencia con la demanda histórica.
- Serie toda en cero → SES con pronóstico 0, WAPE null; ROP SIN_DEMANDA; no entra en la sugerencia.
- Productos inactivos: sin pronóstico (sus filas se eliminan en la siguiente ejecución); variantes inactivas: sin fila de
  desagregación.
- Mes actual nunca entra en la serie (parcial). Una venta anulada de un mes pasado cambia la serie en la siguiente
  ejecución.
- Sugerencia: sin ninguna ejecución analítica → igual funciona con `stockMinimo` y demanda histórica (umbral de respaldo);
  ninguna candidata con cantidad > 0 → 422 "No hay variantes por reponer". Variantes en otro BORRADOR se omiten y se
  listan en `omitidas`.
- Editar/aprobar con `version` vieja → 409; plan no BORRADOR → 409. Aprobar con una variante o producto inactivo → 422
  con el SKU. Línea repetida → 400. Más de 300 líneas → 400.
- Recibir: plan no APROBADO → 409; todas en 0 → 422; costos distintos para variantes del mismo producto → 422 (regla de
  entradas); variante inactiva permitida (D5). Lo recibido de más se acepta (llega más de lo pedido).
- Cancelar: RECIBIDO o CANCELADO → 409; INVENTARIO sobre APROBADO → 403; motivo obligatorio.
- Recibir y cancelar el mismo plan a la vez → exactamente una transición (bloqueo de la fila del plan).
- Alertas: aprobar cierra STOCK_BAJO de las variantes cuya posición supera el umbral; cancelar un APROBADO puede
  reabrirlas; AGOTADO no cambia por un pedido en camino.
- Tendencias: rango > 36 meses o `desde > hasta` → 400; categoría o producto inexistente → serie en ceros (filtro).
- Matriz: VENDEDOR 403 en compras, tendencias y pronósticos; CONTABILIDAD solo lectura (403 en toda escritura).

## Tareas (cada una cierra con un commit y pruebas en verde; < 1 h)
Paso 0 (coordinador, tras aprobar D1–D8): copiar este plan a `docs/fases/fase-6-plan.md`, ADR-010, actualizar
`docs/dominio.md` (secciones "Pronóstico" y "Plan de compras" nuevas; "Alertas" con la posición; "Máquinas de estado" con
CANCELADO; "Datos semilla" 36 meses y planes; matriz y notas de la fase 6; trazabilidad REQ-09/REQ-10) y las
especificaciones `docs/especificaciones/algoritmos-analitica.md` (§4 Holt-Winters **multiplicativo** con índices como
razones de media 1 y exclusión por mes en 0, origen móvil, corrección; §5 política s,S) y
`docs/especificaciones/datos-semilla.md` (36 meses y planes, aplicable desde B12).

### Orden de trabajo y punto de corte
**Núcleo** (primero): A1–A5, B1–B11, F1–F6, T1. **Al terminar el núcleo se para y se reporta al usuario** (estado,
pruebas, qué se ve en la demo con la semilla actual) antes de seguir.

**Después del corte:** B12 (semilla de 36 meses y D8), F7, T2 y la documentación (D1, D2).

Qué pasa antes del corte con lo que depende de B12 (la base de demo sigue siendo la semilla de 24 meses de la fase 5):
- **HW nunca se elige con esos datos** (23 meses completos < 30): en la demo todos los productos salen SES con motivo
  POCA_HISTORIA. HW se valida solo con pruebas unitarias (A2, A3: series exactas y la serie sintética de 36 meses de
  `ModeloDemanda`) y con la integración de B3 con datos a mano de ≥ 30 meses.
- **No hay planes en la semilla**: en camino = 0 en todas las variantes, así que la métrica de alertas no cambia (~34 %)
  hasta que alguien apruebe un plan a mano o se haga B12. El flujo sugerir → aprobar → recibir se prueba en T1 y a mano
  sobre la base actual.
- La corrección por agotados y el ROP con pronóstico (SES) sí funcionan sobre la semilla actual.
- F7 (en camino y métricas nuevas en Inventario, Reorden, Alertas y el panel) queda después del corte: el backend ya
  expone los campos desde B8/B4; la interfaz del núcleo no los muestra todavía.

Analítica (`analitica`), en paralelo desde el día 1:
- A1 `SerieMensual` + `CorrectorCensura` con pruebas a mano (ejemplo 0,875; piso 0,5; observada 0; variante creada a
  mitad de mes; día con entrada que repone).
- A2 `SuavizadoExponencial` y `HoltWintersMultiplicativo`: rejillas, desempates, recorte a 0; inicialización a mano
  (ejemplo de 24 meses, índices de media 1); serie `100 × S_t` exacta; serie con tendencia WAPE < 2 %; combinaciones con
  nivel ≤ 0 descartadas; n < 24 y mes en 0 rechazados.
- A3 (tras A2) `SelectorPronostico`: tramos de n, motivos (POCA_HISTORIA, MES_EN_CERO, NIVEL_NO_POSITIVO), origen móvil,
  WAPE (ejemplo 0,1167), empate → SES, Σ y = 0; prueba con la serie sintética de 36 meses de un producto de cabeza
  (`ModeloDemanda` con λ esperado, y con ruido Poisson `Random(42)`) → HW gana. Es la evidencia de HW antes del corte.
- A4 `Desagregador` y `CalculadoraReorden` con `μL` pronosticado (ejemplo ROP 31; horizonte agotado → última tasa).
- A5 `CalculadoraSugerenciaCompra` (ejemplo 90; lote; SIN_DEMANDA agotada no entra; ROP null; justificación).

Backend:
- B1 V15 + entidades de pronóstico + columnas nuevas + `PropiedadesPronostico`; `@DataJpaTest` V1–V15 con `validate` y
  los CHECK.
- B2 (tras B1) `ConsultasDemandaMensual` y `ConsultasDisponibilidad`: Testcontainers con datos a mano (anulada fuera,
  devolución apta/no apta, mes parcial excluido, kardex con reserva que deja disponible 0).
- B3 (tras A1–A4, B2) `PronosticoService.calcularYGuardar`: serie → corrección → selección → desagregación → tablas
  (inserción por `variante_id` ascendente). Integración con un catálogo pequeño.
- B4 (tras B3) Orquestador y `CalculoAnaliticoService`: pronóstico antes del ABC/ROP, ROP con fuente PRONOSTICO/HISTORICA,
  fallo del pronóstico → FALLIDA con ROP histórico, métricas nuevas. Prueba de concurrencia con una venta (sin 409).
- B5 V16 + `PlanCompra`/`DetallePlan` + repositorios + puertos + reglas ArchUnit de R1; `@DataJpaTest` de CHECK y
  `@Version`.
- B6 (tras B5) `PlanCompraService`: crear manual, editar (versión), cancelar; validaciones y bloqueo del plan.
- B7 (tras B6) Aprobar y recibir: `registrarEntradaDePlan` en `OperacionInventarioService` (referencia, inactivas),
  `PosicionCambiada`. Pruebas: kardex con referencia, `precioCompra`, recibir vs cancelar concurrente → una transición.
- B8 (tras B5) Alertas por posición: `PedidosEnCaminoJpa`, `AlertaService` (STOCK_BAJO con posición, `tipoEsperado`),
  listener; métricas `en_alerta_sin_transito` y `con_pedido_en_camino`; `enCamino` en inventario y reorden. Regresión
  de las alertas de las fases 3 y 5.
- B9 (tras A5, B4, B6) Sugerencia: `SugerenciaCompraAnalitica` + `POST /sugerido` (omitidas, 422, enlace a la ejecución).
- B10 (tras B3) Endpoints de tendencias y pronósticos + DTOs + WebMvc por rol.
- B11 (tras B6, B7) `PlanCompraController` + WebMvc de la matriz completa (incluido 403 de INVENTARIO al cancelar
  APROBADO).
- B12 (**después del corte**; tras B7, B8, B4) Semilla: 36 meses, reposición por planes, pedidos que llegan tras `fin`, verificador (en camino =
  simulador) y resumen. Prueba de semilla corta (3 meses) en verde y mismo resultado con la misma semilla aleatoria.

Frontend, en paralelo con `vi.mock` de `api/*`:
- F1 **Empieza pidiéndole al usuario que ejecute `npm install recharts` dentro de `frontend/` desde su terminal** (no
  se instala sin su confirmación) y espera la confirmación; luego tipos, API, hooks, permisos; `enCamino` en tipos existentes;
  invalidaciones (`compras`, `inventario`, `alertas`, `analitica`).
- F2 Pestaña Tendencias: gráfico de líneas mensual (unidades/ingreso, año anterior punteado), filtros categoría y
  producto, tabla accesible con los mismos datos.
- F3 Pestaña Pronóstico: listado (método, WAPE con semáforo, próximos 3 meses) y detalle con gráfico (observada,
  corregida, backtest SES/HW, pronóstico) y la desagregación por variante.
- F4 Compras: listado por estado, "Generar sugerencia" (GI, categoría opcional, aviso de omitidas), "Nuevo plan" manual
  con `BuscadorVariante`.
- F5 Detalle del plan: borrador editable (cantidades, costos, quitar/agregar, justificación visible, 409 amable que
  recarga), aprobar (solo ADMIN, `DialogoConfirmacion` con total estimado), cancelar con motivo, "atrasado".
- F6 `ModalRecibirPlan`: cantidades y costo por producto (un costo por producto), aviso de variantes inactivas, resumen
  antes de confirmar.
- F7 (**después del corte**) Inventario y Reorden con "en camino" y posición; Alertas con el texto nuevo de STOCK_BAJO ("posición ≤ umbral");
  `PanelEjecucion` con pronóstico (HW/SES, WAPE global) y el % de alerta con y sin tránsito.

Pruebas:
- T1 (tras B9–B11; cierra el núcleo) `CompraFlujoIntegracionTest`: datos por servicios reales (no la semilla) → recalcular → sugerencia → editar → aprobar
  (STOCK_BAJO cerrada, AGOTADO intacta) → recibir (stock = kardex, referencia, `precioCompra`, en camino 0) → cancelar
  otro APROBADO (alerta reabierta); métrica = conteo SQL; matriz de roles completa.
- T2 (usuario, **después del corte**, tras B12 y F7) Corrida real (abajo, "Criterios de aceptación").

Documentación (**después del corte**, tras T2):
- D1 Fichas `REQ-09.md` (Tendencias históricas) y `REQ-10.md` (Planeación de compras), actualización de `REQ-04.md`
  (posición de inventario y métrica final) y `datos-demostracion.md` (36 meses, planes, pronóstico).
- D2 Diagramas: estados del plan de compras, clases de `compra`, secuencia "sugerir → aprobar → recibir". `progreso.md`.

Recorte si falta tiempo (en este orden): comparación con el año anterior en tendencias; columnas de backtest en el
gráfico del pronóstico; plan manual (queda solo el sugerido, editable); ROP con pronóstico (D3 → a); corrección por
agotados (D1 → b, se documenta el sesgo). No se recortan: pronóstico SES/HW con backtest, plan sugerido → aprobado →
recibido, STOCK_BAJO por posición.

## Pruebas
- Puras (A1–A5): ejemplos a mano de cada sección; casos borde de la especificación (serie vacía, un valor, toda en cero,
  constante, < 24 meses cae a SES, devoluciones > ventas recortadas a 0, producto nuevo sin historia).
- Validación del método (A3): con la demanda esperada del modelo de la semilla para un producto de cabeza (36 meses,
  estacionalidad multiplicativa y tendencia conocidas), HW multiplicativo obtiene menor WAPE que SES; con ruido Poisson
  (`Random(42)`), HW sigue ganando en el producto de cabeza; un producto de cola con meses en 0 cae a SES (MES_EN_CERO).
  Evidencia de que la elección no es "a ojo" y la única prueba de HW hasta B12.
- Integración (Testcontainers): consultas mensuales y de disponibilidad; `calcularYGuardar` (tablas reemplazadas,
  inactivos fuera, orden de inserción); orquestador con pronóstico fallido forzado (FALLIDA, ROP histórico aplicado,
  alertas re-evaluadas); plan: versión vieja 409, aprobar dos veces 409, recibir y cancelar a la vez → una transición;
  recepción con variante inactiva; costos distintos por producto 422.
- Alertas (B8): STOCK_BAJO se cierra al aprobar y se reabre al cancelar; AGOTADO no cambia con pedido en camino;
  recepción deja en camino 0; `tipoEsperado` con posición; regresión de SIN_ROTACION y prioridades.
- WebMvc por rol: AN (VENDEDOR 403), GI (CONTABILIDAD y VENDEDOR 403), AD (INVENTARIO 403 al aprobar), 400/409/422.
- ArchUnit: reglas de R1; reglas de oro en verde (la recepción solo pasa por `MovimientoService`).
- Semilla (B12): carga corta determinista; en camino = simulador; planes RECIBIDOS con sus entradas referenciadas.
- Vitest: pestañas por rol; gráficos con datos simulados (Recharts mockeado si hace falta en jsdom); flujo de plan
  (botones por rol y estado, 409 de versión, modal de recepción con costo por producto); "en camino" en inventario.

## Riesgos y decisiones
- Meta 10–15 % no garantizada: AGOTADO ya pesa ~9 % (agotables intencionales de la semilla). Se mide y se explica con el
  desglose por tipo, clase y "agotada sin demanda"; no se cambia la definición para llegar al número (D4).
- Carga de la semilla de 36 meses ~3,5–4 min (meta de 2 min de `docs/especificaciones/datos-semilla.md` ya superada
  en la fase 5); se acepta porque corre una vez. Si pasa de 6 min, bajar a 30 meses (D2 b).
- HW multiplicativo exige series sin meses en 0: los productos de cola (demanda intermitente) y los estancados caen a
  SES. Es lo correcto (un índice estacional de 0 no es estacionalidad) y queda explicado por producto
  (`motivo_sin_hw`); la ejecución informa cuántos usan HW. Riesgo: que en la demo HW aparezca en pocos productos; se
  mide en T2 (se espera en los de cabeza, que concentran el ingreso).
- HW multiplicativo amplifica el error de nivel en los picos (un nivel 10 % alto da un diciembre 16 % alto en unidades).
  Mitigación: recorte a 0, rejilla con γ moderados disponibles y la selección por WAPE frente a SES.
- El backtest de origen móvil (6 meses, abril–septiembre con corte en octubre) **no cubre noviembre–diciembre**: el WAPE
  puede subestimar el error en los picos, justo donde el multiplicativo se juega más. Se documenta en ADR-010 y en la
  ficha REQ-10; con 12 meses de backtest haría falta una semilla de ≥ 37 meses completos (fuera de alcance).
- Antes del corte la demo no muestra HW ni pedidos en camino (semilla de 24 meses sin planes): se explica en el reporte
  del núcleo; no es un defecto.
- La corrección por agotados sobreestima si el quiebre se debió a falta de demanda real (variante que se dejó morir):
  piso 0,5 y solo variantes activas. Se valida contra las ventas perdidas que reporta la semilla.
- ROP con pronóstico sube los umbrales en noviembre: más STOCK_BAJO si no se pide a tiempo (efecto deseado, pero mueve la
  métrica). Se mide en T2.
- Interbloqueos: inserciones del pronóstico en orden de variante (R5); recepción: plan → variantes en orden de id;
  aprobar no toca variantes (solo publica eventos).
- "En camino" inflado por planes nunca recibidos → CANCELADO y marca de "atrasado".
- Evaluación de alertas tras aprobar un plan grande: una transacción corta por línea en el hilo de la petición (< 1 s con
  100 líneas).
- Recharts en jsdom (ResizeObserver) y tamaño del bundle → carga diferida y mock en pruebas.
- Dependencia nueva de npm (red): la instala el usuario al inicio de F1.
- Contrato del puerto `FuenteSugerenciaCompra`: si analitica no está disponible (prueba de compra aislada) se usa una
  implementación falsa; en producción siempre existe (mismo despliegue).
- ADR-010 (pronóstico con HW multiplicativo, corrección, ROP con pronóstico, posición de inventario, plan de compras y
  puertos, semilla de 36 meses).

## Criterios de aceptación
Al cerrar el **núcleo** (punto de corte, se reporta al usuario):
- `./mvnw verify` y `npm run test`/`lint`/`build` en verde; revisión de código sin críticos.
- Todo producto activo con ≥ 3 meses tiene pronóstico de 3 meses y su desagregación; el WAPE y el motivo por el que HW no
  compitió se ven en la interfaz; HW multiplicativo cubierto por las pruebas de A2/A3 y B3.
- T1 en verde (flujo de compra completo, alertas por posición, matriz de roles).

Al cerrar la fase (después del corte):
- Con la semilla de 36 meses, HW compite en los productos con ≥ 30 meses sin meses en 0 y se informa cuántos lo eligen.
- Flujo sugerir → editar → aprobar → recibir funciona por la interfaz con los roles de D7; el kardex muestra las
  ENTRADAS con `PLAN_COMPRA/{id}`; la regla de oro 1 sigue cubierta por ArchUnit.
- Tendencias con gráfico para ADMIN, INVENTARIO y CONTABILIDAD; VENDEDOR 403.
- Recalcular (con pronóstico) < 15 s (meta < 8 s); consultas de tendencias y pronóstico < 3 s (RNF-03).
- **T2 real:** `docker compose down -v`; semilla `dev,seed` con 36 meses (anotar duración, ventas, unidades perdidas,
  planes por estado, en camino); arrancar `dev`; revisar con los 4 usuarios; anotar % de variantes en alerta con y sin
  tránsito (fase 4: 47 %; fase 5: 34,4 %; meta 10–15 %); métodos elegidos y WAPE global; unidades agregadas por la
  corrección vs. unidades perdidas de la semilla (esperado: mismo orden de magnitud, 0,5×–2×); generar la sugerencia como
  INVENTARIO, aprobar como ADMIN, recalcular y anotar la nueva métrica; recibir un plan en camino como INVENTARIO;
  CONTABILIDAD solo lectura. Consultas de diagnóstico (solo lectura):
  `select metodo, count(*), avg(error_wape) from analitica_producto group by metodo;`
  `select al.tipo, a.clase_abc, count(*) from alerta al join analitica_variante a on a.variante_id = al.variante_id
  where al.estado = 'ACTIVA' group by al.tipo, a.clase_abc order by 1, 2;`
  `select p.estado, count(*), sum(d.cantidad_aprobada) from plan_compra p join detalle_plan d on d.plan_id = p.id
  group by p.estado;`

## Asignación sugerida
- Algoritmos: A1–A5 (y apoyo en B3/B4/B9). Backend: B1–B12. Frontend: F1–F7. Pruebas: T1 y la guía de T2.
- Documentación: D1–D2. Coordinación: Paso 0 y `progreso.md`.
