# REQ-07 — Elaborar Reportes Contables Automáticos (FO-ERS)

> Estado a la fecha: implementado en la Fase 7 (backend y frontend), rama `fase-7-cierre-entrega`. Decisiones en
> ADR-011 (D1 a D3, R1 a R10). Los datos de stakeholder, necesidad, caso de uso y prioridad, y el texto de CU-07,
> provienen del documento Word (anexo del plan de la fase 7); las notas de alcance van como observación, sin cambiar el
> texto del Word.

| Campo | Contenido |
|---|---|
| **Código** | REQ-07 |
| **Nombre** | Elaborar Reportes Contables Automáticos |
| **Tipo** | Funcional |
| **Stakeholder** | Contabilidad |
| **Necesidad** | NEC-07 |
| **Caso de uso** | CU-07 «Generar reportes contables automáticos» (movimientos del periodo: también CU-03 «Registrar entradas y salidas») |
| **Relacionados** | REQ-03 (rentabilidad: concilia con el libro); REQ-02 (kardex: fuente de valorización y movimientos); REQ-08 (devoluciones y merma no apta); REQ-10 (lo en camino de los planes aprobados); RNF-03 (rendimiento); REQ-06 (roles) |
| **Prioridad** | Media |
| **Módulos** | `reporte` (`LibroVentasService`, `ValorizacionService`, `MovimientosPeriodoService`, `ConsultasReporte`, `ReporteController`); `comun.exportacion`; frontend: `paginas/reportes` (pestañas «Libro de ventas», «Valorización» y «Movimientos»), `api/reportes`, `lib/descarga` |

## Descripción (Word)

El sistema debe generar automáticamente reportes contables compatibles con auditoría y normas fiscales vigentes.

## Alcance implementado (Fase 7)

Tres reportes de solo lectura, cada uno con su hermano `…/exportar?formato=PDF|XLSX` (mismos filtros, mismo servicio):

- **Libro de ventas** (`GET /api/reportes/libro-ventas`, agrupación DIA o MES). Criterio contable: las ventas cuentan en
  la fecha de la venta y las devoluciones, con el reverso de su costo, en la fecha de la devolución; así un periodo
  cerrado no cambia por una devolución posterior. Por periodo: número de ventas, unidades vendidas y devueltas, ventas a
  lista, descuentos, facturado, devoluciones, ingreso neto, costo de ventas, margen bruto y merma no apta. Sección de
  **ventas anuladas** (número, fecha, total, anulada en y por, motivo), fuera de las cifras. El **detalle** de líneas y
  de devoluciones solo va en el Excel (máximo 50.000 líneas; si no, 422 «Acote el rango»). Sobre un rango que cubra la
  venta y su devolución, **concilia** con la rentabilidad (REQ-03).
- **Valorización del inventario** (`GET /api/reportes/valorizacion`, agrupación CATEGORIA, PRODUCTO o VARIANTE,
  `categoriaId`, `incluirInactivas`, por defecto sí): al momento de la consulta, físico × precio de compra actual
  (incluye lo reservado), valor a precio de venta y margen potencial; lo **en camino** (planes APROBADO) es informativo y
  no suma.
- **Movimientos del periodo** (`GET /api/reportes/movimientos`, agrupación CATEGORIA, PRODUCTO o VARIANTE,
  `categoriaId`): estado del físico desde el kardex: **saldo inicial + entradas − ventas + anulaciones + devoluciones +
  ajustes positivos − ajustes negativos = saldo final**, con el valor a costo de las entradas. Reservas y liberaciones
  no cambian el físico y no entran; la devolución no apta no es movimiento. El detalle del kardex (fecha, SKU, tipo,
  cantidad, saldos, costo, referencia y usuario; sin la observación) solo va en el Excel, máximo 50.000 filas.
- **Rangos** (libro y movimientos): por defecto del día 1 del mes en curso hasta hoy; máximo 731 días; `desde > hasta`
  o fecha futura → 400. Sin datos: 200 con «No hay ventas en el periodo» o «No hay movimientos en el periodo».
- **Archivos**: igual que REQ-03 (Excel con formatos, fila fija, autofiltro y hoja «Notas»; PDF carta horizontal con
  filtros, autor, fecha, totales y paginación). Ningún reporte incluye datos de clientes (Ley 1581).

### Endpoints y roles

| Método y ruta | Rol |
|---|---|
| `GET /api/reportes/libro-ventas` y `/exportar` | ADMIN, CONTABILIDAD (INVENTARIO y VENDEDOR: 403) |
| `GET /api/reportes/valorizacion` y `/exportar` | ADMIN, CONTABILIDAD |
| `GET /api/reportes/movimientos` y `/exportar` | ADMIN, CONTABILIDAD |

