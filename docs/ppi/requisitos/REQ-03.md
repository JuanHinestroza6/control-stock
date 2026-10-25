# REQ-03 — Generar Reportes de Rentabilidad (FO-ERS)

> Estado a la fecha: implementado en la Fase 7 (backend y frontend), rama `fase-7-cierre-entrega`. Decisiones en
> ADR-011 (D1 a D4, R1 a R10). Los datos de stakeholder, necesidad, caso de uso y prioridad, y el texto de CU-06,
> provienen del documento Word (anexo del plan de la fase 7); las notas de alcance van como observación, sin cambiar el
> texto del Word.

| Campo | Contenido |
|---|---|
| **Código** | REQ-03 |
| **Nombre** | Generar Reportes de Rentabilidad |
| **Tipo** | Funcional |
| **Stakeholder** | Gerente |
| **Necesidad** | NEC-03 |
| **Caso de uso** | CU-06 «Generar reportes de ventas y rentabilidad» |
| **Relacionados** | REQ-07 (libro de ventas: concilia con este reporte); REQ-05 (el ingreso neto coincide con «Más vendidos»); REQ-08 (las devoluciones restan); REQ-02 (costo de la última entrada, ADR-006); RNF-03 (rendimiento); REQ-06 (roles) |
| **Prioridad** | Alta |
| **Módulos** | `reporte` (`RentabilidadService`, `ConsultasReporte`, `ReporteController`, `CalculosReporte`, `ValidadorRangoReporte`); `comun.exportacion` (`DocumentoTabular`, `GeneradorExcel`, `GeneradorPdf`, `ExportadorDocumentos`); frontend: `paginas/reportes` (pestaña «Rentabilidad», gráfico de margen con Recharts), `api/reportes`, `lib/descarga`, `componentes/ui/BotonExportar` |

## Descripción (Word)

El sistema debe generar reportes detallados sobre ventas realizadas, márgenes de ganancia y rentabilidad por producto en
rangos de tiempo configurables.

## Alcance implementado (Fase 7)

- **Endpoint** `GET /api/reportes/rentabilidad` con `desde`, `hasta` (inclusivos), `categoriaId`, `agrupacion`
  (CATEGORIA, PRODUCTO o VARIANTE) y `orden` (MARGEN, INGRESO o UNIDADES). Su hermano `GET …/exportar?formato=PDF|XLSX`
  usa los mismos filtros y el mismo servicio: lo exportado es lo mostrado.
- **Métricas** (solo ventas REGISTRADA; por línea q cantidad, pl precio de lista, pu precio unitario, c costo unitario,
  d devuelto, dn devuelto no apto): ventas a lista Σ q·pl; descuentos Σ q·(pl − pu); facturado Σ q·pu; devoluciones
  Σ d·pu; **ingreso neto** Σ (q − d)·pu; **costo de ventas** Σ (q − d)·c; **margen bruto** = neto − costo y su % sobre
  el neto (escala 1; vacío si el neto es 0); **merma no apta** Σ dn·c en columna aparte, con «resultado después de
  mermas». Dinero en `BigDecimal`, escala 2, HALF_UP.
- **Devoluciones en la fecha de la venta** (igual que «Más vendidos», ABC y Tendencias): el ingreso neto del reporte
  coincide con el de «Más vendidos» en el mismo rango.
- **Rangos**: por defecto del día 1 del mes en curso hasta hoy (America/Bogota); máximo 731 días; `desde > hasta` o una
  fecha futura → 400 con `ProblemDetail`.
- **Interfaz**: pestaña «Rentabilidad» de Reportes con filtros, tabla con totales y gráfico de barras del margen del
  top 10 (CU-06, paso 6); botones «Exportar PDF» y «Exportar Excel».
- **Archivos** (R7): Excel con formatos numéricos, primera fila fija, autofiltro y hoja «Notas»; PDF carta horizontal
  con encabezado, filtros, «Generado por… el…», totales y «Página n de m». Respuesta con `Content-Disposition` y
  `Cache-Control: no-store`.
- **Ley 1581**: ninguna consulta selecciona nombre ni contacto del cliente; una prueba los busca en los bytes del PDF y
  del Excel.

### Endpoints y roles

