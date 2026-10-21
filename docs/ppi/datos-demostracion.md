# Datos de demostración (Fases 4, 5 y 6)

> Sección nueva del documento PPI. Implementada en la Fase 4 (rama `fase-4-datos-semilla`, corrida real T1 del
> 2026-10-13), actualizada en la Fase 5 (rama `fase-5-analitica`, corrida real T2 del 2026-10-15, que reemplaza las
> cifras de T1) y en la Fase 6 (rama `fase-6-prediccion-compras`: 36 meses de historia, reposición mediante planes de
> compra reales, pedidos en camino al cerrar y pronóstico). Las cifras de la corrida T2 de la Fase 6 (2026-10-21) se midieron por API y base de datos; la revisión visual por la interfaz la realiza el usuario después. Decisiones en ADR-008, ADR-009
> y ADR-010.

| Campo | Contenido |
|---|---|
| **Módulo** | `semilla` (`co.livorno.controlstock.semilla`), activo solo con el perfil Spring `seed` |
| **Requisitos relacionados** | REQ-01, REQ-02, REQ-04, REQ-05, REQ-09, REQ-10, REQ-13, REQ-14 |
| **Sección del documento Word** | Anexo o apartado de pruebas y datos de demostración |

## Propósito

El módulo genera 36 meses de historia de operación de Livorno Clothing (24 hasta la Fase 5; el cambio a 36 se hizo para que
Holt-Winters tenga datos suficientes, ya que la serie mensual completa de la semilla pasa de 23 a 35 meses) para tres fines: alimentar la analítica
(clasificación ABC, punto de reorden y pronóstico de demanda), disponer de datos creíbles para la sustentación y
ejercitar los requisitos funcionales sobre un volumen realista. Los datos no son de referencia y no se incluyen en las
migraciones.

| Requisito | Uso de los datos |
|---|---|
| REQ-01 Visualizar stock y atributos | Inventario de 299 variantes con estados de stock variados |
| REQ-02 Registrar operaciones de stock | Kardex encadenado con entradas, ventas, ajustes, devoluciones y reservas |
| REQ-04 Alertas de nivel mínimo | Alertas AGOTADO, STOCK_BAJO y SIN_ROTACION activas al final de la carga; STOCK_BAJO por posición de inventario con pedidos en camino |
| REQ-05 Identificar Productos de Mayor Venta | Concentración de ventas tipo Pareto |
| REQ-14 Clasificación ABC | Ingreso de 12 meses para las clases A, B y C |
| REQ-09 Tendencias históricas | Estacionalidad y crecimiento a lo largo de 36 meses, con comparación interanual |
| REQ-10 Planeación de compras | Pronóstico SES y Holt-Winters sobre 35 meses completos, reposiciones mediante planes de compra reales (creados, aprobados y recibidos) y pedidos aprobados que quedan en camino al cierre |
| REQ-13 Gestionar apartados | Reservas en los cuatro estados |

## Procedimiento de generación

- **Solo servicios reales.** Catálogo, entradas, ajustes, ventas, devoluciones, anulaciones y reservas se registran por
  los mismos servicios que usa la API. El stock cambia únicamente mediante `MovimientoService`; no hay inserciones
  directas de stock ni de kardex. El usuario que actúa se fija por operación según su rol (el vendedor vende, devuelve
  y convierte; el encargado de inventario ingresa, ajusta y reserva; el administrador anula).
- **Reloj simulado.** Un `Clock` ajustable, activo solo en `seed`, reemplaza al del sistema para que las operaciones
  queden fechadas en el pasado. Solo avanza, de modo que el kardex resulta cronológico, y retorna al reloj del sistema
  al terminar.
- **Una sola transacción.** Toda la carga es atómica: un error de negocio revierte todo y la base queda como estaba.
  Al final se verifica la consistencia (stock igual a la suma del kardex por variante, reservado igual a la suma de
  reservas ACTIVAS, ningún valor negativo); si falla, se revierte.
- **Alertas y analítica al final.** El listener de alertas por evento se apaga durante la carga. Tras confirmarla, la
  semilla ejecuta la analítica completa con el reloj real (origen SEMILLA): pronóstico de demanda (SES o Holt-Winters
  por producto, con backtesting), clasificación ABC, punto de reorden con pronóstico y alertas de todas las variantes
  (STOCK_BAJO por posición, con los pedidos en camino). Si esa ejecución falla, la carga queda confirmada y se recupera con "Recalcular" como
  ADMIN.
