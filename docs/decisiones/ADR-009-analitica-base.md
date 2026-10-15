# ADR-009: Analítica base (ABC, punto de reorden, SIN_ROTACION y ejecución nocturna)

- Estado: aceptada
- Fecha: 2026-10-14

## Contexto
La fase 5 llena por primera vez `Variante.claseAbc` y `Variante.puntoReorden`, que hoy son null, y completa REQ-04 con
SIN_ROTACION y la re-evaluación nocturna. Había ocho cuestiones abiertas:
1. Cómo clasificar (unidad, criterio, ventana) y cómo calcular el ROP con demanda muy intermitente: unas 240 de 299
   variantes de la semilla venden menos de 1 unidad al mes.
2. Si `stockMinimo` es piso del ROP (skill) o solo respaldo (dominio).
3. Cómo escribir dos columnas de `Variante` sin violar la regla de oro 1, sin provocar 409 a las ventas concurrentes
   (`@Version`) y sin que una venta en curso las pise con un valor viejo (Hibernate reescribe todas las columnas).
4. Cómo explicar en la interfaz por qué una variante tiene su ROP.
5. Orden, transacciones, hora, tolerancia a fallos y exclusión de ejecuciones simultáneas del proceso nocturno y del
   recálculo manual.
6. SIN_ROTACION: nivel (variante o producto), convivencia con AGOTADO/STOCK_BAJO y re-apertura tras atenderla.
7. Dónde viven las consultas, dado que inventario no puede depender de venta.
8. La meta de ~10–15 % de variantes en alerta (hoy 47 % con `stockMinimo`).

## Decisión
1. **ABC por variante** por ingreso neto (Σ (cantidad − devuelta) × precioUnitario, ventas REGISTRADA) de los **últimos
   12 meses** cerrados en el día anterior; cortes 80/95 sobre el acumulado; la primera siempre A; sin ingreso → C;
   desempate por id. Inactivas: clase null.
2. **ROP híbrido sin piso**: ventana de 90 días de demanda neta diaria (vendidas − devueltas aptas, días vacíos = 0,
   recorte a 0); nivel de servicio por clase (A 95 %, B 90 %, C 80 %; z 1,65/1,28/0,84). Si `μL = d̄·L ≥ 5`,
   `ROP = ceil(μL + z·s·√L)`; si `μL < 5`, cuantil de Poisson(μL) al nivel de servicio. Menos de 30 días de historia →
   null (umbral = `stockMinimo`); d̄ = 0 → 0. `stockMinimo` es solo respaldo; `loteMinimo` no interviene.
3. **Escritura dirigida**: `puntoReorden` y `claseAbc` se mapean con `updatable = false`; solo
   `analitica.repositorio.AnaliticaVarianteRepository` los escribe con un UPDATE nativo por variante, en orden de id, sin
   tocar `version` ni `actualizado_en`. ArchUnit restringe esos `@Modifying` al paquete `analitica.repositorio` y prohíbe
   escribir con JDBC fuera de él. El stock nunca se toca (regla de oro 1 intacta).
   **Sin esperar a las ventas** (revisión de la fase 5): cada variante se bloquea con `SELECT … FOR UPDATE SKIP LOCKED`
   antes de su UPDATE; si una venta en curso la tiene, el cálculo la salta y la reintenta (5 × 200 ms). Solo se escriben
   las variantes cuyo ROP o clase cambian (las inactivas con valores se limpian en el mismo recorrido por id) y la
   instantánea se reemplaza al final. Así el cálculo nunca queda encolado detrás de una venta y no hay ciclo de
   bloqueos (los INSERT de la instantánea toman S sobre `variante` por la FK; una venta que ya insertó su movimiento
   también tiene S y pediría X al confirmar).
4. **Instantánea** `analitica_variante` (una fila por variante activa, reemplazada en cada ejecución) con los insumos
   del cálculo, y **registro** `ejecucion_analitica` (origen, usuario, estado, conteos, métrica de alertas). Migración V14,
   que además agrega el índice `movimiento(variante_id, tipo, fecha)`.
5. **Orquestación** a las 02:00 America/Bogota (`app.analitica.cron`, apagada con `app.tareas.habilitadas=false`) y por
   `POST /api/analitica/recalcular` (solo ADMIN, síncrono): registrar EN_CURSO → ABC + ROP en **una** transacción
   (lecturas primero, escrituras al final) → re-evaluación de alertas de todas las variantes, **una transacción por
   variante** → registrar EXITOSA/FALLIDA. La re-evaluación corre aunque falle el cálculo. Guardia en memoria: una
   ejecución a la vez (409 al manual; la nocturna se omite). Corte = hoy; datos `[corte − ventana, corte)`.
6. **SIN_ROTACION por producto** (sin ventas no anuladas en 60 días y producto con ≥ 60 días), alerta por variante con
   disponible > 0. Las tres alertas son **excluyentes**: AGOTADO > SIN_ROTACION > STOCK_BAJO. Se evalúa en
   `AlertaService` con el kardex (VENTA/VENTA_RESERVA sin ANULACION_VENTA), por evento y en la noche; una venta cierra la
   de todo el producto. Atendida a mano no se reabre en el mismo episodio. Variante inactiva → se cierran todas sus
   alertas. El cierre por producto lee primero los ids de las SIN_ROTACION ACTIVAS y actualiza por id (sin subconsulta
   sobre `variante`, que bloquearía las variantes hermanas); `evaluar` corre en READ COMMITTED y, tras abrir una
   SIN_ROTACION, relee las ventas confirmadas por si otra talla vendió mientras tanto.
