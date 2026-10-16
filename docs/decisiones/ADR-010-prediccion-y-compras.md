# ADR-010: Predicción de demanda y plan de compras (pronóstico, posición de inventario y reposición)

- Estado: aceptada
- Fecha: 2026-10-15

## Contexto
La fase 6 agrega el pronóstico de demanda (REQ-10), las tendencias históricas (REQ-09) y el plan de compras (REQ-10), y
debe cerrar la meta de alertas que ADR-009 dejó pendiente (34,4 % de variantes en alerta de stock frente a ~10–15 %).
Había nueve cuestiones abiertas:
1. Qué demanda se pronostica y cómo tratar la censura por agotados (~2,5–3 % de ventas perdidas en la semilla).
2. Qué modelo estacional usar y cómo elegir entre SES y Holt-Winters con una historia corta: la semilla de 24 meses
   empieza a mitad de mes y solo deja **23 meses completos**; con el skill literal (24 de entrenamiento + 3 de prueba)
   Holt-Winters nunca sería elegible.
3. Si el punto de reorden debe usar la demanda pronosticada (ADR-009 dejó como riesgo el ROP rezagado al entrar a la
   temporada alta y la demanda censurada).
4. Cómo reducir las alertas sin maquillar el número: una alerta "disponible ≤ ROP" sigue activa todo el lead time aunque
   ya se haya pedido.
5. El ciclo de vida del plan: el dominio no tenía cancelación (un pedido nunca entregado quedaría "en camino" para
   siempre) ni recepción con cantidades distintas a las pedidas, y prohibía entradas sobre variantes inactivas.
6. Qué variantes y cuántas unidades sugerir (el skill proponía una revisión periódica que rellena todo el catálogo).
7. Quién recibe un pedido (la matriz solo decía "INVENTARIO crea borrador").
8. Dependencias entre módulos: `compra` necesita la sugerencia (analitica) e `inventario` necesita lo que viene en
   camino (compra), pero ADR-009 prohíbe depender de `analitica` y `compra` depende de `inventario` para recibir.
9. Cómo debe reponer la semilla para que la posición de inventario del cierre sea coherente (hoy ni siquiera hace los
   pedidos que llegarían después del último día).

## Decisión
1. **Demanda de inventario mensual por producto, corregida por agotados.** Serie de meses completos (el mes en curso
   nunca entra), máximo 36: vendidas (ventas REGISTRADA, por fecha de venta) − devueltas aptas (por fecha de devolución),
   recorte a 0 por mes; las ANULADAS nunca cuentan. Corrección: `corregida = observada / max(disponibilidad, 0,5)`, con
   `disponibilidad` = fracción de días con stock de las variantes activas del producto, ponderada por su participación
   (reconstruida desde el kardex: un día cuenta "sin stock" si abre en 0 y ningún movimiento lo deja > 0).
2. **Holt-Winters multiplicativo** (m = 12) frente a SES, elegido por **backtesting con origen móvil**: dos cortes
   (n − 6 y n − 3), 3 meses cada uno, WAPE sobre 6 puntos; menor WAPE gana y el empate (< 0,001) va a SES. La moda y la
   semilla tienen estacionalidad multiplicativa (el pico de diciembre ×1,6 crece con la empresa). Inicialización con los
   dos primeros años: `tend_0 = (ȳ2 − ȳ1)/12`, `nivel_0 = ȳ1 − 6,5·tend_0`, índices `r_i = y_i / (nivel_0 + i·tend_0)`
   normalizados a media 1. HW solo compite si hay ≥ 30 meses (24 en el entrenamiento más corto), **ningún mes de la serie
   es 0** y el nivel se mantiene positivo; si no, SES, y el motivo (`POCA_HISTORIA`, `MES_EN_CERO`, `NIVEL_NO_POSITIVO`)
   se guarda. Rejillas: α 0,1…0,9 (SES); {0,1; 0,3; 0,5; 0,7; 0,9}³ (HW). Menos de 3 meses → sin pronóstico; 3–8 →
   SES sin backtest. Se pronostican el mes actual y los dos siguientes, y se desagregan a variantes por su participación
   de 180 días corregida por días con stock. La semilla pasa a **36 meses** (35 completos).
3. **ROP con demanda pronosticada.** `μL` = suma de la tasa diaria pronosticada de la variante en los próximos L días;
   `s` sigue saliendo de la serie diaria observada de 90 días; mismas reglas NORMAL/POISSON y niveles de servicio de
   ADR-009. Sin pronóstico del producto, fórmula de la fase 5. Se guarda la fuente (`PRONOSTICO` | `HISTORICA`).
4. **STOCK_BAJO por posición de inventario.** `posición = disponible + en camino` (Σ cantidad aprobada de planes
   APROBADO). STOCK_BAJO si `0 < disponible` y `posición ≤ umbral`; **AGOTADO sigue sobre el disponible**;
   `Variante.estadoStock()` también (hecho físico; la interfaz muestra "N en camino"). La métrica de ADR-009 conserva su
   definición y se guarda además la contrafactual "en alerta sin contar lo que viene en camino". Aprobar o cancelar un
   plan aprobado publica `PosicionCambiada` por variante; el `AlertaListener` las re-evalúa después del commit.
