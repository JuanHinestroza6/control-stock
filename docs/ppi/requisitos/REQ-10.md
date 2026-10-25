# REQ-10 — Optimizar la Planeación de Compras (FO-ERS)

> Estado a la fecha: implementado en la Fase 6 (backend y frontend), rama `fase-6-prediccion-compras`. Decisiones en
> ADR-010. Incluye el pronóstico de demanda (SES y Holt-Winters multiplicativo con backtesting), la sugerencia de compra
> con política (s, S), el plan de compras con su ciclo de vida y la recepción de pedidos.
> Los datos de stakeholder, necesidad, prioridad y el texto de CU-01 provienen del documento Word (anexo del plan de la
> fase 7). CU-01 es el caso de uso del Word; CU-16, CU-17 y CU-18 son **nuevos** y lo desglosan en lo implementado (ver
> la convención de códigos en `docs/dominio.md`).

| Campo | Contenido |
|---|---|
| **Código** | REQ-10 |
| **Nombre** | Optimizar la Planeación de Compras |
| **Tipo** | Funcional |
| **Stakeholder** | Asistente de gerencia |
| **Necesidad** | NEC-10 |
| **Casos de uso** | CU-01 «Optimizar planeación de compras» (Word, principal); detalle implementado en CU-16 «Consultar el pronóstico de demanda», CU-17 «Gestionar el plan de compras» y CU-18 «Recibir un pedido» (nuevos) |
| **Relacionados** | REQ-04 (la alerta STOCK_BAJO usa la posición de inventario y el punto de reorden usa el pronóstico); REQ-02 (la recepción genera entradas en el kardex); REQ-14 (la clase ABC prioriza la sugerencia y fija el nivel de servicio); REQ-09 (tendencias); REQ-11 (productos activos, `leadTimeDias`, `loteMinimo`); REQ-06 (roles) |
| **Prioridad** | Alta |
| **Módulos** | `compra` (`PlanCompraService`, `PlanCompraController`, `PedidosEnCaminoJpa`, entidades `PlanCompra` y `DetallePlan`); `analitica` (`PronosticoService`, `SelectorPronostico`, `SuavizadoExponencial`, `HoltWintersMultiplicativo`, `CorrectorCensura`, `Desagregador`, `SugerenciaCompraAnalitica`, `CalculadoraSugerenciaCompra`, `AnaliticaController`); `inventario` (`ConsultaPedidosEnCamino`, `OperacionInventarioService.registrarEntradaDePlan`, evento `PosicionCambiada`, `AlertaService`); migraciones V15 y V16; frontend: `paginas/compras`, `paginas/analitica` (pestaña «Pronóstico»), `api/compras`, `api/analitica` |

## Descripción

Word: «El sistema debe sugerir la cantidad óptima de compra de productos basada en demanda proyectada y niveles de
inventario».

### Flujo CU-01 «Optimizar planeación de compras» (Word → sistema)

| Paso | Actor (Administrador; en el sistema también Inventario) | Sistema |
|---|---|---|
| 1–2 | Solicita generar una planeación (Compras → «Generar sugerencia») | Verifica los niveles de inventario: posición = disponible + en camino frente al punto de reorden |
| 3–5 | Espera las sugerencias | Analiza el pronóstico de demanda y el plazo de reposición (`leadTimeDias`) y muestra las variantes con cantidades sugeridas (política (s, S), lote mínimo) en un plan BORRADOR (CU-16, CU-17) |
| 6–7 | Revisa, ajusta y confirma (ADMIN aprueba) | Guarda el plan APROBADO, que funciona como orden de compra preliminar y cuenta como «en camino»; al llegar la mercancía se recibe (CU-18) |

- **Alternativa 1 (faltan datos de ventas)**: con menos de 3 meses de historia no hay pronóstico (`SIN_HISTORIA`); el
  punto de reorden usa la demanda histórica (fuente `HISTORICA`) o, sin demanda, el stock mínimo, y la sugerencia
  justifica cada línea. El sistema no bloquea la planeación.
