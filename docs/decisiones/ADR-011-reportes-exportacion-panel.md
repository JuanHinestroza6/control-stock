# ADR-011: Reportes, exportación y panel de inicio

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto
La fase 7 cierra la primera entrega con los reportes de rentabilidad (REQ-03, CU-06) y contables (REQ-07, CU-07), su
exportación a PDF y Excel, el PDF de tendencias (REQ-09, CU-05, aplazado desde la fase 6), un panel de inicio por rol y
la medición de rendimiento (RNF-03: < 3 s con hasta 10.000 registros). Había varias cuestiones abiertas:
1. El dominio no definía las métricas (margen, costo de ventas, valorización) ni cómo tratar devoluciones y anulaciones.
2. ADR-009 atribuye las devoluciones a la fecha de la venta (demanda comercial), pero un libro contable registra cada
   hecho en la fecha en que ocurre.
3. Dónde vive cada pieza: ArchUnit prohíbe depender de `analitica` (ADR-009), y el PDF de tendencias necesita su servicio.
4. Cómo exportar sin perder los errores en `ProblemDetail` y sin que lo exportado difiera de lo mostrado.
5. Qué ve cada rol en el panel (costos para VENDEDOR, datos de clientes para CONTABILIDAD, Ley 1581).

## Decisión
1. **Tres reportes de solo lectura** en un módulo nuevo `reporte` (consultas SQL agregadas, `readOnly`): **Rentabilidad**
   (REQ-03), **Libro de ventas** y **Valorización de inventario** a costo actual (REQ-07); después del corte, **Movimientos
   del periodo**. Solo ventas REGISTRADA; las ANULADAS aparecen únicamente en la sección de anuladas del libro.
   Métricas por línea (q cantidad, pl `precioLista`, pu `precioUnitario`, c `costoUnitario`, d devuelto total, dn
   devuelto no apto): ventas a lista Σ q·pl; descuentos Σ q·(pl − pu); facturado Σ q·pu; devoluciones Σ d·pu;
   **ingreso neto** Σ (q − d)·pu (igual que Más vendidos y ABC); **costo de ventas** Σ (q − d)·c; **margen bruto** neto −
   costo, % sobre el neto (escala 1, null si el neto es 0); **merma no apta** Σ dn·c en columna aparte, con "resultado
   después de mermas". **Valorización** = físico × `precioCompra` actual (incluye lo reservado y las variantes inactivas
   con físico > 0), más valor a precio de venta, margen potencial y lo en camino (informativo, no suma).
2. **Devoluciones en fecha mixta**: la Rentabilidad las atribuye a la **fecha de la venta** (coherente con Más vendidos,
   ABC y Tendencias); el Libro las registra en la **fecha de la devolución**, con el reverso de su costo, de modo que un
   periodo cerrado no cambia por una devolución posterior. Sobre un rango que cubra la venta y todas sus devoluciones,
   ambos reportes concilian (prueba automática).
3. **Ubicación**: `reporte` lee por SQL las tablas de venta, devolución, catálogo, reservas (solo conteos) y planes, y
   usa el puerto `inventario.servicio.ConsultaPedidosEnCamino`. Los generadores genéricos van en `comun.exportacion`
   (`DocumentoTabular`, `GeneradorExcel` con Apache POI, `GeneradorPdf` con OpenPDF, `ExportadorDocumentos`). La
   exportación de tendencias va en `analitica.web`. ArchUnit: nadie depende de `reporte`; `reporte` no depende de
   `analitica` ni de `semilla` ni invoca `MovimientoService` ni escrituras JDBC; `comun.exportacion` no depende de
   ningún módulo.
4. **Exportación**: endpoint hermano `GET …/exportar?formato=PDF|XLSX` con los mismos filtros y el mismo servicio que el
   JSON; `ResponseEntity<byte[]>` con `Content-Disposition` (expuesto en CORS) y `Cache-Control: no-store`. Generación
   síncrona en memoria. Rango inclusivo de hasta 731 días; `desde > hasta` o fecha futura → 400; por defecto del día 1
   del mes en curso hasta hoy (`Clock`, America/Bogota), intervalos `[desde 00:00, hasta + 1 día 00:00)`. El detalle
   del libro solo va en Excel (SXSSF), máximo 50.000 filas (422 "Acote el rango"). Un periodo sin ventas devuelve 200 con
   el mensaje "No hay ventas en el periodo" (CU-06 alt. 1). Si la exportación falla, la interfaz ofrece el otro formato
   (CU-06 alt. 2). El PDF de tendencias incluye un resumen calculado con las cifras (CU-05 paso 6).
5. **Panel**: `GET /api/panel` agregado, con secciones en `null` según el rol decididas en el servidor. Margen y valor a
   costo solo para ADMIN y CONTABILIDAD; INVENTARIO no ve ventas; VENDEDOR no ve compras ni costos; CONTABILIDAD no ve
   reservas. Reutiliza Más vendidos y la última ejecución con sus roles. Los conteos de agotadas, bajo stock y sin
   rotación van solo en las tarjetas de Inventario; el bloque de alertas lista las más urgentes
   (`GET /api/alertas/urgentes`, ver dominio "Alertas") con enlace a Alertas, sin repetir los conteos.
6. **Ley 1581**: ninguna consulta de `reporte` selecciona `cliente_nombre` ni `cliente_contacto`; una prueba busca el
   nombre del cliente en los bytes del PDF y del XLSX.
7. **Rendimiento (RNF-03)**: prueba `RendimientoReportesTest` con ~12.000 ventas insertadas por JDBC en el contenedor de
   prueba (peor de 5 corridas < 3 s y `EXPLAIN` sin recorrido completo de venta, detalle_venta ni movimiento) y medición
   real sobre la semilla de 36 meses. Migración de índices solo si el `EXPLAIN` lo exige.

## Alternativas consideradas
- Todas las devoluciones por fecha de venta: el libro de un mes cerrado cambiaría con una devolución tardía.
- Todas por fecha de devolución: la rentabilidad dejaría de coincidir con Más vendidos y ABC.
- Merma no apta dentro del costo de ventas: el margen bruto perdería su definición estándar y la pérdida quedaría oculta.
- Negociación por `Accept: application/pdf`: un error 400 no se podría devolver como `ProblemDetail` (406).
- Valorización a una fecha pasada o costo promedio ponderado: exigen reconstruir el costo histórico desde el kardex;
  fuera de alcance (ADR-006 usa el último costo).
- Generación asíncrona o reportes guardados: innecesarios con estos volúmenes; el reporte se genera bajo demanda.
- Usar `TendenciasService` desde `reporte`: rompería la regla de ADR-009.

## Consecuencias
- Se agregan Apache POI (`poi-ooxml`) y OpenPDF al `pom.xml` (descarga con red, una vez).
- El margen es aproximado por el último costo de entrada (ADR-006); se anota en los reportes y en la ficha REQ-03.
- Los reportes son consultas del estado actual, no cierres contables: llevan la marca de tiempo de generación.
- Quedan fuera (observación en las fichas): gastos y validación fiscal (CU-07), envío por correo (CU-06), almacenamiento
  de reportes generados y bloqueo por inconsistencias (la integridad la garantizan el kardex inmutable y el verificador).
- Corrección por agotados (pendiente de la fase 6): no se cambia el algoritmo; la semilla informa las unidades perdidas
  comparables (12 meses, variantes activas, sin las agotadas a propósito ni productos inactivos).
