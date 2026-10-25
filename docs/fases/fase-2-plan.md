# Plan APROBADO — Fase 2 (Operación)

Rama `fase-2-operacion`. Requisitos: REQ-02 (registrar operaciones de stock), REQ-01 (inventario actual y kardex),
REQ-08 (devoluciones). Reglas: `docs/dominio.md` secciones "Tipos de movimiento", "Entradas, ventas y costos",
"Máquinas de estado" y "Devoluciones". ADR-006.

**Regla de oro 1:** el stock solo cambia en `MovimientoService.registrar(...)`, que es el único llamador de
`Variante.aplicarMovimiento(deltaFisico, deltaReservado)`. Lo hace cumplir una prueba ArchUnit.

## Decisiones confirmadas por el usuario
- `Variante.aplicarMovimiento(int deltaFisico, int deltaReservado)` público, valida invariantes (físico ≥ 0, reservado ≥ 0,
  reservado ≤ físico → `IllegalStateException`). Regla ArchUnit: ninguna clase distinta de
  `co.livorno.controlstock.inventario.servicio.MovimientoService` lo invoca (código de producción, sin pruebas).
- Enum `TipoMovimiento` con los 9 tipos y su efecto (ENTRADA, VENTA, VENTA_RESERVA, DEVOLUCION, AJUSTE_POSITIVO,
  AJUSTE_NEGATIVO, RESERVA, LIBERACION, ANULACION_VENTA). La Fase 2 expone ENTRADA, AJUSTE_±, VENTA, DEVOLUCION y
  ANULACION_VENTA; RESERVA, LIBERACION y VENTA_RESERVA se prueban solo en el servicio (endpoints en la Fase 3).
- Concurrencia: `@Version` + 409 sin reintento; variantes procesadas en orden de id; `hibernate.order_updates=true`;
  `PessimisticLockingFailureException` → 409. Anular y devolver bloquean la fila de la venta (`PESSIMISTIC_WRITE`).
- `MovimientoService` recibe `usuarioId` explícito (null = sistema) y toma la fecha del `Clock`. Valida stock, observación
  de ajustes y costo de entradas; la actividad de variante/producto la valida quien llama.
- Venta: `precioUnitario` opcional ≤ `precioLista`; `DetalleVenta` guarda `precioLista`, `precioUnitario`, `costoUnitario`.
  Número `V-000123` derivado del id. `canal` nulo y no expuesto.
- Entradas con `costoUnitario` por línea; actualizan `precioCompra` del producto (último costo > 0; un costo 0 se guarda pero no lo cambia). Mismo costo para todas las
  líneas de un producto dentro de una entrada (422).
- Anulación: solo ADMIN, motivo, ANULACION_VENTA por línea; ANULADA → 409; con devoluciones → 422. Ventas anuladas fuera
  de demanda, ABC, pronóstico y reportes (Fases 5–7). Anular una venta de reserva no reactiva la reserva (Fase 3).
- Alertas: solo se publica `StockCambiado(varianteId)`; el listener llega en la Fase 3.
- Kardex inmutable sin triggers: `@Immutable`, sin setters, repositorio basado en `Repository`.

## Paso manual del usuario (antes de cerrar B1)
B1 agrega al `pom.xml` (scope `test`) `com.tngtech.archunit:archunit-junit5:1.5.1` (
versión verificada en Maven Central el 2026-10-02). El entorno de compilación automatizado trabaja sin red: el usuario ejecuta en su terminal, dentro
de `backend`, `$env:MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"; .\mvnw.cmd test-compile` y avisa. Hasta
entonces B1 no puede compilar las pruebas; B2 puede avanzar en paralelo sin la regla.

