# Plan APROBADO — Fase 1 (Núcleo)

Rama `fase-1-nucleo`. Requisitos: REQ-11 (gestión de productos), REQ-01 (vista matriz con stock), REQ-06 CA-8 (CU-02),
REQ-12 CA-5/CA-6 (CU-12). Reglas: `docs/dominio.md` secciones "Catálogo" y "Usuarios y contraseñas". ADR-004 y ADR-005.

**Regla de oro 1:** en esta fase NO se modifica stock. Las variantes nacen con `stockFisico = 0`, `stockReservado = 0`.
`Variante` no tiene setters de stock ni método `aplicar` (llega en Fase 2 con `MovimientoService`).

## Decisiones confirmadas por el usuario
- Catálogo en el módulo `catalogo` (`Categoria`, `Producto`, `Variante`, enum `Talla` con orden XS, S, M, L, XL, XXL, UNICA).
- Borrado lógico de producto, variante y usuario. Categoría: borrado físico solo sin productos → 409.
- Inmutables: `codigo` del producto; `sku`, `talla`, `color` de la variante. SKU generado por el servidor con sufijo
  numérico ante colisión (`LIV001-M-NEG2`). UNICA exclusiva (422). Generación idempotente (máx. 7 tallas × 20 colores).
- Desactivar producto desactiva todas sus variantes; reactivar producto NO reactiva variantes. No se desactiva variante ni
  producto con reservas activas → 409 (en Fase 1: `stockReservado > 0`; en Fase 3 reforzar con la tabla `reserva`).
- `precioCompra` llega `null` al rol VENDEDOR. `precioVenta < precioCompra` permitido; la UI advierte.
- Paginación: productos y usuarios con `Page` (20 por defecto, máx. 100, orden con lista blanca). Categorías y variantes de
  un producto como `List` (conjuntos pequeños y acotados; excepción consciente a la convención).
- Usuarios: solo ADMIN; nadie se desactiva, se cambia el rol ni se restablece la contraseña a sí mismo desde `/usuarios`
  (422); siempre ≥ 1 ADMIN activo (422, con bloqueo pesimista sobre los ADMIN activos).
- Contraseñas: 12–72 caracteres y ≤ 72 bytes UTF-8, no empieza por `cambiar`. La que asigna el ADMIN es temporal
  (`debeCambiarPassword`). Revocación de tokens por `passwordCambiadoEn` vs `iat` (ADR-005).
- V3 carga las 6 categorías base de `docs/especificaciones/datos-semilla.md`; el generador de la Fase 4 las reutiliza (no las crea).

## Migraciones (InnoDB, utf8mb4, collation utf8mb4_0900_ai_ci, DATETIME(6))
- `V3__crear_tabla_categoria.sql`: `categoria(id BIGINT AI PK, nombre VARCHAR(60) NOT NULL, creado_en, actualizado_en,
  UNIQUE uk_categoria_nombre)`. INSERT de Camisetas, Hoodies, Jeans, Joggers, Chaquetas, Accesorios con `NOW(6)`.
- `V4__crear_tabla_producto.sql`: `producto(id, codigo VARCHAR(20) NOT NULL UNIQUE, nombre VARCHAR(120) NOT NULL,
  categoria_id BIGINT NOT NULL FK → categoria ON DELETE RESTRICT, precio_compra DECIMAL(12,2) NOT NULL,
  precio_venta DECIMAL(12,2) NOT NULL, lead_time_dias INT NOT NULL DEFAULT 15, lote_minimo INT NOT NULL DEFAULT 1,
  activo BOOLEAN NOT NULL DEFAULT TRUE, creado_en, actualizado_en)`. CHECK: `precio_compra >= 0`, `precio_venta > 0`,
  `lead_time_dias BETWEEN 1 AND 365`, `lote_minimo >= 1`. Índices: `idx_producto_categoria(categoria_id)`,
  `idx_producto_activo_nombre(activo, nombre)`.
