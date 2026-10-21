# REQ-09 — Tendencias Históricas (FO-ERS)

> Estado a la fecha: implementado en la Fase 6 (backend y frontend), rama `fase-6-prediccion-compras`. Decisiones en
> ADR-010 (decisión 10). La exportación a PDF (CU-05) queda para la Fase 7.
> **Corrección al documento Word.** En el Word, REQ-08 y REQ-09 tienen el nombre y la descripción cruzados. La ficha
> correcta es: REQ-08 «Gestionar el registro de devoluciones» (CU-09; ver `REQ-08.md`) y REQ-09 «Tendencias históricas»
> (esta ficha). Debe corregirse en la próxima versión del documento.
> Los datos de stakeholder, necesidad, caso de uso y prioridad provienen del documento Word; los que aquí aparecen entre
> corchetes deben confirmarse contra el Word y no se asumen.

| Campo | Contenido |
|---|---|
| **Código** | REQ-09 |
| **Nombre** | Tendencias Históricas |
| **Tipo** | Funcional |
| **Stakeholder** | [confirmar con el Word] |
| **Necesidad** | [confirmar con el Word] |
| **Caso de uso** | CU-05 (consulta y exportación de tendencias; el título exacto debe confirmarse con el Word). La consulta en pantalla está implementada; la exportación a PDF es de la Fase 7 |
| **Relacionados** | REQ-05 (misma demanda comercial: unidades e ingreso netos); REQ-08 (las devoluciones restan); REQ-10 (el pronóstico usa la demanda de inventario, no esta serie); REQ-06 (roles); REQ-03 y REQ-07 (reportes de la Fase 7, donde va el PDF) |
| **Prioridad** | [confirmar con el Word] |
| **Módulos** | `analitica` (`TendenciasService`, `ConsultasTendencias`, `AnaliticaController`); frontend: `paginas/analitica` (pestaña «Tendencias», con Recharts de carga diferida), `api/analitica` |

## Descripción

El sistema debe mostrar cómo evolucionan las ventas a lo largo del tiempo, mes a mes, de modo que la gerencia y el
encargado de inventario identifiquen la estacionalidad y el crecimiento del negocio y los comparen con el mismo mes del
año anterior. La serie se calcula con consultas agregadas de solo lectura; no modifica datos.

## Alcance implementado (Fase 6)

- **Endpoint** `GET /api/analitica/tendencias` con los filtros opcionales `desde` y `hasta` (mes en formato `YYYY-MM`),
  `categoriaId` y `productoId`.
- **Serie**: demanda **comercial** por mes (la misma de «Más vendidos»): solo ventas REGISTRADA; unidades netas =
  vendidas − devueltas (todas las devoluciones, en la fecha de la venta); ingreso neto = Σ (cantidad − devuelta) ×
  `precioUnitario`. Las ventas ANULADAS no cuentan.
- **Comparación interanual**: cada mes incluye las unidades y el ingreso del mismo mes del año anterior y su variación
  (unidades e ingreso).
- **Rango**: por defecto 24 meses completos; máximo 36. Un rango mayor o con `desde` posterior a `hasta` responde 400.
  Una categoría o un producto sin ventas devuelve la serie en ceros.
- **Totales** de unidades e ingreso del periodo en la respuesta.
- **Sin costos ni margen**: la respuesta no contiene costos.
- **Interfaz**: pestaña «Tendencias» de la página Analítica, con gráfico mensual y tabla; rotula la serie como «ventas
  netas» para distinguirla de la «demanda» que usa el pronóstico (REQ-10).

### Endpoint y roles

| Método y ruta | Rol |
|---|---|
| `GET /api/analitica/tendencias` | ADMIN, INVENTARIO, CONTABILIDAD (VENDEDOR: 403) |

### Flujo de consulta de tendencias

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El usuario abre Analítica → «Tendencias» | Consulta los últimos 24 meses completos y muestra el gráfico y la tabla |
| 2 | Cambia el rango de meses, la categoría o el producto | Valida el rango y vuelve a consultar |
| 3 | | Presenta por mes unidades, ingreso, mismo mes del año anterior y variación, y los totales |

- **Caminos alternativos**: rango mayor a 36 meses, formato de mes inválido o `desde` posterior a `hasta` → 400;
  categoría o producto sin ventas → serie en ceros; sin sesión → 401; VENDEDOR → 403.
- **Puntos de extensión**: exportar las tendencias a PDF (CU-05, Fase 7); consultar el pronóstico del producto (REQ-10).
- **Poscondición**: ninguna; la consulta no modifica datos.

## Fuera de alcance de la Fase 6

- **Exportación a PDF de las tendencias (CU-05): Fase 7**, junto con los reportes (OpenPDF y Apache POI), REQ-03 y
  REQ-07. En esta fase la pestaña solo muestra el gráfico y la tabla.

## Diagrama

- No tiene diagrama propio. La consulta comparte módulo con CU-08 y CU-15; el diagrama de clases de `analitica` está en
  [`diagramas/clases-fase5-analitica.puml`](../diagramas/clases-fase5-analitica.puml) (no incluye aún los servicios de
  tendencias y pronóstico de la Fase 6).

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | La serie mensual usa unidades e ingreso netos de ventas REGISTRADA, con devoluciones restadas y sin ventas anuladas | Cumplido | `TendenciasServiceTest`, `TendenciasPronosticosIntegracionTest` |
| CA-2 | Cada mes trae el mismo mes del año anterior y su variación | Cumplido | `TendenciasServiceTest` |
| CA-3 | Rango por defecto de 24 meses y máximo de 36; rango inválido o `desde > hasta` responde 400 | Cumplido | `TendenciasServiceTest`, `AnaliticaControllerPronosticoTest` |
| CA-4 | Los filtros por categoría y producto acotan la serie; sin ventas devuelve ceros | Cumplido | `TendenciasPronosticosIntegracionTest` |
| CA-5 | Solo ADMIN, INVENTARIO y CONTABILIDAD consultan; VENDEDOR recibe 403 y sin token 401 | Cumplido | `AnaliticaControllerPronosticoTest`, `TendenciasPronosticosIntegracionTest` |
| CA-6 | La pestaña muestra gráfico y tabla, valida el rango y maneja los estados vacío y de error | Cumplido | `PestanaTendencias.test.tsx`, `analitica.test.ts` |
| CA-7 | Exportación a PDF de las tendencias | Pendiente (Fase 7, CU-05) | — |

## Observaciones

- Datos del Word por confirmar: stakeholder, necesidad, CU-05 y prioridad (el material del repositorio no los registra).
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados (corregido en esta ficha); NEC-010 debería
  ser NEC-10.
- La serie de tendencias (demanda comercial) y la del pronóstico (demanda de inventario corregida por agotados) son
  distintas a propósito; la interfaz las rotula de forma diferente.
