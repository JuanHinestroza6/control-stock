# REQ-04 — Emitir Alertas de Nivel Mínimo de Stock (FO-ERS)

> Estado a la fecha: implementado en la Fase 3 (alertas AGOTADO y STOCK_BAJO por evento), ampliado en la Fase 5
> (punto de reorden dinámico como umbral, SIN_ROTACION y re-evaluación nocturna) y en la Fase 6 (STOCK_BAJO por
> posición de inventario, punto de reorden con pronóstico y métrica con y sin tránsito), rama
> `fase-6-prediccion-compras`. Decisiones en ADR-007, ADR-009 y ADR-010.

| Campo | Contenido |
|---|---|
| **Código** | REQ-04 |
| **Nombre** | Emitir Alertas de Nivel Mínimo de Stock |
| **Tipo** | Funcional |
| **Stakeholder** | Asistente de gerencia |
| **Necesidad** | NEC-04 |
| **Caso de uso** | CU-10 «Alertas automáticas de stock bajo» (extensión: CU-01 «crear solicitud de compra directa») |
| **Relacionados** | REQ-01 (estado de stock); REQ-02 (movimientos que disparan la evaluación); REQ-13 (apartados modifican el disponible); REQ-10 (planes aprobados = en camino; pronóstico del punto de reorden) |
| **Prioridad** | Media |
| **Módulos** | `inventario` (`AlertaService`, `AlertaListener`, `AlertaController`), `catalogo` (evento `UmbralCambiado`), `analitica` (cálculo del punto de reorden y ejecución nocturna), `compra` (pedidos en camino, evento `PosicionCambiada`); frontend: `paginas/alertas`, campana en `componentes/layout/Topbar`, `hooks/useAlertas`, `paginas/analitica` (pestaña Reorden) |

## Descripción

El sistema debe advertir cuándo el stock disponible de una variante activa llega a su umbral o se agota, para que el
encargado de inventario reponga a tiempo. El umbral es el punto de reorden (`puntoReorden`) cuando existe y, en su
defecto, `stockMinimo`. Las reglas constan en ADR-007 y en `docs/dominio.md` (sección "Alertas").

## Alcance implementado (Fase 3)

- **Tipos**: `AGOTADO` (`disponible == 0`) y `STOCK_BAJO` (`0 < disponible ≤ umbral`). Son **excluyentes** y coinciden con
  `Variante.estadoStock()`: al pasar de BAJO a AGOTADO se cierra la de STOCK_BAJO y se abre la de AGOTADO, y viceversa;
  al volver a OK se cierran ambas. (En la Fase 5 se incorpora `SIN_ROTACION`, con prioridad entre ambas; ver abajo.)
- **Evaluación por evento**: un `@TransactionalEventListener(AFTER_COMMIT)` atiende `StockCambiado` (tras cada movimiento)
  y `UmbralCambiado` (al editar `stockMinimo`) y llama a `AlertaService.evaluar` en una transacción nueva
  (`REQUIRES_NEW`), bloqueando la variante. Un fallo se registra en el log y nunca llega al usuario.
- **Una alerta ACTIVA por variante y tipo**, garantizada por un índice único (columna generada `variante_activa`).
  Reevaluar sin cambios solo actualiza `revisadaEn`. Las variantes inactivas no abren alertas ni aparecen en el listado.
- **Atender**: manual por ADMIN o INVENTARIO (`ACTIVA → ATENDIDA` con `atendidaPor`), o automática cuando la condición
  deja de cumplirse (`atendidaPor` nulo). Atender una alerta ya atendida responde 409.
- **Campana**: contador de alertas activas en la barra superior, visible a ADMIN, INVENTARIO y VENDEDOR.

## Alcance implementado (Fase 5)

- **Punto de reorden (ROP) dinámico como umbral.** Cada variante activa recibe un ROP calculado con 90 días de demanda de
  inventario (ventas menos devoluciones aptas; los días sin ventas cuentan como 0) y su tiempo de reposición `L`
  (`leadTimeDias`). Fórmula híbrida:
  - `μL = d̄ × L ≥ 5`: `ROP = ceil(μL + z × s × √L)` (método NORMAL).
  - `μL < 5`: menor `r` con `P(Poisson(μL) ≤ r)` mayor o igual que el nivel de servicio (método POISSON), apropiado
    para la demanda intermitente de la cola del catálogo.
  - Demanda media 0: ROP 0, solo AGOTADO (SIN_DEMANDA). Menos de 30 días de historia: ROP nulo y el umbral vuelve a
    `stockMinimo` (SIN_HISTORIA).
  - **Niveles de servicio por clase ABC** (REQ-14): A 95 % (z = 1,65), B 90 % (z = 1,28), C 80 % (z = 0,84).
  - `stockMinimo` es solo el respaldo cuando no hay ROP (no es piso); `loteMinimo` no interviene.
