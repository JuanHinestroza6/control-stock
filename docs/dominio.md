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
Usuario        id, nombre, email (único), passwordHash, rol, activo,
               passwordCambiadoEn (nullable), debeCambiarPassword (default false), creadoEn, actualizadoEn
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

## Catálogo (ADR-004)

- **Normalización**: `codigo` del producto en mayúsculas, patrón `[A-Z0-9-]{2,20}`. Color: sin espacios extremos, espacios
  internos colapsados, en mayúsculas (conserva tildes). Nombre de categoría: sin espacios extremos ni repetidos.
  Unicidad sin distinguir mayúsculas ni tildes (collation `utf8mb4_0900_ai_ci`): "Rojo" y "ROJO" son el mismo color.
- **SKU**: lo genera el servidor, nunca el cliente. `COLOR3` = tres primeros caracteres alfanuméricos del color sin tildes
  (`MARRÓN` → `MAR`, `AZUL CIELO` → `AZU`; si el color tiene menos de 3, se usa completo). Si el SKU ya existe en
  cualquier producto, se agrega el menor sufijo numérico libre desde 2: `LIV001-M-NEG`, `LIV001-M-NEG2`, `LIV001-M-NEG3`.
- **Una variante por (producto, talla, color)**. Generar variantes es idempotente: crea las combinaciones faltantes y
  reporta las existentes (activas o inactivas) sin modificarlas.
- **UNICA es exclusiva**: un producto con variantes en talla UNICA no puede tener otras tallas, y viceversa (422).
- **Campos inmutables**: `codigo` del producto; `sku`, `talla` y `color` de la variante. Un error se corrige desactivando
  la variante y generando la correcta. Editables: datos y precios del producto; `stockMinimo` de la variante.
- **Precios**: `precioCompra ≥ 0`, `precioVenta > 0`, escala 2. Se permite `precioVenta < precioCompra` (liquidaciones);
  la interfaz advierte. `precioCompra` no se expone al rol VENDEDOR (llega `null`).
- **Borrado**: lógico (`activo = false`) para producto, variante y usuario. Categoría: borrado físico solo si no tiene
  productos (ni activos ni inactivos); si tiene, 409.
- **Desactivar producto** desactiva también todas sus variantes. **Reactivar producto no reactiva variantes**: se
  reactivan una a una. No se puede reactivar una variante de un producto inactivo (422) ni generar variantes en él (422).
- **Reservas activas bloquean la desactivación**: no se puede desactivar una variante, ni un producto con alguna variante,
  que tenga reservas ACTIVAS (409, mensaje con el SKU y las unidades reservadas). Hasta la Fase 3 se comprueba con
  `stockReservado > 0` (equivalente: el reservado solo sube con reservas activas); en Fase 3 puede reforzarse consultando
  la tabla de reservas.
- **Estado de stock** (`Variante.estadoStock()`, mismo umbral que las alertas): `AGOTADO` si `disponible == 0`; `BAJO` si
  `disponible ≤ umbral` (`puntoReorden`, o `stockMinimo` si no hay ROP); `OK` en otro caso.
- **Categorías base** (migración V3): Camisetas, Hoodies, Jeans, Joggers, Chaquetas, Accesorios. El generador de datos
  semilla las reutiliza.

## Usuarios y contraseñas (ADR-003, ADR-005)

- Solo el ADMIN administra usuarios. No hay borrado físico. El correo se normaliza (sin espacios, minúsculas) y es único.
- Un ADMIN no puede desactivarse, cambiarse el rol ni restablecerse la contraseña a sí mismo desde la administración de
  usuarios (422; su propia contraseña se cambia en "Mi cuenta"). Siempre debe quedar al menos un ADMIN activo (422).
- Política de contraseña: 12 a 72 caracteres y como máximo 72 bytes en UTF-8 (límite de BCrypt); no puede empezar por
  `cambiar` (valor de ejemplo). Al cambiar la propia, la nueva debe ser distinta de la actual.
- La contraseña que asigna el ADMIN (al crear o al restablecer) es **temporal**: marca `debeCambiarPassword = true`.
  Mientras esté activa, el usuario solo puede usar `GET /api/auth/yo` y `PUT /api/auth/yo/password`; el resto responde 403.
  El ADMIN inicial de V2 no queda forzado.
- Restablecer o cambiar la contraseña fija `passwordCambiadoEn` (truncado a segundos) y **revoca los tokens anteriores**:
  un token con `iat < passwordCambiadoEn` responde 401. El cambio de la propia contraseña devuelve un token nuevo.

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
| Mi cuenta (cambiar mi contraseña) | Sí | Sí | Sí | Sí |
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
| REQ-06 Controlar el acceso y roles de usuario (CU-02) | auth, usuario |
| REQ-07 Reportes contables | reporte |
| REQ-08 Devoluciones | venta |
| REQ-09 Tendencias históricas | analitica |
| REQ-10 Planeación de compras | compra + analitica (pronóstico) |
| REQ-11 Gestión de productos | catalogo |
| REQ-12 Registrar y gestionar usuarios (CU-12) | usuario |
| Nuevo: Apartados | inventario (reservas) |
| Nuevo: Clasificación ABC | analitica |

Nota: en el documento PPI, REQ-08 y REQ-09 tienen nombre y descripción cruzados; corregir en la próxima versión.

### Convención de códigos

- `REQ-xx`: requisitos funcionales (tabla anterior). `RNF-xx`: requisitos no funcionales del documento Word, que allí
  aparecen numerados como REQ-01..REQ-05; aquí se renombran a RNF-01..RNF-05 para no chocar con los funcionales.
- `CU-xx`: casos de uso. CU-13 "Iniciar sesión" no existe en el Word; se creó en la fase 0 y se liga a RNF-02.
- `NEC-xx`: necesidades del Word, con dos dígitos (NEC-010 del Word es NEC-10).

| Requisito no funcional | Stakeholder | Necesidades | Prioridad | Área principal |
|---|---|---|---|---|
| RNF-01 Disponibilidad en el sistema | Gerencia | — | Alta | infraestructura (Docker Compose, `/actuator/health`) |
| RNF-02 Seguridad y control | Gerencia | NEC-06 | Alta | auth, usuario, comun (CU-13 Iniciar sesión) |
| RNF-03 Rendimiento de consultas | Contabilidad | NEC-01, NEC-03 | Media | reporte, analitica, inventario (consultas e índices) |
| RNF-04 Usabilidad de la interfaz | Vendedores | NEC-02, NEC-05 | Media | frontend |
| RNF-05 Escalabilidad del sistema | Junta directiva | NEC-09, NEC-10 | Alta | transversal |
