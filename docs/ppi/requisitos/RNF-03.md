# RNF-03 — Rendimiento de Consultas (FO-ERS)

> Estado a la fecha: verificado en la Fase 7 (ADR-011, R8 y R9). En el Word los no funcionales aparecen numerados
> REQ-01..REQ-05; aquí se nombran RNF-01..RNF-05 (convención de `docs/dominio.md`).

| Campo | Contenido |
|---|---|
| **Código** | RNF-03 |
| **Nombre** | Rendimiento de consultas |
| **Tipo** | No funcional |
| **Stakeholder** | Contabilidad |
| **Necesidades** | NEC-01, NEC-03 |
| **Relacionados** | REQ-01 (inventario y kardex); REQ-03 y REQ-07 (reportes); REQ-05 y REQ-09 (más vendidos y tendencias); REQ-10 (planes) |
| **Prioridad** | Media |
| **Módulos** | `reporte` (consultas SQL agregadas de solo lectura), `analitica`, `inventario`, `venta`; índices de las migraciones V8 (movimiento), V9 (venta) y V10 (devolución) |

## Descripción (Word)

El sistema debe responder a consultas de inventario y generación de reportes en menos de 3 segundos para conjuntos de
hasta 10.000 registros.

## Cómo se cumple

- Reportes y panel con **SQL agregado de solo lectura** (`NamedParameterJdbcTemplate`, `@Transactional(readOnly)`): una
  consulta por sección, sin cargar entidades ni N+1.
- Rangos con límites semiabiertos `[desde 00:00, hasta + 1 día 00:00)` sobre columnas indexadas (`venta.fecha`,
  `devolucion.fecha`, `movimiento.fecha`, `movimiento (variante_id, fecha, id)`), máximo 731 días.
- Listados paginados (inventario, ventas, kardex, alertas); el detalle grande solo va en Excel con SXSSF (escritura por
  flujo) y un tope de 50.000 filas.
- Generación síncrona: no hizo falta procesamiento en segundo plano ni índices nuevos (la V17 prevista no se creó
  porque el `EXPLAIN` no la pidió).

## Verificación

### Prueba automática `RendimientoReportesTest` (T1 de la fase 7)

MySQL real en contenedor con ~12.000 ventas, sus líneas y devoluciones, y ~40.000 movimientos en 24 meses, insertados
por JDBC solo para la prueba; consultas por HTTP con el JWT de cada rol; se mide el **peor de 5 corridas** tras una de
calentamiento y se exige < 3 s. Además, `EXPLAIN` de cada consulta en un rango de 30 días: ninguna recorre por completo
`venta`, `detalle_venta` ni `movimiento`.

| Resultado (T1, fase 7) | Valor |
|---|---|
| Consultas medidas | rentabilidad (3 agrupaciones, 12 y 24 meses), libro DIA y MES, valorización, exportaciones PDF y XLSX, panel por rol, tendencias, más vendidos, kardex, inventario y ventas. El reporte de movimientos (B9, posterior a la T1) ya está en la prueba y lo mide la CI del PR |
| Peor tiempo | < 0,5 s en todas (la más lenta: libro de ventas a Excel, 24 meses, 430 ms) |
| `EXPLAIN` en rango acotado | sin `type = ALL` sobre las tablas grandes; sin índices nuevos |

### Medición sobre la base de demostración (portátil, 2026-10-24)

Base local con la semilla (20.242 movimientos), backend con perfil `dev,local`, tiempo de la petición HTTP:

| Consulta | Tiempo |
|---|---|
| Movimientos del periodo, 24 meses, por variante | 133 ms |
| Movimientos de septiembre, por categoría | 95 ms |
| Rentabilidad 9 meses, por variante | 113 ms |
| Valorización por variante | 58 ms |
| Exportar movimientos 24 meses a Excel (detalle completo, 1 MB) | 1,3 s |
| Exportar movimientos 24 meses a PDF, por variante | 0,3 s |

La medición con la semilla de 36 meses (paso de `Measure-Command` de la guía T2) completa esta tabla.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Reportes, exportaciones y consultas de inventario responden en < 3 s con ~12.000 ventas | Cumplido | `RendimientoReportesTest` |
| CA-2 | En un rango acotado ninguna consulta recorre por completo venta, detalle_venta ni movimiento | Cumplido | `RendimientoReportesTest` |
| CA-3 | Los listados son paginados y el detalle grande tiene tope (422 «Acote el rango») | Cumplido | `ReporteControllerTest`, `LibroVentasServiceIntegracionTest`, `MovimientosPeriodoServiceTest` |
| CA-4 | Medición real sobre la semilla de 36 meses | Pendiente (guía T2 de la fase 7) | `Measure-Command` |

## Observaciones

- El volumen de la prueba (12.000 ventas, 40.000 movimientos) supera los 10.000 registros del requisito.
- El `EXPLAIN` de rangos de 24 meses es informativo: con rangos que cubren casi toda la tabla, MySQL puede preferir un
  recorrido completo, que sigue por debajo de 3 s con estos volúmenes.