- **Alternativa 2 (sin permisos)**: VENDEDOR recibe 403; CONTABILIDAD solo lee; solo ADMIN aprueba (INVENTARIO 403 al
  aprobar).
- **Extensión «Registrar proveedor»**: fuera de alcance.
- **Poscondición**: el plan queda registrado con sus líneas por variante, asociado al inventario (en camino y entradas
  `PLAN_COMPRA/{id}` al recibir) y disponible para consulta y ejecución.

El sistema debe ayudar al encargado de inventario a decidir qué reponer y cuánto, a partir de la demanda pronosticada,
y llevar el pedido desde su preparación hasta su recepción. El pronóstico proyecta la demanda de cada producto para el
mes en curso y los dos siguientes; la sugerencia propone las cantidades por variante; el plan de compras formaliza el
pedido (con aprobación del administrador) y la recepción registra la mercancía en el kardex. Lo que está aprobado y aún
no llega cuenta como «en camino» y entra en la posición de inventario que gobierna la alerta STOCK_BAJO. El sistema
nunca modifica stock por sí mismo al sugerir o aprobar: el stock solo cambia al recibir, mediante `MovimientoService`.

## Alcance implementado (Fase 6)

### Pronóstico de demanda (CU-16)

- **Serie**: mensual por producto, de la *demanda de inventario* (vendidas − devueltas aptas; ventas REGISTRADA; recorte a
  0 por mes), solo meses completos anteriores al mes en curso, máximo 36. Se pronostican el mes actual y los dos
  siguientes. Los productos inactivos no se pronostican.
- **Corrección por agotados**: `corregida = observada / max(disponibilidad, 0,5)`, con `disponibilidad` igual a la
  fracción de días con stock de las variantes activas, ponderada por su participación y reconstruida desde el kardex.
  Se guardan ambas series.
- **Método**: suavizado exponencial simple (SES) o **Holt-Winters multiplicativo** (m = 12), elegido por **backtesting
  con origen móvil** (cortes en n − 6 y n − 3, tres meses cada uno, WAPE sobre seis puntos; gana el menor WAPE y un
  empate menor que 0,001 se resuelve a favor de SES). Holt-Winters solo compite con 30 o más meses, sin ningún mes en 0 y
  con nivel positivo; en otro caso se usa SES y se guarda el motivo (`POCA_HISTORIA`, `MES_EN_CERO`,
  `NIVEL_NO_POSITIVO`). Con menos de 3 meses no hay pronóstico (`SIN_HISTORIA`); con 3 a 8 meses se usa SES sin backtest.
- **Desagregación**: el pronóstico del producto se reparte entre sus variantes activas según su participación de 180
  días, corregida por días con stock. Se guardan filas por producto y por variante (V15).
- **Ejecución**: el pronóstico es el primer paso de la ejecución analítica nocturna (02:00 America/Bogota) o del
  recálculo manual del ADMIN, en una transacción propia (todo o nada). Si falla, la ejecución queda FALLIDA y el punto de
  reorden usa la demanda histórica. Solo se conserva la última ejecución.
- **Punto de reorden con pronóstico**: `μL` es la suma de la tasa diaria pronosticada en los próximos `L` días
  (fuente `PRONOSTICO`); sin pronóstico se mantiene la fórmula de la Fase 5 (fuente `HISTORICA`). Ver REQ-04.
- **Interfaz**: pestaña «Pronóstico» con listado por producto, detalle (serie observada y corregida, pronóstico,
  parámetros, WAPE de cada método, motivo sin Holt-Winters y desagregación por variante) y nota del riesgo del backtest.

### Sugerencia de compra (CU-17)

- **Candidatas**: variantes activas de productos activos con `posición ≤ umbral`, donde `posición = disponible + en
  camino` y el umbral es el punto de reorden (o `stockMinimo` si no hay). Se excluyen las que ya están en otro plan en
  BORRADOR (se informan como omitidas).
