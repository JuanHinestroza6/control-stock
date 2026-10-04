# REQ-13 — Gestionar Apartados (FO-ERS) — PROPUESTO

> Requisito **nuevo, no incluido en el documento Word**. El código `REQ-13` es **provisional**: continúa la numeración
> existente (REQ-01..REQ-12) y debe confirmarse o reemplazarse al incorporarlo al documento oficial.
> Estado a la fecha: implementado en la Fase 3 (backend y frontend), rama `fase-3-apartados`.

| Campo | Contenido |
|---|---|
| **Código** | REQ-13 (provisional, propuesto) |
| **Nombre** | Gestionar Apartados |
| **Tipo** | Funcional |
| **Stakeholder** | Por asignar (contrastar con el Word) |
| **Necesidad** | Por asignar (contrastar con el Word) |
| **Caso de uso** | Por asignar (contrastar con el Word) |
| **Relacionados** | REQ-01 (indicador de stock reservado); REQ-02 (movimientos RESERVA, LIBERACION, VENTA_RESERVA); REQ-04 (alertas sobre el disponible); REQ-06 (roles); REQ-11 (desactivación bloqueada por apartados) |
| **Prioridad** | Por asignar (contrastar con el Word) |
| **Módulos** | `inventario` (`ReservaService`, `VencimientoReservasTarea`, `AnonimizacionReservasTarea`, `ReservaController`), `venta` (`VentaService.registrarDesdeReservas`), `catalogo` (`ConsultaReservasActivas`); frontend: `paginas/reservas`, `componentes/dominio` (modales de reserva) |

## Descripción

Livorno Clothing vende por Instagram, TikTok y WhatsApp, donde el cliente suele apartar la prenda antes de pagar. El
sistema debe permitir apartar unidades de una variante para un cliente durante un tiempo limitado, separando el stock
**reservado** del **disponible** (`disponible = físico − reservado`), de modo que lo apartado no pueda venderse a otro.
El apartado puede convertirse en venta, cancelarse, extenderse o vencer automáticamente. Decisiones en ADR-007.

## Alcance implementado (Fase 3)

- **Crear**: de 1 a 20 líneas (variante, cantidad 1–100) de un mismo cliente; genera una reserva ACTIVA por línea y un
  movimiento RESERVA cada una (referencia `RESERVA/{id}`), todo o nada. Número visible `R-000012`. Variante repetida →
  400; variante o producto inactivo o cantidad mayor que el disponible → 422.
- **Cliente**: nombre (2–120) y contacto (3–120, texto libre). Datos mínimos (Ley 1581): CONTABILIDAD no los ve.
- **Vigencia**: 48 h por defecto (1–168 configurable). **Extender** fija `venceEn = ahora + horas`; el total desde la
  creación no supera 7 días (422).
- **Máquina de estados**: `ACTIVA → CONVERTIDA | CANCELADA | VENCIDA`. Toda transición bloquea la fila
  (`PESSIMISTIC_WRITE`) y exige estado ACTIVA (409 en otro caso); el estado manda aunque `venceEn` haya pasado.
- **Cancelar**: motivo obligatorio (5–200); movimiento LIBERACION con el usuario.
- **Vencimiento automático**: tarea programada cada 15 min; cada reserva vencida se procesa en su propia transacción con
  LIBERACION del sistema (`usuarioId` nulo); es idempotente.
- **Convertir en venta**: de 1 a 20 reservas ACTIVAS del mismo cliente generan **una venta**; líneas agrupadas por
  variante, un movimiento VENTA_RESERVA por línea (referencia `VENTA/{id}`), precio opcional por variante
  (`0 < precio ≤ lista`). Anular esa venta solo devuelve físico; las reservas siguen CONVERTIDAS.
- **Anonimización (Ley 1581)**: tarea nocturna (02:30, America/Bogota) que reemplaza nombre y contacto por
  `[anonimizado]` en reservas cerradas hace 90 días o más (configurable). No toca stock. Documentado en ADR-007 y en
  `docs/dominio.md`.
- **Dependencia con catálogo**: no se puede desactivar una variante o producto con reservas ACTIVAS (409).

### Endpoints

| Método y ruta | Rol |
|---|---|
| `GET /api/reservas`, `GET /api/reservas/{id}` | ADMIN, INVENTARIO, VENDEDOR |
| `POST /api/reservas` | ADMIN, INVENTARIO, VENDEDOR |
| `POST /api/reservas/{id}/cancelar` | ADMIN, INVENTARIO, VENDEDOR |
| `POST /api/reservas/{id}/extender` | ADMIN, INVENTARIO, VENDEDOR |
| `POST /api/ventas/desde-reservas` | ADMIN, VENDEDOR |