- **SIN_ROTACION por producto.** Se abre cuando el producto no tiene ventas no anuladas en 60 días (configurable) y existe
  hace 60 días o más; la alerta se crea en cada variante con `disponible > 0`. Una venta de cualquier variante cierra la
  SIN_ROTACION de todas las del producto. Atendida a mano no se reabre dentro del mismo **episodio** (desde
  `max(última venta, creación del producto) + 60 días`).
- **Prioridad AGOTADO > SIN_ROTACION > STOCK_BAJO.** Las tres son excluyentes: una variante tiene como máximo una alerta
  ACTIVA; al cambiar el tipo vigente se cierra la anterior (ATENDIDA automática) y se abre la nueva. Por esto el tipo de la
  alerta ya no coincide siempre con `estadoStock()`: SIN_ROTACION puede reemplazar a BAJO.
- **Re-evaluación nocturna.** Cada ejecución analítica (02:00 America/Bogota o "Recalcular" del ADMIN) re-evalúa las
  alertas de todas las variantes, una transacción por variante; reabre las atendidas a mano cuya condición sigue vigente.
- **Cierre al desactivar.** Desactivar una variante o su producto cierra todas sus alertas ACTIVAS (corrige una omisión
  de la Fase 3).
- **Métrica de alertas.** Cada ejecución guarda el porcentaje de variantes activas con AGOTADO o STOCK_BAJO
  (SIN_ROTACION aparte).
