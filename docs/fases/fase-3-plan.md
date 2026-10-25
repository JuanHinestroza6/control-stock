# Plan APROBADO — Fase 3 (Apartados + alertas por evento)

Rama `fase-3-apartados`. Requisitos: "Nuevo: Apartados" (inventario/reservas), REQ-04 (alertas STOCK_BAJO y AGOTADO por
evento), REQ-01 (indicador de reservado). Reglas: `docs/dominio.md` secciones "Reservas (apartados)", "Alertas",
"Máquinas de estado" y matriz de roles. ADR-006 y ADR-007.

**Regla de oro 1:** reservas, liberaciones y conversiones cambian stock solo con `MovimientoService.registrar(...)`.

## Decisiones confirmadas por el usuario (2026-10-02)
- D1 Una variante por reserva. Crear acepta 1–20 líneas del mismo cliente → una reserva ACTIVA y un RESERVA por línea
  (referencia `RESERVA/{id}`), todo o nada. Número visible `R-000012`.
- D2 Convertir: varias reservas ACTIVAS del mismo cliente (contacto normalizado: minúsculas, sin espacios) → UNA venta.
  Líneas agrupadas por variante (cantidad sumada); un VENTA_RESERVA por línea con referencia `VENTA/{id}` y observación
  "Reservas R-…, R-…". `precioLista` = precioVenta al convertir; `precioUnitario` opcional por variante (≤ lista);
  `costoUnitario` = precioCompra al convertir; vendedor = quien convierte. Cada reserva → CONVERTIDA con `ventaId`.
  Anular esa venta: solo ANULACION_VENTA; las reservas siguen CONVERTIDAS.
- D3 El estado manda: una ACTIVA se convierte, cancela o extiende aunque `venceEn` haya pasado. Toda transición bloquea la
  fila (`PESSIMISTIC_WRITE`) y verifica ACTIVA (si no, 409 "La reserva R-000012 ya está CONVERTIDA."). Orden: reserva →
  variante.
- D4 Vencimiento: `@Scheduled(fixedDelayString = "${app.reservas.vencimiento.intervalo:PT15M}", initialDelayString =
  "PT1M")`; ids ACTIVAS con `venceEn <= ahora` (orden venceEn, límite 500); cada una en su transacción con
  `ReservaService.vencer(id, ahora)`: bloquea; si sigue ACTIVA → LIBERACION (`usuarioId` null, referencia `RESERVA/{id}`,
  observación "Vencimiento automático") + VENCIDA; si no, la omite. Fallo → log y siguiente ciclo. `@EnableScheduling` en
  `comun.config.ConfiguracionTareas` con `@ConditionalOnProperty(name = "app.tareas.habilitadas", matchIfMissing = true)`;
  `false` en `application-test.yml`.
- D5 `vigenciaHoras` opcional al crear (1–168; por defecto `app.reservas.vigencia-horas: 48`). Extender: `venceEn = ahora +
  horas` (1–168); si el nuevo `venceEn` > `creadaEn + 7 días` → 422 "Una reserva no puede durar más de 7 días."
- D6 Crear, cancelar y extender: ADMIN, INVENTARIO, VENDEDOR (cualquiera; queda `cerradaPor`). Convertir: ADMIN, VENDEDOR.
- D7 `clienteNombre` 2–120, `clienteContacto` 3–120 (texto libre), espacios normalizados. Sin canal. Motivo de cancelación
  obligatorio 5–200 (observación de la LIBERACION: "Cancelada: {motivo}").
- D8 Desactivación: interfaz `catalogo.servicio.ConsultaReservasActivas` (resumen de apartados activos por variante y
  primera variante con apartados de un producto) implementada en inventario; 409 "La variante {sku} tiene {n} apartados
  activos ({u} unidades)." Se mantiene `stockReservado > 0`. ArchUnit: catalogo no depende de inventario ni de venta;
  inventario no depende de venta.
- D9 Alertas en esta fase: STOCK_BAJO y AGOTADO por evento, listado, atender y campana. **Excluyentes**: AGOTADO si
  disponible = 0; STOCK_BAJO solo si 0 < disponible ≤ umbral; al pasar de BAJO a AGOTADO se cierra la de BAJO (y
  viceversa); en OK se cierran ambas. SIN_ROTACION y re-evaluación nocturna → Fase 5.