| Método y ruta | Rol |
|---|---|
| `GET /api/reportes/rentabilidad` | ADMIN, CONTABILIDAD (INVENTARIO y VENDEDOR: 403) |
| `GET /api/reportes/rentabilidad/exportar?formato=PDF\|XLSX` | ADMIN, CONTABILIDAD |

### Flujo CU-06 (Word → sistema)

| Paso | Actor (Administrador o Contabilidad) | Sistema |
|---|---|---|
| 1–2 | Accede a «Reportes» | Muestra las pestañas Rentabilidad, Libro de ventas, Valorización y Movimientos |
| 3–4 | Selecciona «Rentabilidad», el rango, la categoría, la agrupación y el orden | Calcula márgenes y totales por grupo y muestra la tabla y el gráfico del top 10 |
| 5–6 | Pulsa «Exportar PDF» o «Exportar Excel» | Genera el archivo con totales, utilidades y notas (el gráfico se ve en pantalla; el PDF lleva la tabla) |

- **Alternativa 1 (periodo sin ventas)**: 200 con el reporte vacío y el mensaje «No hay ventas en el periodo», en
  pantalla, en el PDF y en el Excel.
- **Alternativa 2 (formato no disponible)**: si la exportación falla, la interfaz muestra el error y ofrece descargar en
  el otro formato (PDF ↔ Excel).
- **Extensión «Enviar reporte por correo»**: fuera de alcance (ver observaciones).
- **Poscondición**: el reporte queda disponible para consulta y exportación; se genera bajo demanda (no se almacena).

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | El ejemplo a mano (q 3, pl 50.000, pu 45.000, c 20.000, 1 devuelta apta al mes siguiente) da neto 90.000, costo 40.000 y margen 50.000 (55,6 %) | Cumplido | `RentabilidadServiceIntegracionTest`, `ReportesFlujoIntegracionTest` |
| CA-2 | Las ventas anuladas no cuentan; la devolución no apta va como merma y rebaja el costo | Cumplido | `RentabilidadServiceIntegracionTest` |
| CA-3 | Tres agrupaciones y tres órdenes, con desempate por id; el neto coincide con «Más vendidos» | Cumplido | `RentabilidadServiceIntegracionTest` |
| CA-4 | Periodo sin ventas → 200 con mensaje, también en PDF y Excel | Cumplido | `RentabilidadServiceIntegracionTest`, `PestanaRentabilidad.test.tsx` |
| CA-5 | Rango inválido o futuro → 400 `ProblemDetail`, sin archivo | Cumplido | `ValidadorRangoReporteTest`, `ReporteControllerTest` |
| CA-6 | PDF y Excel con el formato R7; nombre del archivo con tildes codificado | Cumplido | `GeneradorPdfTest`, `GeneradorExcelTest`, `ReporteControllerTest` |
| CA-7 | Si falla un formato, la interfaz ofrece el otro | Cumplido | `PestanaRentabilidad.test.tsx`, `descarga.test.ts` |
| CA-8 | Solo ADMIN y CONTABILIDAD; INVENTARIO y VENDEDOR 403; sin token 401 | Cumplido | `ReporteControllerTest`, `ReportesFlujoIntegracionTest` |
| CA-9 | Ningún archivo ni respuesta contiene datos del cliente (Ley 1581) | Cumplido | `ReportesFlujoIntegracionTest` |
| CA-10 | Responde en menos de 3 s con ~12.000 ventas (RNF-03) | Cumplido (T1: peor 24 meses por variante < 0,5 s) | `RendimientoReportesTest` |

## Observaciones (alcance; el texto del Word no se cambia)

- **Correo** (extensión de CU-06): fuera de alcance.
- **«Almacenado»** (poscondición): el reporte se genera bajo demanda y no se guarda; cada archivo lleva la marca
  «Generado por… el…».
- **Gráfico en el PDF**: recortado según el orden de recorte del plan; el gráfico del margen (top 10) está en la
  pantalla y el PDF lleva la tabla completa. El PDF de tendencias (REQ-09) sí incluye su gráfico.
- El margen usa el último costo de entrada vigente al vender (ADR-006): es aproximado si los costos cambiaron entre
  compras. El reporte lo anota.