- `V5__crear_tabla_variante.sql`: `variante(id, producto_id BIGINT NOT NULL FK → producto, sku VARCHAR(40) NOT NULL UNIQUE,
  talla VARCHAR(5) NOT NULL, color VARCHAR(30) NOT NULL, stock_fisico INT NOT NULL DEFAULT 0,
  stock_reservado INT NOT NULL DEFAULT 0, stock_minimo INT NOT NULL DEFAULT 0, punto_reorden INT NULL,
  clase_abc CHAR(1) NULL, activo BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0, creado_en, actualizado_en)`.
  UNIQUE `uk_variante_producto_talla_color(producto_id, talla, color)` (cubre el índice de la FK). CHECK: talla IN
  (XS,S,M,L,XL,XXL,UNICA), `clase_abc IN ('A','B','C')`, `stock_fisico >= 0`, `stock_reservado >= 0`,
  `stock_reservado <= stock_fisico`, `stock_minimo >= 0`, `punto_reorden IS NULL OR punto_reorden >= 0`.
- `V6__agregar_credenciales_usuario.sql`: `ALTER TABLE usuario ADD password_cambiado_en DATETIME(6) NULL,
  ADD debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE` (el ADMIN de V2 queda en FALSE).

## Contrato de la API (fijo: backend y frontend avanzan en paralelo)

Convenciones:
- Roles: **Auth** = autenticado; **CR** = `hasAnyRole('ADMIN','INVENTARIO')`; **AD** = `hasRole('ADMIN')`.
- Página (Spring Data `VIA_DTO`): `{ "content": T[], "page": { "size", "number", "totalElements", "totalPages" } }`.
  Parámetros `page` (desde 0), `size`, `sort=campo,asc|desc`. Campo de orden fuera de la lista blanca → 400.
- Dinero: número JSON con 2 decimales (`59900.00`). Fechas: ISO-8601 local (`2026-10-01T10:15:30`).
- Errores: `ProblemDetail`; validación → 400 con `errores: [{campo, mensaje}]`; no existe → 404; duplicado o estado en
  conflicto → 409; regla de negocio → 422. Cambio de contraseña requerido → 403 con `codigo: "CAMBIO_PASSWORD_REQUERIDO"`.
- `EstadoRequest { activo: boolean (@NotNull) }`. Cambiar al estado que ya tiene es un no-op (200).
- `CategoriaRef { id, nombre }`.

### Categorías
| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/categorias` | Auth | — | 200 `CategoriaResponse[]` ordenado por nombre |
| `POST /api/categorias` | CR | `CategoriaRequest` | 201 `CategoriaResponse`; 409 nombre duplicado |
| `PUT /api/categorias/{id}` | CR | `CategoriaRequest` | 200 `CategoriaResponse`; 404; 409 |
| `DELETE /api/categorias/{id}` | CR | — | 204; 404; 409 "La categoría tiene productos asociados" |

- `CategoriaRequest { nombre: @NotBlank @Size(max=60) }`
- `CategoriaResponse { id, nombre, totalProductos: number }`

### Productos
| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/productos?q&categoriaId&activo&page&size&sort` | Auth | — | 200 `Page<ProductoResumenResponse>` |
| `GET /api/productos/{id}` | Auth | — | 200 `ProductoDetalleResponse`; 404 |
| `POST /api/productos` | CR | `CrearProductoRequest` | 201 `ProductoDetalleResponse`; 404 categoría; 409 código |
| `PUT /api/productos/{id}` | CR | `ActualizarProductoRequest` | 200 `ProductoDetalleResponse` |
| `PATCH /api/productos/{id}/estado` | CR | `EstadoRequest` | 200 `ProductoDetalleResponse`; 409 reservas activas |

- `q` (máx. 60) busca en código o nombre (contiene; `%` y `_` escapados). `activo` por defecto: todos.
  Orden permitido: `codigo, nombre, precioVenta, creadoEn` (por defecto `nombre,asc`).
