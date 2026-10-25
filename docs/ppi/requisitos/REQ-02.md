# REQ-02 — Registrar Automáticamente Operaciones de Stock (FO-ERS)

> Estado a la fecha: implementado en la Fase 2 (backend y frontend), rama `fase-2-operacion`.

| Campo | Contenido |
|---|---|
| **Código** | REQ-02 |
| **Nombre** | Registrar Automáticamente Operaciones de Stock |
| **Tipo** | Funcional |
| **Stakeholder** | Vendedor |
| **Necesidad** | NEC-02 |
| **Caso de uso** | CU-03 «Registrar entradas y salidas» |
| **Relacionados** | REQ-01 (consulta de stock y kardex); REQ-08 (devoluciones); REQ-06 (permisos por rol) |
| **Prioridad** | Alta |
| **Módulos** | `inventario` (`MovimientoService`, `OperacionInventarioService`), `venta` (`VentaService`); frontend: `paginas/inventario`, `paginas/ventas`, `componentes/dominio/ModalAjuste` |

## Descripción

El sistema debe permitir registrar las operaciones que modifican el stock de las variantes: entradas de mercancía con
costo, ajustes manuales con observación obligatoria, ventas y anulaciones de ventas. Toda operación deja un renglón
inmutable en el kardex (`Movimiento`) con los saldos físico y reservado antes y después. Las decisiones constan en
ADR-006 y las reglas en `docs/dominio.md`.

## Alcance implementado (Fase 2)

- **Punto único de escritura (regla de oro 1)**: solo `MovimientoService.registrar` invoca
  `Variante.aplicarMovimiento(deltaFisico, deltaReservado)`; una prueba ArchUnit lo verifica. Valida `físico ≥ 0`,
  `reservado ≥ 0`, `reservado ≤ físico` y `cantidad ≤ disponible` en VENTA, RESERVA y AJUSTE_NEGATIVO (422).
- **Kardex inmutable (regla de oro 2)**: `Movimiento` con `@Immutable`, sin modificadores y creado por la fábrica
  `Movimiento.de`; su repositorio no ofrece actualización ni borrado. Los errores se corrigen con un ajuste.
- **Tipos de movimiento**: `ENTRADA`, `VENTA`, `VENTA_RESERVA`, `DEVOLUCION`, `AJUSTE_POSITIVO`, `AJUSTE_NEGATIVO`,
  `RESERVA`, `LIBERACION` y `ANULACION_VENTA`. En esta fase se emiten ENTRADA, VENTA, AJUSTE_*, DEVOLUCION y
  ANULACION_VENTA; los de reserva corresponden a la Fase 3.
- **Entradas con costo**: `POST /api/inventario/entradas` registra un movimiento ENTRADA por línea, con `costoUnitario`
  obligatorio (≥ 0). Un costo mayor que cero actualiza `precioCompra` del producto; las variantes de un mismo producto
  deben traer el mismo costo (422). La operación es de todo o nada.
- **Ajustes**: `POST /api/inventario/ajustes` (AJUSTE_POSITIVO o AJUSTE_NEGATIVO) con observación obligatoria; se permite
  sobre variantes inactivas.
- **Ventas**: `POST /api/ventas` crea la venta y un movimiento VENTA por línea en una sola transacción. Cada línea
  guarda `precioLista`, `precioUnitario` (`0 < precio ≤ lista`) y `costoUnitario`. El costo no se muestra al VENDEDOR.
- **Anulación**: `POST /api/ventas/{id}/anular` (solo ADMIN, motivo obligatorio) genera un movimiento `ANULACION_VENTA`
  por línea. Anular dos veces responde 409; con devoluciones registradas, 422.
- **Concurrencia (regla de oro 7)**: `@Version` en `Variante`; un conflicto responde 409 sin reintento en el servidor.
  Las líneas se procesan ordenadas por `varianteId` para evitar interbloqueos.