- **Política (s, S)**: se repone hasta `S = umbral + demanda pronosticada de R = 30 días posteriores al lead time`
  (`app.compras.periodo-revision-dias`); la cantidad es `S − posición` redondeada hacia arriba al múltiplo de
  `loteMinimo`. Las líneas con cantidad 0 no entran. Sin candidatas, 422. Cada línea lleva una justificación legible
  (por ejemplo, «ROP 31 + demanda 30 d 68 − disponible 10 − en camino 0 = 89 → lote 6: 90»).
- **Prioridad y tope de 300 líneas**: un plan admite hasta 300 líneas. Con más candidatas entran primero las agotadas
  (disponible 0), luego las de clase ABC A, B, C y sin clase, luego el mayor faltante relativo
  `(umbral − posición) / max(umbral, 1)` y por último el id. Las excluidas se informan en `excluidasPorTope` y siguen
  siendo candidatas en la siguiente sugerencia.
- **Puerto**: `compra` obtiene la sugerencia por `FuenteSugerenciaCompra`, implementado en `analitica`; así `compra` no
  depende de `analitica`.

### Plan de compras (CU-17)

- **Ciclo de vida**: `BORRADOR → APROBADO → RECIBIDO`, más `CANCELADO` desde BORRADOR o APROBADO (motivo de 5 a 200
  caracteres). RECIBIDO y CANCELADO son finales. Toda transición bloquea la fila del plan (`PESSIMISTIC_WRITE`) y
  verifica el estado; si no corresponde, 409 («El plan PC-000012 ya está APROBADO.»). Diagrama de estados abajo.
- **Crear**: *sugerido* (origen SUGERIDO) o *manual* (origen MANUAL); ambos quedan en BORRADOR y son editables. Máximo
  300 líneas, una por variante (repetida → 400), cantidad aprobada de 1 a 100.000. Un plan puede mezclar productos. Sin
  proveedor ni borrado físico. Número visible `PC-000012`.
- **Concurrencia**: `@Version` en el plan; editar o aprobar con una versión vieja responde 409. Orden de bloqueo: plan y
  luego variantes por id.
- **Aprobar** (solo ADMIN): exige variantes y productos activos (422 con el SKU). Las líneas pasan a estar «en camino» y
  se publica `PosicionCambiada(varianteId)` por línea.
- **Cancelar**: un APROBADO cancelado deja de estar en camino (se publica `PosicionCambiada`) y puede reabrir alertas.
- **Llegada esperada** (calculada, no guardada): `aprobadoEn + max(leadTimeDias)` de los productos del plan; la interfaz
  marca «atrasado» cuando ya pasó.
- **Interfaz**: página Compras (listado con filtro por estado, «Sugerir plan» y «Plan manual»), detalle del plan
  (edición con aviso de conflicto y fusión del borrador, aprobar, cancelar, justificación por línea) y variantes omitidas
  o excluidas por tope tras la sugerencia.

### Recepción del pedido (CU-18)

- **Recepción única**: por cada línea se informa la cantidad recibida (0 a 100.000; por defecto la aprobada) y el costo
  unitario; deben venir todas las líneas una sola vez. Lo recibido de más se acepta; lo no recibido deja de estar en
  camino.
- **Entrada al kardex**: una ENTRADA por línea con cantidad mayor que 0, mediante `OperacionInventarioService` y
  `MovimientoService` (referencia `PLAN_COMPRA/{id}`), con las reglas de las entradas: mismo costo por producto (422 si
  no) y `precioCompra` del producto igual al último costo mayor que 0. El stock solo cambia por `MovimientoService`
  (regla de oro 1). Todas las líneas en 0 → 422 («use Cancelar»).
- **Variantes inactivas**: se permite recibir sobre ellas (la mercancía ya pedida llega igual); aprobar sí exige activas.
- **Alertas**: cada entrada publica `StockCambiado`; las líneas recibidas en 0 publican `PosicionCambiada`. El
  `AlertaListener` las re-evalúa después del commit.