- **Protecciones.** Si ya existen ventas, el generador registra que hay datos y no hace nada; si hay productos o
  movimientos pero no ventas, aborta con un mensaje claro; no se ejecuta con el perfil `prod`. La aplicación termina al
  finalizar la carga (código 0 si fue exitosa, 1 si falló).
- **Determinismo.** Semilla aleatoria fija (42 por defecto) y fecha final configurable (`app.semilla.fecha-fin`, vacía
  equivale a ayer). Con la misma semilla, la misma fecha final y una base vacía, el resultado es el mismo.
- **Usuarios de demostración.** Cuatro usuarios, uno por rol: `admin@livorno.co`, `inventario@livorno.co`,
  `ventas@livorno.co` y `contabilidad@livorno.co`. Su contraseña se toma de la variable de entorno `SEED_PASSWORD`
  (obligatoria y sujeta a la política de contraseñas); no se almacena en el repositorio ni se documenta. No quedan con
  cambio de contraseña obligatorio.

## Modelo de demanda

- **Estacionalidad colombiana.** Factor mensual de enero a diciembre: 0,8; 0,7; 0,8; 0,85; 1,0; 1,25; 1,0; 0,9; 0,95;
  1,0; 1,3; 1,6 (picos por la prima de mitad de año, el Día sin IVA y la temporada navideña). Se combina con un
  crecimiento mensual de la empresa de 1,5 %.
- **Distribución de Pareto por grupos.** Los 30 productos se ordenan por rango y se agrupan en cabeza (20 %: 6 productos),
  medio y cola. Los 6 productos de cabeza concentran aproximadamente el 78 % de las unidades, con un perfil decreciente
  dentro del grupo. En la simulación, el 20 % superior alcanzó el 77,6 % de las unidades y el producto mayor el 15,7 %.
- **Reposición por tiempo de entrega.** La reposición se dispara cuando la posición (disponible más lo que ya viene en
  camino) no cubre la demanda esperada durante el tiempo de entrega del producto más 7 días de seguridad. Cuando aun así
  falta stock, la venta se pierde.
- **Reposición mediante planes de compra reales (Fase 6, ADR-010).** Desde la Fase 6 la reposición ya no es una entrada
  directa: el encargado de inventario crea un plan de compras, el administrador lo aprueba y el encargado de inventario
  lo recibe cuando llega (fecha de la simulación más el tiempo de entrega del producto), con los mismos servicios que usa
  la API; la recepción genera las entradas por `MovimientoService` con referencia `PLAN_COMPRA/{id}`. La semilla tiene su
  propia política de pedido (no ejecuta la sugerencia del sistema, que exigiría correr la analítica dentro de la
  simulación). Un pedido cuya llegada cae después del último día simulado queda **APROBADO (en camino)** al terminar, de
  modo que la posición de inventario del cierre sea coherente; el verificador comprueba que lo que está en camino coincide
  con lo que simuló el generador.
- **Casos para la analítica.** Un producto descontinuado (LIV017, jean skinny, queda inactivo) y tres productos
  estancados (LIV004, LIV026 y LIV030) que no venden en los últimos 90 días de la historia, para ejercitar la alerta
  SIN_ROTACION de la Fase 5. En la Fase 5 se ajustó la semilla: en la primera corrida de T2 los estancados seguían
  vendiendo por reservas convertidas en venta y no aparecía ninguna SIN_ROTACION; ahora tampoco reservan ni convierten
  en esos 90 días.

## Ejecución y regeneración

1. Agregar `SEED_PASSWORD` al archivo `.env` (12 a 72 caracteres, sin empezar por "cambiar").
2. Partir de una base vacía: `docker compose down -v` y `docker compose up -d mysql` (esperar el estado `healthy`).
3. Cargar desde `backend/`: `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,seed"` (Windows) o
   `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,seed` (Linux, Mac, Git Bash). La carga no levanta servidor web,
   imprime el resumen y termina sola.
4. Arrancar con normalidad usando el perfil `dev`.
5. Para regenerar, se borra la base (`docker compose down -v`); el código no borra datos porque el kardex es inmutable.

