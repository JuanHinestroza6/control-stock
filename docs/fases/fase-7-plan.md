# Plan APROBADO — Fase 7 (Cierre de entrega)

## Contexto
La fase 6 ya está en main (PR #10, c3c5cce). La fase 7 cierra la primera entrega con estas piezas:
- panel de inicio por rol;
- reportes de rentabilidad (REQ-03) y contables (REQ-07) con PDF y Excel;
- PDF de tendencias (CU-05, que venía de la fase 6);
- rendimiento medido (RNF-03, consultas de menos de 3 s);
- documento PPI y diagramas;
- ensayo de sustentación.

Esto es lo que hay hoy:
- No existe el módulo `reporte`. `/reportes` muestra "Próximamente".
- POI y OpenPDF no están en el pom y para traerlos hace falta red.
- `InicioPage` es solo una grilla de accesos.
- ArchUnit impide que un módulo dependa de `analitica`. Por eso la exportación de tendencias va en `analitica.web`.
- Los 14 `.puml` ya validan con el jar de la extensión PlantUML de VS Code (`-checkonly`), así que el pendiente (c) queda resuelto.

Regla de oro 1: los reportes y el panel solo leen (SQL agregado, `readOnly`). ArchUnit prohíbe que `reporte` use `MovimientoService` o escriba por JDBC.

Rama: `fase-7-cierre-entrega`.

## Decisiones (confirmadas por el usuario el 2026-10-21; en todas eligió la opción recomendada)
- **D1 Reportes**: núcleo con tres reportes: Rentabilidad (REQ-03), Libro de ventas y Valorización de inventario a costo actual (REQ-07). Después del corte, Movimientos del periodo.
- **D2 Devoluciones**:
  - En Rentabilidad cuentan en la fecha de la venta, coherente con Más vendidos, ABC y Tendencias.
  - En el Libro cuentan en la fecha de la devolución (criterio contable).
  - Sobre un rango que cubra la venta y su devolución, los dos reportes concilian.
- **D3 Costo**:
  - Costo de ventas = Σ (q − devueltas) × c.
  - La devolución no apta va como merma en una columna aparte, con la línea "resultado después de mermas".
- **D4 Panel**: el margen y el valor del inventario a costo solo los ven ADMIN y CONTABILIDAD.
- **D5 Corrección por agotados**:
  - No se toca el algoritmo.
  - La semilla pasa a reportar "unidades perdidas comparables": 12 meses, variantes activas, sin las agotadas a propósito ni LIV017.
- **D6 Despliegue**: sin Dockerfile nuevo. El diagrama muestra el objetivo de ADR-001 y el uso local actual.

- **P3**: el usuario corre `mvnw.cmd -DskipTests dependency:resolve` con red cuando el pom de B0 esté listo.

## Ajustes que salen de los CU del Word (Anexo)
- **CU-06, paso 6 ("totales, utilidades y gráficos")**: la pestaña Rentabilidad lleva un gráfico de barras con el margen del top 10 (Recharts). En el PDF el gráfico es recortable: si falta tiempo, solo va la tabla.
- **CU-06, alternativa 1 (periodo sin ventas)**: se genera el reporte vacío con el mensaje "No hay ventas en el periodo", en pantalla y en el PDF/Excel. La respuesta es 200, no un error.
- **CU-06, alternativa 2 (formato no disponible)**: si falla la exportación, el frontend ofrece descargar en el otro formato (PDF ↔ Excel).
- **CU-05, paso 6 ("análisis y conclusiones")**: el PDF de tendencias lleva un párrafo de resumen armado con las cifras: total del periodo, variación frente al año anterior y meses de mayor y menor venta. Se calcula sin depender del pronóstico.
- **Fuera de alcance, como observación en las fichas** (el texto del Word no se cambia):
  - gastos y validación fiscal (CU-07);
  - correo (CU-06);
  - proveedor (CU-01);
  - "almacenado": el reporte se genera bajo demanda, no se guarda;
  - "inconsistencias" (CU-07 alternativa 1): la integridad la garantizan el kardex y el verificador.
- **REQ-10 en el Word usa CU-01.** La ficha lo toma como CU principal y referencia CU-16, CU-17 y CU-18 como el detalle implementado. INVENTARIO crea y recibe; solo ADMIN aprueba.
- **REQ-09**: se usa el nombre "Analizar Tendencias Históricas de Venta" (en el Word está cruzado con REQ-08).

## Decisiones técnicas (ADR-011 "Reportes, exportación y panel")
- **R1 Ubicación**:
  - Módulo nuevo `reporte` (consultas JDBC de solo lectura).
  - Generadores genéricos en `comun.exportacion` (`DocumentoTabular`, `GeneradorExcel`, `GeneradorPdf`, `ExportadorDocumentos`).
  - Tendencias en `analitica.web`.
  - ArchUnit nuevo:
    - nadie depende de `reporte`;
    - `reporte` no depende de `analitica` ni de `semilla`;
    - `comun.exportacion` no depende de ningún módulo.
- **R2 Exportación**:
  - Cada reporte tiene un endpoint hermano `GET …/exportar?formato=PDF|XLSX` con los mismos filtros y el mismo servicio que el JSON.
  - Responde `byte[]` con `Content-Disposition` y `Cache-Control: no-store`. `Content-Disposition` se expone en CORS.
  - Un 400 llega como `ProblemDetail`.
- **R3 Límites**:
  - Generación síncrona.
  - Rango inclusivo de 731 días como máximo. `desde > hasta` o una fecha futura → 400.
  - El detalle del libro solo va en Excel (SXSSF), con 50.000 filas como máximo (si no, 422).
- **R4 Fechas**: por defecto, del día 1 del mes hasta hoy (`Clock`, America/Bogota). Intervalos `[desde 00:00, hasta+1 00:00)`.
- **R5 Panel**:
  - `GET /api/panel` agregado; las secciones llegan en null según el rol, decidido en el servidor.
  - Se reutilizan alertas/resumen, más vendidos y la última ejecución.
  - Refresco cada 60 s.
- **R6 Dinero**:
  - `SUM` sobre DECIMAL y `setScale(2, HALF_UP)`.
  - Margen % con escala 1; null si el ingreso es 0.
- **R7 Formato de los archivos**:
  - Excel: formatos numéricos, primera fila fija, autofiltro y hoja Notas.
  - PDF: carta horizontal, con encabezado, filtros, "Generado por… el…", totales y "Página n de m".
- **R8 Rendimiento**:
  - `RendimientoReportesTest`: unas 12.000 ventas por JDBC en el contenedor; peor de 5 corridas < 3 s; `EXPLAIN` sin `ALL` sobre venta, detalle_venta ni movimiento.
  - Además, medición real sobre la semilla con `Measure-Command`.
- **R9 Índices**: V17 solo si el `EXPLAIN` lo pide.
- **R10 Ley 1581**: ningún reporte selecciona datos de cliente. Una prueba busca el nombre del cliente en los bytes del PDF y del XLSX.
- **R11 PlantUML**: el comando `-checkonly` con el jar de la extensión se documenta en el README del PPI y en `docs/especificaciones/diagramas-uml.md`.

## Métricas (solo ventas REGISTRADA)
| Métrica | Fórmula |
|---|---|
| Ventas a lista | Σ q·pl |
| Descuentos | Σ q·(pl − pu) |
| Facturado | Σ q·pu |
| Devoluciones | Σ d·pu |
| Ingreso neto | Σ (q − d)·pu |
| Costo de ventas | Σ (q − d)·c |
| Margen bruto | neto − costo |
| Merma no apta | Σ dn·c |
| Valorización | físico × `precioCompra` actual (incluye lo reservado y las inactivas con stock) |

Valorización, extras:
- Valor a precio de venta y margen potencial.
- Lo en camino, a título informativo.

Ejemplo a mano: q 3, pl 50.000, pu 45.000, c 20.000, y 1 unidad devuelta apta el mes siguiente.
- Rentabilidad: neto 90.000, costo 40.000, margen 50.000 (55,6 %).
- Libro, mes de la venta: neto 135.000, costo 60.000, margen 75.000.
- Libro, mes de la devolución: neto −45.000, costo −20.000, margen −25.000.
- Los dos meses del libro suman lo mismo que la rentabilidad.

## Endpoints
AC = ADMIN y CONTABILIDAD; AN = ADMIN, INVENTARIO y CONTABILIDAD.

| Ruta | Rol |
|---|---|
| `GET /api/panel` | todos (secciones según el rol) |
| `GET /api/reportes/rentabilidad` y `/exportar` (desde, hasta, categoriaId, agrupacion CATEGORIA\|PRODUCTO\|VARIANTE, orden) | AC |
| `GET /api/reportes/libro-ventas` y `/exportar` (agrupacion DIA\|MES) | AC |
| `GET /api/reportes/valorizacion` y `/exportar` (categoriaId, agrupacion, incluirInactivas) | AC |
| `GET /api/reportes/movimientos` y `/exportar` (después del corte) | AC |
| `GET /api/analitica/tendencias/exportar` | AN |

Visibilidad en el panel:
- INVENTARIO no ve ventas ni margen.
- VENDEDOR ve ventas, pero sin margen ni costos, y tampoco ve compras.
- CONTABILIDAD no ve reservas.

## Tareas (< 1 h cada una, con un commit por tarea)
- **Paso 0 (coordinador)**:
  - Crear la rama.
  - Copiar este plan a `docs/fases/fase-7-plan.md`.
  - Escribir ADR-011.
  - En `dominio.md`: sección "Reportes y panel", fila del panel en la matriz y trazabilidad de REQ-03, REQ-07 y RNF-03.
  - Documentar el patrón `/exportar` en `docs/especificaciones/convenciones-backend.md`.
- **Backend**:
  - B0: POI y OpenPDF en el pom (**el usuario corre `dependency:resolve` con red**), esqueleto de `reporte` y ArchUnit.
  - B1: `GeneradorExcel`.
  - B2: `GeneradorPdf`.
  - B3: Rentabilidad.
  - B4: Libro.
  - B5: Valorización.
  - B6: `ReporteController` y `/exportar`, con WebMvc por rol.
  - B7: exportar tendencias.
  - B8: Panel.
  - Después del corte:
    - B9: Movimientos.
    - B10: perdidas comparables en la semilla (`analitica`).
    - B11: V17 si hace falta.
  - B1, B3, B4, B5 y B8 pueden ir en paralelo cuando B0 esté listo.
- **Frontend**, en paralelo con `vi.mock`:
  - F1: tipos, API, hooks, permisos, `lib/descarga.ts` y ruta.
  - F2: panel por rol.
  - F3: Rentabilidad.
  - F4: Libro.
  - F5: Valorización.
  - F6: exportar desde Tendencias.
  - F7: Movimientos (después del corte).
- **Pruebas**:
  - T1: rendimiento con `EXPLAIN`.
  - T2: flujo integrado con servicios reales (ejemplo a mano, conciliación, sin datos de cliente, matriz de roles).
  - T3: guía T2 real y guion de sustentación.
- **Documentación**:
  - D1: fichas REQ-03, REQ-07 y RNF-03; completar REQ-09 y REQ-10; REQ-01 con el panel.
  - D2: diagramas de paquetes, componentes, despliegue, casos de uso general, clases general y secuencia "exportar". Puede empezar pronto.
  - D3: trazabilidad final, README del PPI y `progreso.md`.

**Punto de corte (núcleo)**: Paso 0, B0–B8, F1–F6, T1 y T2. Al llegar ahí se para y se reporta.

Si falta tiempo, se recorta en este orden:
1. Movimientos.
2. Gráfico del PDF de tendencias.
3. Serie de 7 días del panel.
4. Agrupación MES del libro.
5. Excel de tendencias.

## Verificación
- `./mvnw -o verify` y `npm run test`, `lint` y `build` en verde. revisión de código sin críticos.
- Las cifras coinciden con el ejemplo a mano y la conciliación libro ↔ rentabilidad se cumple. El neto de rentabilidad es igual al de Más vendidos.
- El panel del VENDEDOR no muestra costos. Las exportaciones no traen datos de cliente. T1 < 3 s.
- T2 real del usuario sobre la semilla de 36 meses:
  - panel con los 4 roles;
  - abrir los PDF y Excel de los tres reportes;
  - valorización contra SQL;
  - PDF de tendencias como INVENTARIO (VENDEDOR → 403);
  - `Measure-Command` por endpoint;
  - ensayo cronometrado de unos 15 min.

## Riesgos
- Sin red no se pueden bajar POI ni OpenPDF. B3–B5, B8 y el frontend no los necesitan.
- Un error en la descarga llega como blob: `lib/descarga.ts` lo convierte a `ProblemDetail`.
- El margen sale del último costo (ADR-006); se documenta.
- Pruebas lentas en CI: la caché de contextos se mantiene en 8 y no se agregan `@MockitoBean` nuevos a las pruebas que usan contenedor.
- Sin Graphviz: `!pragma layout smetana`.
- Las reservas de la semilla vencen pronto: regenerar la víspera de la demo.

## Anexo: textos del Word para las fichas (pasar tal cual a D1; las notas de alcance van como observación)

REQ-03 "Generar Reportes de Rentabilidad" | Funcional | Gerente | NEC-03 | Alta
Descripción: El sistema debe generar reportes detallados sobre ventas realizadas, márgenes de ganancia y rentabilidad por producto en rangos de tiempo configurables.
CU-06 "Generar reportes de ventas y rentabilidad" | Iniciador: Administrador | Otros: Sistema Control Stock
Precondición: Deben existir ventas registradas.
Flujo: 1 Accede a "Reportes de ventas" / 2 Muestra opciones de tipo de reporte. 3 Selecciona "Reporte de ventas y rentabilidad" / 4 Calcula los márgenes y totales por producto. 5 Confirma la generación / 6 Genera el informe con totales, utilidades y gráficos.
Alt 1: Si no hay ventas en el periodo, genera un reporte vacío con un mensaje informativo.
Alt 2: Si el formato de exportación elegido no está disponible, propone guardarlo en otro formato compatible.
Extensión: Enviar reporte por correo electrónico.
Poscondición: El reporte queda generado, almacenado y disponible para consulta o exportación.

REQ-07 "Elaborar Reportes Contables Automáticos" | Funcional | Contabilidad | NEC-07 | Media
Descripción: El sistema debe generar automáticamente reportes contables compatibles con auditoría y normas fiscales vigentes.
CU-07 "Generar reportes contables automáticos" | Iniciador: Administrador contable
Precondición: Deben existir datos de ventas, compras y gastos.
Flujo: 1 Selecciona "Reportes contables" / 2 Compila los registros contables recientes. 3 Indica el periodo contable / 4 Calcula totales y genera los estados financieros. 5 Solicita exportar / 6 Genera archivo PDF y Excel con el reporte contable.
Alt 1: Si hay inconsistencias en los registros, alerta al usuario y bloquea la generación hasta corregirlas.
Alt 2: Si el archivo no puede generarse por falta de espacio o permisos, guarda una copia temporal y notifica al administrador.
Extensión: Validar datos fiscales antes de exportar.
Poscondición: El reporte queda generado, almacenado y disponible para administración, auditoría y consulta.

RNF-03 "Rendimiento de consultas" | No funcional | Contabilidad | NEC-01, NEC-03 | Media
Descripción: El sistema debe responder a consultas de inventario y generación de reportes en menos de 3 segundos para conjuntos de hasta 10.000 registros.

REQ-09 "Analizar Tendencias Históricas de Venta" | Funcional | Junta directiva | NEC-09 | Alta (en el Word el nombre está cruzado con REQ-08; usar este nombre)
Descripción: El sistema debe generar gráficos e informes de tendencias de ventas históricas para apoyar proyecciones estratégicas.
CU-05 "Visualizar tendencias históricas de ventas" | Iniciador: Administrador
Precondición: Deben existir registros de ventas históricos.
Flujo: 1 Selecciona "Tendencias de ventas" / 2 Muestra un panel de estadísticas. 3 Elige un rango de fechas y categorías / 4 Procesa los datos y genera gráficos. 5 Solicita descargar el informe / 6 Genera archivo PDF con análisis y conclusiones.
Alt 1: Si no hay ventas en el rango, indica que no hay datos disponibles.
Alt 2: Si ocurre un error al procesar, cancela la generación del gráfico y sugiere reintentar.
Extensión: Comparar periodos de venta.
Poscondición: El reporte de tendencias queda generado, almacenado y disponible para consulta.

REQ-10 "Optimizar la Planeación de Compras" | Funcional | Asistente de gerencia | NEC-10 | Alta
Descripción: El sistema debe sugerir la cantidad óptima de compra de productos basada en demanda proyectada y niveles de inventario.
CU-01 "Optimizar planeación de compras" | Iniciador: Administrador
Precondición: Datos actualizados de inventario e historial de ventas.
Flujo: 1 Solicita generar una planeación / 2 Verifica niveles de inventario. 3 Espera sugerencias / 4 Analiza tendencias de ventas y fechas de reposición; 5 Muestra la lista de productos con cantidades sugeridas. 6 Revisa y confirma / 7 Guarda la planeación aprobada y genera una orden de compra preliminar.
Alt 1: Si faltan datos de ventas, solicita completar el registro histórico.
Alt 2: Si el usuario no tiene permisos, deniega la solicitud e indica que debe iniciar sesión como administrador.
Extensión: Registrar proveedor.
Poscondición: La planeación queda registrada, asociada a inventario y ventas, y disponible para consulta y ejecución.

Notas de alcance (observación, sin cambiar el texto del Word):
- CU-07 menciona "gastos" y "normas fiscales vigentes": el sistema no registra gastos ni hace validación fiscal; los reportes cubren ventas, compras (entradas con costo), mermas y valorización.
- CU-06 "enviar por correo" y CU-01 "registrar proveedor" quedan como extensiones fuera de alcance.
- CU-01: en el sistema también INVENTARIO crea y recibe planes; solo ADMIN aprueba (D7 de la fase 6).