- **Evento**: tras cada movimiento se publica `StockCambiado` (base de las alertas de la Fase 4).

### Endpoints

| Método y ruta | Rol |
|---|---|
| `POST /api/inventario/entradas` | ADMIN, INVENTARIO |
| `POST /api/inventario/ajustes` | ADMIN, INVENTARIO |
| `POST /api/ventas` | ADMIN, VENDEDOR |
| `POST /api/ventas/{id}/anular` | ADMIN |

### Flujo de registrar venta

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El vendedor abre `/ventas/nueva` y agrega variantes con cantidad (y precio opcional) | Muestra el stock disponible de cada variante |
| 2 | Confirma la venta | Valida variantes activas y precio ≤ lista; registra la venta |
| 3 | | Registra un movimiento VENTA por línea mediante `MovimientoService` y publica `StockCambiado` |
| 4 | | Devuelve la venta con total y número (`V-000123`) |

- **Caminos alternativos**: stock insuficiente o variante inactiva → 422 y no se guarda nada; variante repetida → 400;
  conflicto de concurrencia → 409 y el formulario conserva los datos para confirmar de nuevo.
- **Puntos de extensión**: anular venta (ADMIN); registrar devolución (REQ-08).
- **Poscondición**: `stockFisico` disminuido en cada variante y un renglón de kardex por línea con referencia `VENTA/{id}`.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Cada tipo de movimiento aplica su efecto sobre físico y reservado | Cumplido | `TipoMovimientoTest`, `MovimientoTest`, `MovimientoServiceTest` |
| CA-2 | Ningún movimiento deja físico, reservado o disponible negativos (422) | Cumplido | `MovimientoServiceTest`, `MovimientoRepositoryTest` |
| CA-3 | Solo `MovimientoService` modifica el stock | Cumplido | `ReglasArquitecturaTest` |
| CA-4 | El kardex es inmutable (sin setters, entidad `@Immutable`) | Cumplido | `MovimientoTest` |
| CA-5 | La entrada exige costo, actualiza `precioCompra` y es de todo o nada | Cumplido | `OperacionInventarioServiceTest`, `InventarioOperacionesControllerTest`, `EntradaNuevaPage.test.tsx` |
| CA-6 | El ajuste exige observación y rol ADMIN o INVENTARIO (403 en otro caso) | Cumplido | `MovimientoServiceTest`, `InventarioOperacionesControllerTest`, `ModalAjuste.test.tsx` |
| CA-7 | La venta es de todo o nada y guarda precio de lista, precio y costo por línea | Cumplido | `VentaServiceTest`, `VentaTest`, `VentaControllerTest`, `VentaNuevaPage.test.tsx` |
| CA-8 | La anulación (solo ADMIN) restituye el stock con `ANULACION_VENTA`; doble anulación 409 | Cumplido | `VentaServiceTest`, `VentaControllerTest`, `accionesVenta.test.tsx` |
| CA-9 | Dos operaciones concurrentes sobre la misma variante no corrompen el stock (409) | Cumplido | `MovimientoConcurrenciaIntegracionTest` |
| CA-10 | Flujo de punta a punta: entrada, venta, devolución y anulación coherentes con el kardex | Cumplido | `OperacionIntegracionTest`, `VentaFlujoIntegracionTest` |
| CA-11 | El costo no se expone al VENDEDOR | Cumplido | `VentaServiceTest`, `VentaControllerTest`, `ventas.test.tsx` |

## Observaciones

- Datos del Word: Vendedor, NEC-02, CU-03, prioridad Alta.
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados; NEC-010 debería ser NEC-10.
- Pendiente aceptado (ADR-006): dos entradas simultáneas del mismo producto, gana la última en confirmar (`Producto` sin `@Version`).
- Diagramas: `diagramas/clases-fase2-operacion.puml`, `diagramas/secuencia-registrar-venta.puml`.