## Resultados de la corrida real T2 de la Fase 6 (2026-10-21, semilla de 36 meses)

Corrida con base vacía y `SEED_PASSWORD` en `.env`, perfiles `dev,seed`, semilla aleatoria por defecto y rango del 2023-10-21 al 2026-10-20, ejecutada por API y base de datos; la revisión visual por la interfaz la realiza el usuario después. Estas cifras reemplazan a las de la corrida de la Fase 5 (que se conserva abajo como referencia). La carga subió de cerca de 2 minutos a 4 min 26 s, algo por encima de la previsión de 3,5 a 4 minutos de ADR-010.

| Indicador | Resultado T2 (Fase 6) | Referencia: T2 de la Fase 5 (24 meses) |
|---|---|---|
| Meses de historia / meses completos | 36 / 35 (por diseño) | 24 / 23 |
| Tiempo de carga con perfiles `dev,seed` | 4 min 26 s en total (generación 243,6 s; analítica de cierre 6,5 s) | 129,99 s |
| Ventas registradas (de ellas, anuladas) | 20.281 (139 anuladas) | 12.227 (91 anuladas) |
| Unidades en ventas registradas | 30.920 | 18.810 |
| Ingreso (ventas registradas) | $3.749.748.100 | $2.224.875.800 |
| Devoluciones | 1.093 (216 no aptas) | 607 |
| Reservas (activas) | 85 (49 convertidas, 13 canceladas, 13 vencidas, 10 activas) | 96 (14 activas) |
| Productos / variantes (activas) | 30 / 299 (291 activas; 1 producto desactivado) | 30 / 299 (291 activas) |
| Planes de compra: creados / recibidos / en camino | 3.673 / 3.575 / 98 (ningún BORRADOR ni CANCELADO al cierre) | no existían |
| Unidades en camino al cierre | 1.060 | no existían |
| Segunda ejecución | Detectó ventas y no hizo nada | Detectó datos y no creó nada |

### Pronóstico y analítica sobre los datos de demostración (Fase 6)

Ejecución analítica de origen SEMILLA: EXITOSA en 6.523 ms. Recálculo manual como ADMIN: EXITOSA, 6.458 ms en el servidor (6,5 s en el cliente), con las mismas métricas (meta menor que 8 s cumplida).

| Indicador | Resultado |
|---|---|
| Productos pronosticados | 29 de 30 (el desactivado no se pronostica) |
| Productos con Holt-Winters / con SES (y motivos sin Holt-Winters) | Holt-Winters 7 (LIV001, LIV002, LIV009, LIV014, LIV018, LIV020, LIV027); SES 22: 9 por `MES_EN_CERO` y 13 que compitieron con Holt-Winters y ganó SES |
| WAPE global del pronóstico | 0,1828. WAPE medio: Holt-Winters 0,1706 (rango 0,0926 a 0,2987); SES 0,4201 (rango 0,1539 a 0,9865, productos de cola y estancados). LIV001: SES 0,1886 frente a Holt-Winters 0,0926 |
| Clase ABC (A / B / C) | 78 / 82 / 131 |
| Método del punto de reorden (NORMAL / POISSON / SIN_DEMANDA / SIN_HISTORIA) y fuente (PRONOSTICO / HISTORICA) | NORMAL 42; POISSON 222; SIN_DEMANDA 27; SIN_HISTORIA 0. Fuente PRONOSTICO en las 291 variantes |
| Variantes con pedido en camino | 101 |
| Alertas activas (AGOTADO / STOCK_BAJO / SIN_ROTACION) | 26 / 16 / 18 (LIV004, LIV026 y LIV030) |
| Variantes en alerta de stock, con posición | 42 de 291 = **14,4 %** (meta de ~10 % a 15 % cumplida; T2 de la Fase 5: 34,4 %; Fase 4: cerca de 47 %) |
| Variantes en alerta de stock si no se contara lo que viene en camino (contrafactual) | 113 de 291 = 38,8 % |