5. **Plan de compras** `BORRADOR → APROBADO → RECIBIDO`, más **CANCELADO** (desde BORRADOR o APROBADO, motivo
   obligatorio). **Recepción única** con cantidad recibida (0–100.000) y costo por línea; lo no recibido deja de estar en
   camino. La recepción genera una ENTRADA por línea con cantidad > 0 vía `OperacionInventarioService` →
   `MovimientoService` (referencia `PLAN_COMPRA/{id}`, mismas reglas de costo por producto y de `precioCompra`) y **se
   permite sobre variantes inactivas** (unidades ya pedidas); aprobar exige variantes y productos activos. `@Version` en
   el plan (editar o aprobar con versión vieja → 409); toda transición bloquea la fila del plan y verifica el estado;
   orden de bloqueo plan → variantes (por id). Número visible `PC-000012`. Sin proveedor ni borrado físico.
6. **Sugerencia (s, S).** Candidatas: variantes activas de productos activos con `posición ≤ umbral` que no estén en otro
   BORRADOR (se informan como omitidas). Cantidad hasta `S = umbral + demanda pronosticada de R = 30 días después del
   lead time`, redondeada hacia arriba al múltiplo de `loteMinimo`; las de cantidad 0 no entran. Equivale a la fórmula
   del skill (`μ(L+R) + SS`); solo cambia qué variantes entran. Cada línea lleva una justificación legible.
7. **Roles.** Crear (sugerido o manual) y editar borradores: ADMIN e INVENTARIO. Aprobar: solo ADMIN. Cancelar: ADMIN;
   INVENTARIO solo descarta borradores. Recibir: ADMIN e INVENTARIO. Ver planes: ADMIN, INVENTARIO y CONTABILIDAD.
   Tendencias y pronóstico: ADMIN, INVENTARIO y CONTABILIDAD. VENDEDOR: 403 en todo lo anterior.
8. **Módulo `compra` y puertos** (patrón de `ConsultaReservasActivas`): `inventario.servicio.ConsultaPedidosEnCamino`,
   implementado en `compra`, y `compra.servicio.FuenteSugerenciaCompra`, implementado en `analitica`. ArchUnit:
   `catalogo`, `inventario`, `venta` y `usuario` no dependen de `compra`; `compra` no depende de `venta` ni de
   `analitica`; sigue vigente "ningún módulo salvo semilla depende de analitica".
9. **Instantáneas explicables y orquestación.** Migración **V15**: `pronostico` (3 meses por producto, `variante_id`
   null, y por variante; único por producto, variante y periodo con una columna generada), `analitica_producto` (método,
   α/β/γ, WAPE de cada método, motivo sin HW, unidades observadas y corregidas), `analitica_producto_mes` (serie
   observada, disponibilidad, corregida y pronósticos del backtest) y columnas nuevas en `analitica_variante` (fuente de
   demanda, `μL`, tasa) y `ejecucion_analitica` (productos pronosticados, HW/SES, WAPE global, alerta sin tránsito,
   variantes con pedido en camino). Migración **V16**: `plan_compra` y `detalle_plan`. La ejecución analítica pasa a
   registrar → pronóstico (transacción propia, todo o nada) → ABC + ROP (con el pronóstico en memoria; si falló, ROP
   histórico y ejecución FALLIDA con mensaje) → alertas → registro. Las filas de `pronostico` se insertan en orden
   ascendente de `variante_id`, el mismo orden en que bloquean las ventas, para que la S que toma la FK no forme un ciclo
   de espera con una venta de varias líneas.
10. **Tendencias** (REQ-09): demanda comercial (unidades e ingreso netos, como "Más vendidos") por mes, con el mismo mes
    del año anterior; máximo 36 meses. Gráficos con Recharts (carga diferida), que instala el usuario.
11. **Semilla:** 36 meses y reposición mediante el plan de compras real (INVENTARIO crea, ADMIN aprueba, INVENTARIO
    recibe), con su propia política de pedido; se permiten pedidos que llegan después del último día, que quedan en
    camino. El verificador comprueba que lo que está en camino coincide con la simulación.

## Alternativas consideradas
- Pronosticar sin corregir la censura — hereda el sesgo de las ventas perdidas; la corrección es una función pura
  verificable contra las unidades perdidas que informa la semilla.
- Imputar la demanda día a día en los días sin stock — ruido puro en variantes de 0,02 u/día.
- Holt-Winters aditivo (skill original) — supone un pico de amplitud fija en unidades; con una empresa que crece 1,5 %
  mensual subestima diciembre cada año más. Descartado por el usuario a favor del multiplicativo.