## Migraciones (InnoDB, utf8mb4, collation utf8mb4_0900_ai_ci, DATETIME(6))
- `V8__crear_tabla_movimiento.sql`: `movimiento(id BIGINT AI PK, variante_id BIGINT NOT NULL FK → variante,
  tipo VARCHAR(20) NOT NULL, cantidad INT NOT NULL, fisico_antes INT NOT NULL, fisico_despues INT NOT NULL,
  reservado_antes INT NOT NULL, reservado_despues INT NOT NULL, costo_unitario DECIMAL(12,2) NULL,
  usuario_id BIGINT NULL FK → usuario, fecha DATETIME(6) NOT NULL, referencia_tipo VARCHAR(20) NULL,
  referencia_id BIGINT NULL, observacion VARCHAR(255) NULL)`.
  CHECK: `tipo IN (9 tipos)`, `cantidad > 0`, los cuatro saldos `>= 0`, `reservado_despues <= fisico_despues`,
  `(tipo = 'ENTRADA') = (costo_unitario IS NOT NULL)`, `costo_unitario IS NULL OR costo_unitario >= 0`,
  `(referencia_tipo IS NULL) = (referencia_id IS NULL)`.
  Índices: `idx_movimiento_variante_fecha(variante_id, fecha, id)` (cubre la FK), `idx_movimiento_fecha(fecha)`,
  `idx_movimiento_tipo_fecha(tipo, fecha)`, `idx_movimiento_referencia(referencia_tipo, referencia_id)`.
- `V9__crear_tablas_venta.sql`: `venta(id, fecha DATETIME(6) NOT NULL, usuario_id BIGINT NOT NULL FK → usuario,
  canal VARCHAR(20) NULL, total DECIMAL(14,2) NOT NULL, estado VARCHAR(12) NOT NULL, anulada_en DATETIME(6) NULL,
  anulada_por BIGINT NULL FK → usuario, motivo_anulacion VARCHAR(255) NULL)`. CHECK: `total >= 0`,
  `estado IN ('REGISTRADA','ANULADA')`, `(estado = 'ANULADA') = (anulada_en IS NOT NULL)`. Índices:
  `idx_venta_fecha(fecha)`, `idx_venta_estado_fecha(estado, fecha)`.
  `detalle_venta(id, venta_id BIGINT NOT NULL FK → venta, variante_id BIGINT NOT NULL FK → variante, cantidad INT NOT NULL,
  precio_lista DECIMAL(12,2) NOT NULL, precio_unitario DECIMAL(12,2) NOT NULL, costo_unitario DECIMAL(12,2) NOT NULL)`.
  CHECK: `cantidad > 0`, `precio_lista > 0`, `precio_unitario > 0`, `precio_unitario <= precio_lista`,
  `costo_unitario >= 0`. UNIQUE `uk_detalle_venta_variante(venta_id, variante_id)`; índice `idx_detalle_venta_variante(variante_id)`.
- `V10__crear_tabla_devolucion.sql`: `devolucion(id, detalle_venta_id BIGINT NOT NULL FK → detalle_venta,
  cantidad INT NOT NULL CHECK > 0, motivo VARCHAR(255) NOT NULL, apta_para_venta BOOLEAN NOT NULL, fecha DATETIME(6) NOT NULL,
  usuario_id BIGINT NOT NULL FK → usuario)`. Índice `idx_devolucion_fecha(fecha)`.

## Contrato de la API (fijo: backend y frontend avanzan en paralelo)

Convenciones de la Fase 1 (página VIA_DTO, dinero con 2 decimales, fechas ISO-8601 locales, `ProblemDetail`, 400/404/
409/422). Roles: **Auth** autenticado; **CR** `hasAnyRole('ADMIN','INVENTARIO')`; **KX** `hasAnyRole('ADMIN','INVENTARIO',
'CONTABILIDAD')`; **VT** `hasAnyRole('ADMIN','VENDEDOR')`; **VV** `hasAnyRole('ADMIN','VENDEDOR','CONTABILIDAD')`;
**AD** `hasRole('ADMIN')`.
- `TipoMovimiento` (string): los 9 tipos. `EstadoVenta = "REGISTRADA" | "ANULADA"`.
- Fechas de filtro `desde`/`hasta`: `LocalDate` ISO (`2026-10-02`), ambas inclusivas → `[desde 00:00, hasta+1 00:00)`.
  `desde > hasta` → 400 "La fecha inicial no puede ser posterior a la final."
- 409 de concurrencia: título "Conflicto de concurrencia", detail "Los datos cambiaron mientras registrabas la operación.
  Revisa y vuelve a confirmar."
- 422 de stock: "Stock insuficiente para {sku}: disponible {d}, solicitado {q}."