Verificación de lo esperado: los productos de cabeza compitieron con Holt-Winters (ganó en 7 productos) y los de cola, con meses en 0, usaron SES; las alertas bajaron de 38,8 % (contrafactual) a 14,4 % por los pedidos en camino; AGOTADO pesó 26 de 291 variantes (8,9 %), de modo que la meta efectiva de ~11 % a 17 % se cumple. Al final, AGOTADO por clase: A 2, B 4, C 18; SIN_ROTACION: B 6, C 12 (cifras tomadas tras el ciclo de compra de prueba, con AGOTADO 24).

**Observación sobre la corrección por agotados.** En los últimos 12 meses las unidades observadas (11.601) pasaron a 11.890,13 corregidas (+289 unidades), mientras que la semilla informa 2.817 unidades perdidas en 36 meses (cerca de 939 por año). La corrección recupera cerca de 0,3 veces lo perdido, por debajo del rango de 0,5 a 2 veces previsto en el plan. Causas probables: el piso de disponibilidad de 0,5, la inclusión solo de variantes activas, la ponderación por participación y que la demanda perdida por quiebres cortos dentro del día no se ve en los días con stock.

**Otros datos de la corrida.** Usuarios de demostración 4; unidades iniciales 1.597; ventas perdidas 2.463 líneas (2,5 % evitables), 2.817 unidades; entradas 3.575; ajustes 72; alzas de precio 3. Flujo por API: matriz de roles correcta (compras, tendencias y pronóstico: ADMIN, INVENTARIO y CONTABILIDAD 200, VENDEDOR 403, sin token 401; sugerir y recalcular restringidos según la matriz; INVENTARIO 403 al aprobar); tendencias de 24 meses con 25 puntos; el plan sugerido PC-003674 (20 líneas) se aprobó y se recibió con 20 ENTRADAS `PLAN_COMPRA/3674` (152 unidades), y tras ese ciclo las alertas activas quedaron AGOTADO 24, STOCK_BAJO 0 y SIN_ROTACION 18.

## Resultados de la corrida real T2 de la Fase 5 (2026-10-15, semilla de 24 meses)

Reemplaza a la corrida T1 de la Fase 4 (2026-10-13), cuyos resultados se conservan abajo solo como referencia. La
semilla se ajustó para los productos estancados, por lo que las cifras cambian respecto de T1 (misma semilla 42; fecha
final ayer, 2026-10-15, distinta de la de T1).

| Indicador | Resultado T2 | Resultado T1 (Fase 4) |
|---|---|---|
| Tiempo de carga con perfiles `dev,seed` | 129,99 s (2 min 10 s), BUILD SUCCESS | 107,4 s |
| Ventas registradas (de ellas, anuladas) | 12.227 (91 anuladas) | 12.148 (83) |
| Unidades en ventas registradas | 18.810 | 18.592 |
| Ingreso (24 meses, ventas registradas) | $2.224.875.800 | $2.196.015.600,00 |
| Devoluciones | 607 | 666 |
| Reservas | 96 (14 activas) | 91 (13 activas) |
| Productos / variantes | 30 / 299 (291 activas) | 30 / 299 |
| Alertas activas de stock | AGOTADO 27; STOCK_BAJO 73 (100 de 291, 34,4 %) | AGOTADO 30; STOCK_BAJO 111 (141 de 299, cerca de 47 %) |
| Segunda ejecución | Detectó datos y no creó nada (tampoco otra ejecución analítica) | Detectó datos y no creó nada |

Las cifras de T1 que cambian son ventas, unidades, ingreso, devoluciones, reservas y alertas; la estructura del catálogo
(30 productos, 299 variantes, 8 inactivas por el descontinuado LIV017 y otras) no cambia. Los datos de ventas perdidas,
entradas, ajustes y alzas de precio de T1 no se volvieron a medir en T2. Revisión manual por los cuatro roles: correcta
(matriz de Analítica, columnas Clase y Umbral en inventario, SIN_ROTACION en Alertas y campana con desglose).

### Analítica sobre los datos de demostración

Ejecución analítica de origen SEMILLA: EXITOSA en 4.951 ms; 291 variantes activas analizadas, todas con ROP calculado.

| Clase ABC (ingreso neto de 12 meses: $1.197.540.900) | Variantes | Ingreso neto | Participación |
|---|---|---|---|
| A | 79 | $956.487.400 | 79,9 % |
| B | 86 | $181.121.400 | 15,1 % |
| C | 126 | $59.932.200 | 5,0 % |