- Holt-Winters multiplicativo con meses en 0 (o con un épsilon) — un índice estacional 0 o casi 0 vuelve inestable la
  actualización (`y / s`) y no representa estacionalidad sino ausencia de demanda; SES es el modelo correcto ahí.
- Backtest con los 3 últimos meses (skill literal) — el método lo decide un solo tramo de 3 puntos (julio–septiembre, el
  más plano del año).
- Backtest de 12 meses con origen móvil — cubriría noviembre–diciembre, pero exige ≥ 36 meses completos más el
  entrenamiento de 24: una semilla de casi 4 años.
- Mantener la semilla en 24 meses con HW inicializado con un solo año — sin tendencia inicial confiable ni evaluación
  fuera de muestra para HW.
- ROP solo con la demanda observada — deja vivos los dos riesgos de ADR-009 (rezago en temporada y censura).
- `μL = max(observada, pronosticada)` — sube en temporada y nunca baja después.
- Omitir AGOTADO cuando hay pedido en camino, o un tipo nuevo PEDIDO_EN_CAMINO — esconde agotados reales o no reduce la
  carga de avisos.
- Recepciones parciales con estado RECIBIDO_PARCIAL — otra máquina de estados para un caso que se resuelve con un
  segundo plan.
- Dominio literal sin cancelación — el "en camino" inflado ocultaría alertas para siempre.
- Sugerencia por revisión periódica para todas las variantes (skill literal) — planes de cientos de líneas de 1 unidad.
- Sugerencia solo para clases A y B — deja la cola sin reposición sugerida aunque esté en alerta.
- Matriz literal (solo ADMIN edita, aprueba y recibe) — INVENTARIO ya registra entradas; recibir es una entrada con
  referencia. La segregación que importa (quien pide no aprueba) se conserva.
- Que `compra` dependa de `analitica` o `inventario` de `compra` — rompe ADR-009 o crea un ciclo; los puertos lo evitan.
- Quitar la FK de `pronostico.variante_id` para no tomar S sobre `variante` — las variantes nunca se borran, pero la
  integridad referencial se pierde sin necesidad: el orden de inserción basta.
- Semilla que siga la sugerencia del sistema — exigiría correr la analítica dentro de la simulación.

## Consecuencias
- Migraciones V15 y V16; módulo nuevo `compra`; `OperacionInventarioService` gana un método interno de entrada con
  referencia que admite variantes inactivas; `AlertaService` lee la posición por un puerto; evento `PosicionCambiada`.
- `docs/dominio.md`: secciones "Pronóstico" y "Plan de compras"; "Alertas" con la posición (STOCK_BAJO y
  `estadoStock()` pueden diferir: una variante BAJO con pedido en camino no tiene alerta); "Máquinas de estado" con
  CANCELADO; entradas de planes sobre variantes inactivas; "Datos semilla" con 36 meses y planes; matriz y notas de la
  fase 6; trazabilidad REQ-09 y REQ-10.
- Skill `algoritmos-analitica`: HW multiplicativo con su inicialización y exclusión por meses en 0, origen móvil,
  corrección por agotados, ROP con pronóstico y política (s, S). Skill `datos-semilla`: 36 meses y reposición por planes.
- La carga de la semilla sube de ~2 min a ~3,5–4 min.
- Orden de trabajo con punto de corte: antes de la semilla de 36 meses la base de demo tiene 23 meses completos y no
  tiene planes, así que todos los productos salen SES (HW se valida con pruebas unitarias e integración con datos a
  mano) y la métrica de alertas no cambia hasta que se aprueben planes.
- La exportación PDF de tendencias (CU-05) pasa a la fase 7 con los reportes.
- Riesgos residuales aceptados:
  - **El backtest no cubre noviembre–diciembre** (los 6 meses evaluados van de abril a septiembre con corte en octubre):
    el WAPE puede subestimar el error en los picos, justo donde el modelo multiplicativo amplifica un error de nivel
    (un nivel 10 % alto da un diciembre 16 % alto en unidades). Se documenta en la interfaz y en REQ-10.
  - Los productos de cola y los estancados tienen meses en 0 y usan SES: HW puede aparecer en pocos productos (los de
    cabeza, que concentran el ingreso). Se informa cuántos lo eligen en cada ejecución.
  - La meta de 10–15 % no está garantizada: AGOTADO ya pesa ~9 % (variantes que la semilla deja agotar a propósito y
    quiebres reales). Se mide y se explica con el desglose; la definición no se cambia para llegar al número.
  - La corrección por agotados sobreestima si el quiebre se debió a falta de demanda (piso 0,5 y solo variantes
    activas).
  - Un plan aprobado y nunca recibido mantiene la alerta cerrada hasta que se cancela; la interfaz lo marca "atrasado"
    cuando pasa `aprobadoEn + max(leadTimeDias)`.
  - Sin historial de pronósticos (solo el último), sin proveedores y una sola instancia del backend (guardia en memoria
    de ADR-009).
