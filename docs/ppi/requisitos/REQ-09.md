# REQ-09 — Analizar Tendencias Históricas de Venta (FO-ERS)

> Estado a la fecha: consulta implementada en la Fase 6 (rama `fase-6-prediccion-compras`, ADR-010 decisión 10) y
> exportación a PDF y Excel en la Fase 7 (rama `fase-7-cierre-entrega`, ADR-011).
> **Corrección al documento Word.** En el Word, REQ-08 y REQ-09 tienen el nombre y la descripción cruzados. La ficha
> correcta es: REQ-08 «Gestionar el registro de devoluciones» (CU-09; ver `REQ-08.md`) y REQ-09 «Analizar Tendencias
> Históricas de Venta» (esta ficha). Debe corregirse en la próxima versión del documento.
> Los datos de stakeholder, necesidad, caso de uso y prioridad, y el texto de CU-05, provienen del documento Word (anexo
> del plan de la fase 7).

| Campo | Contenido |
|---|---|
| **Código** | REQ-09 |
| **Nombre** | Analizar Tendencias Históricas de Venta |
| **Tipo** | Funcional |
| **Stakeholder** | Junta directiva |
| **Necesidad** | NEC-09 |
| **Caso de uso** | CU-05 «Visualizar tendencias históricas de ventas» (extensión «Comparar periodos») |
| **Relacionados** | REQ-05 (misma demanda comercial: unidades e ingreso netos); REQ-08 (las devoluciones restan); REQ-10 (el pronóstico usa la demanda de inventario, no esta serie); REQ-03 y REQ-07 (mismos generadores de PDF y Excel); REQ-06 (roles) |
| **Prioridad** | Alta |
| **Módulos** | `analitica` (`TendenciasService`, `ConsultasTendencias`, `ExportarTendenciasService`, `AnaliticaController`); `comun.exportacion` (`GeneradorPdf`, `GeneradorExcel`); frontend: `paginas/analitica` (pestaña «Tendencias», con Recharts de carga diferida y botones de exportar), `api/analitica` |

## Descripción (Word)

El sistema debe generar gráficos e informes de tendencias de ventas históricas para apoyar proyecciones estratégicas.

## Alcance implementado

### Consulta (Fase 6)

- **Endpoint** `GET /api/analitica/tendencias` con los filtros opcionales `desde` y `hasta` (mes `YYYY-MM`),
  `categoriaId` y `productoId`.
- **Serie**: demanda **comercial** por mes (la misma de «Más vendidos»): solo ventas REGISTRADA; unidades netas =
  vendidas − devueltas (todas las devoluciones, en la fecha de la venta); ingreso neto = Σ (cantidad − devuelta) ×
  `precioUnitario`. Las ventas ANULADAS no cuentan.
- **Comparar periodos** (extensión de CU-05): cada mes trae las unidades y el ingreso del mismo mes del año anterior y
  su variación; el gráfico los dibuja punteados.
- **Rango**: por defecto 24 meses completos; máximo 36. Un rango mayor o con `desde` posterior a `hasta` responde 400.
  Una categoría o un producto sin ventas devuelve la serie en ceros. Totales del periodo en la respuesta. Sin costos.
- **Interfaz**: pestaña «Tendencias» de Analítica con gráfico mensual y tabla; rotula la serie como «ventas netas» para
  distinguirla de la «demanda» del pronóstico (REQ-10).

### Exportación (Fase 7)

- `GET /api/analitica/tendencias/exportar?formato=PDF|XLSX` con los mismos filtros y el mismo servicio que la consulta.
- **PDF**: tabla mensual, **gráfico de líneas** (año actual y anterior) y un **párrafo de resumen** calculado con las
  cifras, sin depender del pronóstico (CU-05, paso 6, «análisis y conclusiones»): total del periodo, variación frente al
  año anterior y meses de mayor y menor venta. **Excel**: la misma tabla con la hoja «Notas».
