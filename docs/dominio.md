# Dominio — Control Stock

Fuente de verdad del modelo de datos y las reglas de negocio. Si el código contradice este archivo, gana este archivo
(o se actualiza con un ADR).

## Glosario

- **Producto**: prenda genérica ("Camiseta Oversize Basic"). Tiene precios y tiempo de reposición.
- **Variante (SKU)**: combinación concreta producto + talla + color. Es la unidad de inventario. Todo el stock vive aquí.
- **Stock físico**: unidades presentes en bodega.
- **Stock reservado**: unidades apartadas por clientes (redes sociales) pendientes de pago/entrega.
- **Stock disponible**: `físico − reservado`. Es lo que se puede vender o apartar. Nunca negativo.
- **Kardex**: historial inmutable de movimientos por variante.
- **Punto de reorden (ROP)**: nivel de stock disponible en el que hay que pedir más.

## Entidades

```
Usuario        id, nombre, email (único), passwordHash, rol, activo, creadoEn, actualizadoEn
Categoria      id, nombre (único), creadoEn, actualizadoEn
Producto       id, codigo (único), nombre, categoriaId, precioCompra, precioVenta,
               leadTimeDias (default 15), loteMinimo (default 1), activo, creadoEn, actualizadoEn
Variante       id, productoId, sku (único), talla, color,
               stockFisico, stockReservado, stockMinimo, puntoReorden (nullable),
               claseAbc (A|B|C, nullable), activo, version, creadoEn, actualizadoEn
Movimiento     id, varianteId, tipo, cantidad (>0),
               fisicoAntes, fisicoDespues, reservadoAntes, reservadoDespues,
               usuarioId, fecha, referenciaTipo, referenciaId, observacion
Venta          id, fecha, usuarioId, canal (nullable, reservado para el futuro), total, estado
DetalleVenta   id, ventaId, varianteId, cantidad, precioUnitario, costoUnitario
Devolucion     id, detalleVentaId, cantidad, motivo, aptaParaVenta, fecha, usuarioId
Reserva        id, varianteId, cantidad, clienteNombre, clienteContacto,
               creadaEn, venceEn, estado, ventaId (nullable)
Alerta         id, varianteId, tipo, estado, creadaEn, revisadaEn, atendidaEn, atendidaPor
Pronostico     id, productoId, varianteId (nullable), periodo (YYYY-MM), cantidadProyectada,
               metodo, errorWape, calculadoEn
PlanCompra     id, fecha, usuarioId, estado
DetallePlan    id, planId, varianteId, cantidadSugerida, cantidadAprobada, justificacion
```

Auditoría: Usuario, Categoria, Producto y Variante heredan de `EntidadAuditable` (`creadoEn`, `actualizadoEn`, llenados por
JPA Auditing con un `Clock` en America/Bogota). Movimiento y las demás entidades con fecha propia (`fecha`, `creadaEn`,
`calculadoEn`) no heredan; Movimiento es inmutable. Base de datos: MySQL 8.4 LTS (ADR-002).

Tallas válidas: `XS, S, M, L, XL, XXL` (y `UNICA` para accesorios). Colores: texto libre normalizado en mayúsculas.
SKU sugerido: `{codigoProducto}-{talla}-{COLOR3}` → `LIV001-M-NEG`.

## Tipos de movimiento y su efecto

| Tipo | Físico | Reservado | Origen |
|---|---|---|---|
| ENTRADA | +q | = | Compra o ingreso de mercancía |
| VENTA | −q | = | Venta directa (sin reserva) |
| VENTA_RESERVA | −q | −q | Venta que convierte una reserva |
| DEVOLUCION | +q | = | Devolución apta para venta |
| AJUSTE_POSITIVO / AJUSTE_NEGATIVO | ±q | = | Conteo físico, merma, corrección |
| RESERVA | = | +q | Cliente aparta |
| LIBERACION | = | −q | Reserva vencida o cancelada |

Validaciones en `MovimientoService` (lanzan excepción de negocio, no se guarda nada):
- Ningún movimiento puede dejar `físico < 0`, `reservado < 0` ni `disponible < 0`.
- VENTA y RESERVA requieren `cantidad ≤ disponible`.
- AJUSTE requiere `observacion` obligatoria y rol ADMIN o INVENTARIO.

Después de cada movimiento se publica un evento (`@TransactionalEventListener(AFTER_COMMIT)`) que evalúa alertas.

## Máquinas de estado

- **Reserva**: `ACTIVA → CONVERTIDA` (al vender) | `ACTIVA → CANCELADA` (manual) | `ACTIVA → VENCIDA` (tarea programada cada 15 min).
  Vencimiento por defecto: 48 h. CANCELADA y VENCIDA generan movimiento LIBERACION.
- **Venta**: `REGISTRADA → ANULADA` (anular genera movimientos inversos; solo ADMIN).
- **Alerta**: `ACTIVA → ATENDIDA`. Si ya hay una ACTIVA para la misma variante y tipo, no se crea otra: se actualiza `revisadaEn`.
- **PlanCompra**: `BORRADOR → APROBADO → RECIBIDO` (al recibir genera ENTRADAS).

## Devoluciones

- Solo sobre un `DetalleVenta` existente; la suma devuelta nunca supera la cantidad vendida.
- `aptaParaVenta = true` → movimiento DEVOLUCION (+físico). `false` → se registra la devolución sin sumar stock.

## Alertas

| Tipo | Condición |
|---|---|
| STOCK_BAJO | `disponible ≤ puntoReorden` (o `≤ stockMinimo` si no hay ROP calculado) |
| AGOTADO | `disponible == 0` |
| SIN_ROTACION | sin ventas en 60 días y `físico > 0` (se calcula en la tarea nocturna) |

## Roles y permisos

| Funcionalidad | ADMIN | INVENTARIO | VENDEDOR | CONTABILIDAD |
|---|---|---|---|---|
| Usuarios y roles | CRUD | — | — | — |
| Productos / variantes | CRUD | CRUD | Ver | Ver |
| Entradas y ajustes | Sí | Sí | — | — |
| Ventas y devoluciones | Sí | — | Sí | Ver |
| Reservas | Sí | Sí | Sí | — |
| Kardex | Ver | Ver | — | Ver |
| Alertas | Ver / atender | Ver / atender | Ver | — |
| Analítica (ABC, ROP, pronóstico) | Sí | Ver | Más vendidos | Ver |
| Plan de compras | CRUD / aprobar | Crear borrador | — | Ver |
| Reportes contables / rentabilidad | Sí | — | — | Sí |

## Trazabilidad con el documento PPI

| Requisito PPI | Módulo |
|---|---|
| REQ-01 Visualizar stock en tiempo real | catalogo, inventario |
| REQ-02 Registrar operaciones de stock | inventario |
| REQ-03 Reportes de rentabilidad | reporte |
| REQ-04 Alertas de stock mínimo | inventario (alertas) + analitica (ROP) |
| REQ-05 Productos más vendidos | analitica |
| REQ-06 / REQ-12 Usuarios y roles | usuario, auth |
| REQ-07 Reportes contables | reporte |
| REQ-08 Devoluciones | venta |
| REQ-09 Tendencias históricas | analitica |
| REQ-10 Planeación de compras | compra + analitica (pronóstico) |
| REQ-11 Gestión de productos | catalogo |
| Nuevo: Apartados | inventario (reservas) |
| Nuevo: Clasificación ABC | analitica |

Nota: en el documento PPI, REQ-08 y REQ-09 tienen nombre y descripción cruzados; corregir en la próxima versión.