### Flujo de crear un apartado

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El usuario abre `/reservas/nueva`, ingresa nombre y contacto del cliente y busca variantes | Muestra el stock disponible de cada variante |
| 2 | Indica cantidades (y vigencia opcional) y confirma | Valida variantes activas y cantidad ≤ disponible |
| 3 | | Crea una reserva ACTIVA por línea y un movimiento RESERVA mediante `MovimientoService`; publica `StockCambiado` |
| 4 | | Devuelve las reservas con número `R-xxxxxx` y fecha de vencimiento |

- **Caminos alternativos**: stock insuficiente o variante inactiva → 422 y no se guarda nada; variante repetida → 400;
  conflicto de concurrencia → 409.
- **Puntos de extensión**: extender, cancelar, convertir en venta (VENDEDOR o ADMIN), vencimiento automático.
- **Poscondición**: `stockReservado` aumentado en cada variante y un renglón de kardex RESERVA por línea.

### Flujo de convertir reservas en venta

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El vendedor selecciona reservas ACTIVAS del mismo cliente en `/reservas` y elige "Convertir en venta" | Muestra precios por variante, total y descuento |
| 2 | Confirma (con precios opcionales menores o iguales a la lista) | Bloquea las reservas, valida estado y cliente |
| 3 | | Crea la venta y un movimiento VENTA_RESERVA por variante; marca cada reserva CONVERTIDA con `ventaId` |
| 4 | | Devuelve el detalle de la venta con las reservas de origen |

- **Caminos alternativos**: reserva no ACTIVA → 409; clientes distintos → 422; precio mayor a la lista o variante
  inactiva → 422; ids repetidos o precio de variante ajena → 400; reserva inexistente → 404.
- **Poscondición**: físico y reservado disminuidos por igual y reservas CONVERTIDAS.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Crear reserva es de todo o nada y mueve solo el reservado | Cumplido | `ReservaServiceTest`, `ReservaControllerTest`, `ReservaNuevaPage.test.tsx` |
| CA-2 | Las transiciones de estado respetan la máquina (409 si no está ACTIVA) y la regla de 7 días | Cumplido | `ReservaTest`, `ReservaServiceTest`, `ReservaRepositoryTest` |
| CA-3 | El vencimiento libera el stock por el sistema, es idempotente y omite reservas ya cerradas | Cumplido | `VencimientoReservasTareaTest`, `VencimientoReservasIntegracionTest` |
| CA-4 | Convertir agrupa por variante, aplica precios y costo al convertir, y cierra las reservas | Cumplido | `VentaDesdeReservasServiceTest`, `VentaControllerTest`, `conversionReservas.test.tsx` |
| CA-5 | Convertir rechaza reserva no ACTIVA (409) y clientes distintos (422) | Cumplido | `VentaDesdeReservasServiceTest`, `ReservasAlertasIntegracionTest` |
| CA-6 | Concurrencia entre vencer, cancelar y convertir sin dejar disponible negativo ni liberar dos veces | Cumplido | `ReservaConcurrenciaIntegracionTest` |
| CA-7 | Roles: crear, cancelar y extender (ADMIN, INVENTARIO, VENDEDOR); convertir (ADMIN, VENDEDOR) | Cumplido | `ReservaControllerTest`, `VentaControllerTest`, `ReservasAlertasIntegracionTest`, `ReservasPage.test.tsx` |
| CA-8 | Flujo de punta a punta coherente con el kardex, el stock y las alertas | Cumplido | `ReservasAlertasIntegracionTest` |
| CA-9 | Las reservas cerradas se anonimizan tras el plazo y las ACTIVAS no | Cumplido | `ReservaTest`, `AnonimizacionReservasTareaTest`, `AnonimizacionReservasIntegracionTest` |
| CA-10 | No se desactiva una variante o producto con reservas ACTIVAS | Cumplido | `VarianteServiceTest`, `ProductoServiceTest`, `DependenciasEntreModulosTest` |
| CA-11 | La interfaz muestra el stock reservado junto al disponible | Cumplido | `indicadoresReservado.test.tsx`, `ReservasPage.test.tsx` |

## Observaciones

- Requisito propuesto: no existe en el Word. Stakeholder, necesidad, caso de uso y prioridad quedan por asignar y no se
  asumen. Se sugiere crear un caso de uso propio (los códigos CU-01..CU-13 ya están usados).
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados; NEC-010 debería ser NEC-10.
- Pendiente aceptado: el campo `canal` de la venta sigue reservado para el futuro (no se captura el canal del apartado).
- Diagramas: `diagramas/estados-reserva.puml`, `diagramas/secuencia-convertir-reservas.puml`.
