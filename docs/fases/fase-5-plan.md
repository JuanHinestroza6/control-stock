# Plan APROBADO — Fase 5 (Analítica base)

Rama `fase-5-analitica`. Requisitos: REQ-05 (productos más vendidos), REQ-14 nuevo "Clasificación ABC", REQ-04 (punto de
reorden dinámico como umbral de alertas, SIN_ROTACION y re-evaluación nocturna). Reglas: `docs/dominio.md` (secciones
"Alertas", "Datos semilla", matriz de roles), ADR-006, ADR-007, ADR-008 y ADR-009. Algoritmos:
`docs/especificaciones/algoritmos-analitica.md` (se actualiza en el Paso 0 según las decisiones).

**Regla de oro 1:** la analítica nunca toca `stockFisico` ni `stockReservado`. Solo escribe `puntoReorden` y `claseAbc`
con una sentencia dirigida desde el módulo `analitica` (ver B1/B3), sin pasar por `@Version`.

## Objetivo
Calcular cada noche (y bajo demanda del ADMIN) la clasificación ABC y el punto de reorden de cada variante activa,
re-evaluar todas las alertas (incluida SIN_ROTACION) y publicar los productos más vendidos, con evidencia medible del
efecto sobre el volumen de alertas.

## Contradicciones detectadas antes de planear
1. **`docs/especificaciones/algoritmos-analitica.md` vs. dominio (umbral):** la especificación dice
   `ROP = max(ROP, stockMinimo)`; `docs/dominio.md` dice "umbral = `puntoReorden`, o `stockMinimo` si no hay ROP"
   (respaldo, no piso). Gana el dominio salvo que se decida otra cosa
   (D2). Con el piso, en la semilla el ROP no cambiaría nada en ~80 % de las variantes (su `stockMinimo` es 2).
2. **Especificación (ventana ABC 90 días) vs. propuesta de 12 meses:** ver D1. Se corrige la especificación en el
   Paso 0.
3. **SIN_ROTACION:** el dominio la define por variante con `físico > 0` y nada dice de su convivencia con AGOTADO /
   STOCK_BAJO, ni de la re-evaluación nocturna frente a "atender a mano la cierra; el siguiente movimiento la reabre".
   Ver D3.
4. **Hallazgo (bug menor de la fase 3):** desactivar una variante o producto no cierra sus alertas ACTIVAS (quedan ocultas
   porque el listado filtra variantes activas). `AlertaService.evaluar` solo cierra los tipos distintos del vigente. Se
   corrige en B4: variante inactiva → se cierran todas sus alertas ACTIVAS.
5. **Meta de alertas 10–15 %:** con alertas sobre el disponible (sin pedidos en camino) no es alcanzable solo con
   parámetros del ROP sin hacer trampa (análisis en D4).

## Decisiones confirmadas por el usuario (2026-10-14)

Se eligió la opción recomendada en las cuatro: D1 (a), D2 (b), D3 (a), D4 (a).

### D1 [ELEGIDA (a)] Clasificación ABC: unidad, criterio y ventana
- **(a) Elegida:** por **variante**, por **ingreso neto** (Σ (cantidad − devuelta) × precioUnitario de ventas
  REGISTRADA), ventana **12 meses móviles** cerrados en el día anterior, cortes 80/95.
- (b) Igual pero ventana de 90 días (lo que dice la especificación hoy).
- (c) Por producto (la clase se copia a todas sus variantes), 12 meses.
- Porqué (a): el campo `claseAbc` y el ROP viven en la variante, y la clase alimenta el nivel de servicio del ROP de esa
  variante. 12 meses cubren un ciclo estacional completo: diciembre (×1,6) o una promoción no voltean las clases ni hacen
  oscilar los ROP. Con 90 días la cola es puro ruido (la mayoría de variantes vende menos de 1 unidad/mes). El margen se
  descarta: depende del costo, que llega en fases posteriores a los reportes, y el ingreso es el criterio clásico y el que
  se puede defender con Pareto.

### D2 [ELEGIDA (b)] Modelo del punto de reorden
- (a) Especificación literal: normal para todo, `ROP = ceil(d̄·L + z·σ·√L)`, piso `max(ROP, stockMinimo)`.
- **(b) Elegida:** ventana 90 días diaria; **normal** si la demanda esperada en el lead time `μL = d̄·L ≥ 5`,
  **cuantil de Poisson(μL)** al nivel de servicio de la clase si `μL < 5` (demanda intermitente); **sin piso**:
  `stockMinimo` es solo el respaldo cuando `puntoReorden` es null (dominio literal); historia mínima 30 días.
- (c) Normal para todo sin piso.
- Porqué (b): la aproximación normal con `ceil` infla el ROP de la cola (μL = 0,46 → ROP 2), y el piso lo devuelve a
  `stockMinimo`: el "ROP dinámico" sería invisible en la mayoría de variantes. El cuantil de Poisson es el método de
  libro para demanda intermitente y es una función de 10 líneas probada a mano. Una variante que casi no vende queda con
  ROP 0 ("se repone al agotarse") de forma natural, sin una regla artificial.