### Inventario
| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/inventario?q&categoriaId&estadoStock&incluirInactivas=false&page&size&sort` | Auth | — | 200 `Page<InventarioItemResponse>` |
| `POST /api/inventario/entradas` | CR | `EntradaRequest` | 201 `MovimientoResponse[]`; 400; 404; 422 |
| `POST /api/inventario/ajustes` | CR | `AjusteRequest` | 201 `MovimientoResponse`; 404; 422 |

- `q` (máx. 60) busca en SKU, código o nombre del producto (contiene, `%` y `_` escapados). `estadoStock` filtra en JPQL
  con el mismo umbral que `Variante.estadoStock()`. Orden permitido: `sku`, `producto.nombre`, `stockFisico`
  (por defecto `producto.nombre,asc` y `sku,asc`). Con `incluirInactivas=false` excluye variantes inactivas.
- `InventarioItemResponse { varianteId, sku, productoId, productoCodigo, productoNombre, categoria: CategoriaRef, talla,
  color, stockFisico, stockReservado, stockDisponible, stockMinimo, puntoReorden, estadoStock, precioVenta, activo }`
  (sin costos).
- `EntradaRequest { observacion?: @Size(max=255), lineas: @NotEmpty @Size(max=100) @Valid LineaEntradaRequest[] }`;
  `LineaEntradaRequest { varianteId: @NotNull, cantidad: @NotNull @Min(1) @Max(100000),
  costoUnitario: @NotNull @DecimalMin("0.00") @Digits(10,2) }`.
  400 "La variante {id} está repetida en la entrada." 422: variante o producto inactivo ("La variante {sku} está
  inactiva."); costos distintos del mismo producto ("Las variantes de {codigo} deben tener el mismo costo en una
  entrada."). Efecto: un ENTRADA por línea (referencia nula, `observacion` común) y `precioCompra` del producto = costo.
- `AjusteRequest { varianteId: @NotNull, tipo: @NotNull (AJUSTE_POSITIVO|AJUSTE_NEGATIVO; otro → 400),
  cantidad: @NotNull @Min(1) @Max(100000), observacion: @NotBlank @Size(min=5, max=255) }`. 422 si negativo > disponible.
- `MovimientoResponse { id, fecha, tipo, cantidad, fisicoAntes, fisicoDespues, reservadoAntes, reservadoDespues,
  costoUnitario (null salvo ENTRADA), varianteId, sku, productoNombre, usuarioNombre (null = sistema), referenciaTipo,
  referenciaId, observacion }`.

### Kardex
| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/movimientos?varianteId&tipo&desde&hasta&page&size` | KX | — | 200 `Page<MovimientoResponse>`; 400 |

- Orden fijo `fecha desc, id desc` (parámetro `sort` ignorado). `varianteId` inexistente → página vacía.
- Una sola consulta con `join fetch` de variante, producto y usuario (sin N+1).

### Ventas y devoluciones
| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `POST /api/ventas` | VT | `RegistrarVentaRequest` | 201 `VentaDetalleResponse`; 400; 404; 409; 422 |
| `GET /api/ventas?desde&hasta&estado&page&size&sort` | VV | — | 200 `Page<VentaResumenResponse>` |
| `GET /api/ventas/{id}` | VV | — | 200 `VentaDetalleResponse`; 404 |
| `POST /api/ventas/{id}/anular` | AD | `AnularVentaRequest` | 200 `VentaDetalleResponse`; 404; 409; 422 |
| `POST /api/ventas/{id}/devoluciones` | VT | `DevolucionRequest` | 201 `DevolucionResponse`; 404; 409; 422 |

- `RegistrarVentaRequest { lineas: @NotEmpty @Size(max=50) @Valid LineaVentaRequest[] }`;
  `LineaVentaRequest { varianteId: @NotNull, cantidad: @NotNull @Min(1) @Max(10000),
  precioUnitario?: @DecimalMin(value="0.00", inclusive=false) @Digits(10,2) }`.
  400 variante repetida. 422: inactiva; stock insuficiente; "El precio de {sku} no puede superar el de lista ({lista})."
  Efecto: venta REGISTRADA + un VENTA por línea con referencia `VENTA/{id}`; todo o nada.
- `VentaResumenResponse { id, numero, fecha, vendedor (nombre), unidades, total, estado }`. Orden permitido: `fecha`,
  `total` (por defecto `fecha,desc`).
- `VentaDetalleResponse { id, numero, fecha, vendedor, estado, total, descuentoTotal, anuladaEn, anuladaPor (nombre),
  motivoAnulacion, lineas: LineaVentaResponse[] }`;
  `LineaVentaResponse { detalleId, varianteId, sku, productoNombre, talla, color, cantidad, precioLista, precioUnitario,
  costoUnitario (null para VENDEDOR), subtotal, cantidadDevuelta, devoluciones: DevolucionResponse[] }`.
