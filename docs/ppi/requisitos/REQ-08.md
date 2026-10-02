# REQ-08 — Devoluciones (FO-ERS)

> Aviso de inconsistencia del Word: en el documento oficial REQ-08 y REQ-09 tienen el nombre y la descripción cruzados
> (nota en `docs/dominio.md`). Esta ficha usa el nombre "Devoluciones" con la descripción que le corresponde; al pegarla,
> corregir también REQ-09 "Tendencias históricas" para que su descripción trate de analítica.
> Estado a la fecha: implementado en la Fase 2.

| Campo | Contenido |
|---|---|
| **Código** | REQ-08 |
| **Nombre** | Devoluciones |
| **Tipo** | Funcional |
| **Stakeholder** | Por asignar (contrastar con el Word) |
| **Necesidad** | Por asignar (contrastar con el Word) |
| **Caso de uso** | Por asignar (contrastar con el Word) |
| **Relacionados** | REQ-02 (movimientos y kardex); REQ-01 (stock visible); REQ-03 y REQ-07 (reportes que consumirán las devoluciones) |
| **Prioridad** | Por asignar (contrastar con el Word) |
| **Módulos** | `venta` (`DevolucionService`, `Devolucion`), `inventario` (`MovimientoService`); frontend: `paginas/ventas/VentaDetallePage`, `componentes/dominio/ModalDevolucion` |

## Descripción

El sistema debe permitir registrar la devolución total o parcial de unidades de una venta, indicando el motivo y si la
prenda es apta para venta. Una devolución apta reingresa las unidades al stock; una no apta se registra sin sumar stock.

## Alcance implementado (Fase 2)

- `POST /api/ventas/{id}/devoluciones` (ADMIN, VENDEDOR) con `detalleVentaId`, `cantidad`, `motivo` y `aptaParaVenta`.
- La suma devuelta de una línea nunca supera la cantidad vendida (422 con las unidades restantes).
- Apta: movimiento `DEVOLUCION` (+físico) con referencia `DEVOLUCION/{id}` mediante `MovimientoService`. No apta: solo la
  fila de `Devolucion`, sin movimiento.
- Una venta anulada no admite devoluciones (422); una venta con devoluciones no puede anularse (422, se corrige con ajustes).
- `Devolucion` es inmutable (`@Immutable`) y el motivo es obligatorio.
- Bloqueo pesimista de la fila de la venta (ADR-006): una devolución no apta no toca `Variante` y su `@Version` no la protege.
- Las devoluciones se muestran por línea en `GET /api/ventas/{id}`.

### Flujo básico

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El vendedor abre el detalle de la venta y elige "Devolver" en una línea | Muestra las unidades aún devolvibles |
| 2 | Indica cantidad, motivo y si es apta para venta | Valida venta no anulada y cantidad pendiente |
| 3 | Confirma | Guarda la devolución; si es apta, registra DEVOLUCION en el kardex |

- **Caminos alternativos**: cantidad mayor a la pendiente o venta anulada → 422; línea inexistente → 404.
- **Poscondición**: devolución registrada; `stockFisico` aumentado solo si es apta.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Devolución apta suma stock con movimiento DEVOLUCION; no apta no lo toca | Cumplido | `DevolucionServiceTest`, `VentaFlujoIntegracionTest` |
| CA-2 | La suma devuelta no supera lo vendido (422) | Cumplido | `DevolucionServiceTest` |
| CA-3 | Venta anulada no admite devoluciones; venta con devoluciones no se anula | Cumplido | `DevolucionServiceTest`, `VentaServiceTest`, `OperacionIntegracionTest` |
| CA-4 | Solo ADMIN y VENDEDOR registran devoluciones (403 en otro caso) | Cumplido | `VentaControllerTest`, `accionesVenta.test.tsx` |
| CA-5 | La interfaz permite devolver desde el detalle de la venta | Cumplido | `accionesVenta.test.tsx`, `ventas.test.tsx` |

## Observaciones

- Stakeholder, NEC, CU y prioridad del Word: por asignar. NEC-010 del Word debería ser NEC-10.
- `ModalDevolucion` no tiene archivo de prueba propio; se cubre desde `accionesVenta.test.tsx`.
