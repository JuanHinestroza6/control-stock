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
               costoUnitario (obligatorio en ENTRADA, null en los demás tipos),
               usuarioId (nullable: null = proceso del sistema), fecha, referenciaTipo, referenciaId, observacion
Venta          id, fecha, usuarioId, canal (nullable, reservado para el futuro), total, estado,
               anuladaEn (nullable), anuladaPor (nullable), motivoAnulacion (nullable)
DetalleVenta   id, ventaId, varianteId, cantidad, precioLista, precioUnitario, costoUnitario
Devolucion     id, detalleVentaId, cantidad, motivo, aptaParaVenta, fecha, usuarioId
Reserva        id, varianteId, cantidad, clienteNombre, clienteContacto,
               creadaEn, venceEn, estado, ventaId (nullable), usuarioId (quien la crea),
               cerradaEn (nullable), cerradaPor (nullable: null = proceso del sistema), motivoCancelacion (nullable)
Alerta         id, varianteId, tipo, estado, creadaEn, revisadaEn, atendidaEn, atendidaPor
Pronostico     id, productoId, varianteId (nullable: null = total del producto), periodo (día 1 del mes; se muestra
               YYYY-MM), cantidadProyectada, metodo (SES|HOLT_WINTERS), errorWape (nullable), calculadoEn, ejecucionId
               (V15; único por producto, variante y periodo)
PlanCompra     id, estado (BORRADOR|APROBADO|RECIBIDO|CANCELADO), origen (SUGERIDO|MANUAL), observacion,
               creadoEn/creadoPor, aprobadoEn/aprobadoPor, recibidoEn/recibidoPor, canceladoEn/canceladoPor,
               motivoCancelacion, ejecucionId (nullable), version, actualizadoEn (V16)
DetallePlan    id, planId, varianteId, cantidadSugerida (nullable), cantidadAprobada (1–100.000), costoEstimado,
               justificacion, cantidadRecibida (nullable), costoRecibido (nullable); único (plan, variante) (V16)