- `CrearProductoRequest { codigo: @NotBlank @Pattern([A-Za-z0-9-]{2,20}) (se guarda en mayúsculas),
  nombre: @NotBlank @Size(max=120), categoriaId: @NotNull, precioCompra: @NotNull @DecimalMin("0.00") @Digits(10,2),
  precioVenta: @NotNull @DecimalMin(value="0.00", inclusive=false) @Digits(10,2),
  leadTimeDias?: @Min(1) @Max(365) (null → 15), loteMinimo?: @Min(1) @Max(10000) (null → 1) }`
- `ActualizarProductoRequest`: igual sin `codigo`; `leadTimeDias` y `loteMinimo` obligatorios (`@NotNull`).
- `ProductoResumenResponse { id, codigo, nombre, categoria: CategoriaRef, precioVenta, activo, totalVariantes,
  stockDisponibleTotal }` (totales solo de variantes activas; una sola consulta agregada, sin N+1).
- `ProductoDetalleResponse { id, codigo, nombre, categoria: CategoriaRef, precioCompra (null para VENDEDOR), precioVenta,
  leadTimeDias, loteMinimo, activo, totalVariantes, creadoEn, actualizadoEn }`
- Desactivar: si alguna variante tiene `stockReservado > 0` → 409 "No se puede desactivar el producto: la variante
  {sku} tiene {n} unidades en apartados activos." Si no, producto y todas sus variantes quedan inactivos.
  Activar: solo el producto.

### Variantes
| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `GET /api/productos/{id}/variantes?incluirInactivas=false` | Auth | — | 200 `VarianteResponse[]` (orden talla, color) |
| `POST /api/productos/{id}/variantes/generar` | CR | `GenerarVariantesRequest` | 201 `GenerarVariantesResponse`; 422 |
| `GET /api/productos/{id}/matriz?incluirInactivas=false` | Auth | — | 200 `MatrizResponse` |
| `PUT /api/variantes/{id}` | CR | `ActualizarVarianteRequest` | 200 `VarianteResponse` |
| `PATCH /api/variantes/{id}/estado` | CR | `EstadoRequest` | 200 `VarianteResponse`; 409; 422 |

- `Talla = "XS"|"S"|"M"|"L"|"XL"|"XXL"|"UNICA"`; `EstadoStock = "OK"|"BAJO"|"AGOTADO"`.
- `GenerarVariantesRequest { tallas: @NotEmpty @Size(max=7) Talla[], colores: @NotEmpty @Size(max=20)
  (@NotBlank @Size(max=30) @Pattern(letras, dígitos, espacios, tildes, ñ))[], stockMinimo: @NotNull @Min(0) @Max(100000) }`.
  Duplicados (tras normalizar) se ignoran. 422: producto inactivo; mezcla UNICA con otras tallas (incluidas las existentes).
- `GenerarVariantesResponse { creadas: VarianteResponse[], omitidas: [{ talla, color, sku, motivo: "EXISTE"|"EXISTE_INACTIVA" }] }`
- `VarianteResponse { id, productoId, sku, talla, color, stockFisico, stockReservado, stockDisponible, stockMinimo,
  puntoReorden (null), claseAbc (null), estadoStock, activo }`
- `ActualizarVarianteRequest { stockMinimo: @NotNull @Min(0) @Max(100000) }`
- `MatrizResponse { productoId, tallas: Talla[] (orden del enum, solo las presentes), colores: string[] (alfabético),
  celdas: [{ talla, color, varianteId, sku, stockFisico, stockReservado, stockDisponible, estadoStock, activo }] }`
  (celda ausente = combinación no creada).
- Estado de variante: desactivar con `stockReservado > 0` → 409 "La variante {sku} tiene {n} unidades en apartados
  activos."; activar con producto inactivo → 422 "Activa primero el producto {codigo}."