### Flujo CU-07 (Word → sistema)

| Paso | Actor (Contabilidad o Administrador) | Sistema |
|---|---|---|
| 1–2 | Selecciona «Reportes» y la pestaña contable (Libro de ventas, Valorización o Movimientos) | Compila los registros del periodo por omisión (mes en curso) |
| 3–4 | Indica el periodo, la agrupación y la categoría | Calcula totales y muestra el resumen con sus notas |
| 5–6 | Pulsa «Exportar PDF» o «Exportar Excel» | Genera el archivo (el Excel agrega las hojas de detalle) |

- **Alternativa 1 (inconsistencias)**: no aplica como bloqueo: la integridad la garantizan el kardex inmutable, los
  saldos verificados y el cuadre del reporte de movimientos (ver observaciones).
- **Alternativa 2 (no se puede generar el archivo)**: la interfaz muestra el error y ofrece el otro formato; un detalle
  demasiado grande responde 422 «Acote el rango».
- **Extensión «Validar datos fiscales antes de exportar»**: fuera de alcance.
- **Poscondición**: los reportes quedan disponibles para consulta, auditoría y exportación; se generan bajo demanda.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Libro: ejemplo a mano por mes (venta 135.000 de neto el mes de la venta; −45.000 el mes de la devolución) y conciliación con la rentabilidad sobre el rango completo | Cumplido | `LibroVentasServiceIntegracionTest`, `ReportesFlujoIntegracionTest` |
| CA-2 | Libro: anuladas aparte y fuera de las cifras; DIA y MES suman lo mismo; periodos sin movimiento se omiten | Cumplido | `LibroVentasServiceIntegracionTest` |
| CA-3 | Libro: el Excel trae las hojas de detalle y devoluciones y el PDF no; más de 50.000 líneas → 422 solo en Excel | Cumplido | `LibroVentasServiceIntegracionTest`, `ReporteControllerTest` |
| CA-4 | Valorización: incluye lo reservado, tres agrupaciones con el mismo total, inactivas opcionales, en camino informativo | Cumplido | `ValorizacionServiceIntegracionTest` |
| CA-5 | Movimientos: el saldo final cuadra con los flujos y con el físico; descuenta lo posterior al rango; sin reservas ni devoluciones no aptas; tres agrupaciones con el mismo total | Cumplido en unitarias y verificado en vivo sobre la base local; la prueba de integración la corre la CI del PR | `MovimientosPeriodoServiceIntegracionTest`, `MovimientosPeriodoServiceTest` |
| CA-6 | Movimientos: el Excel trae el detalle del kardex con tipo y referencia legibles; más de 50.000 → 422 solo en Excel | Cumplido | `MovimientosPeriodoServiceTest`, `ReporteControllerTest` |
| CA-7 | Sin datos: 200 con mensaje, en pantalla y en los archivos | Cumplido | servicios anteriores, `PestanaLibroVentas.test.tsx`, `PestanaValorizacion.test.tsx`, `PestanaMovimientos.test.tsx` |
| CA-8 | Solo ADMIN y CONTABILIDAD; ningún archivo ni respuesta trae datos del cliente | Cumplido | `ReporteControllerTest`, `ReportesFlujoIntegracionTest` |
| CA-9 | Rendimiento < 3 s con ~12.000 ventas y ~40.000 movimientos | Cumplido (T1) en libro y valorización; movimientos: en la prueba, lo mide la CI (en vivo: 24 meses en 133 ms) | `RendimientoReportesTest` |

## Observaciones (alcance; el texto del Word no se cambia)

- **Gastos y normas fiscales** (CU-07): el sistema no registra gastos ni hace validación fiscal; los reportes cubren
  ventas, devoluciones y mermas, compras (entradas con costo), valorización y movimientos de inventario.
- **«Inconsistencias»** (alternativa 1): la integridad la garantizan el kardex inmutable (sin UPDATE ni DELETE), el
  verificador de la semilla y el cuadre del reporte de movimientos (saldo inicial + flujos = saldo final). Verificado en
  la base local: el saldo al 1 de septiembre (1.321) y el final (1.496) coinciden con el `fisico_despues` del último
  movimiento de cada variante.
- **«Almacenado»** (poscondición): los reportes se generan bajo demanda y no se guardan.
- Los reportes son consultas del estado actual, no cierres contables: llevan la marca de tiempo de generación. La
  valorización usa el costo actual (ADR-006).