```
Instantáneas del pronóstico (V15, solo la última ejecución): `analitica_producto` (método, α/β/γ, WAPE de SES y de HW,
motivo sin HW, meses de historia, unidades observadas y corregidas de 12 meses) y `analitica_producto_mes` (serie mensual:
observada, disponibilidad, corregida y pronósticos del backtest).

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
  que tenga reservas ACTIVAS (409, mensaje con el SKU, el número de apartados y las unidades). Desde la Fase 3 se consulta
  la tabla de reservas (ADR-007) a través de la interfaz `catalogo.servicio.ConsultaReservasActivas`, implementada en
  inventario, y se mantiene también la comprobación `stockReservado > 0` como defensa.
- **Estado de stock** (`Variante.estadoStock()`, hecho físico sobre el disponible): `AGOTADO` si `disponible == 0`; `BAJO`
  si `disponible ≤ umbral` (`puntoReorden`, o `stockMinimo` si no hay ROP); `OK` en otro caso. Desde la Fase 6 no cuenta
  lo que viene en camino (ver "Alertas"): una variante BAJO con pedido en camino puede no tener alerta; la interfaz
  muestra "N en camino" junto al estado.
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
| ANULACION_VENTA | +q | = | Anulación de una venta (solo ADMIN, ADR-006) |

Único punto de escritura (ADR-006): `MovimientoService` es el único que invoca `Variante.aplicarMovimiento(deltaFisico,
deltaReservado)`, método público que además valida los invariantes. Una prueba ArchUnit lo hace cumplir.

Validaciones en `MovimientoService` (lanzan `ExcepcionNegocio` → 422, no se guarda nada):
- Ningún movimiento puede dejar `físico < 0`, `reservado < 0` ni `disponible < 0`.
- VENTA, RESERVA y AJUSTE_NEGATIVO requieren `cantidad ≤ disponible`; LIBERACION y VENTA_RESERVA, `cantidad ≤ reservado`.
- AJUSTE_POSITIVO y AJUSTE_NEGATIVO requieren `observacion` no vacía.
- ENTRADA requiere `costoUnitario ≥ 0`; los demás tipos no llevan costo.
- El rol lo controla el `@PreAuthorize` del endpoint que origina el movimiento (ajustes: ADMIN o INVENTARIO). El usuario
  llega explícito en la solicitud (`usuarioId`, null en procesos del sistema) y la fecha sale del `Clock` inyectado.
- Variante o producto inactivo: lo valida el servicio que llama. Entradas y ventas → 422. Ajustes, devoluciones y
  anulaciones sí se permiten sobre variantes inactivas (reflejan unidades físicas reales). También la **recepción de un
  plan de compras** (ADR-010): la entrada con referencia `PLAN_COMPRA/{id}` se permite aunque la variante se haya
  desactivado después de aprobar el plan (la mercancía ya pedida llega igual); aprobar sí exige variantes y productos activos.

Concurrencia (ADR-006): `@Version` en `Variante`. Un choque (`OptimisticLockingFailureException`) o un bloqueo de la base
(`PessimisticLockingFailureException`) responde 409 sin reintento en el servidor; el cliente conserva el formulario y el
usuario vuelve a confirmar. Las operaciones de varias líneas procesan las variantes ordenadas por id. Las operaciones
sobre reservas bloquean primero la fila de la reserva y después tocan la variante (orden reserva → variante, ADR-007).

Kardex inmutable: entidad `@Immutable`, sin setters, repositorio sin métodos de actualización ni borrado. Sin triggers.

Después de cada movimiento se publica `StockCambiado(varianteId)`. Desde la Fase 3 un
`@TransactionalEventListener(AFTER_COMMIT)` evalúa las alertas en una transacción nueva (ver "Alertas", ADR-007).

## Entradas, ventas y costos (ADR-006)

- **Entrada**: una o varias líneas (variante, cantidad, `costoUnitario`) en una transacción, con observación común
  opcional. Cada línea genera un movimiento ENTRADA con su costo y **actualiza `precioCompra` del producto** con ese costo
  (último costo de entrada; un costo 0 se guarda pero no actualiza `precioCompra`). Todas las líneas de un mismo producto en una entrada deben traer el mismo costo (422 si no).
  Variante repetida en la misma entrada → 400.
- **Ajuste**: una variante, AJUSTE_POSITIVO o AJUSTE_NEGATIVO, observación obligatoria; no lleva costo ni toca precios.
- **Venta**: una o varias líneas; variante repetida → 400; variante o producto inactivo → 422. `precioLista` = `precioVenta`
  del producto al vender; `precioUnitario` opcional en la solicitud (si falta, igual a `precioLista`; si viene,
  `0 < precioUnitario ≤ precioLista`); `costoUnitario` = `precioCompra` del producto al vender. Descuento de la línea =
  `(precioLista − precioUnitario) × cantidad`. `total = Σ cantidad × precioUnitario` (escala 2, HALF_UP). Número visible
  `V-000123` derivado del id. El costo no se expone al rol VENDEDOR.
- **Ventas anuladas**: se excluyen de la demanda, los más vendidos, la clasificación ABC, el pronóstico y los reportes de
  rentabilidad y contables (Fases 5–7).

## Máquinas de estado

- **Reserva**: `ACTIVA → CONVERTIDA` (al vender) | `ACTIVA → CANCELADA` (manual, motivo obligatorio) | `ACTIVA → VENCIDA`
  (tarea programada cada 15 min). Vencimiento por defecto: 48 h. CANCELADA y VENCIDA generan movimiento LIBERACION.
  Toda transición fija `cerradaEn` y `cerradaPor` (null en VENCIDA); sobre una reserva no ACTIVA → 409. Detalle en
  "Reservas (apartados)".
- **Venta**: `REGISTRADA → ANULADA` (solo ADMIN, motivo obligatorio). Anular genera un movimiento ANULACION_VENTA (+físico)
  por línea y guarda `anuladaEn`, `anuladaPor` y `motivoAnulacion`. Anular una venta ANULADA → 409. Una venta con
  devoluciones no se anula (422: se corrige con ajustes). Anular y devolver bloquean la fila de la venta
  (`PESSIMISTIC_WRITE`). Si la venta vino de reservas, anular solo devuelve el físico (ANULACION_VENTA): las reservas
  siguen CONVERTIDAS y no se reactivan.
- **Alerta**: `ACTIVA → ATENDIDA` (manual por ADMIN o INVENTARIO, o automática cuando la condición deja de cumplirse, con
  `atendidaPor` null). Si ya hay una ACTIVA para la misma variante y tipo, no se crea otra: se actualiza `revisadaEn`.
- **PlanCompra** (ADR-010): `BORRADOR → APROBADO → RECIBIDO` | `BORRADOR → CANCELADO` | `APROBADO → CANCELADO` (motivo
  obligatorio). Recibir genera ENTRADAS. Toda transición bloquea la fila del plan (`PESSIMISTIC_WRITE`) y verifica el
  estado; si no corresponde, 409 "El plan PC-000012 ya está APROBADO." RECIBIDO y CANCELADO son finales. Detalle en
  "Plan de compras".

## Devoluciones

- Solo sobre un `DetalleVenta` existente de la venta indicada (si no, 404); la suma devuelta nunca supera la cantidad
  vendida (422). No se devuelve sobre una venta ANULADA (422). Motivo obligatorio. Roles: ADMIN y VENDEDOR.
- `aptaParaVenta = true` → movimiento DEVOLUCION (+físico). `false` → se registra la devolución sin sumar stock.
- Como una devolución no apta no modifica `Variante`, la validación se protege bloqueando la fila de la venta.

## Reservas (apartados) (ADR-007)

- **Una variante por reserva.** Crear acepta de 1 a 20 líneas (variante, cantidad 1–100) de un mismo cliente y genera una
  reserva ACTIVA por línea en una sola transacción, con un movimiento RESERVA cada una (referencia `RESERVA/{id}`).
  Variante repetida → 400; variante o producto inactivo → 422; cantidad > disponible → 422. Número visible `R-000012`.
- **Cliente**: `clienteNombre` (2–120) y `clienteContacto` (3–120, texto libre: teléfono, @usuario de Instagram o TikTok),
  sin espacios extremos ni repetidos. Datos mínimos (Ley 1581): no se exportan y CONTABILIDAD no los ve.
- **Vigencia**: `vigenciaHoras` opcional al crear (1–168, por defecto 48, configurable). `venceEn = creadaEn + vigencia`.
  **Extender** una reserva ACTIVA fija `venceEn = ahora + horas` (1–168); el total desde `creadaEn` no puede superar 7 días
  (422).
- **El estado manda**: una reserva ACTIVA se puede convertir, cancelar o extender aunque `venceEn` ya haya pasado y la
  tarea aún no la haya procesado. Toda transición bloquea la fila de la reserva (`PESSIMISTIC_WRITE`) y verifica que siga
  ACTIVA; si no, 409 "La reserva R-000012 ya está CONVERTIDA."
- **Cancelar**: motivo obligatorio (5–200). Movimiento LIBERACION con el usuario y referencia `RESERVA/{id}`.
- **Vencimiento automático**: tarea `@Scheduled` cada 15 min (configurable, se apaga en pruebas y en el perfil `seed`). Busca
  reservas ACTIVAS con `venceEn ≤ ahora` (hora del `Clock`, America/Bogota) y procesa **cada una en su propia
  transacción**: si sigue ACTIVA, movimiento LIBERACION con `usuarioId` null y estado VENCIDA; si no, la omite
  (idempotente). Un fallo se registra en el log y se reintenta en el ciclo siguiente.
- **Anonimización (Ley 1581)**: tarea nocturna (02:30 America/Bogota) que reemplaza nombre y contacto del cliente por
  `[anonimizado]` en reservas cerradas (CANCELADA, VENCIDA, CONVERTIDA) hace 90 días o más (`app.reservas.anonimizacion.dias`,
  configurable). Idempotente; no genera movimientos ni toca stock; las ACTIVAS nunca se anonimizan.
- **Convertir en venta**: una o varias reservas ACTIVAS (máx. 20, ids sin repetir) **del mismo cliente** (contacto
  normalizado igual; si no, 422) → **una venta**. Las líneas se agrupan por variante (cantidad sumada); un movimiento
  VENTA_RESERVA por línea con referencia `VENTA/{id}`. `precioLista` = `precioVenta` del producto **al convertir**;
  `precioUnitario` opcional por variante (`0 < precio ≤ lista`; permite respetar un precio cotizado menor);
  `costoUnitario` = `precioCompra` al convertir. El vendedor de la venta es quien convierte. Cada reserva queda CONVERTIDA
  con `ventaId`. Se bloquean las reservas en orden de id y se procesan las variantes en orden de id.
- **Roles**: crear, cancelar y extender: ADMIN, INVENTARIO y VENDEDOR (cualquiera, no solo quien la creó; queda
  `cerradaPor`). Convertir: ADMIN y VENDEDOR (crea una venta).
- Un AJUSTE_NEGATIVO no puede consumir unidades reservadas (requiere `cantidad ≤ disponible`): primero se cancela la reserva.

## Alertas (ADR-007, ADR-009)

| Tipo | Condición | Fase |
|---|---|---|
| AGOTADO | `disponible == 0` | 3 (por evento) |
| SIN_ROTACION | el **producto** no tiene ventas no anuladas en 60 días, existe hace ≥ 60 días y la variante tiene `disponible > 0` | 5 (por evento y nocturna) |
| STOCK_BAJO | `0 < disponible` y `posición ≤ umbral` (`puntoReorden`, o `stockMinimo` si no hay ROP calculado); `posición = disponible + en camino` | 3 (por evento); posición desde la 6 |

- **Las tres son excluyentes**, con prioridad **AGOTADO > SIN_ROTACION > STOCK_BAJO** (ADR-009): una variante tiene como
  máximo una alerta ACTIVA. Al cambiar el tipo vigente se cierra la anterior y se abre la nueva; si no aplica ninguna, se
  cierran todas. Cerrar = ATENDIDA automática con `atendidaPor` null. AGOTADO coincide con `Variante.estadoStock()`;
  STOCK_BAJO coincide solo si no hay pedido en camino (Fase 6); SIN_ROTACION puede reemplazar a BAJO (el estado de stock
  sigue diciendo BAJO).
- **Posición de inventario** (ADR-010): `en camino` = Σ `cantidadAprobada` de las líneas de planes APROBADO (puerto
  `inventario.servicio.ConsultaPedidosEnCamino`, implementado en `compra`). **AGOTADO sigue sobre el disponible**: con
  pedido en camino no se puede vender, así que la alerta se mantiene. Aprobar o cancelar un plan APROBADO publica
  `PosicionCambiada(varianteId)` por línea; el `AlertaListener` las re-evalúa AFTER_COMMIT. Recibir publica `StockCambiado`.
- **SIN_ROTACION** (ADR-009): la condición es por producto (una venta VENTA o VENTA_RESERVA del kardex sin
  ANULACION_VENTA de la misma referencia cuenta como rotación) y la alerta se abre en cada variante con `disponible > 0`.
  Una venta de cualquier variante cierra la SIN_ROTACION de todas las del producto. **Episodio** = desde
  `max(última venta, creación del producto) + 60 días`: atendida a mano no se reabre dentro del mismo episodio. Días
  configurables (`app.alertas.sin-rotacion-dias`, 60).
- Se evalúan con un `@TransactionalEventListener(AFTER_COMMIT)` sobre `StockCambiado` y sobre `UmbralCambiado` (publicado al
  editar `stockMinimo`), en una transacción nueva (`REQUIRES_NEW`). Un fallo del listener se registra en el log y nunca
  llega al usuario (la operación ya se confirmó). Variantes inactivas no generan alertas y no aparecen en el listado;
  desactivar una variante (o su producto) cierra todas sus alertas ACTIVAS.
- Una sola alerta ACTIVA por variante y tipo, garantizada además por un índice único en la base.
- Atender a mano una AGOTADO o STOCK_BAJO cuya condición sigue vigente la cierra; el siguiente movimiento de esa variante
  o la re-evaluación nocturna vuelve a abrirla.
- **Re-evaluación nocturna** (Fase 5): la ejecución analítica re-evalúa todas las variantes (ver "Analítica"). Calcular el
  ROP no publica `UmbralCambiado`: la re-evaluación completa lo cubre.
- **Métrica de alertas** (ADR-009): cada ejecución analítica guarda el porcentaje de variantes activas con AGOTADO o
  STOCK_BAJO (SIN_ROTACION aparte). Línea base de la Fase 4 con `stockMinimo`: 141 de 299 (≈ 47 %). La meta de ~10–15 %
  se busca en la Fase 6, cuando STOCK_BAJO usa la posición de inventario (disponible + pedidos aprobados en camino). Desde
  entonces se guarda además la métrica **contrafactual** "en alerta si no se contara lo que viene en camino"
  (`en_alerta_sin_transito`) y el número de variantes con pedido en camino, para mostrar el efecto. La definición no
  cambia para llegar al número: AGOTADO ya pesa ~9 %, de modo que la meta queda en ~11–17 % y se explica con el desglose.
- El listener se puede apagar con `app.alertas.por-evento=false` (por defecto `true`). Solo el perfil `seed` lo apaga; el
  generador ejecuta la analítica completa al terminar (ver "Datos semilla" y "Analítica").

## Analítica (ADR-009)

- **Ventas que cuentan**: solo REGISTRADA; las ANULADAS se excluyen de ABC, ROP, más vendidos y SIN_ROTACION.
  - *Demanda comercial* (ABC, más vendidos): unidades netas = vendidas − devueltas (todas), en la fecha de la venta;
    ingreso neto = Σ (cantidad − devuelta) × `precioUnitario`.
  - *Demanda de inventario* (ROP): por día, vendidas (fecha de la venta, incluidas las convertidas de reservas) −
    devueltas **aptas** (fecha de la devolución), recortada a 0. Las reservas ACTIVAS no son demanda.
  - La demanda observada está censurada por los agotados; el pronóstico la corrige (Fase 6, ver "Pronóstico").
- **Clasificación ABC por variante**: ingreso neto de los **últimos 12 meses** (`[corte − 365 d, corte)`, corte = hoy,
  excluye el día en curso). Orden por ingreso descendente (desempate por id); cortes sobre el acumulado: A ≤ 80 %, B ≤ 95 %,
  C el resto; la primera siempre es A; sin ingreso → C. Universo: variantes activas de productos activos; las inactivas
  quedan con `claseAbc` null.
- **Punto de reorden por variante** (ROP híbrido, sin piso): ventana de 90 días de demanda de inventario (días sin ventas
  = 0). Nivel de servicio por clase: A 95 % (z 1,65), B 90 % (z 1,28), C 80 % (z 0,84); sin clase → C. `L = leadTimeDias`.
  - Menos de 30 días de historia → `puntoReorden` null (umbral = `stockMinimo`), método SIN_HISTORIA.
  - Demanda media 0 → ROP 0 (solo AGOTADO), método SIN_DEMANDA.
  - `μL = d̄ × L ≥ 5` → `ROP = ceil(μL + z × s × √L)`, método NORMAL.
  - `μL < 5` → menor `r` con `P(Poisson(μL) ≤ r) ≥` nivel de servicio, método POISSON.
  - `stockMinimo` es solo el respaldo cuando no hay ROP (no es piso); `loteMinimo` no interviene en el ROP (sí en la
    sugerencia de compra).
  - **Desde la Fase 6 (ADR-010, D3 b)**: si el producto tiene pronóstico, `μL` = suma de la tasa diaria pronosticada de la
    variante en los próximos `L` días (fuente `PRONOSTICO`); `s` sigue saliendo de la serie diaria observada de 90
    días; misma regla NORMAL/POISSON y mismos niveles de servicio. Sin pronóstico del producto, fórmula de la Fase 5
    (fuente `HISTORICA`). Queda en `analitica_variante`.
- **Escritura** (regla de oro 1 intacta): `puntoReorden` y `claseAbc` no se actualizan desde la entidad
  (`updatable = false`); solo los escribe un UPDATE dirigido desde `analitica.repositorio`, por variante en orden de id,
  sin tocar `version` ni `actualizadoEn` (el cálculo no provoca 409 a las ventas). Nunca toca stock.
- **Instantánea** `analitica_variante` (insumos del último cálculo por variante, para explicar el ROP) y **registro**
  `ejecucion_analitica` (origen NOCTURNA, MANUAL o SEMILLA; estado EN_CURSO, EXITOSA o FALLIDA; conteos y métrica).
- **Ejecución**: cada noche a las 02:00 America/Bogota (`app.analitica.cron`; apagada en pruebas y en `seed`) y con
  "Recalcular" (solo ADMIN, síncrono). Desde la Fase 6 el primer paso es el pronóstico (transacción propia, todo o nada;
  si falla, la ejecución queda FALLIDA con "El pronóstico falló; el ROP usó la demanda histórica" y el ABC/ROP se aplica
  igual). Pasos: ABC + ROP en una sola transacción (todo o nada) → re-evaluación de las
  alertas de todas las variantes, una transacción por variante (corre aunque falle el paso anterior) → registro. Una
  ejecución a la vez: un recálculo manual con otro en curso → 409; la nocturna se omite.
- **Más vendidos** (REQ-05): ranking top-N (1–50, por defecto 10) por producto o variante, por unidades o ingreso neto,
  rango de fechas inclusivo (por defecto los últimos 30 días, máximo 731) y categoría opcional. Sin costos ni margen.
- **Roles**: más vendidos, los cuatro roles; ABC, reorden y última ejecución: ADMIN, INVENTARIO y CONTABILIDAD;
  recalcular: solo ADMIN. La clase ABC se muestra también en inventario a todos (no revela costos).
- **Tendencias históricas** (REQ-09, Fase 6): serie mensual de la demanda **comercial** (unidades e ingreso netos, como
  "Más vendidos") con el mismo mes del año anterior y su variación; por defecto 24 meses completos, máximo 36; filtros por
  categoría y producto. Roles: ADMIN, INVENTARIO y CONTABILIDAD. La exportación PDF (CU-05) va con los reportes de la Fase 7.

## Pronóstico (ADR-010)

- **Serie**: mensual por producto, de la *demanda de inventario* (vendidas − devueltas aptas; REGISTRADA; recorte a 0 por
  mes), solo meses completos anteriores al mes en curso, máximo 36. Se pronostican el mes actual y los dos siguientes.
  Productos inactivos no se pronostican.
- **Corrección por agotados**: `corregida = observada / max(disponibilidad, 0,5)`, con `disponibilidad` = fracción de días
  con stock de las variantes activas ponderada por su participación, reconstruida desde el kardex (día sin stock: abre en 0
  y ningún movimiento lo deja > 0). Se guardan ambas series.
- **Método**: SES u **Holt-Winters multiplicativo** (m = 12), elegido por backtesting con origen móvil (cortes n − 6 y
  n − 3, 3 meses cada uno, WAPE sobre 6 puntos; empate < 0,001 → SES). HW solo compite con n ≥ 30 meses, sin ningún mes
  en 0 y con nivel positivo; si no, SES y se guarda el motivo (`POCA_HISTORIA`, `MES_EN_CERO`, `NIVEL_NO_POSITIVO`).
  Menos de 3 meses → SIN_HISTORIA (sin pronóstico; ROP y sugerencia usan la demanda histórica); 3–8 meses → SES sin backtest; 9–29 meses → SES con backtest (WAPE) y motivo `POCA_HISTORIA`.
- **Desagregación**: el pronóstico del producto se reparte a sus variantes activas por su participación de 180 días,
  corregida por días con stock. Se guardan filas por producto (`variante_id` null) y por variante.
- **Regla de oro 1 intacta**: el pronóstico nunca toca stock. Las filas de `pronostico` se insertan en orden ascendente
  de `variante_id` (mismo orden en que bloquean las ventas). Solo se conserva la última ejecución.
- **Roles**: ver pronóstico y tendencias: ADMIN, INVENTARIO y CONTABILIDAD; VENDEDOR 403.
- Riesgo aceptado: el backtest (abril–septiembre con corte en octubre) no cubre noviembre–diciembre; el WAPE puede
  subestimar el error en los picos.

## Plan de compras (ADR-010)

- **Ciclo de vida**: ver "Máquinas de estado". Número visible `PC-000012`; `@Version` en el plan: editar o aprobar con
  versión vieja → 409. Orden de bloqueo: plan → variantes (por id). Sin proveedor ni borrado físico; un plan puede mezclar
  productos. Máximo 300 líneas; variante repetida → 400.
- **Crear**: *sugerido* (origen SUGERIDO) o *manual*; ambos quedan en BORRADOR, editables. Aprobar exige variantes y
  productos activos (422 con el SKU).
- **Sugerencia (s, S)**: candidatas = variantes activas de productos activos con `posición ≤ umbral` que no estén en otro
  BORRADOR (las omitidas se informan). Cantidad hasta `S = umbral + demanda pronosticada de R = 30 días posteriores al
  lead time` (`app.compras.periodo-revision-dias`), redondeada hacia arriba al múltiplo de `loteMinimo`; las de cantidad 0
  no entran. Sin ninguna candidata → 422. Cada línea lleva una justificación legible. Nunca toca stock. Sin ROP y sin
  demanda pronosticada ni histórica, el respaldo es `S = 2 × stockMinimo`. Con más de 300 candidatas entran 300 por
  prioridad: agotadas primero, luego clase A, B, C y sin clase, luego mayor faltante relativo
  `(umbral − posición) / max(umbral, 1)` y por último el id; el plan informa `excluidasPorTope`.
- **Recibir** (recepción única): por línea, cantidad recibida (0–100.000, por defecto la aprobada) y costo. Una ENTRADA
  por línea con cantidad > 0, vía `OperacionInventarioService` → `MovimientoService`, referencia `PLAN_COMPRA/{id}`, con las
  reglas de entradas (mismo costo por producto, `precioCompra` = último costo > 0). Lo no recibido deja de estar en
  camino. Todas las líneas en 0 → 422 ("use Cancelar"). Se permite sobre variantes inactivas.
- **Cancelar**: motivo obligatorio (5–200). Un APROBADO cancelado deja de estar en camino y puede reabrir alertas.
- **Llegada esperada** (calculada): `aprobadoEn + max(leadTimeDias)`; la interfaz marca "atrasado" si ya pasó.
- **Roles**: crear y editar BORRADOR: ADMIN e INVENTARIO. Aprobar: solo ADMIN. Cancelar: ADMIN (BORRADOR o APROBADO);
  INVENTARIO solo descarta BORRADOR (403 sobre APROBADO). Recibir: ADMIN e INVENTARIO. Ver: ADMIN, INVENTARIO y
  CONTABILIDAD (con costos). VENDEDOR: 403.
- **Módulos**: `compra` depende de `catalogo`, `inventario` y `usuario`; usa dos puertos:
  `inventario.servicio.ConsultaPedidosEnCamino` (implementado en `compra`) y `compra.servicio.FuenteSugerenciaCompra`
  (implementado en `analitica`). `compra` no depende de `venta` ni de `analitica`.

## Datos semilla (ADR-008)

- **Propósito**: 36 meses de historia de demostración (35 completos al cerrar; Holt-Winters compite desde 30 meses,
  ADR-010) para la analítica (ABC, ROP, pronóstico) y la sustentación. No son datos de referencia: no van en migraciones.
- **Módulo `semilla`** (`co.livorno.controlstock.semilla`): todas sus clases llevan `@Profile("seed")` y ningún otro módulo
  depende de él (ArchUnit). Se ejecuta con los perfiles `dev,seed`, carga y **termina la aplicación** (código 0 si todo
  salió bien, 1 si falló). Después se arranca normal con `dev`.
- **Solo servicios reales**: catálogo, entradas, ajustes, ventas, devoluciones, anulaciones y reservas pasan por los mismos
  servicios que usa la API; el stock cambia solo vía `MovimientoService`. Nada de inserts directos de stock ni de kardex.
  El generador fija el usuario que actúa en el contexto de seguridad (VENDEDOR vende, devuelve y convierte; INVENTARIO
  recibe pedidos, ajusta y reserva; ADMIN anula y aprueba planes); los vencimientos usan `ReservaService.vencer(id, ahora)`
  con `usuarioId` null.
- **Reposición por planes de compra** (ADR-010, D8): la política de pedido es propia de la semilla (punto de pedido =
  demanda esperada del lead time + 7 días; cantidad = 30–60 días de demanda; no usa el ROP ni la sugerencia del
  sistema). Cada pedido es un plan por producto y día: INVENTARIO lo crea, ADMIN lo aprueba a la misma hora e INVENTARIO
  lo recibe al llegar (la ENTRADA lleva referencia `PLAN_COMPRA/{id}`). Los pedidos que llegan después de `fin` quedan
  APROBADOS (en camino). El verificador comprueba que Σ cantidad aprobada de los planes APROBADO por variante coincida
  con la simulación; el resumen imprime planes creados, recibidos y en camino y las métricas de la analítica de cierre
  (productos HW/SES, WAPE global, % en alerta con y sin lo en camino).
- **Reloj simulado (solo en `seed`)**: un `Clock` ajustable marcado `@Primary` reemplaza al del sistema; los servicios y la
  auditoría fechan con él. Solo avanza (retroceder lanza excepción), así el kardex queda cronológico, y vuelve al reloj
  del sistema al terminar. Las operaciones de cada día simulado se ejecutan en orden de hora.
- **Todo o nada**: una sola transacción para toda la carga (con `flush` y `clear` por día simulado). Cualquier error de
  negocio revierte todo y deja la base como estaba; el generador valida el disponible antes de vender.
- **Protecciones e idempotencia**: si ya hay ventas, registra "ya hay datos" y no hace nada; si hay productos o
  movimientos pero no ventas, aborta con un mensaje claro; no corre con un perfil `prod` activo. Para regenerar se borra la
  base (`docker compose down -v`); no hay borrado en código (el kardex es inmutable).
- **Usuarios demo**: `admin@livorno.co`, `inventario@livorno.co`, `ventas@livorno.co`, `contabilidad@livorno.co`, uno por
  rol, con la contraseña de la variable de entorno `SEED_PASSWORD` (obligatoria; misma política de contraseñas). No quedan
  con cambio de contraseña obligatorio. Si el correo ya existe, se reutiliza. Conviven con el ADMIN inicial de V2.
- **Determinismo**: semilla aleatoria fija (42 por defecto) y fecha final configurable (`app.semilla.fecha-fin`, anterior a hoy;
  vacía = ayer). Con la misma semilla, la misma fecha final y una base vacía, el resultado es el mismo.
- **Datos de cliente** de las reservas: ficticios. Las reservas ACTIVAS se crean en las últimas 48 h de la historia con
  vigencia de 72 a 168 h; conviene regenerar la víspera de la sustentación.
- **Verificación al terminar**: stock = suma del kardex por variante, reservado = suma de reservas ACTIVAS, ningún stock
  negativo; si falla, la carga se revierte.
- **Analítica al cerrar** (ADR-009): tras el commit de la carga se ejecuta la analítica completa con el reloj real (origen
  SEMILLA): la demo arranca con clase ABC, ROP y alertas coherentes.

## Roles y permisos

| Funcionalidad | ADMIN | INVENTARIO | VENDEDOR | CONTABILIDAD |
|---|---|---|---|---|
| Usuarios y roles | CRUD | — | — | — |
| Mi cuenta (cambiar mi contraseña) | Sí | Sí | Sí | Sí |
| Productos / variantes | CRUD | CRUD | Ver | Ver |
| Entradas y ajustes | Sí | Sí | — | — |
| Ventas y devoluciones | Sí | — | Sí | Ver |
| Reservas (crear, cancelar, extender) | Sí | Sí | Sí | — |
| Convertir reservas en venta | Sí | — | Sí | — |
| Kardex | Ver | Ver | — | Ver |
| Alertas | Ver / atender | Ver / atender | Ver | — |
| Analítica (ABC, ROP, pronóstico, tendencias) | Sí | Ver | Más vendidos | Ver |
| Plan de compras | CRUD / aprobar / recibir | Crear y editar borrador / recibir | — | Ver |
| Reportes contables / rentabilidad | Sí | — | — | Sí |

Notas de la matriz (Fase 2): la consulta de inventario actual (stock por variante, sin costos) está abierta a todo
usuario autenticado porque el VENDEDOR la usa para buscar al vender. Anular ventas: solo ADMIN. Los costos (`precioCompra`,
`costoUnitario` de ventas y entradas) no se exponen al VENDEDOR.

Notas de la matriz (Fase 3): convertir reservas crea una venta, por eso INVENTARIO no convierte aunque gestione reservas.
Atender alertas: ADMIN e INVENTARIO; VENDEDOR solo las ve (incluida la campana).

Notas de la matriz (Fase 5): en Analítica, "Sí" del ADMIN incluye recalcular; INVENTARIO y CONTABILIDAD ven ABC, reorden y
la última ejecución; VENDEDOR solo ve más vendidos (sin costos). La clase ABC se muestra en inventario a todos los roles.

Notas de la matriz (Fase 6): en Plan de compras, INVENTARIO crea y edita borradores, descarta borradores y recibe pedidos
(la recepción es una entrada); aprobar y cancelar un plan aprobado: solo ADMIN. CONTABILIDAD ve planes con costos, sin
escribir. Tendencias y pronóstico: ADMIN, INVENTARIO y CONTABILIDAD; VENDEDOR solo "Más vendidos" (403 en compras,
tendencias y pronósticos).

## Trazabilidad con el documento PPI

| Requisito PPI | Módulo |
|---|---|
| REQ-01 Visualizar stock y atributos en tiempo real (CU-04) | catalogo, inventario |
| REQ-02 Registrar automáticamente operaciones de stock (CU-03) | inventario |
| REQ-03 Reportes de rentabilidad | reporte |
| REQ-04 Emitir alertas de nivel mínimo de stock (CU-10) | inventario (alertas) + analitica (ROP) |
| REQ-05 Identificar Productos de Mayor Venta (CU-08) | analitica |
| REQ-06 Controlar el acceso y roles de usuario (CU-02) | auth, usuario |
| REQ-07 Reportes contables | reporte |
| REQ-08 Gestionar el registro de devoluciones (CU-09) | venta |
| REQ-09 Tendencias históricas (CU-05; Fase 6, PDF en Fase 7) | analitica |
| REQ-10 Planeación de compras (CU-16, CU-17, CU-18 nuevos; Fase 6, ADR-010) | compra + analitica (pronóstico, sugerencia) + inventario (posición, recepción) |
| REQ-11 Gestión de productos (CU-11) | catalogo |
| REQ-12 Registrar y gestionar usuarios (CU-12) | usuario |
| REQ-13 Gestionar apartados (CU-14, nuevo) | inventario (reservas), venta (conversión) |
| REQ-14 Clasificación ABC (CU-15, nuevo) | analitica |

Nota: en el documento PPI, REQ-08 y REQ-09 tienen nombre y descripción cruzados; corregir en la próxima versión.

### Convención de códigos

- `REQ-xx`: requisitos funcionales (tabla anterior). `RNF-xx`: requisitos no funcionales del documento Word, que allí
  aparecen numerados como REQ-01..REQ-05; aquí se renombran a RNF-01..RNF-05 para no chocar con los funcionales.
- `CU-xx`: casos de uso. Nuevos, no presentes en el Word: CU-13 "Iniciar sesión" (fase 0, ligado a RNF-02), CU-14
  "Gestionar apartados" (fase 3, REQ-13), CU-15 "Clasificar productos (ABC)" (fase 5, REQ-14), CU-16 "Consultar el
  pronóstico de demanda", CU-17 "Gestionar el plan de compras" y CU-18 "Recibir un pedido" (fase 6, REQ-10). Las
  tendencias históricas (REQ-09) usan el CU-05 del Word; su exportación a PDF es de la fase 7.
- `NEC-xx`: necesidades del Word, con dos dígitos (NEC-010 del Word es NEC-10). Nueva: NEC-11 "Apartar prendas para
  clientes que reservan por redes sociales" (REQ-13).

| Requisito no funcional | Stakeholder | Necesidades | Prioridad | Área principal |
|---|---|---|---|---|
| RNF-01 Disponibilidad en el sistema | Gerencia | — | Alta | infraestructura (Docker Compose, `/actuator/health`) |
| RNF-02 Seguridad y control | Gerencia | NEC-06 | Alta | auth, usuario, comun (CU-13 Iniciar sesión) |
| RNF-03 Rendimiento de consultas | Contabilidad | NEC-01, NEC-03 | Media | reporte, analitica, inventario (consultas e índices) |
| RNF-04 Usabilidad de la interfaz | Vendedores | NEC-02, NEC-05 | Media | frontend |
| RNF-05 Escalabilidad del sistema | Junta directiva | NEC-09, NEC-10 | Alta | transversal |
