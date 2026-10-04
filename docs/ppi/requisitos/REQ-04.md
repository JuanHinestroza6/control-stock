# REQ-04 — Emitir Alertas de Nivel Mínimo de Stock (FO-ERS)

> Estado a la fecha: implementado parcialmente en la Fase 3 (backend y frontend), rama `fase-3-apartados`.
> Alcance de esta fase: alertas STOCK_BAJO y AGOTADO por evento. SIN_ROTACION y la re-evaluación nocturna corresponden a la Fase 5.

| Campo | Contenido |
|---|---|
| **Código** | REQ-04 |
| **Nombre** | Emitir Alertas de Nivel Mínimo de Stock |
| **Tipo** | Funcional |
| **Stakeholder** | Asistente de gerencia |
| **Necesidad** | NEC-04 |
| **Caso de uso** | CU-10 |
| **Relacionados** | REQ-01 (estado de stock); REQ-02 (movimientos que disparan la evaluación); REQ-13 (apartados modifican el disponible) |
| **Prioridad** | Media |
| **Módulos** | `inventario` (`AlertaService`, `AlertaListener`, `AlertaController`), `catalogo` (evento `UmbralCambiado`); frontend: `paginas/alertas`, campana en `componentes/layout/Topbar`, `hooks/useAlertas` |

## Descripción

El sistema debe advertir cuándo el stock disponible de una variante activa llega a su umbral o se agota, para que el
encargado de inventario reponga a tiempo. El umbral es el punto de reorden (`puntoReorden`) cuando existe y, en su
defecto, `stockMinimo`. Las reglas constan en ADR-007 y en `docs/dominio.md` (sección "Alertas").

## Alcance implementado (Fase 3)

- **Tipos**: `AGOTADO` (`disponible == 0`) y `STOCK_BAJO` (`0 < disponible ≤ umbral`). Son **excluyentes** y coinciden con
  `Variante.estadoStock()`: al pasar de BAJO a AGOTADO se cierra la de STOCK_BAJO y se abre la de AGOTADO, y viceversa;
  al volver a OK se cierran ambas. El tipo `SIN_ROTACION` existe en la enumeración pero no se genera todavía.
- **Evaluación por evento**: un `@TransactionalEventListener(AFTER_COMMIT)` atiende `StockCambiado` (tras cada movimiento)
  y `UmbralCambiado` (al editar `stockMinimo`) y llama a `AlertaService.evaluar` en una transacción nueva
  (`REQUIRES_NEW`), bloqueando la variante. Un fallo se registra en el log y nunca llega al usuario.
- **Una alerta ACTIVA por variante y tipo**, garantizada por un índice único (columna generada `variante_activa`).
  Reevaluar sin cambios solo actualiza `revisadaEn`. Las variantes inactivas no abren alertas ni aparecen en el listado.
- **Atender**: manual por ADMIN o INVENTARIO (`ACTIVA → ATENDIDA` con `atendidaPor`), o automática cuando la condición
  deja de cumplirse (`atendidaPor` nulo). Atender una alerta ya atendida responde 409.
- **Campana**: contador de alertas activas en la barra superior, visible a ADMIN, INVENTARIO y VENDEDOR.

### Endpoints

| Método y ruta | Rol |
|---|---|
| `GET /api/alertas?estado&tipo&page&size` | ADMIN, INVENTARIO, VENDEDOR |
| `GET /api/alertas/resumen` | ADMIN, INVENTARIO, VENDEDOR |
| `POST /api/alertas/{id}/atender` | ADMIN, INVENTARIO |

### Flujo de alerta por stock bajo

| Paso | Actor | Sistema |
|---|---|---|
| 1 | Un usuario registra una operación que cambia el stock (venta, ajuste, reserva, etc.) | Confirma la transacción y publica `StockCambiado` |
| 2 | | Tras el commit evalúa la variante y abre la alerta STOCK_BAJO o AGOTADO (cierra la que ya no aplica) |
| 3 | El encargado de inventario ve el contador en la campana y abre `/alertas` | Lista las alertas activas con SKU, stock disponible y umbral |
| 4 | Atiende la alerta (tras reponer o decidir) | Registra `atendidaPor` y `atendidaEn`; la alerta pasa a ATENDIDA |

- **Caminos alternativos**: alerta ya atendida → 409; el stock se repone antes de atenderla → se cierra sola con
  `atendidaPor` nulo; rol VENDEDOR → solo consulta (sin botón de atender, 403 en el endpoint); falla del listener → se
  registra en el log y el siguiente movimiento de la variante reevalúa.
- **Puntos de extensión**: consultar el kardex de la variante desde la alerta (REQ-02); plan de compras (REQ-10, fase futura).
- **Poscondición**: a lo sumo una alerta ACTIVA por variante y tipo, coherente con el estado de stock vigente.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | AGOTADO y STOCK_BAJO se abren según el disponible y el umbral, con prioridad del punto de reorden sobre `stockMinimo` | Cumplido | `AlertaServiceTest` |
| CA-2 | Los dos tipos son excluyentes y al volver a OK se cierran ambos | Cumplido | `AlertaServiceTest`, `ReservasAlertasIntegracionTest` |
| CA-3 | Una sola alerta ACTIVA por variante y tipo (índice único), también bajo concurrencia | Cumplido | `AlertaRepositoryTest`, `AlertaConcurrenciaIntegracionTest` |
| CA-4 | Las variantes inactivas no abren alertas y se cierran las vigentes | Cumplido | `AlertaServiceTest` |
| CA-5 | Un fallo de la evaluación no se propaga al usuario | Cumplido | `AlertaServiceTest` |
| CA-6 | Atender cierra la alerta con usuario y fecha; una ya atendida responde 409; inexistente 404 | Cumplido | `AlertaServiceTest`, `AlertaControllerTest` |
| CA-7 | Solo ADMIN e INVENTARIO atienden; VENDEDOR consulta; otros roles reciben 403 | Cumplido | `AlertaControllerTest`, `ReservasAlertasIntegracionTest` |
| CA-8 | El resumen separa activas, agotadas y de stock bajo | Cumplido | `AlertaServiceTest`, `AlertaControllerTest` |
| CA-9 | La campana muestra el contador y enlaza a `/alertas`; la página filtra y atiende según el rol | Cumplido | `Topbar.test.tsx`, `AlertasPage.test.tsx` |
| CA-10 | SIN_ROTACION y re-evaluación nocturna | Pendiente (Fase 5) | — |

## Observaciones

- Stakeholder, necesidad, caso de uso y prioridad no se registran aquí: deben tomarse del Word y no se asumen.
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados; NEC-010 debería ser NEC-10.
- El umbral dinámico (ROP calculado) llega con la analítica (Fase 5); mientras tanto se usa `stockMinimo`.
- Diagramas: `diagramas/secuencia-convertir-reservas.puml` (muestra el evento `StockCambiado` que dispara la evaluación).