### Usuarios (todo AD)
| Método y ruta | Request | Respuesta |
|---|---|---|
| `GET /api/usuarios?q&rol&activo&page&size&sort` | — | 200 `Page<UsuarioResponse>` (orden: `nombre, email, rol, creadoEn`) |
| `GET /api/usuarios/{id}` | — | 200 `UsuarioResponse`; 404 |
| `POST /api/usuarios` | `CrearUsuarioRequest` | 201 `UsuarioResponse` (`debeCambiarPassword = true`); 409 correo |
| `PUT /api/usuarios/{id}` | `ActualizarUsuarioRequest` | 200 `UsuarioResponse`; 409 correo; 422 propio rol / último ADMIN |
| `PATCH /api/usuarios/{id}/estado` | `EstadoRequest` | 200 `UsuarioResponse`; 422 propio / último ADMIN |
| `PUT /api/usuarios/{id}/password` | `RestablecerPasswordRequest` | 204; 422 si es el propio usuario |

- `CrearUsuarioRequest { nombre: @NotBlank @Size(max=100), email: @NotBlank @Email @Size(max=150), rol: @NotNull Rol,
  passwordTemporal: @PasswordValida }`
- `ActualizarUsuarioRequest { nombre, email, rol }` (mismas validaciones).
- `RestablecerPasswordRequest { passwordTemporal: @PasswordValida }` → marca `debeCambiarPassword`, fija
  `passwordCambiadoEn` (revoca tokens del usuario).
- `UsuarioResponse { id, nombre, email, rol, activo, debeCambiarPassword, creadoEn, actualizadoEn }` (nunca el hash).
- `@PasswordValida` (constraint propia, mensaje por campo): no vacía, 12–72 caracteres, ≤ 72 bytes UTF-8, no empieza por `cambiar`.
- Mensajes 422: "No puedes desactivar tu propia cuenta.", "No puedes cambiar tu propio rol.", "Usa Mi cuenta para
  cambiar tu contraseña.", "Debe quedar al menos un administrador activo."

### Autenticación (cambios sobre la Fase 0)
| Método y ruta | Rol | Request | Respuesta |
|---|---|---|---|
| `POST /api/auth/login` | público | `LoginRequest` | 200 `LoginResponse` (ahora con `usuario.debeCambiarPassword`) |
| `GET /api/auth/yo` | Auth (permitido aunque deba cambiar) | — | 200 `UsuarioSesionResponse` |
| `PUT /api/auth/yo/password` | Auth (permitido aunque deba cambiar) | `CambiarPasswordRequest` | 200 `LoginResponse` (token nuevo); 422 |

- `UsuarioSesionResponse { id, nombre, email, rol, debeCambiarPassword }`.
- `CambiarPasswordRequest { actual: @NotBlank @Size(max=72), nueva: @PasswordValida }`. 422 "La contraseña actual no es
  correcta." / "La nueva contraseña debe ser distinta de la actual." Pone `debeCambiarPassword = false`, fija
  `passwordCambiadoEn` y emite token nuevo.
- Convertidor JWT: además de inactivo → 401, `iat < passwordCambiadoEn` → 401 (ambos en segundos; `passwordCambiadoEn` se
  guarda truncado a segundos con el mismo `Clock` que emite el token).
- Filtro de cambio obligatorio: `authorizeHttpRequests` permite `GET /api/auth/yo` y `PUT /api/auth/yo/password` a cualquier
  autenticado; `anyRequest()` usa un `AuthorizationManager` que niega si `principal.debeCambiarPassword()`. El
  `AccessDeniedHandler` responde 403 con `codigo: "CAMBIO_PASSWORD_REQUERIDO"` y detail "Debes cambiar tu contraseña
  temporal antes de continuar." `UsuarioAutenticado` agrega `debeCambiarPassword`.

### Infraestructura común
- `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` en `comun.config`.
- `SolicitudInvalidaException` (400) en `comun.excepcion`; `PropertyReferenceException` → 400 "Campo de orden no permitido".
- Helper `comun.web.Ordenamiento.validar(Pageable, Set<String> permitidos)`.

## Frontend
- Tipos: `tipos/paginacion.ts` (`Pagina<T>`), `tipos/catalogo.ts`, `tipos/usuarios.ts`; `tipos/auth.ts` agrega
  `debeCambiarPassword` (sesiones viejas sin el campo → `false`).