7. **Analítica como modelo de lectura**: consultas SQL agregadas de solo lectura sobre venta, detalle y devolución desde
   `analitica.repositorio`; ningún módulo salvo `semilla` depende de `analitica` (ArchUnit).
8. **Métrica de alertas** guardada en cada ejecución (`% de variantes activas con AGOTADO o STOCK_BAJO`, SIN_ROTACION
   aparte). La meta 10–15 % se alcanza en la fase 6 midiendo la posición de inventario (disponible + pedidos aprobados en
   camino); en la fase 5 se mide la línea base con el ROP.
9. **Semilla**: el cierre ejecuta la orquestación completa (origen SEMILLA, reloj real); la demo arranca con clases, ROP
   y alertas coherentes.

## Alternativas consideradas
- ABC en ventana de 90 días (skill original) — reclasifica con la estacionalidad (diciembre ×1,6) y hace oscilar el
  nivel de servicio y el ROP; en la cola es ruido puro.
- ABC por margen — depende del costo y de las reglas de visibilidad de costos; el ingreso es el criterio clásico.
- ABC por producto — el ROP y las alertas son por variante; una talla marginal heredaría la clase A del producto.
- `ROP = max(ROP, stockMinimo)` — con la semilla (`stockMinimo` = 2 en la cola) el ROP dinámico sería invisible en ~80 %
  de las variantes; contradice el dominio.
- Fórmula normal para todo — con `ceil` infla el ROP de la demanda intermitente (μL = 0,46 → 2 en vez de 1).
- Guardar por entidad JPA — incrementa `version` (409 a ventas concurrentes) y una venta con la entidad cargada
  reescribiría el ROP viejo.
- UPDATE único con JOIN — MySQL no garantiza el orden de bloqueo en un UPDATE de varias tablas; riesgo de interbloqueo
  con ventas de varias líneas.
- Calcular el ROP en caliente para la interfaz — lo mostrado podría no coincidir con lo aplicado.
- Publicar `UmbralCambiado` por cada ROP — 300 evaluaciones por evento; la re-evaluación completa ya lo cubre.
- Una transacción para todo (incluidas las alertas) — un fallo en una alerta revertiría la clasificación entera.
- Transacción por variante para ABC/ROP — dejaría clases de dos ejecuciones mezcladas si falla a mitad.
- Recálculo asíncrono con sondeo — complejidad innecesaria para < 5 s estimados.
- ShedLock o bloqueo con nombre de MySQL — una sola instancia; el bloqueo con nombre exige fijar la conexión del pool.
- SIN_ROTACION por variante — con demanda intermitente ~25 % de las variantes de la cola pasan 60 días sin vender:
  ~60 alertas falsas.
- SIN_ROTACION independiente de STOCK_BAJO — mensaje contradictorio "pide más" y "no se vende" sobre la misma variante.
- Para la meta de alertas: STOCK_BAJO solo para A y B, o umbral = stock de seguridad — bajan el número ya, pero cambian
  el significado de REQ-04 sin atacar la causa (pedidos en camino).

## Consecuencias
- Migración V14 (`ejecucion_analitica`, `analitica_variante`, índice de movimiento). Cambio en `Variante`:
  `updatable = false` en dos columnas.
- `docs/dominio.md`: nueva sección "Analítica"; "Alertas" con SIN_ROTACION por producto, prioridad y episodios; el
  estado de alerta deja de coincidir siempre con `estadoStock()` (SIN_ROTACION puede reemplazar a BAJO).
- Skill `algoritmos-analitica`: ventana ABC de 12 meses, ROP híbrido sin piso, dos definiciones de demanda.
- Las alertas AGOTADO/STOCK_BAJO atendidas a mano se reabren en la noche si la condición sigue.
- Hasta la siguiente ejecución, una variante nueva usa `stockMinimo`; una variante desactivada y reactivada entre dos
  ejecuciones conserva su ROP y su clase anteriores (solo la ejecución los limpia), y el inventario muestra la clase de
  una variante recién desactivada hasta entonces.
- Riesgos residuales aceptados (revisión de la fase 5):
  - Una variante ocupada por una venta durante todos los reintentos conserva su ROP y clase anteriores (log `warn` con
    los ids) hasta la siguiente ejecución; mientras tanto la instantánea ya muestra el valor nuevo.
  - Una venta que llegue mientras el cálculo confirma espera unos milisegundos; solo recibiría 409 si superara el
    `innodb_lock_wait_timeout` (50 s).
  - Si otra talla del producto vende entre la relectura de `evaluar` y su commit, la SIN_ROTACION recién abierta queda
    abierta hasta la siguiente evaluación de esa variante o la noche.
- La meta de alertas queda medida pero pendiente: la fase 6 cambia STOCK_BAJO a la posición de inventario y la semilla
  repone con planes de compra.
- Sin historial de clases ni de ROP (solo la última instantánea); el historial de ejecuciones sí queda.
- Una sola instancia del backend (guardia en memoria).