- Contradicciones resueltas: `Reserva` agrega `usuarioId`, `cerradaEn`, `cerradaPor`, `motivoCancelacion`; INVENTARIO no
  convierte.

## Migraciones (InnoDB, utf8mb4, collation utf8mb4_0900_ai_ci, DATETIME(6))
- `V11__crear_tabla_reserva.sql`: `reserva(id BIGINT AI PK, variante_id BIGINT NOT NULL FK → variante, cantidad INT NOT
  NULL, cliente_nombre VARCHAR(120) NOT NULL, cliente_contacto VARCHAR(120) NOT NULL, creada_en DATETIME(6) NOT NULL,
  vence_en DATETIME(6) NOT NULL, estado VARCHAR(12) NOT NULL, venta_id BIGINT NULL FK → venta, usuario_id BIGINT NOT NULL
  FK → usuario, cerrada_en DATETIME(6) NULL, cerrada_por BIGINT NULL FK → usuario, motivo_cancelacion VARCHAR(200) NULL)`.
  CHECK: `cantidad > 0`; `estado IN ('ACTIVA','CONVERTIDA','CANCELADA','VENCIDA')`; `vence_en > creada_en`;
  `(estado = 'CONVERTIDA') = (venta_id IS NOT NULL)`; `(estado = 'ACTIVA') = (cerrada_en IS NULL)`;
  `(estado = 'CANCELADA') = (motivo_cancelacion IS NOT NULL)`.
  Índices: `idx_reserva_estado_vence(estado, vence_en)`, `idx_reserva_variante_estado(variante_id, estado)`,
  `idx_reserva_venta(venta_id)`, `idx_reserva_usuario(usuario_id)`, `idx_reserva_cerrada_por(cerrada_por)`.
- `V12__crear_tabla_alerta.sql`: `alerta(id BIGINT AI PK, variante_id BIGINT NOT NULL FK → variante, tipo VARCHAR(15) NOT
  NULL, estado VARCHAR(10) NOT NULL, creada_en DATETIME(6) NOT NULL, revisada_en DATETIME(6) NOT NULL, atendida_en
  DATETIME(6) NULL, atendida_por BIGINT NULL FK → usuario, variante_activa BIGINT AS (CASE WHEN estado = 'ACTIVA' THEN
  variante_id END) STORED)`. CHECK: `tipo IN ('STOCK_BAJO','AGOTADO','SIN_ROTACION')`, `estado IN ('ACTIVA','ATENDIDA')`,
  `(estado = 'ATENDIDA') = (atendida_en IS NOT NULL)`. UNIQUE `uk_alerta_activa(variante_activa, tipo)` (la columna
  generada no se mapea en JPA). Índices: `idx_alerta_estado_tipo(estado, tipo, creada_en)`,
  `idx_alerta_variante(variante_id)`, `idx_alerta_atendida_por(atendida_por)`.

## Contrato de la API (fijo: backend y frontend avanzan en paralelo)
Convenciones de las fases 1–2 (página VIA_DTO, `ProblemDetail`, fechas ISO locales, 409 de concurrencia de ADR-006).
Roles: **RS** `hasAnyRole('ADMIN','INVENTARIO','VENDEDOR')`; **VT** `hasAnyRole('ADMIN','VENDEDOR')`; **AL** = RS;
**AA** `hasAnyRole('ADMIN','INVENTARIO')`. `EstadoReserva = ACTIVA | CONVERTIDA | CANCELADA | VENCIDA`;
`TipoAlerta = STOCK_BAJO | AGOTADO | SIN_ROTACION`; `EstadoAlerta = ACTIVA | ATENDIDA`.

| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/reservas?estado&q&varianteId&porVencer&page&size&sort` | RS | — | 200 `Page<ReservaResponse>` |
| `GET /api/reservas/{id}` | RS | — | 200 `ReservaResponse`; 404 |
| `POST /api/reservas` | RS | `CrearReservaRequest` | 201 `ReservaResponse[]`; 400; 404; 409; 422 |
| `POST /api/reservas/{id}/cancelar` | RS | `CancelarReservaRequest` | 200 `ReservaResponse`; 404; 409 |
| `POST /api/reservas/{id}/extender` | RS | `ExtenderReservaRequest` | 200 `ReservaResponse`; 404; 409; 422 |
| `POST /api/ventas/desde-reservas` | VT | `ConvertirReservasRequest` | 201 `VentaDetalleResponse`; 400; 404; 409; 422 |
| `GET /api/alertas?estado&tipo&page&size` | AL | — | 200 `Page<AlertaResponse>` |
| `GET /api/alertas/resumen` | AL | — | 200 `ResumenAlertasResponse` |
| `POST /api/alertas/{id}/atender` | AA | — | 200 `AlertaResponse`; 404; 409 |

- Reservas: `q` (máx. 60) busca en nombre o contacto del cliente y SKU (contiene, comodines escapados). `porVencer=true`
  → ACTIVA con `venceEn < ahora + 12 h`. Orden permitido `venceEn`, `creadaEn` (por defecto `venceEn,asc`).
- `CrearReservaRequest { clienteNombre @NotBlank @Size(2,120), clienteContacto @NotBlank @Size(3,120),
  vigenciaHoras? @Min(1) @Max(168), lineas @NotEmpty @Size(max=20) @Valid [{ varianteId @NotNull, cantidad @NotNull
  @Min(1) @Max(100) }] }`. 400 "La variante {id} está repetida en la reserva."; 422 inactiva o stock insuficiente
  (mensaje de ADR-006). Variantes procesadas por id.
- `ReservaResponse { id, numero, varianteId, sku, productoNombre, talla, color, cantidad, precioVenta, clienteNombre,
  clienteContacto, estado, creadaEn, venceEn, creadaPor, cerradaEn, cerradaPor (null = sistema), motivoCancelacion,
  ventaId, ventaNumero }`.
- `CancelarReservaRequest { motivo @NotBlank @Size(5,200) }`. `ExtenderReservaRequest { horas @NotNull @Min(1) @Max(168) }`.
- `ConvertirReservasRequest { reservaIds @NotEmpty @Size(max=20) Long[], precios? @Valid [{ varianteId @NotNull,
  precioUnitario @NotNull @DecimalMin(value="0.00", inclusive=false) @Digits(10,2) }] }`. 400: ids repetidos, precio de
  una variante que no está en las reservas o repetido. 404 reserva inexistente. 409 reserva no ACTIVA. 422: "Las reservas
  deben ser del mismo cliente.", variante o producto inactivo, precio > lista. `VentaDetalleResponse` agrega
  `reservas: string[]` (números R-…; vacío en ventas directas).
- `AlertaResponse { id, tipo, estado, varianteId, sku, productoNombre, talla, color, stockDisponible, umbral, creadaEn,
  revisadaEn, atendidaEn, atendidaPor (null = sistema) }`, solo variantes activas, orden `creadaEn desc`.
  `ResumenAlertasResponse { activas, agotado, stockBajo }`. Atender una ATENDIDA → 409 "La alerta ya fue atendida."

## Backend: piezas
- inventario: `Reserva` (fábrica, `cancelar`, `vencer`, `convertir(Long ventaId, …)`, `extender`, `numero()`),
  `EstadoReserva`, `ReservaRepository` (`bloquear(id)`, `bloquearVarias(ids)` ordenadas por id, `idsVencidas(ahora,
  Pageable)`, resumen de activas por variante/producto), `ReservaService`, `VencimientoReservasTarea`,
  `ReservaController`, `ConsultaReservasActivasJpa` (implementa la interfaz de catalogo); `Alerta`, `TipoAlerta`,
  `EstadoAlerta`, `AlertaRepository`, `AlertaService.evaluar(varianteId)` (`REQUIRES_NEW`), `AlertaListener`,
  `AlertaController`.
- venta: `VentaService.registrarDesdeReservas(...)` reutiliza la construcción de líneas de `registrar` (extraer método);
  ruta en `VentaController`.
- catalogo: `ConsultaReservasActivas` (interfaz), `catalogo.evento.UmbralCambiado(varianteId)` publicado por
  `VarianteService` al editar `stockMinimo`.
- comun: `ConfiguracionTareas`; propiedades `app.tareas.habilitadas`, `app.reservas.vigencia-horas`,
  `app.reservas.vencimiento.intervalo`.

## Frontend
- Tipos `tipos/reservas.ts`, `tipos/alertas.ts`; API `api/reservas.ts`, `api/alertas.ts`; hooks `useReservas`,
  `useAlertas` (claves `['reservas', filtros]`, `['reserva', id]`, `['alertas', filtros]`, `['alertas', 'resumen']`).
  Toda mutación de stock invalida además `reservas` y `alertas`.
- `lib/permisos.ts`: `puedeReservar` (RS), `puedeConvertirReserva` (VT), `puedeAtenderAlertas` (AA).
- Componentes: `BadgeEstadoReserva`, `BadgeTipoAlerta`, `ModalCancelarReserva`, `ModalExtenderReserva`,
  `ModalConvertirReservas` (precios por variante con las validaciones de `lib/carrito`, total y descuento).
- Páginas: `/reservas` (RS; filtros estado —ACTIVA por defecto—, q, por vencer, `varianteId` por query; vencimiento
  relativo en `alerta` si < 12 h y `critico` si pasado; selección múltiple solo del mismo cliente → "Convertir en venta"
  para VT; cancelar y extender), `/reservas/nueva` (RS; cliente + `BuscadorVariante` + líneas con cantidad ≤ disponible
  + vigencia), `/alertas` (AL; filtros tipo/estado, atender para AA, enlace al kardex para KX).
- Indicadores: en `/inventario` el reservado > 0 enlaza a `/reservas?varianteId=`; `MatrizTallaColor` muestra "R n";
  `BuscadorVariante` muestra el reservado; `VentaDetallePage` muestra las reservas de origen.
- Topbar: la campana usa `/api/alertas/resumen` (`refetchInterval` 60 s) y lleva a `/alertas`.
- `App.tsx`: rutas reales de reservas y alertas; `ProximamentePage` sigue para analítica, compras y reportes.
- Ante 409 el formulario o modal no se limpia y muestra "Volver a confirmar".

## Tareas (cada una cierra con un commit y pruebas en verde)
Paso 0 (coordinador): copiar este plan, ADR-007, `docs/dominio.md` y `docs/especificaciones/reglas-inventario.md`.

Backend:
- B1 V11 + `Reserva` + `EstadoReserva` + `ReservaRepository`. `@DataJpaTest`: V1–V11 con `validate`, CHECK, bloqueo.
- B2 `ReservaService` crear/cancelar/extender/listar/obtener + `ReservaController` + DTOs. Mockito + WebMvc por rol.
- B3 `ConfiguracionTareas` + `VencimientoReservasTarea` + `vencer(id, ahora)`. (paralelo con B4 tras B2)
- B4 Conversión en `VentaService` + `POST /api/ventas/desde-reservas` + `reservas[]` en el detalle de venta.
- B5 `ConsultaReservasActivas` en catalogo/inventario + reglas ArchUnit de dirección de módulos. (tras B1)
- B6 V12 + `Alerta` + `AlertaService` + `AlertaListener` + `UmbralCambiado`. Independiente de B1: puede empezar el día 1;
  si coincide con B5 en `VarianteService`, B5 primero. Si B6 crea su migración antes que B1, numerar V11/V12 por orden
  de commit (nunca dos migraciones con el mismo número).
- B7 `AlertaController` (listar, resumen, atender).

Frontend, en paralelo con `vi.mock` de `api/*`:
- F1 Tipos, API, hooks, permisos e invalidaciones. F2 `ReservasPage` + `BadgeEstadoReserva`. F3 `ReservaNuevaPage`.
- F4 `ModalCancelarReserva` y `ModalExtenderReserva`. F5 Conversión (selección + `ModalConvertirReservas`).
- F6 Indicadores de reservado. F7 `AlertasPage` + campana. F8 Rutas en `App.tsx` y navegación por rol.

Pruebas:
- T1 (tras B4) Concurrencia y consistencia de reservas con Testcontainers.
- T2 (tras B7) Integración de punta a punta, listener de alertas y matriz de roles.
- T3 Prueba manual con el backend real (la hace el usuario).

Documentación (al final): ficha "Apartados", ficha REQ-04 (parcial: SIN_ROTACION en Fase 5), diagrama
de estados de Reserva, diagrama de secuencia "convertir reservas en venta".

Recorte si falta tiempo (en este orden): extender, conversión múltiple (una reserva → una venta), `UmbralCambiado`,
indicador "R n" en la matriz, bloque de alertas completo (B6, B7, F7) a la Fase 5.

## Pruebas
- Puras `Reserva`: transiciones válidas; cancelar/convertir/extender/vencer sobre no ACTIVA → 409; extender > 7 días →
  422; `numero()`.
- Mockito `ReservaService`: crear → RESERVA con referencia por línea, orden por id, repetida 400, inactiva 422, stock
  insuficiente 422 sin guardar; cancelar → LIBERACION con usuario y observación; `vencer` → LIBERACION con `usuarioId`
  null; `vencer` sobre no ACTIVA → sin movimiento; vigencia por defecto 48 h con `Clock` fijo.
- Conversión: agrupa dos reservas de la misma variante en una línea; precio null toma la lista actual (no la de la fecha
  de la reserva); precio > lista 422; clientes distintos 422; reserva CANCELADA 409; `costoUnitario` = precioCompra;
  fallo en una línea revierte todo; anular la venta deja las reservas CONVERTIDAS y `stockReservado` sin cambios.
- Desactivación: variante/producto con reserva ACTIVA → 409 con número de apartados; con reservas cerradas → permitido.
  ArchUnit de dirección de módulos.
- Alertas (`AlertaService`): OK→BAJO crea STOCK_BAJO; BAJO→AGOTADO cierra STOCK_BAJO y crea AGOTADO; AGOTADO→BAJO
  inverso; →OK cierra ambas con `atendidaPor` null; ACTIVA existente solo actualiza `revisadaEn`; variante inactiva no
  crea; duplicado por carrera (UNIQUE) → actualiza la existente. Atender ATENDIDA → 409.
- T1 Testcontainers: (a) hilo A bloquea la reserva y espera en una barrera; hilo B llama `vencer(id, ahora)` y queda
  esperando; A convierte y confirma → CONVERTIDA, un VENTA_RESERVA, ninguna LIBERACION, reservado 0; (b) orden inverso →
  la conversión recibe 409 y no hay venta; (c) cancelar vs convertir → una gana; (d) dos reservas de la última unidad →
  una falla (409/422); (e) reserva vs venta directa de la misma variante → stock ≥ 0, sin 500; (f) 200 operaciones
  aleatorias → Σ reservas ACTIVAS = `stockReservado` y stock = suma del kardex; (g) la tarea con 3 vencidas y una de ellas
  bloqueada por otra transacción vence las otras 2.
- T2: flujo crear → convertir → anular; listener real: venta que agota → alerta AGOTADO tras el commit; transacción
  revertida → sin alerta; listener que falla no convierte la respuesta en 500.
- `@DataJpaTest`: V11–V12, CHECK de reserva, UNIQUE de alerta activa (dos ATENDIDA del mismo tipo sí se permiten).
- WebMvc por rol: CONTABILIDAD 403 en reservas y alertas; INVENTARIO 403 en `desde-reservas`; VENDEDOR 403 en atender.
- Vitest: filtros y botones por rol en reservas; selección solo del mismo cliente; modal de conversión (total, descuento,
  precio > lista); campana con el contador; 409 sin perder el formulario.

## Riesgos
- Excepción en el listener AFTER_COMMIT → 500 con la operación ya confirmada: try/catch y log obligatorios.
- Spring 6.1+ no arranca si el listener transaccional no usa `REQUIRES_NEW`/`NOT_SUPPORTED`.
- Tarea programada activa en `@SpringBootTest` → pruebas inestables: `app.tareas.habilitadas=false` en test.
- Hasta 15 min de reservado de más tras vencer; aceptado y documentado.
- Varias instancias sin ShedLock: el bloqueo de fila mantiene el vencimiento idempotente.
- Interbloqueos: siempre reserva → variante; reservas y variantes en orden de id.
- Alertas: atender a mano una condición vigente se reabre con el siguiente movimiento; crear reservas abre alertas.
- Datos personales (Ley 1581): mínimos, sin exportación ni acceso de CONTABILIDAD.
- Fase 4: el seed crea reservas vía `ReservaService` con `Clock` y la tarea apagada. Fase 5: SIN_ROTACION, re-evaluación
  nocturna y demanda de ventas convertidas por la fecha de la venta.