- API y hooks: `api/{categorias,productos,variantes,usuarios}.ts`, `api/auth.ts` (+`cambiarPassword`); hooks con TanStack
  Query (`['productos', filtros]`, `['producto', id]`, `['variantes', id]`, `['matriz', id]`, `['categorias']`, `['usuarios', filtros]`).
- `lib/permisos.ts`: `puedeEditarCatalogo(rol)`; botones ocultos por rol.
- UI base: `Tabla` (zebra), `Paginacion`, `Selector`, `Badge`, `EstadoCarga`/`EstadoError`/`EstadoVacio`, `Modal`,
  `DialogoConfirmacion`.
- Dominio: `BadgeStock`, `MatrizTallaColor`, `GeneradorVariantes`, `FormularioProducto`, `FormularioUsuario`,
  `ModalPassword`, `FormularioCambioPassword`.
- Páginas: `/productos` (listado con filtros), `/productos/nuevo` y `/productos/:id/editar` (CR), `/productos/:id` (datos,
  matriz, variantes, generador), `/productos/categorias`, `/usuarios`, `/cuenta` (todos).
- Cambio obligatorio: `RutaProtegida` redirige a `/cuenta` si `usuario.debeCambiarPassword` (salvo en `/cuenta`); el
  interceptor ante 403 con `codigo === 'CAMBIO_PASSWORD_REQUERIDO'` marca la sesión y navega a `/cuenta`; tras cambiar,
  `guardarSesion(respuesta)` con el token nuevo. Enlace "Mi cuenta" en el Topbar.
- `App.tsx`: rutas reales para productos, usuarios y cuenta; el resto sigue con `ProximamentePage`.

## Tareas (cada una cierra con un commit y pruebas en verde)
Backend, en orden; B5 puede ir en paralelo desde el inicio:
- B1 `comun`: Page VIA_DTO, `SolicitudInvalidaException`, `PropertyReferenceException` → 400, `Ordenamiento`. Prueba del JSON de página.
- B2 V3 + `Categoria` (entidad, repositorio, servicio, controlador, DTOs). Pruebas de servicio, WebMvc por rol, migración.
- B3 V4 + `Producto` + `ProductoRepository` (consulta agregada filtrada). `@DataJpaTest` con Testcontainers.
- B4 `ProductoService`/`ProductoController`: crear, editar, detalle (costo null para VENDEDOR), listar.
- B5 `GeneradorSku` (clase pura): normalización, tildes, COLOR3, sufijos por colisión.
- B6 V5 + `Variante` (`@Version`, sin setters de stock, `estadoStock()`) + repositorio. Prueba por reflexión.
- B7 Generar y listar variantes (idempotencia, UNICA, producto inactivo).
- B8 Matriz; `PUT /variantes/{id}`; estado de variante; estado de producto con cascada y 409 por reservado.
- B9 V6 + `Usuario` (métodos `asignarPasswordTemporal`, `cambiarPassword`, `actualizarDatos`, `cambiarRol`);
  `UsuarioAutenticado`, `UsuarioSesionResponse` y login con `debeCambiarPassword`. Ajustar pruebas de la Fase 0.
- B10 Revocación en `ConvertidorUsuarioJwt` (`iat` vs `passwordCambiadoEn`, precisión de segundos) + filtro
  `debeCambiarPassword` (`AuthorizationManager` + 403 con `codigo`). Pruebas con `Clock` fijo.
- B11 `@PasswordValida` + `UsuarioService` (crear, editar, rol, estado, restablecer; reglas y bloqueo pesimista).
- B12 `UsuarioController` + WebMvc (todo rol no ADMIN → 403).
- B13 `PUT /api/auth/yo/password` (token nuevo, 422).