- La pestaña «Tendencias» tiene «Exportar PDF» y «Exportar Excel»; si un formato falla ofrece el otro.

### Endpoints y roles

| Método y ruta | Rol |
|---|---|
| `GET /api/analitica/tendencias` | ADMIN, INVENTARIO, CONTABILIDAD (VENDEDOR: 403) |
| `GET /api/analitica/tendencias/exportar?formato=PDF\|XLSX` | ADMIN, INVENTARIO, CONTABILIDAD (VENDEDOR: 403) |

### Flujo CU-05 (Word → sistema)

| Paso | Actor (Administrador; también Inventario y Contabilidad) | Sistema |
|---|---|---|
| 1–2 | Selecciona Analítica → «Tendencias» | Muestra el panel con los últimos 24 meses completos: gráfico, tabla y totales |
| 3–4 | Elige el rango de meses, la categoría o el producto | Valida el rango, procesa los datos y regenera el gráfico con el año anterior |
| 5–6 | Pulsa «Exportar PDF» | Genera el PDF con tabla, gráfico y el párrafo de análisis y conclusiones |

- **Alternativa 1 (sin ventas en el rango)**: la serie llega en ceros y la interfaz indica que no hay datos; el PDF se
  genera con el mensaje.
- **Alternativa 2 (error al procesar)**: la interfaz muestra el error con «Reintentar»; si la exportación falla, ofrece
  el otro formato.
- **Extensión «Comparar periodos»**: comparación interanual mes a mes (implementada).
- **Poscondición**: el informe queda disponible para consulta y descarga; se genera bajo demanda (no se almacena).

## Diagrama

- Casos de uso: [`diagramas/casos-uso-general.puml`](../diagramas/casos-uso-general.puml) (CU-05 con su extensión).
- Secuencia de la exportación (común a REQ-03, REQ-07 y REQ-09):
  [`diagramas/secuencia-exportar-reporte.puml`](../diagramas/secuencia-exportar-reporte.puml).

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | La serie mensual usa unidades e ingreso netos de ventas REGISTRADA, con devoluciones restadas y sin ventas anuladas | Cumplido | `TendenciasServiceTest`, `TendenciasPronosticosIntegracionTest` |
| CA-2 | Cada mes trae el mismo mes del año anterior y su variación (comparar periodos) | Cumplido | `TendenciasServiceTest` |
| CA-3 | Rango por defecto de 24 meses y máximo de 36; rango inválido o `desde > hasta` responde 400 | Cumplido | `TendenciasServiceTest`, `AnaliticaControllerPronosticoTest` |
| CA-4 | Los filtros por categoría y producto acotan la serie; sin ventas devuelve ceros | Cumplido | `TendenciasPronosticosIntegracionTest` |
| CA-5 | Solo ADMIN, INVENTARIO y CONTABILIDAD consultan y exportan; VENDEDOR recibe 403 y sin token 401 | Cumplido | `AnaliticaControllerPronosticoTest`, `AnaliticaControllerConsultasTest`, `ReportesFlujoIntegracionTest` |
| CA-6 | La pestaña muestra gráfico y tabla, valida el rango y maneja los estados vacío y de error | Cumplido | `PestanaTendencias.test.tsx`, `analitica.test.ts` |
| CA-7 | Exportación a PDF (con gráfico y resumen calculado) y a Excel, con los mismos filtros; sin datos de clientes | Cumplido (Fase 7) | `ExportarTendenciasServiceTest`, `AnaliticaControllerConsultasTest`, `ReportesFlujoIntegracionTest`, `PestanaTendencias.test.tsx` |

## Observaciones

- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados (corregido en esta ficha); NEC-010 debería
  ser NEC-10.
- La serie de tendencias (demanda comercial) y la del pronóstico (demanda de inventario corregida por agotados) son
  distintas a propósito; la interfaz las rotula de forma diferente.
- «Almacenado» (poscondición): el informe se genera bajo demanda y no se guarda.