- **Interfaz.** Pestaña Reorden en Analítica (método, nivel de servicio, umbral efectivo y su fuente; filtro "solo en
  alerta"; ayuda con la fórmula), columnas Clase y Umbral en inventario, tipo SIN_ROTACION en la página de alertas y
  desglose (agotado, stock bajo, sin rotación) en la campana.

## Alcance implementado (Fase 6)

- **Posición de inventario.** `posición = disponible + en camino`, donde *en camino* es la suma de la cantidad aprobada de
  las líneas de planes de compra en estado APROBADO (REQ-10). `inventario` la obtiene por el puerto
  `ConsultaPedidosEnCamino`, implementado en `compra`.
- **STOCK_BAJO por posición.** Se abre cuando `0 < disponible` y `disponible + en camino ≤ umbral` (el umbral sigue siendo
  el punto de reorden, o `stockMinimo` si no hay). **AGOTADO sigue evaluándose sobre el disponible** (`disponible == 0`):
  con un pedido en camino la variante no se puede vender, así que la alerta se mantiene. La prioridad AGOTADO >
  SIN_ROTACION > STOCK_BAJO no cambia.
- **Estado de stock.** `Variante.estadoStock()` sigue siendo un hecho físico sobre el disponible; por eso una variante
  puede estar en estado BAJO sin alerta STOCK_BAJO cuando tiene un pedido suficiente en camino. La interfaz muestra «N en
  camino» junto al estado en Inventario, Reorden y Alertas.
- **Nuevo evento.** Aprobar un plan o cancelar uno APROBADO publica `PosicionCambiada(varianteId)` por línea; el
  `AlertaListener` la re-evalúa `AFTER_COMMIT` en una transacción nueva (igual que `StockCambiado`). Recibir un plan
  publica `StockCambiado` por las entradas (y `PosicionCambiada` por las líneas recibidas en 0).
- **Punto de reorden con pronóstico.** Si el producto tiene pronóstico (REQ-10), `μL` es la suma de la tasa diaria
  pronosticada de la variante en los próximos `L` días (fuente `PRONOSTICO`); `s` sale de la serie diaria observada de 90
  días y las reglas NORMAL, POISSON y los niveles de servicio por clase no cambian. Sin pronóstico se mantiene la fórmula
  de la Fase 5 (fuente `HISTORICA`). La fuente consta en la instantánea `analitica_variante`.
- **Métrica con y sin tránsito.** Cada ejecución analítica guarda la métrica de la Fase 5 (porcentaje de variantes activas
  con AGOTADO o STOCK_BAJO) y, además, la métrica **contrafactual** «en alerta si no se contara lo que viene en camino»
  (`en_alerta_sin_transito`) y el número de variantes con pedido en camino, para mostrar el efecto de los pedidos. La
  definición de la métrica no cambia para llegar al número.
- **Interfaz.** «En camino» y posición en Inventario y Reorden, filtro «Solo en alerta» de Reorden alineado con la
  posición (lo resuelve el servidor) y panel de ejecución con la métrica con y sin tránsito.

### Resultados de la prueba T2 de la Fase 6 (semilla de 36 meses con reposición por planes)

Prueba ejecutada el 2026-10-21 sobre la semilla de 36 meses (base vacía, perfiles `dev,seed`, semilla aleatoria por defecto), por la API y la base de datos. La revisión visual por la interfaz la realiza el usuario después.

| Indicador | Resultado |
|---|---|
| Variantes activas analizadas | 291 (de 299; un producto desactivado) |
| Variantes con pedido en camino / unidades en camino | 101 variantes (98 planes APROBADO, 1.060 unidades) |
| Alertas ACTIVAS | AGOTADO 26; STOCK_BAJO 16; SIN_ROTACION 18 (LIV004, LIV026 y LIV030) |
| Variantes en alerta de stock con posición (AGOTADO o STOCK_BAJO) | 42 de 291 = **14,4 %** (T2 de la Fase 5: 34,4 %; Fase 4: cerca de 47 %) |
| Variantes en alerta sin contar lo que viene en camino (contrafactual) | 113 de 291 = 38,8 % |
| Meta de ~10 % a 15 % | Cumplida (14,4 %, dentro del rango efectivo de ~11 % a 17 %). Desglose: AGOTADO 26 (8,9 %) y STOCK_BAJO 16 (5,5 %); los pedidos en camino reducen la métrica de 38,8 % a 14,4 % |
| Duración de la ejecución analítica con pronóstico (meta menor que 8 s) | Cumplida: 6.523 ms en la ejecución de cierre (origen SEMILLA, EXITOSA) y 6.458 ms en el recálculo manual como ADMIN |

### Resultados de la prueba T2 de la Fase 5 (2026-10-15, semilla de 24 meses)

| Indicador | Resultado |
|---|---|
| Variantes activas analizadas / con ROP calculado | 291 / 291 |
| ROP por método | NORMAL 26; POISSON 201; SIN_DEMANDA 64; SIN_HISTORIA 0 |
| Alertas ACTIVAS | AGOTADO 27; STOCK_BAJO 73; SIN_ROTACION 18 (LIV004: 7, LIV026: 8, LIV030: 3) |
| Variantes en alerta de stock (AGOTADO o STOCK_BAJO) | 100 de 291 = **34,4 %** (línea base de la Fase 4 con `stockMinimo`: 141 de 299, cerca de 47 %) |
| Por clase | A 41,8 %; B 43,0 %; C 23,8 % |
| Duración de la ejecución analítica | 4.951 ms (semilla); recálculo manual 4.844 ms en la primera corrida (meta < 5 s) |

Desglose por clase y método (variantes / en alerta de stock): A-NORMAL 26/10; A-POISSON 53/23; B-POISSON 76/37;
B-SIN_DEMANDA 10/0; C-POISSON 72/18; C-SIN_DEMANDA 54/12.

**Decisión D4 (ADR-009).** La meta de 10 % a 15 % de variantes en alerta no se alcanza en la Fase 5: una alerta
"disponible ≤ ROP" sigue activa durante el tiempo de reposición aunque la mercancía ya se haya pedido. La Fase 6 evalúa
STOCK_BAJO con la posición de inventario (disponible más pedidos aprobados en camino); ver arriba.

### Endpoints de alertas y reorden

| Método y ruta | Rol |
|---|---|
| `GET /api/alertas?estado&tipo&page&size` | ADMIN, INVENTARIO, VENDEDOR |
| `GET /api/alertas/resumen` | ADMIN, INVENTARIO, VENDEDOR |
| `GET /api/alertas/urgentes?limite` (fase 7, bloque del panel: AGOTADO por umbral y antigüedad, STOCK_BAJO por cobertura; 1–20, 5 por defecto) | ADMIN, INVENTARIO, VENDEDOR |
| `POST /api/alertas/{id}/atender` | ADMIN, INVENTARIO |
| `GET /api/analitica/reorden` (Fase 5) | ADMIN, INVENTARIO, CONTABILIDAD |
| `POST /api/analitica/recalcular` (Fase 5, re-evalúa todas las alertas; Fase 6, incluye el pronóstico) | ADMIN |

### Flujo de alerta por stock bajo

| Paso | Actor | Sistema |
|---|---|---|
| 1 | Un usuario registra una operación que cambia el stock (venta, ajuste, reserva, etc.) | Confirma la transacción y publica `StockCambiado` |
| 2 | | Tras el commit evalúa la variante y abre la alerta AGOTADO (disponible 0) o STOCK_BAJO (disponible + en camino ≤ umbral), y cierra la que ya no aplica |
| 3 | El encargado de inventario ve el contador en la campana y abre `/alertas` | Lista las alertas activas con SKU, stock disponible y umbral |
| 4 | Atiende la alerta (tras reponer o decidir) | Registra `atendidaPor` y `atendidaEn`; la alerta pasa a ATENDIDA |

- **Caminos alternativos**: alerta ya atendida → 409; el stock se repone antes de atenderla → se cierra sola con
  `atendidaPor` nulo; rol VENDEDOR → solo consulta (sin botón de atender, 403 en el endpoint); falla del listener → se
  registra en el log y el siguiente movimiento de la variante reevalúa.
- **Puntos de extensión**: consultar el kardex de la variante desde la alerta (REQ-02); preparar un plan de compras para
  reponer (REQ-10, Fase 6); aprobar o cancelar un plan (re-evalúa la posición sin mover stock).
- **Poscondición**: a lo sumo una alerta ACTIVA por variante y tipo, coherente con el estado de stock vigente.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | AGOTADO y STOCK_BAJO se abren según el disponible y el umbral, con prioridad del punto de reorden sobre `stockMinimo` | Cumplido | `AlertaServiceTest` |
| CA-2 | Los dos tipos son excluyentes y al volver a OK se cierran ambos | Cumplido | `AlertaServiceTest`, `ReservasAlertasIntegracionTest` |
| CA-3 | Una sola alerta ACTIVA por variante y tipo (índice único), también bajo concurrencia | Cumplido | `AlertaRepositoryTest`, `AlertaConcurrenciaIntegracionTest` |
| CA-4 | Las variantes inactivas no abren alertas y se cierran las vigentes | Cumplido | `AlertaServiceTest` |
| CA-5 | Un fallo de la evaluación no se propaga al usuario | Cumplido | `AlertaServiceTest` |
| CA-6 | Atender cierra la alerta con usuario y fecha; una ya atendida responde 409; inexistente 404 | Cumplido | `AlertaServiceTest`, `AlertaControllerTest` |
| CA-7 | Solo ADMIN e INVENTARIO atienden; VENDEDOR consulta; otros roles reciben 403 | Cumplido | `AlertaControllerTest`, `ReservasAlertasIntegracionTest` |
| CA-8 | El resumen separa activas, agotadas y de stock bajo | Cumplido | `AlertaServiceTest`, `AlertaControllerTest` |
| CA-9 | La campana muestra el contador y enlaza a `/alertas`; la página filtra y atiende según el rol | Cumplido | `Topbar.test.tsx`, `AlertasPage.test.tsx` |
| CA-10 | SIN_ROTACION por producto: se abre con 60 días sin ventas en variantes con disponible, no con productos de menos de 60 días, y una venta de cualquier variante la cierra en todas | Cumplido (Fase 5) | `SinRotacionIntegracionTest`, `SinRotacionConcurrenciaIntegracionTest`, `AlertaServiceTest` |
| CA-11 | Prioridad AGOTADO > SIN_ROTACION > STOCK_BAJO, una sola alerta ACTIVA por variante y sin reapertura en el mismo episodio | Cumplido (Fase 5) | `SinRotacionIntegracionTest`, `AlertaServiceTest` |
| CA-12 | Las ventas anuladas no cuentan como rotación; las ventas de reservas convertidas sí | Cumplido (Fase 5) | `SinRotacionIntegracionTest`, `AnaliticaFlujoIntegracionTest` |
| CA-13 | Desactivar una variante o su producto cierra sus alertas ACTIVAS | Cumplido (Fase 5) | `SinRotacionIntegracionTest`, `AlertaServiceTest` |
| CA-14 | ROP híbrido normal/Poisson por clase, con SIN_HISTORIA y SIN_DEMANDA, y nivel de servicio verificado por simulación | Cumplido (Fase 5) | `CalculadoraReordenTest`, `CalculadoraReordenMonteCarloTest`, `DistribucionPoissonTest`, `SerieDemandaTest` |
| CA-15 | La re-evaluación nocturna y el recálculo manual re-evalúan todas las variantes, guardan la métrica y un fallo en una variante no detiene al resto | Cumplido (Fase 5) | `EjecucionAnaliticaServiceIntegracionTest`, `AnaliticaNocturnaTareaTest`, `AnaliticaFlujoIntegracionTest` |
| CA-16 | La consulta de reorden entrega método, umbral efectivo y su fuente, y filtra por alerta, clase y método | Cumplido (Fase 5) | `AnaliticaConsultasIntegracionTest`, `AnaliticaControllerConsultasTest` |
| CA-17 | La interfaz muestra SIN_ROTACION, el desglose de la campana, Clase y Umbral en inventario y la pestaña Reorden | Cumplido (Fase 5) | `AlertasPage.test.tsx`, `Topbar.test.tsx`, `InventarioPage.test.tsx`, `PestanaReorden.test.tsx` |
| CA-18 | STOCK_BAJO usa la posición (disponible + en camino); AGOTADO sigue sobre el disponible; aprobar cierra STOCK_BAJO si la posición supera el umbral y cancelar un APROBADO puede reabrirlo | Cumplido (Fase 6) | `AlertaServiceTest`, `PlanCompraAlertasIntegracionTest` |
| CA-19 | Aprobar o cancelar publica `PosicionCambiada`; un fallo del listener no revierte la aprobación; las variantes inactivas con pedido en camino no generan alerta | Cumplido (Fase 6) | `PlanCompraAlertasIntegracionTest` |
| CA-20 | El ROP usa la demanda pronosticada cuando hay pronóstico y la histórica si no; la ejecución guarda la métrica contrafactual y las variantes con pedido en camino | Cumplido (Fase 6) | `CalculadoraReordenPronosticoTest`, `EjecucionAnaliticaPronosticoIntegracionTest`, `PlanCompraAlertasIntegracionTest` |
| CA-21 | Inventario, Reorden, Alertas y el panel de ejecución muestran lo que viene en camino y la métrica con y sin tránsito | Cumplido (Fase 6) | `InventarioPage.test.tsx`, `PestanaReorden.test.tsx`, `AlertasPage.test.tsx`, `AnaliticaPage.test.tsx` |
| CA-22 | Meta de ~10 % a 15 % de variantes en alerta de stock | Cumplido (T2 de la Fase 6, 2026-10-21: 42 de 291 variantes = 14,4 %; Fase 5: 34,4 %). Medido por API y base de datos; la revisión visual por la interfaz queda a cargo del usuario | `SemillaIntegracionTest`; corrida real T2 |

## Observaciones

- Stakeholder, necesidad, caso de uso y prioridad no se registran aquí: deben tomarse del Word y no se asumen.
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados; NEC-010 debería ser NEC-10.
- El umbral dinámico (ROP calculado) llega con la Fase 5; sin ROP calculado se usa `stockMinimo`. Desde la Fase 6 el STOCK_BAJO usa la posición de inventario.
- Riesgo aceptado (ADR-010): un plan aprobado y nunca recibido mantiene cerrada la alerta STOCK_BAJO hasta que se cancela;
  la interfaz de compras lo marca «atrasado» cuando pasa `aprobadoEn + max(leadTimeDias)`.
- T2 de la Fase 6 (2026-10-21, por API y base de datos; la revisión visual por la interfaz la realiza el usuario): meta cumplida con 14,4 %. Tras el ciclo de compra de prueba (PC-003674 aprobado y recibido) las alertas activas quedaron AGOTADO 24, STOCK_BAJO 0 y SIN_ROTACION 18; AGOTADO por clase: A 2, B 4, C 18; SIN_ROTACION: B 6, C 12.
- La meta de ~10 % a 15 % no estaba garantizada: AGOTADO ya pesaba cerca de 9 % (variantes que la semilla deja agotar a
  propósito y quiebres reales). Se mide y se explica con el desglose; la definición no se cambia para llegar al número.
- Riesgos residuales aceptados (ADR-009): una variante ocupada por una venta durante todos los reintentos conserva su ROP hasta la siguiente ejecución; ventana de milisegundos en la apertura de SIN_ROTACION.
- Diagramas: `diagramas/secuencia-convertir-reservas.puml` (muestra el evento `StockCambiado` que dispara la evaluación);
  `diagramas/secuencia-plan-compra.puml` (aprobar y recibir un plan: `PosicionCambiada`, `StockCambiado` y el listener de
  alertas); `diagramas/clases-fase6-compra.puml` (puerto `ConsultaPedidosEnCamino`); `diagramas/clases-fase5-analitica.puml`.