- `AnularVentaRequest { motivo: @NotBlank @Size(min=5, max=255) }`. 409 "La venta {numero} ya está anulada." 422
  "La venta {numero} tiene devoluciones; corrige el stock con ajustes." Efecto: ANULACION_VENTA por línea (referencia
  `VENTA/{id}`, observación = motivo), estado ANULADA, `anuladaEn`, `anuladaPor`.
- `DevolucionRequest { detalleVentaId: @NotNull, cantidad: @NotNull @Min(1), motivo: @NotBlank @Size(min=5, max=255),
  aptaParaVenta: @NotNull }`. 404 si el detalle no pertenece a la venta. 422: venta anulada; "Solo quedan {n} unidades
  por devolver de {sku}." Efecto: devolución + (si apta) DEVOLUCION con referencia `DEVOLUCION/{id}`.
- `DevolucionResponse { id, detalleVentaId, cantidad, motivo, aptaParaVenta, fecha, usuarioNombre }`.

### Infraestructura común
- `ManejadorGlobalExcepciones`: `PessimisticLockingFailureException` (incluye `CannotAcquireLockException`) → 409 con el
  mismo mensaje; el 409 optimista cambia su detail al de esta fase.
- `application.yml`: `spring.jpa.properties.hibernate.order_updates: true`.

## Frontend
- Tipos: `tipos/inventario.ts` (`InventarioItemResponse`, `MovimientoResponse`, `TipoMovimiento`, requests de entrada y
  ajuste), `tipos/ventas.ts`.
- API y hooks: `api/{inventario,movimientos,ventas}.ts`; claves `['inventario', filtros]`, `['movimientos', filtros]`,
  `['ventas', filtros]`, `['venta', id]`. Toda mutación invalida `inventario`, `movimientos`, `matriz`, `variantes`,
  `productos` y, si aplica, `ventas`/`venta`.
- `lib/permisos.ts`: `puedeMoverStock` (CR), `puedeVender` (VT), `puedeAnularVenta` (AD), `puedeVerKardex` (KX),
  `puedeVerCostos` (todos menos VENDEDOR).
- Componentes de dominio: `BuscadorVariante` (debounce 300 ms sobre `/api/inventario`, muestra SKU, disponible y precio),
  `MatrizCantidades` (inputs de cantidad por talla × color de un producto, costo único por producto prellenado con
  `precioCompra`), `BadgeTipoMovimiento`, `CarritoVenta`, `ModalAjuste`, `ModalDevolucion`, `ModalAnularVenta`.
- Páginas: `/inventario` (tabla con filtros q, categoría, estado, inactivas; acciones CR "Entrada" y "Ajuste"; enlace
  al kardex para KX), `/inventario/entradas/nueva` (CR), `/inventario/kardex?varianteId=` (KX), `/ventas` (VV, botón
  "Nueva venta" para VT), `/ventas/nueva` (VT), `/ventas/:id` (VV; devolver para VT; anular para AD).
- Ante 409 de concurrencia el formulario no se limpia y se muestra el mensaje con "Volver a confirmar".
- `App.tsx`: rutas reales de inventario y ventas; `ProximamentePage` queda para el resto.

## Tareas (cada una cierra con un commit y pruebas en verde)
Paso 0 (coordinación): copiar este plan, ADR-006, `dominio.md` y `docs/especificaciones/reglas-inventario.md`.

Backend:
- B1 `Variante.aplicarMovimiento` + pruebas de invariantes + ArchUnit en el `pom.xml` + `ReglasArquitecturaTest`.
  **Requiere el paso manual del usuario** (`.\mvnw.cmd test-compile`).
- B2 V8 + `TipoMovimiento` (efectos) + `Movimiento` (`@Immutable`, fábrica) + `MovimientoRepository` (`Repository`).
  `@DataJpaTest`: V1–V8 con `validate` y CHECK.
- B3 `MovimientoService` + `SolicitudMovimiento` + `StockCambiado` + 409 pesimista + `order_updates`. Mockito por tipo.
- B4 Inventario actual: consulta + `GET /api/inventario`. (paralelo con B5–B7 tras B3)
- B5 Entradas (costo, `precioCompra`, mismo costo por producto) y ajustes.
- B6 Kardex: consulta filtrada + `GET /api/movimientos`.
- B7 V9 + `Venta` (agregado con detalles en cascada, `anular(...)`) + `DetalleVenta` + repositorio (con `bloquear(id)`).
  Puede empezar tras B2.
