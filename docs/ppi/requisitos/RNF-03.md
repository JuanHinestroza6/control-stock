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
| Consultas medidas | rentabilidad (3 agrupaciones, 12 y 24 meses), libro DIA y MES, valorización, exportaciones PDF y XLSX, panel por rol, tendencias, más vendidos, kardex, inventario y ventas |
| Peor tiempo | < 0,5 s en todas (la más lenta: libro de ventas a Excel, 24 meses, 430 ms) |
| `EXPLAIN` en rango acotado | sin `type = ALL` sobre las tablas grandes; sin índices nuevos |

**CI del PR #11 (2026-10-25)**, con el reporte de movimientos ya incluido: 12.150 ventas, 18.094 líneas, 600
devoluciones y 43.300 movimientos; 34 consultas, todas < 0,8 s (peor de 5 corridas). Las cuatro consultas del reporte de
movimientos usan rango sobre `idx_movimiento_fecha` en el `EXPLAIN` de 30 días.

| Consulta (CI, peor de 5) | ms |
|---|---|
| Rentabilidad 24 meses, por producto | 250 |
| Libro DIA 12 meses / MES 24 meses | 115 / 147 |
| Valorización por variante | 14 |
| **Movimientos 12 meses por producto / 24 meses por variante** | **142 / 121** |
| Exportar rentabilidad 24 meses por variante, PDF / XLSX | 425 / 412 |
| Exportar libro DIA 24 meses, PDF / XLSX (la más lenta) | 313 / **787** |
| **Exportar movimientos 12 meses por variante, PDF / XLSX** | **182 / 725** |
| Exportar tendencias, PDF / XLSX | 248 / 155 |
| Panel (4 roles) | 7 a 21 |
| Tendencias 24 meses / más vendidos 24 meses | 189 / 199 |
| Kardex, inventario y ventas (listados) | 16 a 36 |

### Medición sobre la semilla de 36 meses (T2 de la fase 7, portátil, 2026-10-25)

Base de demostración recién cargada (20.337 ventas, 33.011 movimientos), backend `dev,local` en el portátil, peticiones
con curl como ADMIN o CONTABILIDAD. Consultas JSON: peor de 3 tras una de calentamiento; exportaciones: una corrida.

| Consulta | Tiempo |
|---|---|
| Panel (ADMIN) | 40 ms |
| Rentabilidad 24 meses, por variante | 211 ms |
| Libro de ventas 24 meses, por día | 125 ms |
| Valorización por variante | 13 ms |
| Movimientos 24 meses, por variante | 88 ms |
| Tendencias (24 meses) | 223 ms |
| **Exportar libro de ventas 24 meses por día, PDF / Excel (1,0 MB, con detalle)** | 232 ms / **852 ms** |
| **Exportar movimientos 24 meses por variante, PDF / Excel (1,2 MB, 23.299 movimientos de detalle)** | 138 ms / **844 ms** |
| Exportar rentabilidad, libro 12 meses, valorización y movimientos del mes, PDF y Excel | 21 a 633 ms |
| Exportar tendencias, PDF / Excel | 506 ms / 233 ms |
| Recalcular la analítica (ADMIN) | 3,39 s (EXITOSA; 3.381 ms en el servidor; no es una consulta: meta propia < 8 s) |

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Reportes, exportaciones y consultas de inventario responden en < 3 s con ~12.000 ventas | Cumplido | `RendimientoReportesTest` |
| CA-2 | En un rango acotado ninguna consulta recorre por completo venta, detalle_venta ni movimiento | Cumplido | `RendimientoReportesTest` |
| CA-3 | Los listados son paginados y el detalle grande tiene tope (422 «Acote el rango») | Cumplido | `ReporteControllerTest`, `LibroVentasServiceIntegracionTest`, `MovimientosPeriodoServiceTest` |
| CA-4 | Medición real sobre la semilla de 36 meses | Cumplido (T2 de la fase 7, 2026-10-25: todo < 0,9 s; la exportación más pesada, 852 ms) | curl sobre la base de demostración |
| CA-5 | El reporte de movimientos cumple lo mismo | Cumplido (CI del PR #11: 121 a 725 ms; T2: 88 a 844 ms; `EXPLAIN` con rango) | `RendimientoReportesTest`, T2 |

## Observaciones

- El volumen de la prueba (12.000 ventas, 43.300 movimientos) y el de la semilla (20.337 ventas, 33.011 movimientos)
  superan los 10.000 registros del requisito.
- La medición de la T2 se hizo por API en el portátil (Windows 10, MySQL 9.7 nativo); la revisión visual por la
  interfaz la hace el usuario.
- El `EXPLAIN` de rangos de 24 meses es informativo: con rangos que cubren casi toda la tabla, MySQL puede preferir un
  recorrido completo, que sigue por debajo de 3 s con estos volúmenes.