Frontend, en paralelo con el backend usando `vi.mock` de `api/*`:
- F1 `Pagina<T>`, `Tabla`, `Paginacion`, `Selector`, `Badge`, estados.
- F2 `Modal`, `DialogoConfirmacion`.
- F3 Tipos, API y hooks del catálogo + página de categorías.
- F4 Listado de productos (filtros, paginación, totales).
- F5 `FormularioProducto` (crear/editar, aviso venta < costo, costo oculto a VENDEDOR).
- F6 Detalle de producto + tabla de variantes (stock mínimo, estado, mensajes 409/422).
- F7 `MatrizTallaColor` + `BadgeStock`.
- F8 `GeneradorVariantes` (vista previa de combinaciones, resumen creadas/omitidas).
- F9 Sesión con `debeCambiarPassword`: tipos, `RutaProtegida` → `/cuenta`, interceptor 403 `CAMBIO_PASSWORD_REQUERIDO`.
- F10 Página `/cuenta` (cambiar contraseña, guarda la sesión nueva) + enlace en Topbar.
- F11 Usuarios: listado y formulario crear/editar.
- F12 Usuarios: estado, rol y restablecer contraseña (acciones ocultas sobre el propio usuario).

Pruebas:
- T1 (tras B8) Integración catálogo de punta a punta.
- T2 (tras B13) Integración usuarios y seguridad: revocación, cambio obligatorio, matriz de roles.
- T3 Prueba manual con backend real; ajustes de contrato.

Documentación (al final): ficha REQ-11; CA de REQ-06 y REQ-12; diagrama de clases.

Recorte si falta tiempo (en este orden): F3 página de categorías (las 6 vienen en V3), búsqueda `q`, F12 restablecer en UI.

## Pruebas
- Unitarias (Mockito): duplicados 409; generación idempotente; UNICA 422; producto inactivo 422; cascada al desactivar;
  reactivar no reactiva variantes; reservado > 0 → 409 (stock puesto con `ReflectionTestUtils` solo en la prueba); auto
  desactivar/rol/restablecer 422; último ADMIN 422; restablecer marca `debeCambiarPassword` y `passwordCambiadoEn`.
- Puras: `GeneradorSku` (`MARRÓN` → `MAR`, color de 1–2 letras, `NEG`/`NEG2`/`NEG3`, espacios); `estadoStock` (0 →
  AGOTADO, ≤ ROP → BAJO, sin ROP usa stockMinimo); `@PasswordValida` (11 caracteres, 73 bytes con emojis, `cambiar...`).
- Convertidor con `Clock` fijo: token con `iat` un segundo antes del cambio → 401; mismo segundo → válido; sin
  `passwordCambiadoEn` → válido.
- WebMvc: matriz de roles por endpoint (VENDEDOR/CONTABILIDAD 200 en GET y 403 en escritura del catálogo; no ADMIN 403 en
  `/usuarios`); 400 con `errores[]`; orden inválido 400; usuario con `debeCambiarPassword` → 403 con `codigo` salvo `/yo`.
- `@DataJpaTest` + Testcontainers: V1–V6 con `ddl-auto=validate`; 6 categorías base; unicidad sin tildes ni mayúsculas;
  CHECK `stock_reservado <= stock_fisico` rechaza un INSERT nativo; consulta agregada y paginación.
- `@SpringBootTest`: ADMIN crea producto, genera 3 × 2 → matriz con 6 celdas en 0/AGOTADO; VENDEDOR no ve `precioCompra`
  y recibe 403 al crear; ADMIN crea usuario → login con `debeCambiarPassword` → 403 en `/api/productos` → cambia
  contraseña → token nuevo funciona y el viejo da 401; restablecer revoca el token vigente.
- Vitest: formularios (zod), botones por rol, matriz con celdas vacías e inactivas, generador, redirección a `/cuenta`,
  interceptor 403, estados de carga/error/vacío, paginación.

## Riesgos
- Serialización de `Page` con Spring Data 4 + Jackson 3: fijada en B1 con una prueba del JSON.
- La matriz muestra ceros hasta la Fase 2/4: avisarlo en la demo.
- Ventana de 1 s en la revocación (ADR-005), aceptada.
- B9 cambia `UsuarioAutenticado` y respuestas de auth: actualizar pruebas de la Fase 0 en el mismo commit.
- Fase 2: `Variante.aplicar()` no puede ser paquete-privado (paquetes distintos); decidir entonces público + convención/prueba o ArchUnit.
- Semana 1 cargada: aplicar el orden de recorte.