### Endpoints y roles

| Método y ruta | Rol |
|---|---|
| `GET /api/analitica/pronosticos`, `GET /api/analitica/pronosticos/{productoId}` | ADMIN, INVENTARIO, CONTABILIDAD |
| `POST /api/analitica/recalcular` (incluye el pronóstico) | ADMIN |
| `GET /api/compras/planes`, `GET /api/compras/planes/{id}` | ADMIN, INVENTARIO, CONTABILIDAD (con costos) |
| `POST /api/compras/planes` (manual), `POST /api/compras/planes/sugerido`, `PUT /api/compras/planes/{id}` | ADMIN, INVENTARIO |
| `POST /api/compras/planes/{id}/aprobar` | ADMIN |
| `POST /api/compras/planes/{id}/cancelar` | ADMIN (BORRADOR o APROBADO); INVENTARIO solo BORRADOR (403 sobre APROBADO) |
| `POST /api/compras/planes/{id}/recibir` | ADMIN, INVENTARIO |

VENDEDOR recibe 403 en todos los anteriores. CONTABILIDAD solo lee (403 en toda escritura).

### Flujo «sugerir, aprobar y recibir un plan de compras»

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El encargado de inventario abre Compras y pulsa «Sugerir plan» (categoría opcional) | Calcula las candidatas con la política (s, S), omite las que ya están en otro borrador, aplica el tope de 300 y crea un plan BORRADOR con justificación por línea |
| 2 | Revisa el borrador y ajusta cantidades, quita o agrega líneas | Valida y guarda con control de versión (409 si otro usuario lo cambió) |
| 3 | El administrador revisa el borrador y pulsa «Aprobar» | Verifica variantes activas y versión, pasa el plan a APROBADO y publica `PosicionCambiada` por línea |
| 4 | | Tras el commit, el listener re-evalúa las alertas: STOCK_BAJO se cierra si la posición supera el umbral; AGOTADO se mantiene |
| 5 | Llega la mercancía; el encargado abre el plan y pulsa «Recibir», informando cantidad y costo por línea | Registra una ENTRADA por línea con cantidad > 0 (`PLAN_COMPRA/{id}`), actualiza `precioCompra` y pasa el plan a RECIBIDO |
| 6 | | Publica `StockCambiado`; el listener abre o cierra las alertas según el nuevo disponible |

- **Caminos alternativos**: sin candidatas → 422; todas las candidatas ya en otro borrador → 422 con sus SKU; versión
  vieja al editar o aprobar → 409; variante o producto inactivo al aprobar → 422; plan que ya no está en el estado
  esperado (aprobar, recibir o cancelar) → 409; recibir con todas las líneas en 0 → 422; costos distintos para variantes
  de un mismo producto → 422; INVENTARIO cancela un APROBADO → 403; VENDEDOR → 403; un pedido nunca recibido se cierra
  con Cancelar (motivo obligatorio).
- **Puntos de extensión**: consultar el kardex de la variante desde la recepción (REQ-02); consultar el pronóstico del
  producto desde la sugerencia (CU-16); atender la alerta de la variante (REQ-04).
- **Poscondición**: el plan queda en un estado coherente (los finales no cambian); el «en camino» es la suma de las
  cantidades aprobadas de los planes APROBADO; el stock solo cambió, si cambió, por las entradas de la recepción.

## Diagramas

