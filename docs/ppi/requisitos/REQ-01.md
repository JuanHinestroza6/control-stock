# REQ-01 — Visualizar Stock y Atributos en Tiempo Real (FO-ERS)

> Estado a la fecha: matriz por producto (Fase 1); inventario actual y kardex consultable (Fase 2); panel de inicio por
> rol con indicadores de inventario y alertas más urgentes (Fase 7, ADR-011).

| Campo | Contenido |
|---|---|
| **Código** | REQ-01 |
| **Nombre** | Visualizar Stock y Atributos en Tiempo Real |
| **Tipo** | Funcional |
| **Stakeholder** | Contabilidad |
| **Necesidad** | NEC-01 |
| **Caso de uso** | CU-04 «Visualizar inventario en tiempo real» |
| **Relacionados** | REQ-11 (catálogo y matriz); REQ-02 (movimientos que alimentan el kardex); REQ-04 (alertas); REQ-07 (valorización y movimientos del periodo); RNF-03 (rendimiento) |
| **Prioridad** | Alta |
| **Módulos** | `catalogo` (matriz), `inventario` (`InventarioService`, `KardexService`, `AlertaService.urgentes`), `reporte` (`PanelService`, `ConsultasPanel`, `PanelController`); frontend: `paginas/inventario` (`InventarioPage`, `KardexPage`), `paginas/inicio` (`InicioPage`, `BloquesInicio`) |

## Descripción

El sistema debe mostrar el stock físico, reservado y disponible de cada variante con su estado (`OK`, `BAJO`,
`AGOTADO`) y permitir consultar el historial de movimientos (kardex) con filtros.

## Alcance implementado

- **Inventario actual** (`GET /api/inventario`, cualquier usuario autenticado): una fila por variante con stock físico,
  reservado, disponible, mínimo, punto de reorden, estado y precio de venta; filtros `q`, `categoriaId`, `estadoStock`,
  `incluirInactivas` y paginación. Vista `/inventario`.
- **Matriz talla × color** (`GET /api/productos/{id}/matriz`, Fase 1): con valores reales tras los movimientos.
- **Kardex** (`GET /api/movimientos`, ADMIN, INVENTARIO y CONTABILIDAD): filtros `varianteId`, `tipo`, `desde` y `hasta`;
  muestra saldos antes y después, costo (solo en entradas), usuario, referencia y observación. Vista `/inventario/kardex`.
- "Tiempo real" se entiende como consulta directa a la base en cada solicitud (sin caché de stock); el frontend
  refresca con TanStack Query tras cada operación. No hay actualización por empuje (WebSocket).
- Desde `/inventario`, ADMIN e INVENTARIO acceden al ajuste (`ModalAjuste`) y a la entrada (`/inventario/entradas/nueva`).

### Panel de inicio (Fase 7)

- `GET /api/panel` (todos los roles; las secciones no permitidas llegan `null`, decidido en el servidor), refresco cada
  60 s. Sección **Inventario** para todos: variantes activas, unidades físicas y disponibles, y tarjetas de **agotadas**,
  **bajo stock** y **sin rotación**; valor a costo y a precio de venta solo para ADMIN y CONTABILIDAD. Ventas (ADMIN,
  VENDEDOR, CONTABILIDAD; margen sin el VENDEDOR), apartados (sin CONTABILIDAD) y compras (sin VENDEDOR).
- Bloque **«Alertas más urgentes»** (ADMIN, INVENTARIO, VENDEDOR): `GET /api/alertas/urgentes?limite=5` lista las
  AGOTADO por mayor umbral y antigüedad y luego las STOCK_BAJO por menor cobertura (disponible / umbral), con SKU,
  talla, color, disponible / umbral y enlace a Alertas. Los conteos por tipo están solo en las tarjetas de Inventario
  (sin duplicarlos en el bloque).

| Método y ruta | Rol |
|---|---|
| `GET /api/panel` | Todos (secciones según el rol) |
| `GET /api/alertas/urgentes?limite` | ADMIN, INVENTARIO, VENDEDOR |

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | El inventario lista variantes con físico, reservado, disponible y estado, y filtra por texto, categoría y estado | Cumplido | `InventarioConsultaRepositoryTest`, `InventarioControllerConsultaTest`, `InventarioPage.test.tsx` |
| CA-2 | El estado `OK`/`BAJO`/`AGOTADO` coincide con la regla del dominio | Cumplido | `VarianteTest`, `dominio.test.tsx` |
| CA-3 | El kardex filtra por variante, tipo y fechas, y solo lo ven ADMIN, INVENTARIO y CONTABILIDAD (403 en otro caso) | Cumplido | `KardexConsultaRepositoryTest`, `KardexServiceTest`, `MovimientoControllerTest`, `KardexPage.test.tsx` |
| CA-4 | La matriz y el inventario reflejan el stock tras entradas y ventas | Cumplido | `OperacionIntegracionTest`, `CatalogoIntegracionTest` |
| CA-5 | El panel muestra a cada rol solo sus secciones; el VENDEDOR no ve costos ni margen | Cumplido (Fase 7) | `PanelControllerTest`, `PanelServiceIntegracionTest`, `ReportesFlujoIntegracionTest`, `InicioPage.test.tsx` |
| CA-6 | Agotadas y bajo stock solo como tarjetas de Inventario; el bloque de alertas lista las más urgentes en orden y enlaza a Alertas | Cumplido (Fase 7) | `AlertaServiceTest`, `AlertaControllerTest`, `InicioPage.test.tsx` |

## Observaciones

- Datos del Word: Contabilidad, NEC-01, CU-04 «Visualizar inventario en tiempo real», prioridad Alta.
- Complementa REQ-11, cuya ficha ya declaraba la matriz bajo REQ-01.