### D3 [ELEGIDA (a)] SIN_ROTACION: nivel, convivencia y re-apertura
- **(a) Elegida:** condición **por producto** ("el producto no tiene ventas no anuladas en 60 días y existe hace
  ≥ 60 días"), materializada como alerta **por variante** activa con `disponible > 0`. Las tres alertas son **excluyentes**
  con prioridad **AGOTADO > SIN_ROTACION > STOCK_BAJO**. Una venta de cualquier variante cierra la SIN_ROTACION de todas
  las variantes del producto. Atendida a mano, **no se reabre en el mismo episodio** (episodio = desde `última venta +
  60 días`); AGOTADO y STOCK_BAJO conservan la regla actual (la reabre el siguiente movimiento o la re-evaluación).
- (b) Por variante, literal (`físico > 0`), independiente de las otras dos.
- (c) Por variante, excluyente, sin regla de episodio.
- Porqué (a): con demanda intermitente, una variante de la cola (≈0,023 u/día) pasa 60 días sin ventas con probabilidad
  e^(−1,4) ≈ 25 %: la regla por variante abriría unas 60 alertas falsas (~20 % de las variantes) además de los productos
  estancados de la semilla. Por producto solo se marcan los estancados reales (LIV004, LIV026, LIV030). La exclusión evita
  el mensaje contradictorio "pide más" + "no se vende"; con `disponible > 0` no choca con AGOTADO (si todo está apartado,
  hay demanda). Sin la regla de episodio, atender una SIN_ROTACION sería inútil: la noche siguiente la reabre.

### D4 [ELEGIDA (a)] Umbral de alertas y meta de ~10–15 % de variantes en alerta
**Diagnóstico (sin trampa):** una alerta "disponible ≤ ROP" queda activa todo el lead time de cada ciclo, aunque ya se haya
pedido. La fracción del tiempo en alerta es ≈ `L / (Q/d)` (lead time sobre duración del ciclo). En la semilla `L` = 10–30
días y cada pedido cubre 30–60 días: ≈ 25–65 %, ~40 % en promedio, **con cualquier ROP correcto**. Además ~15 variantes se
dejan agotar a propósito (5 %) y la cola tiene stocks de 0–3 unidades. El ROP de D2(b) reduce las alertas de la cola,
pero la estimación para la fase 5 es **30–45 %** (se mide en T2). La meta solo se logra midiendo la **posición de
inventario** (disponible + pedidos aprobados en camino), que llega con el plan de compras de la fase 6.
- **(a) Elegida:** fase 5 = ROP de D2(b) como umbral (dominio), **métrica fija** guardada en cada ejecución
  (`% variantes activas con AGOTADO o STOCK_BAJO`, aparte SIN_ROTACION), línea base medida en T2 y desglosada por clase y
  método. La meta 10–15 % se traslada a la **fase 6**: STOCK_BAJO pasa a usar `disponible + en tránsito ≤ ROP` y la
  semilla repone mediante planes de compra que siguen la sugerencia del sistema.
- (b) (a) + desde ya STOCK_BAJO solo para clases A y B (las C solo generan AGOTADO). Baja ~10–15 puntos ya en la fase 5;
  es una política ABC estándar ("gestión por excepción"), pero agrega una regla al dominio y oculta avisos de la cola.
- (c) Umbral = stock de seguridad (alerta "crítica") y "por pedir" como listado aparte en la pestaña de reorden. Llega a
  la meta ya, pero cambia el significado de REQ-04 ("nivel mínimo") y duplica lo que hará el plan de compras.
- Porqué (a): no maquilla el número; deja una métrica reproducible (antes/después) para la sustentación y ataca la causa
  real (pedidos en camino) en la fase donde existen los pedidos. Cambiar la reposición de la semilla en la fase 5 no
  serviría: la fracción `L/(Q/d)` no depende de cuándo se pide.

## Decisiones técnicas resueltas por el equipo (se documentan en ADR-009)
- R1 **Escritura dirigida.** `Variante.puntoReorden` y `claseAbc` pasan a `@Column(updatable = false)`: ninguna entidad
  cargada (una venta en curso) puede pisarlos con un valor viejo. Solo los escribe
  `analitica.repositorio.AnaliticaVarianteRepository` con un `UPDATE variante SET punto_reorden = ?, clase_abc = ? WHERE id
  = ?` nativo (`@Modifying`), **sin incrementar `version`**: el cálculo nocturno no provoca 409 a los vendedores. ArchUnit:
  todo `@Modifying` que mencione `punto_reorden`/`clase_abc` vive en `analitica.repositorio`. No se toca `actualizado_en`
  (no es una edición de usuario). Alternativa descartada: guardar por entidad (sube `version` → 409 a ventas
  concurrentes, y además Hibernate reescribe todas las columnas).
- R2 **Instantánea explicable.** Tabla `analitica_variante` (una fila por variante activa, se reemplaza en cada
  ejecución) con los insumos del cálculo (ingreso, participación, d̄, σ, L, z, SS, método). La interfaz muestra por qué
  una variante tiene su ROP sin recalcular en caliente (lo mostrado = lo aplicado). Sin historial (fuera de alcance).
- R3 **Registro de ejecuciones.** Tabla `ejecucion_analitica` (origen NOCTURNA/MANUAL/SEMILLA, usuario, inicio, fin,
  estado, conteos y la métrica de D4). Sirve para "última ejecución" en la interfaz, trazabilidad (RNF-02) y la medición.
- R4 **Orquestación** (`EjecucionAnaliticaService`, no transaccional, como `VencimientoReservasTarea`):
  1. registrar EN_CURSO (transacción propia); marcar FALLIDA "interrumpida" cualquier EN_CURSO anterior;
  2. **ABC + ROP en una sola transacción**: primero todas las lecturas y cálculos en memoria, al final las escrituras
     (instantánea + UPDATE por variante en orden de id + limpiar inactivas). Las clases son un conjunto: o se aplican
     todas o ninguna. La ventana de bloqueo de filas de `variante` queda en milisegundos;
  3. **re-evaluar alertas de todas las variantes** (orden de id) con `AlertaService.evaluar` (`REQUIRES_NEW`, una
     transacción por variante); los fallos se cuentan y se registran, no detienen el ciclo. Corre aunque el paso 2 falle
     (SIN_ROTACION y la limpieza nocturna siguen siendo útiles);
  4. registrar EXITOSA/FALLIDA con conteos y métrica.
  Corte: `fechaCorte = hoy (Clock)`, datos de `[corte − ventana, corte)`: excluye el día en curso, así el resultado es el
  mismo a las 02:00 o a las 15:00.
- R5 **Hora:** `app.analitica.cron = "0 0 2 * * *"`, zona America/Bogota (antes de la anonimización de las 02:30; tocan
  tablas distintas). Apagada con `app.tareas.habilitadas=false` (pruebas y `seed`).
- R6 **Sin ejecuciones simultáneas:** guardia en memoria (`AtomicBoolean`) en el orquestador; una segunda solicitud
  manual → 409 "Ya hay un recálculo en curso."; la nocturna, si choca, se omite con log. Una sola instancia (igual que el
  limitador de login); varias instancias requerirían ShedLock (fuera de alcance).
- R7 **Recálculo manual síncrono** (`POST /api/analitica/recalcular`, solo ADMIN): estimado < 5 s con la semilla (~300
  variantes, 3 consultas agregadas + 300 UPDATE + 300 evaluaciones). Si T2 mide > 15 s, se pasa a asíncrono (no antes).
- R8 **Analítica como modelo de lectura:** consultas SQL agregadas (`NamedParameterJdbcTemplate`, solo lectura) sobre
  `venta`, `detalle_venta` y `devolucion` desde `analitica.repositorio`. La dirección es válida (analitica está arriba de
  todo); ArchUnit: ningún módulo salvo `semilla` depende de `analitica`. SIN_ROTACION, en cambio, se evalúa en inventario
  con el kardex (VENTA/VENTA_RESERVA no anuladas), para no romper "inventario no depende de venta".
- R9 **Definiciones de demanda** (una por propósito, documentadas):
  - *Comercial* (ABC, más vendidos): unidades netas = vendidas − devueltas (todas), atribuidas a la fecha de la venta;
    ingreso neto = Σ (cantidad − devuelta) × precioUnitario. Ventas ANULADAS excluidas.
  - *Inventario* (ROP): por día, vendidas (fecha de la venta, incluidas las convertidas de reservas) − devueltas **aptas**
    (fecha de la devolución), recortado a 0. Una devolución no apta no vuelve al estante: es consumo.
  - Las reservas ACTIVAS no son demanda. La demanda observada está censurada por agotados (~2,5–3 % de ventas perdidas en
    la semilla): se documenta y se corrige en el pronóstico (fase 6).
- R10 **Más vendidos:** ranking top-N (1–50, por defecto 10; no paginado, justificado por ser un ranking), por producto o
  por variante, por unidades o por ingreso; rango de fechas inclusivo (por defecto últimos 30 días, máx. 731 días),
  categoría opcional. Sin costos ni margen: lo ven los 4 roles. Incluye productos inactivos (se vendieron) con marca.
- R11 **La clase ABC no se oculta al VENDEDOR** en inventario/catálogo: no revela costos ni márgenes y orienta la venta.
  Las pantallas de analítica (ABC, reorden) sí son solo ADMIN/INVENTARIO/CONTABILIDAD.
- R12 **Semilla:** el cierre ejecuta la orquestación completa (origen SEMILLA, reloj real) en lugar del bucle propio de
  alertas: la demo arranca con ABC y ROP calculados y alertas coherentes. Sin cambios en la simulación ni en la carga.
- R13 **Sin Recharts en esta fase:** barras de participación con CSS. Recharts llega en la fase 6 (tendencias, REQ-09).
- R14 **ROP no publica `UmbralCambiado`:** la re-evaluación completa del paso 3 lo reemplaza. Editar `stockMinimo` sigue
  publicándolo (no-op si hay ROP; la interfaz lo explica).
- R15 `loteMinimo` no afecta el ROP (es un nivel de disparo, no una cantidad); se usa en la sugerencia de compra (fase 6).

## Algoritmos (paquete `analitica.calculo`, funciones puras)

### ABC (`ClasificadorAbc`)
```
Universo: variantes activas de productos activos. Ventana: [corte − 365 d, corte).
ingreso_i = Σ (cantidad − devuelta) × precioUnitario   (BigDecimal escala 2 HALF_UP)
Ordenar por ingreso desc, desempate por varianteId asc (determinismo). total = Σ ingreso_i.
participacion_i = ingreso_i / total; acumulado_i = Σ_{j≤i} participacion_j  (BigDecimal escala 6 HALF_UP)
Clase: A si posicion = 1 o acumulado_i ≤ 0,80; B si acumulado_i ≤ 0,95; C en otro caso.
ingreso_i = 0 → C. total = 0 → todas C. Variantes inactivas → clase null.
```
Ejemplo de prueba: ingresos 500, 200, 150, 100, 50 (total 1000) → acumulados 0,50 / 0,70 / 0,85 / 0,95 / 1,00 → A, A,
B, B, C. Una sola variante con 90 % → A por ser la primera.

### Demanda (`SerieDemanda`)
```
n = min(90, días desde creadoEn de la variante hasta corte)   (días completos)
serie[0..n-1] = demanda neta diaria (R9), días sin ventas = 0, negativos → 0
d̄ = Σ serie / n ;  s = sqrt( Σ (x − d̄)² / (n − 1) )  (n = 1 → s = 0)
```
La consulta SQL devuelve filas dispersas (variante, día, neto); el relleno con ceros es en memoria (≤ 300 × 90 valores).

### Punto de reorden (`CalculadoraReorden`)
```
Parámetros por clase (configurables): A z=1,65 p=0,95 | B z=1,28 p=0,90 | C z=0,84 p=0,80 ; clase null → C
L = producto.leadTimeDias
si n < 30                      → ROP = null, método SIN_HISTORIA (umbral = stockMinimo)
si d̄ = 0                       → ROP = 0,    método SIN_DEMANDA (solo AGOTADO; SIN_ROTACION cubre el caso)
μL = d̄ × L
si μL ≥ 5  → SS = z × s × √L ; ROP = ceil(μL + SS)          método NORMAL
si μL < 5  → ROP = mín r tal que P(Poisson(μL) ≤ r) ≥ p      método POISSON ; SS = ROP − μL (informativo)
Sin piso de stockMinimo (D2). ROP entero ≥ 0.
```
Ejemplos a mano: d̄=2, s=1, L=16, A → μL=32, SS=6,6, ROP=ceil(38,6)=39. μL=0,46, C (p=0,80): P(0)=0,631, P(≤1)=0,922 →
ROP=1. μL=0,14, C: P(0)=0,869 → ROP=0. μL=2, A (p=0,95): P(≤4)=0,947, P(≤5)=0,983 → ROP=5.
`DistribucionPoisson.cuantil(λ, p)`: suma acumulada iterativa (`término_k = término_{k−1} × λ / k`), λ < 5 sin problemas
numéricos.

### SIN_ROTACION (en `inventario.servicio.AlertaService`, con el kardex)
```
ventaReciente(producto, ahora) = existe movimiento VENTA o VENTA_RESERVA de una variante del producto con
  fecha ≥ ahora − 60 d y sin ANULACION_VENTA con la misma referencia (VENTA/{id}) y variante
sinRotacion(v) = ¬ventaReciente(producto) ∧ producto.creadoEn ≤ ahora − 60 d ∧ disponible(v) > 0
tipoVigente(v) = null si v inactiva; AGOTADO si disponible = 0; SIN_ROTACION si sinRotacion(v);
                 STOCK_BAJO si disponible ≤ umbral; null en otro caso
Episodio: inicio = max(últimaVenta(producto), producto.creadoEn) + 60 d. No se abre SIN_ROTACION si existe una
  SIN_ROTACION ATENDIDA con atendidaPor no null y atendidaEn ≥ inicio.
Tras una venta (ventaReciente = true): cerrar las SIN_ROTACION ACTIVAS de todas las variantes del producto.
```
`app.alertas.sin-rotacion-dias: 60` (configurable).

### Métrica de alertas (D4)
`porcentajeAlertaStock = variantes activas (de productos activos) con alerta ACTIVA AGOTADO o STOCK_BAJO / variantes
activas × 100` (escala 1 HALF_UP), medida al terminar el paso 3 y guardada en `ejecucion_analitica` junto con
`sin_rotacion_activas`. Línea base actual (fase 4, umbral = stockMinimo): 141 / 299 ≈ 47 %.

## Cambios en base de datos
`V14__crear_tablas_analitica.sql` (InnoDB, utf8mb4, `utf8mb4_0900_ai_ci`, DATETIME(6)):
- `ejecucion_analitica(id BIGINT AI PK, origen VARCHAR(10) NOT NULL, usuario_id BIGINT NULL FK → usuario, iniciada_en
  DATETIME(6) NOT NULL, terminada_en DATETIME(6) NULL, estado VARCHAR(10) NOT NULL, fecha_corte DATE NOT NULL,
  variantes_analizadas INT NULL, clase_a INT NULL, clase_b INT NULL, clase_c INT NULL, con_punto_reorden INT NULL,
  alertas_evaluadas INT NULL, alertas_fallidas INT NULL, variantes_activas INT NULL, en_alerta_stock INT NULL,
  sin_rotacion_activas INT NULL, duracion_ms BIGINT NULL, mensaje VARCHAR(255) NULL)`.
  CHECK: `origen IN ('NOCTURNA','MANUAL','SEMILLA')`, `estado IN ('EN_CURSO','EXITOSA','FALLIDA')`,
  `(estado = 'EN_CURSO') = (terminada_en IS NULL)`, `(origen = 'MANUAL') = (usuario_id IS NOT NULL)`.
  Índices: `idx_ejecucion_estado_inicio(estado, iniciada_en)`, `idx_ejecucion_usuario(usuario_id)`.
- `analitica_variante(variante_id BIGINT PK FK → variante, ejecucion_id BIGINT NOT NULL FK → ejecucion_analitica,
  unidades_netas INT NOT NULL, ingreso_neto DECIMAL(14,2) NOT NULL, participacion DECIMAL(9,6) NOT NULL,
  acumulado DECIMAL(9,6) NOT NULL, posicion INT NOT NULL, clase_abc CHAR(1) NOT NULL, dias_historia INT NOT NULL,
  demanda_media DECIMAL(10,4) NOT NULL, desviacion DECIMAL(10,4) NOT NULL, lead_time_dias INT NOT NULL,
  nivel_servicio DECIMAL(4,3) NOT NULL, z DECIMAL(4,2) NOT NULL, stock_seguridad DECIMAL(10,2) NULL,
  punto_reorden INT NULL, metodo_reorden VARCHAR(12) NOT NULL, calculado_en DATETIME(6) NOT NULL)`.
  CHECK: `clase_abc IN ('A','B','C')`, `metodo_reorden IN ('NORMAL','POISSON','SIN_DEMANDA','SIN_HISTORIA')`,
  `(metodo_reorden = 'SIN_HISTORIA') = (punto_reorden IS NULL)`, `ingreso_neto >= 0`, `unidades_netas >= 0`.
  Índices: `idx_analitica_variante_ejecucion(ejecucion_id)`, `idx_analitica_variante_clase(clase_abc, posicion)`.
- `CREATE INDEX idx_movimiento_variante_tipo_fecha ON movimiento (variante_id, tipo, fecha)`: última venta por producto
  para SIN_ROTACION sin recorrer todo el kardex de la variante. Solo índice; la tabla y sus datos no cambian.
- Índices existentes que ya cubren las consultas (verificar con EXPLAIN en T2): `idx_venta_estado_fecha`,
  `uk_detalle_venta_variante (venta_id, …)`, `idx_devolucion_detalle_venta`, `idx_devolucion_fecha`,
  `idx_movimiento_referencia`.

## Archivos a crear o modificar (orden de dependencia)
Migración
- `db/migration/V14__crear_tablas_analitica.sql` — tablas e índice anteriores.

Modelo
- `catalogo/modelo/Variante.java` — `puntoReorden` y `claseAbc` con `updatable = false` + javadoc (ADR-009).
- `analitica/modelo/EjecucionAnalitica.java`, `OrigenEjecucion`, `EstadoEjecucion` — fábrica `iniciar`, `terminar`,
  `fallar`; sin setters.
- `analitica/modelo/AnaliticaVariante.java`, `MetodoReorden` — instantánea; `Persistable<Long>` (o `persist`) para no
  hacer SELECT por fila con id asignado.

Cálculo (puro)
- `analitica/calculo/ClasificadorAbc.java` (+ records `IngresoVariante`, `ClasificacionAbc`).
- `analitica/calculo/SerieDemanda.java` — relleno de ceros, recorte, media y desviación muestral.
- `analitica/calculo/DistribucionPoisson.java` — `cuantil(λ, p)`.
- `analitica/calculo/CalculadoraReorden.java` (+ `ParametrosClase`, `ResultadoReorden`).

Configuración
- `analitica/config/PropiedadesAnalitica.java` — record `@ConfigurationProperties("app.analitica")` validado (ventanas,
  cortes 0 < A < B < 1, historia mínima, umbral Poisson, parámetros por clase, cron).
- `application.yml` — bloques `app.analitica.*` y `app.alertas.sin-rotacion-dias`.

Repositorio
- `analitica/repositorio/ConsultasVentaAnalitica.java` — solo lectura: `ingresoNetoPorVariante(desde, hasta)`,
  `demandaDiariaNeta(desde, hasta)`, `masVendidos(filtro)` (+ total para participación con `SUM(...) OVER ()`).
- `analitica/repositorio/AnaliticaVarianteRepository.java` — `aplicarAVariante(id, rop, clase)` y
  `limpiarInactivas()` (`@Modifying` nativos), `deleteAllInBatch`, consultas paginadas ABC/reorden con `join fetch`.
- `analitica/repositorio/EjecucionAnaliticaRepository.java` — última (cualquiera y EXITOSA), marcar interrumpidas.
- `catalogo/repositorio/VarianteRepository.java` — `idsOrdenados()`; universo `activasConProducto()`.
- `inventario/repositorio/MovimientoRepository.java` — lecturas `hayVentaDelProductoDesde(productoId, desde)` y
  `ultimaVentaDelProducto(productoId)` (sin `@Modifying`).
- `inventario/repositorio/AlertaRepository.java` — `cerrarActivasDelProducto(productoId, tipo, ahora)`,
  `existeAtencionManualDesde(varianteId, tipo, desde)`, conteo de variantes activas en alerta por tipo.

Servicio
- `inventario/servicio/AlertaService.java` — SIN_ROTACION, prioridad, episodios, inactivas cierran todo,
  `tipoEsperado(varianteId)` público (lo usa el verificador de la semilla), resumen con `sinRotacion`.
- `analitica/servicio/CalculoAnaliticoService.java` — `calcularYAplicar(corte, ejecucionId)` (`@Transactional`).
- `analitica/servicio/RegistroEjecucionService.java` — iniciar/terminar/fallar (`REQUIRES_NEW`).
- `analitica/servicio/EjecucionAnaliticaService.java` — orquestador con guardia y métrica.
- `analitica/servicio/AnaliticaNocturnaTarea.java` — `@Scheduled(cron = "${app.analitica.cron}", zone =
  "America/Bogota")`, try/catch con log.
- `analitica/servicio/ConsultaAnaliticaService.java` — páginas ABC/reorden, resumen ABC, última ejecución.
- `analitica/servicio/MasVendidosService.java` — validación de rango y ranking.

DTO
- `analitica/dto/`: `FiltroMasVendidos`, `CriterioRanking (UNIDADES|INGRESO)`, `AgrupacionRanking
  (PRODUCTO|VARIANTE)`, `MasVendidosResponse`, `MasVendidoItem`, `AbcVarianteResponse`, `ResumenAbcResponse`,
  `ClaseResumen`, `ReordenVarianteResponse`, `EjecucionAnaliticaResponse`.
- `inventario/dto/ResumenAlertasResponse.java` — `+ sinRotacion`. `inventario/dto/InventarioItemResponse.java` —
  `+ claseAbc` (aditivo).

Web
- `analitica/web/AnaliticaController.java` — endpoints de la tabla siguiente.

Semilla
- `semilla/GeneradorDatosSemilla.java` — cierre con `EjecucionAnaliticaService.ejecutar(SEMILLA, null)`.
- `semilla/VerificadorConsistencia.java` — `verificarAlertas` compara con `AlertaService.tipoEsperado`.
- `semilla/ResumenSemilla.java` — clases A/B/C, variantes con ROP, % en alerta, SIN_ROTACION.

Arquitectura (pruebas)
- `DependenciasEntreModulosTest` — ningún módulo salvo `semilla` depende de `analitica`.
- `ReglasArquitecturaTest` — `@Modifying` con `punto_reorden`/`clase_abc`/`puntoReorden`/`claseAbc` solo en
  `analitica.repositorio`; `Variante.puntoReorden`/`claseAbc` anotados `updatable = false` (reflexión).

Frontend
- `tipos/analitica.ts`, `api/analitica.ts`, `hooks/useAnalitica.ts` (claves `['analitica', …]`), `lib/permisos.ts`
  (`puedeVerAnaliticaCompleta`, `puedeRecalcularAnalitica`).
- `paginas/analitica/AnaliticaPage.tsx` (pestañas por rol, `?tab=`), `MasVendidosTab.tsx`, `AbcTab.tsx`,
  `ReordenTab.tsx`, `PanelEjecucion.tsx`; `componentes/dominio/BadgeClaseAbc.tsx`, `BadgeMetodoReorden.tsx`.
- `App.tsx` — ruta real `/analitica`. `InventarioPage.tsx` — columnas Clase y Umbral. `ModalStockMinimo.tsx` — nota.
  `AlertasPage.tsx`, `Topbar.tsx`, `tipos/alertas.ts`, `tipos/inventario.ts` — SIN_ROTACION y campos nuevos.

## Endpoints (contrato fijo: backend y frontend en paralelo)
Roles: **AN** `hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')`; **MV** `hasAnyRole('ADMIN','INVENTARIO','VENDEDOR',
'CONTABILIDAD')`; **AD** `hasRole('ADMIN')`. Convenciones: página VIA_DTO, `ProblemDetail`, fechas ISO locales.

| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/analitica/mas-vendidos?desde&hasta&categoriaId&criterio&agrupacion&limite` | MV | — | 200 `MasVendidosResponse`; 400 |
| `GET /api/analitica/abc?clase&categoriaId&q&page&size&sort` | AN | — | 200 `Page<AbcVarianteResponse>` |
| `GET /api/analitica/abc/resumen` | AN | — | 200 `ResumenAbcResponse` |
| `GET /api/analitica/reorden?clase&categoriaId&q&metodo&enAlerta&page&size&sort` | AN | — | 200 `Page<ReordenVarianteResponse>` |
| `GET /api/analitica/ejecuciones/ultima` | AN | — | 200 `EjecucionAnaliticaResponse`; 204 si nunca corrió |
| `POST /api/analitica/recalcular` | AD | — | 200 `EjecucionAnaliticaResponse` (EXITOSA o FALLIDA); 409 en curso |
| `GET /api/alertas/resumen` (cambio) | sin cambio | — | `+ sinRotacion` |
| `GET /api/inventario` (cambio) | sin cambio | — | ítems `+ claseAbc` |

- `MasVendidosResponse { desde, hasta, criterio, agrupacion, categoriaId, totalUnidades, totalIngreso, items: [
  { posicion, productoId, productoCodigo, productoNombre, categoria: CategoriaRef, varianteId?, sku?, talla?, color?,
  unidades, ingreso, participacion (0–100, escala 1), activo } ] }`. Validación (400): `desde ≤ hasta`, rango ≤ 731 días,
  `limite` 1–50. Por defecto `hasta = hoy`, `desde = hoy − 29`, `UNIDADES`, `PRODUCTO`, 10. Desempate: el otro criterio y
  luego id. Excluye ítems con neto 0.
- `AbcVarianteResponse { varianteId, sku, productoId, productoNombre, categoria, talla, color, posicion, unidadesNetas,
  ingresoNeto, participacion, acumulado (0–100, escala 2), claseAbc }`. Orden permitido: `posicion` (defecto),
  `ingresoNeto`, `sku`.
- `ResumenAbcResponse { ejecucion: EjecucionAnaliticaResponse | null, ventanaDesde, ventanaHasta, totalIngreso,
  clases: [{ clase, variantes, ingreso, participacion }] }`.
- `ReordenVarianteResponse { varianteId, sku, productoNombre, categoria, talla, color, claseAbc, diasHistoria,
  demandaMedia, desviacion, leadTimeDias, nivelServicio, z, stockSeguridad, puntoReorden, metodo, stockMinimo,
  umbralEfectivo, fuenteUmbral (ROP|STOCK_MINIMO), stockDisponible, estadoStock }`. `enAlerta=true` → `disponible ≤
  umbralEfectivo`. Orden: `sku` (defecto), `puntoReorden`, `demandaMedia`.
- `EjecucionAnaliticaResponse { id, origen, usuario (nombre o null), iniciadaEn, terminadaEn, estado, fechaCorte,
  duracionMs, variantesAnalizadas, claseA, claseB, claseC, conPuntoReorden, alertasEvaluadas, alertasFallidas,
  variantesActivas, enAlertaStock, porcentajeAlertaStock, sinRotacionActivas, mensaje }`.

## Reglas de negocio y casos borde
- Ventas ANULADAS fuera de ABC, ROP, más vendidos y SIN_ROTACION (por kardex: VENTA con ANULACION_VENTA de la misma
  referencia y variante).
- Una devolución resta en ABC y más vendidos (todas) y en la demanda del ROP (solo aptas, por su fecha, recorte a 0).
- Variante creada hace < 30 días: ROP null (umbral = stockMinimo), clase calculada igual (casi siempre C).
- Variante o producto inactivo: sin fila en la instantánea, `puntoReorden` y `claseAbc` null, alertas ACTIVAS cerradas.
  Reactivada: usa `stockMinimo` hasta la siguiente ejecución.
- Sin ventas en la ventana (total 0): todas C; ROP 0 (SIN_DEMANDA) si tienen ≥ 30 días.
- Recalcular con otro recálculo en curso → 409; el nocturno choca → se omite con log.
- Fallo del paso ABC+ROP → se revierte entero (quedan los valores anteriores), ejecución FALLIDA con mensaje genérico
  (sin stacktrace), la re-evaluación de alertas corre igual.
- Una venta concurrente con el cálculo: no recibe 409 (la versión no cambia); su UPDATE no reescribe ROP/clase.
- Más vendidos: rango inválido o > 731 días → 400; categoría inexistente → lista vacía (filtro, no 404).
- SIN_ROTACION: producto nuevo (< 60 días) nunca; disponible 0 → AGOTADO gana; venta anulada no cuenta como rotación.
- Matriz: VENDEDOR solo más vendidos (403 en ABC, reorden, ejecuciones); recalcular solo ADMIN (INVENTARIO y
  CONTABILIDAD 403).

## Tareas (cada una cierra con un commit y pruebas en verde)
Paso 0 (coordinador): copiar este plan a `docs/fases/fase-5-plan.md`, ADR-009, actualizar `docs/dominio.md` (secciones
"Analítica" nueva, "Alertas", trazabilidad REQ-14) y `docs/especificaciones/algoritmos-analitica.md` (ventana ABC, sin piso, Poisson,
definiciones de demanda) según D1–D4.

Backend:
- B1 V14 + entidades `EjecucionAnalitica`/`AnaliticaVariante` + repositorios base + `Variante` `updatable=false` +
  `PropiedadesAnalitica` + reglas ArchUnit. Pruebas: `@DataJpaTest` V1–V14 con `validate` y CHECK; guardar una
  `Variante` cargada no cambia `punto_reorden` fijado por SQL; ArchUnit en verde.
- B2 (tras B1) `ConsultasVentaAnalitica`: ingreso neto y demanda diaria. Testcontainers con datos a mano: anulada
  excluida, devolución apta/no apta, bordes `[desde, hasta)`, venta convertida de reserva incluida.
- B3 (tras A1, A2, B2) `CalculoAnaliticoService.calcularYAplicar`: universo, ABC, ROP, instantánea, UPDATE en orden de
  id, limpiar inactivas. Pruebas de integración + concurrencia con una venta (sin 409, `version` intacta, ROP no pisado).
- B4 (independiente, desde el día 1) `AlertaService`: SIN_ROTACION, prioridad, episodios, inactivas, `tipoEsperado`,
  resumen `sinRotacion`, consultas de kardex + índice de V14 (si B1 no está, B4 espera solo por el índice).
- B5 (tras B3, B4) Orquestador + `RegistroEjecucionService` + `AnaliticaNocturnaTarea` + guardia + métrica.
- B6 (tras B2) Más vendidos: consulta, servicio, endpoint, DTOs.
- B7 (tras B5) Endpoints ABC/reorden/ejecución/recalcular + `InventarioItemResponse.claseAbc`.
- B8 (tras B5) Semilla: cierre con la orquestación, verificador con `tipoEsperado`, resumen ampliado.

Analítica (`analitica`), en paralelo con B1:
- A1 `ClasificadorAbc` con pruebas a mano (ejemplo 500/200/150/100/50; primera > 80 %; empates; total 0; vacío).
- A2 `SerieDemanda`, `DistribucionPoisson`, `CalculadoraReorden` con pruebas a mano y prueba Monte Carlo (abajo).

Frontend, en paralelo con `vi.mock` de `api/*` desde el día 1:
- F1 Tipos, API, hooks, permisos; `sinRotacion` y `claseAbc` en tipos existentes; invalidaciones tras recalcular
  (`analitica`, `inventario`, `alertas`).
- F2 `AnaliticaPage` con pestañas por rol (VENDEDOR solo "Más vendidos") + `PanelEjecucion` (última ejecución, estado,
  % en alerta; botón "Recalcular" solo ADMIN con `DialogoConfirmacion`, deshabilitado mientras corre, 409 amable).
- F3 Pestaña Más vendidos: rango (30/90/365 días y personalizado), categoría, criterio, agrupación, top N; tabla con
  posición, unidades, ingreso y barra CSS de participación.
- F4 Pestaña ABC: tarjetas por clase (variantes, % ingreso), tabla paginada con filtros, `BadgeClaseAbc`.
- F5 Pestaña Reorden: tabla con d̄/día, σ, L, nivel de servicio, SS, ROP, método, stock mínimo, umbral efectivo,
  disponible y estado; filtro "en alerta"; ayuda con la fórmula.
- F6 Inventario (columnas Clase y Umbral con fuente), nota en `ModalStockMinimo` ("Con ROP calculado, el stock mínimo
  solo aplica si no hay ROP"), Alertas y campana con SIN_ROTACION ("El producto no vende hace 60+ días").

Pruebas:
- T1 (tras B7, B8) `AnaliticaFlujoIntegracionTest`: datos por servicios reales, recalcular por HTTP como ADMIN, endpoints,
  alertas coherentes, matriz de roles completa (403), métrica guardada = conteo SQL.
- T2 (usuario) Corrida real: `docker compose down -v`, semilla `dev,seed`, anotar el resumen (clases, ROP, % en alerta
  por tipo, SIN_ROTACION, tiempo del cierre); arrancar `dev`, revisar `/analitica` con los 4 usuarios, recalcular como
  ADMIN y anotar la duración; EXPLAIN de las 3 consultas agregadas. Consulta de diagnóstico (solo lectura) para el
  desglose por clase y método:
  `select a.clase_abc, a.metodo_reorden, count(*) variantes, sum(al.id is not null) en_alerta from analitica_variante a
  left join alerta al on al.variante_id = a.variante_id and al.estado = 'ACTIVA' and al.tipo in ('AGOTADO','STOCK_BAJO')
  group by a.clase_abc, a.metodo_reorden order by 1, 2;`

Documentación (tras T2):
- D1 Fichas `docs/ppi/requisitos/REQ-05.md` y `REQ-14.md` (Clasificación ABC; stakeholder, necesidad, caso de uso y
  prioridad **"Por asignar"**), actualización de `REQ-04.md` (ROP dinámico, SIN_ROTACION, re-evaluación nocturna, métrica
  con los números de T2), `README.md` y `datos-demostracion.md` (clases y % en alerta).
- D2 `docs/progreso.md` y ajustes finales de `docs/dominio.md` con lo medido en T2.

Recorte si falta tiempo (en este orden): agrupación por variante en más vendidos; prueba Monte Carlo; regla de episodio de
SIN_ROTACION (se reabre cada noche); columnas σ/z/nivel de servicio en la pestaña Reorden; desglose de conteos en
`ejecucion_analitica` (se conserva la métrica).

## Pruebas
- Puras (A1, A2): ABC del ejemplo; primera variante > 80 % → A; desempate por id; total 0 → todas C. `SerieDemanda`:
  vacía, un solo valor (s = 0), toda en cero, constante, día con devoluciones > ventas recortado a 0, relleno de ceros
  (3 ventas en 90 días → d̄ = 3/90). `CalculadoraReorden`: los cuatro ejemplos a mano, n < 30 → null/SIN_HISTORIA, d̄ = 0
  → 0/SIN_DEMANDA, clase null → parámetros de C, frontera μL = 5 usa NORMAL. `DistribucionPoisson`: λ = 0, 0,46, 2;
  p = 0,80/0,90/0,95.
- Monte Carlo (A2, `Random(42)`): demanda Poisson diaria con λ ∈ {0,05; 0,5; 3}, L = 15, política "pedir Q al llegar a
  ROP"; la fracción de ciclos sin quiebre durante el lead time ≥ nivel de servicio de la clase − 3 puntos. Es la evidencia
  de que los parámetros no se ajustaron "a ojo".
- Integración (Testcontainers): consultas de B2 y más vendidos (anuladas, devoluciones, categoría, empates, rango);
  `calcularYAplicar` con un catálogo pequeño → clases y ROP esperados, inactivas en null, instantánea reemplazada;
  concurrencia: hilo A carga la variante y espera en una barrera, B ejecuta el cálculo y confirma, A registra una venta
  → venta OK, ROP de B intacto, `version` solo cambió por la venta.
- Alertas (B4): producto sin ventas 60 días → SIN_ROTACION en variantes con disponible > 0; venta de una variante →
  se cierran las de todo el producto; AGOTADO gana; SIN_ROTACION gana sobre STOCK_BAJO; venta anulada no cuenta;
  producto de < 60 días no; atendida a mano no se reabre en el mismo episodio y sí tras una venta + 60 días; variante
  inactiva → todas cerradas; regresión de las transiciones de la fase 3.
- Orquestador (B5): EN_CURSO → EXITOSA con conteos; fallo forzado en el cálculo → FALLIDA, valores anteriores intactos,
  alertas re-evaluadas igual; segunda llamada concurrente → 409; EN_CURSO huérfana → FALLIDA "interrumpida"; tarea
  nocturna captura excepciones; métrica = conteo SQL.
- WebMvc por rol: AN (VENDEDOR 403), MV (los 4 pasan), AD (INVENTARIO y CONTABILIDAD 403); 400 de validación de más
  vendidos; 409 del recálculo; 204 sin ejecuciones.
- Semilla (B8): tras `generar()`, toda variante activa tiene clase; con ≥ 30 días tiene ROP; alertas = `tipoEsperado`;
  ejecución SEMILLA EXITOSA; el resumen trae el % y coincide con el conteo SQL; segunda ejecución sin cambios.
- ArchUnit: dependencias de `analitica`; `@Modifying` de ROP/clase solo en analitica; reglas de oro en verde.
- Vitest: pestañas por rol (VENDEDOR solo ve Más vendidos y no ve "Recalcular"); recalcular invalida consultas y muestra
  409; filtros de más vendidos arman la query; badges de clase y método; SIN_ROTACION en alertas y campana.

## Riesgos y decisiones
- ROP pisado por una entidad vieja → `updatable = false` + prueba de integración.
- 409 a vendedores por el cálculo → UPDATE nativo sin `version`.
- (Revisión final) Interbloqueos con ventas: el cálculo usa `FOR UPDATE SKIP LOCKED` por variante con reintentos y solo
  escribe lo que cambia; ver ADR-009 punto 3 y riesgos residuales.
- Interbloqueos → lecturas primero, escrituras al final, UPDATE por variante en orden de id (igual que ventas). Si
  MySQL elige al cálculo como víctima, la ejecución queda FALLIDA y se reintenta (manual o la noche siguiente).
- Recálculo manual lento en el hilo HTTP → medido en T2; asíncrono solo si > 15 s.
- Guardia en memoria → una sola instancia (documentado, igual que el limitador de login).
- Ventana de 90 días y estacionalidad: el ROP va un poco rezagado al entrar a diciembre; demanda censurada por agotados
  subestima d̄. Ambos se atacan con el pronóstico (fase 6).
- Poisson ignora la sobredispersión (ventas de 2 unidades) en la cola; se acepta para μL < 5.
- El nivel de servicio se cumple con revisión continua (la alerta se evalúa tras cada movimiento; Monte Carlo de A2 por
  evento). Con revisión diaria o ventas de 2+ unidades en variantes de alta rotación, el stock puede cruzar el ROP
  entre revisiones y el servicio real baja unos puntos (prototipo diario, λ = 3: A 0,924, B 0,861, C 0,737).
- La meta 10–15 % no se alcanza en la fase 5 (D4): se mide y se explica; se cierra en la fase 6.
- La re-evaluación nocturna reabre AGOTADO/STOCK_BAJO atendidas a mano si la condición sigue (equivale a un movimiento).
- Acoplamiento por SQL de analitica con tablas de venta → modelo de lectura documentado; las pruebas de integración
  detectan un cambio de esquema.
- Semilla con `fecha-fin` anterior a ayer: hueco sin ventas al final de la ventana del ROP; regenerar con fecha-fin = ayer.
- ADR-009 aceptado (2026-10-14).

## Criterios de aceptación
- `./mvnw verify` y `npm run test`/`lint`/`build` en verde; revisión de código sin críticos.
- Tras la semilla: 100 % de variantes activas con clase; ROP en todas las de ≥ 30 días; alertas = `tipoEsperado`;
  ejecución SEMILLA EXITOSA con la métrica guardada.
- Recalcular como ADMIN en < 15 s (meta < 5 s); consultas de analítica < 3 s (RNF-03) con la base semilla.
- Más vendidos visible para los 4 roles, sin costos; ABC y reorden 403 para VENDEDOR.
- SIN_ROTACION aparece en los productos estancados de la semilla y no en productos con ventas recientes.
- Métrica de alertas registrada antes/después (47 % → valor de T2) y la decisión D4 documentada en REQ-04.

## Asignación sugerida
- Backend: B1–B8. Algoritmos: A1, A2 (y apoyo en B3). Frontend: F1–F6. Pruebas: T1 y la guía de T2.
- Documentación: D1 (REQ-05, REQ-14, REQ-04, datos de demostración). Coordinación: Paso 0 y D2.