- Estados del plan de compras: [`diagramas/estados-plan-compra.puml`](../diagramas/estados-plan-compra.puml)
- Clases del módulo `compra` y sus puertos: [`diagramas/clases-fase6-compra.puml`](../diagramas/clases-fase6-compra.puml)
- Secuencia sugerir, aprobar y recibir: [`diagramas/secuencia-plan-compra.puml`](../diagramas/secuencia-plan-compra.puml)

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | SES y Holt-Winters multiplicativo se calculan a mano en series de referencia; los índices estacionales tienen media 1 y los niveles no positivos se descartan | Cumplido | `SuavizadoExponencialTest`, `HoltWintersMultiplicativoTest` |
| CA-2 | La selección por backtesting con origen móvil respeta los tramos de meses, el WAPE, el empate a favor de SES y los motivos sin Holt-Winters | Cumplido | `SelectorPronosticoTest`, `SelectorPronosticoBordesTest`, `SelectorPronosticoSemillaTest` |
| CA-3 | La corrección por agotados y la desagregación a variantes cumplen los casos de referencia (piso 0,5, variante creada a mitad de mes) | Cumplido | `CorrectorCensuraTest`, `DesagregadorTest`, `SerieMensualTest` |
| CA-4 | El pronóstico se guarda por producto y variante, es único por periodo y se inserta en orden de variante; un fallo deja la ejecución FALLIDA y el ROP usa la demanda histórica | Cumplido | `PronosticoServiceIntegracionTest`, `EjecucionAnaliticaPronosticoIntegracionTest`, `PronosticoIntegridadTest` |
| CA-5 | La sugerencia (s, S) calcula la cantidad con lote mínimo, justificación y casos borde (sin ROP, sin demanda, sin pronóstico) | Cumplido | `CalculadoraSugerenciaCompraTest`, `SugerenciaCompraAnaliticaTest` |
| CA-6 | Con más de 300 candidatas entran por prioridad (agotadas, clase ABC, faltante relativo, id) y se informan las excluidas y las omitidas por estar en otro borrador | Cumplido | `PlanCompraPrioridadTest`, `SugerenciaCompraIntegracionTest` |
| CA-7 | El plan recorre BORRADOR, APROBADO, RECIBIDO o CANCELADO; transiciones inválidas responden 409; la versión vieja responde 409 | Cumplido | `PlanCompraTest`, `PlanCompraServiceIntegracionTest`, `PlanCompraRepositoryTest` |
| CA-8 | Recibir genera una ENTRADA por línea con cantidad > 0 (referencia `PLAN_COMPRA/{id}`), con las reglas de costo, sobre variantes inactivas; todas en 0 → 422; recibir y cancelar a la vez producen una sola transición | Cumplido | `PlanCompraRecepcionIntegracionTest`, `CompraFlujoIntegracionTest` |
| CA-9 | Aprobar o cancelar un APROBADO re-evalúa las alertas por posición; AGOTADO no cambia; un fallo del listener no revierte la aprobación | Cumplido | `PlanCompraAlertasIntegracionTest`, `AlertaServiceTest` |
| CA-10 | Matriz de roles: VENDEDOR 403; CONTABILIDAD solo lee; solo ADMIN aprueba; INVENTARIO no cancela un APROBADO | Cumplido | `PlanCompraControllerTest`, `PlanCompraHttpIntegracionTest`, `AnaliticaControllerPronosticoTest` |
| CA-11 | `compra` no depende de `venta` ni de `analitica`; `inventario` y `catalogo` no dependen de `compra` | Cumplido | `DependenciasEntreModulosTest`, `ReglasModulosDetectanViolacionesTest` |
| CA-12 | La interfaz sugiere, crea, edita (con fusión ante edición concurrente), aprueba, cancela y recibe según el rol, y muestra el pronóstico y su detalle | Cumplido | `compras.test.tsx`, `planDetalle.test.tsx`, `recibirPlan.test.tsx`, `fusionBorrador.test.ts`, `PestanaPronostico.test.tsx`, `compras.test.ts` |
| CA-13 | Con la semilla de 36 meses, los productos de cabeza eligen Holt-Winters y la reposición por planes deja pedidos en camino coherentes | Cumplido (T2 de la Fase 6, 2026-10-21, por API y base de datos). Holt-Winters ganó en 7 productos (LIV001, LIV002, LIV009, LIV014, LIV018, LIV020 y LIV027; WAPE medio 0,1706 frente a 0,4201 de SES) y 22 usaron SES (9 por `MES_EN_CERO`; 13 compitieron y ganó SES). Pedidos en camino: 98 planes APROBADO, 1.060 unidades, 101 variantes. Revisión visual por la interfaz pendiente del usuario | `SemillaIntegracionTest` (verificación automática); corrida real T2 |