- B8 `VentaService.registrar` + listar + detalle (costo null para VENDEDOR) + controlador.
- B9 Anulación.
- B10 V10 + `Devolucion` + `DevolucionService` + endpoint.

Frontend, en paralelo con `vi.mock` de `api/*`:
- F1 Tipos, API, hooks y permisos. F2 `InventarioPage`. F3 `BuscadorVariante`. F4 `MatrizCantidades` + página de entradas.
- F5 `ModalAjuste`. F6 Kardex + `BadgeTipoMovimiento`. F7 Nueva venta (`CarritoVenta`). F8 Listado y detalle de ventas.
- F9 `ModalDevolucion` y `ModalAnularVenta`. F10 Rutas en `App.tsx` y prueba de navegación por rol.

Pruebas:
- T1 (tras B3) Concurrencia y consistencia del stock con Testcontainers.
- T2 (tras B10) Integración de punta a punta y matriz de roles.
- T3 Prueba manual con el backend real.

Documentación (al final): fichas REQ-02 y REQ-08, actualización de REQ-01, diagrama de secuencia
"registrar venta" y diagrama de clases de inventario y ventas.

Recorte si falta tiempo (en este orden): precio editable en la UI (el backend lo soporta), entrada por matriz (una línea
por vez con `BuscadorVariante`), filtros de tipo y fechas del kardex.

## Pruebas
- Puras: efecto de los 9 tipos; `aplicarMovimiento` rechaza físico < 0, reservado < 0 y reservado > físico; ArchUnit.
- Mockito `MovimientoService`: antes/después por tipo; VENTA, RESERVA y AJUSTE_NEGATIVO > disponible → 422 sin `save`;
  LIBERACION y VENTA_RESERVA > reservado → 422; ajuste sin observación → 422; ENTRADA sin costo → 422; evento publicado
  una vez; `usuarioId` null aceptado; fecha del `Clock` fijo.
- Entradas: actualiza `precioCompra` (costo 0 no lo cambia); costos distintos del mismo producto → 422; repetida → 400; inactiva → 422.
- Ventas: precio null toma la lista; > lista 422; `costoUnitario` = `precioCompra`; `precioLista` guardado; total y
  descuento HALF_UP; inactiva 422; fallo en la línea 2 revierte la 1; orden por id. Anular: 409 doble, 422 con
  devoluciones, ANULACION_VENTA por línea. Devolución: suma > vendida 422; apta → DEVOLUCION; no apta → sin movimiento;
  venta anulada 422; detalle de otra venta 404.
- T1 Testcontainers: dos transacciones cargan la variante (stock 1) antes de una barrera y venden 1 → exactamente una
  `OptimisticLockingFailureException`, stock final 0 y un solo movimiento; 10 hilos venden 1 de stock 5 → éxitos ≤ 5,
  stock ≥ 0, sin 500; 200 movimientos aleatorios → stock = suma del kardex y `fisicoAntes(n) = fisicoDespues(n−1)`; dos
  devoluciones no aptas simultáneas de la última unidad pendiente → una 422.
- `@DataJpaTest`: V8–V10; CHECK de costo solo en ENTRADA; consulta de inventario por estado; kardex paginado y filtrado.
- WebMvc por rol: VENDEDOR 403 en entradas, ajustes, movimientos y anular; INVENTARIO 403 en ventas; CONTABILIDAD 200 en
  GET de ventas y movimientos y 403 en POST; `costoUnitario` null para VENDEDOR; 409 con el detail de concurrencia.
- Vitest: carrito (total, repetida suma cantidad, precio > lista), `MatrizCantidades`, filtros, botones por rol, 409 sin
  perder el formulario.

## Riesgos
- ArchUnit sin descargar bloquea B1: el paso manual va primero.
- Deadlocks en ventas de varias líneas: orden por id + `order_updates`; si aparecen → 409.
- Un costo de entrada 0 pone `precioCompra` en 0 y deja margen 100 % en las ventas siguientes.
- Dos entradas simultáneas del mismo producto: gana el último costo confirmado (aceptado).
- Datos semilla (Fase 4) dependen del `Clock` inyectable y del `usuarioId` explícito.
- Fases 5–7 deben filtrar `venta.estado = 'REGISTRADA'`.
