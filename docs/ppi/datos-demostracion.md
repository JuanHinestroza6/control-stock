# Datos de demostración (Fases 4 y 5)

> Sección nueva del documento PPI. Implementada en la Fase 4 (rama `fase-4-datos-semilla`, corrida real T1 del
> 2026-10-13) y actualizada en la Fase 5 (rama `fase-5-analitica`, corrida real T2 del 2026-10-15, que reemplaza las
> cifras de T1). Decisiones en ADR-008 y ADR-009.

| Campo | Contenido |
|---|---|
| **Módulo** | `semilla` (`co.livorno.controlstock.semilla`), activo solo con el perfil Spring `seed` |
| **Requisitos relacionados** | REQ-01, REQ-02, REQ-04, REQ-05, REQ-09, REQ-10, REQ-13, REQ-14 |
| **Sección del documento Word** | Anexo o apartado de pruebas y datos de demostración |

## Propósito

El módulo genera 24 meses de historia de operación de Livorno Clothing para tres fines: alimentar la analítica
(clasificación ABC, punto de reorden y pronóstico de demanda), disponer de datos creíbles para la sustentación y
ejercitar los requisitos funcionales sobre un volumen realista. Los datos no son de referencia y no se incluyen en las
migraciones.

| Requisito | Uso de los datos |
|---|---|
| REQ-01 Visualizar stock y atributos | Inventario de 299 variantes con estados de stock variados |
| REQ-02 Registrar operaciones de stock | Kardex encadenado con entradas, ventas, ajustes, devoluciones y reservas |
| REQ-04 Alertas de nivel mínimo | Alertas AGOTADO y STOCK_BAJO activas al final de la carga |
| REQ-05 Identificar Productos de Mayor Venta | Concentración de ventas tipo Pareto |
| REQ-14 Clasificación ABC | Ingreso de 12 meses para las clases A, B y C |
| REQ-09 Tendencias históricas | Estacionalidad y crecimiento a lo largo de 24 meses |
| REQ-10 Planeación de compras | Historia de demanda y reposiciones con tiempo de entrega |
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
  semilla ejecuta la analítica completa con el reloj real (origen SEMILLA): clasificación ABC, punto de reorden y
  alertas de todas las variantes. Si esa ejecución falla, la carga queda confirmada y se recupera con "Recalcular" como
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
- **Reposición por tiempo de entrega.** La reposición se dispara cuando el disponible no cubre la demanda esperada
  durante el tiempo de entrega del producto más 7 días de seguridad. Cuando aun así falta stock, la venta se pierde.
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

## Resultados de la corrida real T2 (2026-10-15)

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
  Fase 6 debe tenerlo en cuenta.
- **Reservas activas efímeras.** Las 14 reservas ACTIVAS (13 en T1) vencen a los pocos días de la carga; conviene regenerar la
  base la víspera de la sustentación con una fecha final fija.
- **Datos de clientes ficticios.** Los nombres y contactos de las reservas no corresponden a personas reales.
- **Sin historial previo de alertas.** Solo quedan las alertas ACTIVAS correspondientes al stock final.
- **Alertas elevadas.** Con el punto de reorden de la Fase 5 el 34,4 % de las variantes queda en alerta de stock; se
  reduce en la Fase 6 con la posición de inventario.