| Método del punto de reorden | Variantes |
|---|---|
| NORMAL | 26 |
| POISSON | 201 |
| SIN_DEMANDA | 64 |
| SIN_HISTORIA | 0 |

| Alerta activa | Variantes | Observación |
|---|---|---|
| AGOTADO | 27 | |
| STOCK_BAJO | 73 | |
| SIN_ROTACION | 18 | LIV004 (7), LIV026 (8), LIV030 (3); última venta de esos productos entre el 7 y el 10 de julio de 2026 |

Métrica de alertas de stock: 100 de 291 variantes activas (34,4 %), frente a 141 de 299 (cerca de 47 %) de la línea base
de la Fase 4 con `stockMinimo`. Por clase: A 41,8 %; B 43,0 %; C 23,8 %. Desglose (variantes / en alerta):

| Clase y método | Variantes | En alerta |
|---|---|---|
| A-NORMAL | 26 | 10 |
| A-POISSON | 53 | 23 |
| B-POISSON | 76 | 37 |
| B-SIN_DEMANDA | 10 | 0 |
| C-POISSON | 72 | 18 |
| C-SIN_DEMANDA | 54 | 12 |

Recálculo manual como ADMIN: 4.844 ms (meta menor que 5 s), medido en la primera corrida de T2 con la misma configuración,
antes del ajuste de la semilla de estancados.

**La meta de 10 % a 15 % no se alcanza en la Fase 5** (decisión D4, ADR-009): una alerta "disponible menor o igual que el
ROP" sigue activa durante el tiempo de reposición aunque ya se haya pedido. En la Fase 6 la alerta usará la posición de
inventario (disponible más pedidos aprobados en camino).

### Corrida T1 de la Fase 4 (2026-10-13), solo referencia

| Indicador | Resultado |
|---|---|
| Tiempo de carga | 107,4 s (total con Maven: 1 min 54 s) |
| Usuarios de demostración | 4 |
| Productos / variantes | 30 / 299 |
| Unidades iniciales | 1.521 |
| Ventas registradas (de ellas, anuladas) | 12.148 (83) |
| Unidades vendidas | 18.592 |
| Ingreso | $2.196.015.600,00 |
| Ventas perdidas | 1.510 líneas (3,0 % evitables), 1.743 unidades |
| Devoluciones (no aptas) | 666 (132) |
| Entradas | 2.321 |
| Ajustes | 51 |
| Alzas de precio | 2 |
| Productos desactivados | 1 |
| Reservas | 91 (convertidas 51, canceladas 10, vencidas 17, activas 13) |
| Alertas activas | AGOTADO 30; STOCK_BAJO 111 (141 de 299 variantes) |

## Limitaciones

- **Demanda censurada.** Las ventas perdidas por falta de stock no quedan registradas como demanda; el pronóstico de la
  Fase 6 la corrige con la disponibilidad reconstruida desde el kardex (`observada / max(disponibilidad, 0,5)`), con el
  riesgo de sobreestimar si el quiebre se debió a falta de demanda.
- **Política de pedido propia.** La reposición de la semilla no sigue la sugerencia (s, S) del sistema, por lo que los
  planes de la demostración no reflejan lo que el sistema propondría hoy.
- **El backtest no cubre noviembre y diciembre.** Con los seis meses evaluados de abril a septiembre, el WAPE puede
  subestimar el error de los picos (riesgo aceptado, ADR-010).
- **Reservas activas efímeras.** Las 10 reservas ACTIVAS de la T2 de la Fase 6 (13 en T1) vencen a los pocos días de la carga; conviene regenerar la
  base la víspera de la sustentación con una fecha final fija.
- **Datos de clientes ficticios.** Los nombres y contactos de las reservas no corresponden a personas reales.
- **Sin historial previo de alertas.** Solo quedan las alertas ACTIVAS correspondientes al stock final.
- **Alertas elevadas.** Con el punto de reorden de la Fase 5 el 34,4 % de las variantes quedaba en alerta de stock; la
  Fase 6 lo reduce con la posición de inventario (T2 de la Fase 6: 14,4 %).
- **Pedidos en camino efímeros.** Los planes APROBADO que quedan en camino al cierre de la carga dependen de la fecha
  final; conviene regenerar la base la víspera de la sustentación.
