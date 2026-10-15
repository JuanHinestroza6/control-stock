# REQ-05 — Identificar Productos de Mayor Venta (FO-ERS)

> Estado a la fecha: implementado en la Fase 5 (backend y frontend), rama `fase-5-analitica`. Decisiones en ADR-009.
> Los datos de stakeholder, necesidad, caso de uso y prioridad provienen del documento Word.

| Campo | Contenido |
|---|---|
| **Código** | REQ-05 |
| **Nombre** | Identificar Productos de Mayor Venta |
| **Tipo** | Funcional |
| **Stakeholder** | Vendedor |
| **Necesidad** | NEC-05 |
| **Caso de uso** | CU-08 |
| **Relacionados** | REQ-08 (las devoluciones restan de las unidades y del ingreso); REQ-14 (el ingreso neto por variante coincide con el de la clasificación ABC); REQ-06 (roles) |
| **Prioridad** | Media |
| **Módulos** | `analitica` (`MasVendidosService`, `AnaliticaController`, consulta agregada en `analitica.repositorio`); frontend: `paginas/analitica` (pestaña "Más vendidos"), `api/analitica` |

## Descripción

El sistema debe permitir identificar qué productos o variantes se venden más en un periodo, para orientar la venta y la
reposición. El ranking se calcula con consultas agregadas de solo lectura sobre ventas, detalles y devoluciones.

## Alcance implementado (Fase 5)

- **Endpoint** `GET /api/analitica/mas-vendidos` con los filtros opcionales `desde`, `hasta`, `categoriaId`, `criterio`
  (unidades o ingreso neto), `agrupacion` (producto o variante) y `limite`.
- **Valores por defecto**: últimos 30 días (fecha de Bogotá), criterio por unidades, agrupación por producto, límite 10.
  Rango inclusivo de hasta 731 días (732 se rechaza con 400); límite de 1 a 50.
- **Reglas de cálculo**: solo ventas REGISTRADA (las ANULADAS se excluyen); unidades netas = vendidas − devueltas (todas
  las devoluciones, en la fecha de la venta); ingreso neto = Σ (cantidad − devuelta) × `precioUnitario`. Desempate
  determinista (por ingreso, unidades e id). Se incluyen productos o variantes inactivos con una marca.
- **Sin costos ni margen**: la respuesta no contiene campos de costo, por lo que los cuatro roles pueden consultarla.
- **Interfaz**: pestaña "Más vendidos" de la página Analítica, con periodo (predefinido o personalizado, máximo 731
  días), criterio, agrupación y categoría; tabla con totales, participación (un decimal, HALF_UP) y barra.

### Endpoint y roles

| Método y ruta | Rol |
|---|---|
| `GET /api/analitica/mas-vendidos` | ADMIN, INVENTARIO, VENDEDOR, CONTABILIDAD |

### Flujo de consulta de los más vendidos

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El usuario abre Analítica → "Más vendidos" | Consulta con los valores por defecto y muestra el ranking |
| 2 | Cambia periodo, criterio, agrupación o categoría | Valida el rango y vuelve a consultar |
| 3 | | Presenta unidades, ingreso neto, participación y totales del periodo |

- **Caminos alternativos**: rango inválido, criterio o agrupación desconocidos o límite fuera de rango → 400; periodo sin
  ventas o categoría sin ventas → lista vacía con totales en cero; sin sesión → 401.
- **Poscondición**: ninguna; la consulta no modifica datos.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Ranking por producto o variante, por unidades o ingreso, con desempate determinista y participación | Cumplido | `MasVendidosIntegracionTest`, `MasVendidosServiceTest` |
| CA-2 | Las ventas anuladas no suman y las devoluciones restan sin duplicar filas | Cumplido | `MasVendidosIntegracionTest`, `AnaliticaFlujoIntegracionTest` |
| CA-3 | Valores por defecto (30 días, límite 10) y validación del rango (máximo 731 días) y del límite | Cumplido | `MasVendidosServiceTest`, `AnaliticaControllerTest` |
| CA-4 | El rango de fechas es inclusivo y el filtro de categoría inexistente o sin ventas devuelve lista vacía | Cumplido | `MasVendidosIntegracionTest` |
| CA-5 | La respuesta no expone campos de costo | Cumplido | `MasVendidosIntegracionTest` |
| CA-6 | El ingreso por variante coincide con el ingreso neto de la clasificación ABC | Cumplido | `MasVendidosIntegracionTest` |
| CA-7 | Los cuatro roles consultan; sin token responde 401 | Cumplido | `AnaliticaControllerTest`, `AnaliticaFlujoIntegracionTest` |
| CA-8 | La pestaña aplica filtros, valida el rango y maneja estados vacío y de error; el VENDEDOR la ve | Cumplido | `PestanaMasVendidos.test.tsx`, `AnaliticaPage.test.tsx`, `analitica.test.ts` |

## Observaciones

- Datos del Word: Vendedor, NEC-05, CU-08, prioridad Media. El nombre de CU-08 no consta en el repositorio y no se asume.
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados; NEC-010 debería ser NEC-10.
- La demanda observada está censurada por las ventas perdidas por falta de stock (2,5 % a 3 % en la semilla); lo corrige
  el pronóstico de la Fase 6.