## Observaciones

- Observación de la T2 (2026-10-21), corrección por agotados: en los últimos 12 meses las unidades observadas (11.601) pasaron a 11.890,13 corregidas (+289 unidades), mientras que la semilla informa 2.817 unidades perdidas en 36 meses (cerca de 939 por año). La corrección recupera cerca de 0,3 veces lo perdido, por debajo del rango de 0,5 a 2 veces previsto en el plan. Causas probables: el piso de disponibilidad de 0,5, la inclusión solo de variantes activas, la ponderación por participación y que la demanda perdida por quiebres cortos dentro del día no se refleja en los días con stock. No se modificó el algoritmo.
- Flujo de compra verificado por API en la T2: el encargado de inventario generó la sugerencia PC-003674 (20 líneas, 0 omitidas, 0 excluidas por tope), el administrador la aprobó y el encargado de inventario la recibió: 20 ENTRADAS con referencia `PLAN_COMPRA/3674` (152 unidades). Ejemplo LIV002-L-BLA: físico 10, en camino 0, estado BAJO; aprobado: en camino 15; recibido: físico 25, en camino 0, estado OK. Matriz de roles correcta (compras, tendencias y pronóstico: ADMIN, INVENTARIO y CONTABILIDAD 200, VENDEDOR 403, sin token 401; sugerir y recalcular restringidos; INVENTARIO 403 al aprobar). Tendencias de 24 meses: 25 puntos; detalle del pronóstico de LIV001: HOLT_WINTERS con 16 variantes.
- La T2 se ejecutó por API y base de datos; la revisión visual por la interfaz la realiza el usuario después.

- Datos del Word: Asistente de gerencia, NEC-10, CU-01, prioridad Alta. CU-16, CU-17 y CU-18 desglosan CU-01 en lo
  implementado (pronóstico, plan y recepción).
- Alcance frente al Word: en el sistema también INVENTARIO crea y recibe planes; solo ADMIN aprueba (D7 de la fase 6).
  «Registrar proveedor» (extensión de CU-01) queda fuera de alcance.
- Fase 7 (B10): el resumen de la semilla informa ahora las unidades perdidas comparables (12 meses completos antes del
  mes del corte de la analítica de cierre, sin variantes agotables a propósito ni el descontinuado) y la razón de la
  corrección por agotados. La corrección también ve las variantes agotables (siguen activas) y el denominador no, así
  que la razón tiende a quedar algo alta. La cifra de la semilla de 36 meses se registra en la T2 de la fase 7.
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados (ver `REQ-09.md`); NEC-010 debería ser
  NEC-10.
- **Riesgo aceptado: el backtest no cubre noviembre y diciembre.** Los seis meses evaluados van de abril a septiembre con
  corte en octubre; el WAPE puede subestimar el error en los picos, donde el modelo multiplicativo amplifica un error de
  nivel (un nivel 10 % alto produce un diciembre 16 % alto). La interfaz lo advierte.
- Otros riesgos aceptados (ADR-010): los productos de cola tienen meses en 0 y usan SES; la corrección por agotados
  sobreestima si el quiebre se debió a falta de demanda; un plan aprobado y nunca recibido mantiene cerrada la alerta
  hasta cancelarlo (la interfaz lo marca «atrasado»); dos sugerencias simultáneas pueden meter la misma variante en dos
  borradores; sin historial de pronósticos, sin proveedores y con una sola instancia del backend.
- Recepciones parciales múltiples y proveedores quedan fuera de alcance: un faltante se resuelve con un nuevo plan.
