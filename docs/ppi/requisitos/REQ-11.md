# REQ-11 — Gestión de Productos (FO-ERS)

> Estado a la fecha: implementado en la Fase 1 (backend y frontend). La vista de matriz talla × color con stock (REQ-01)
> queda disponible con valores en cero hasta que la Fase 2 registre movimientos.

| Campo | Contenido |
|---|---|
| **Código** | REQ-11 |
| **Nombre** | Gestión de Productos |
| **Tipo** | Funcional |
| **Stakeholder** | Por asignar (completar con el Word) |
| **Necesidad** | Por asignar (completar con el Word) |
| **Caso de uso** | Por asignar (completar con el Word) |
| **Relacionados** | REQ-01 "Visualizar stock en tiempo real" (matriz de variantes); REQ-06 (permisos por rol) |
| **Prioridad** | Por asignar (completar con el Word) |
| **Módulos** | `catalogo` (`web`, `servicio`, `repositorio`, `modelo`, `dto`); frontend: `paginas/productos`, `componentes/dominio`, `api`, `lib/permisos` |

## Descripción

El sistema debe permitir registrar, consultar, editar y dar de baja lógica los productos del catálogo de Livorno Clothing
y su inventario por variantes (talla × color), organizados en categorías. El producto es la prenda genérica; la variante
(SKU) es la unidad de inventario. Las decisiones de diseño constan en ADR-004 y las reglas en `docs/dominio.md`.

## Alcance implementado (Fase 1)

- **Categorías**: `GET/POST/PUT/DELETE /api/categorias`. Nombre único (sin distinguir tildes ni mayúsculas); borrado físico
  solo si no tiene productos (409). Seis categorías base cargadas por la migración `V3`.
- **Productos**: listado paginado con filtros (`q` por código o nombre, categoría, estado) y orden de lista blanca
  (`codigo, nombre, precioVenta, creadoEn`); detalle, alta, edición y cambio de estado. El `codigo` es único, se guarda en
  mayúsculas y es inmutable. `leadTimeDias` (15) y `loteMinimo` (1) toman valores por defecto. Dinero en `BigDecimal` con
  escala 2. Se permite `precioVenta < precioCompra`; la interfaz advierte.
- **Costo oculto**: `precioCompra` se devuelve `null` al rol VENDEDOR.
- **Variantes**: generación idempotente de combinaciones talla × color (máximo 7 tallas y 20 colores); el SKU lo genera el
  servidor (`{codigo}-{talla}-{COLOR3}`, con sufijo numérico ante colisión: `LIV001-M-NEG2`) y es inmutable, igual que talla
  y color. `UNICA` no se mezcla con otras tallas (422). Solo se edita `stockMinimo`.
- **Baja lógica**: desactivar un producto desactiva todas sus variantes; reactivarlo no las reactiva. No se desactiva una
  variante ni un producto con unidades apartadas (`stockReservado > 0`) → 409; activar una variante con producto inactivo → 422.
- **Matriz (REQ-01)**: `GET /api/productos/{id}/matriz` devuelve tallas (orden del enum), colores y celdas con
  `stockFisico`, `stockReservado`, `stockDisponible` y `estadoStock` (`OK`, `BAJO`, `AGOTADO`). Una celda ausente indica
  combinación no creada.
- **Regla de oro 1**: las variantes nacen con stock 0/0 y `Variante` no tiene métodos que modifiquen el stock; esto lo
  introduce `MovimientoService` en la Fase 2. `puntoReorden` y `claseAbc` existen pero permanecen nulos hasta la analítica.
- **Seguridad**: lectura para cualquier usuario autenticado; escritura con `@PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")`.
  En el frontend, `puedeEditarCatalogo(rol)` oculta los botones de escritura.
- **Frontend**: `/productos` (listado con filtros y paginación), `/productos/nuevo` y `/productos/:id/editar`,
  `/productos/:id` (datos, matriz, variantes, generador) y `/productos/categorias`.

### Endpoints

| Método y ruta | Rol |
|---|---|
| `GET /api/categorias` | Autenticado |
| `POST`, `PUT /{id}`, `DELETE /{id}` en `/api/categorias` | ADMIN, INVENTARIO |
| `GET /api/productos`, `GET /api/productos/{id}` | Autenticado |
| `POST /api/productos`, `PUT /api/productos/{id}`, `PATCH /api/productos/{id}/estado` | ADMIN, INVENTARIO |
| `GET /api/productos/{id}/variantes`, `GET /api/productos/{id}/matriz` | Autenticado |
| `POST /api/productos/{id}/variantes/generar`, `PUT /api/variantes/{id}`, `PATCH /api/variantes/{id}/estado` | ADMIN, INVENTARIO |

## Pendiente

- Reforzar la regla de apartados activos (hoy `stockReservado > 0`) con la tabla de reservas en la Fase 3.
- La matriz muestra ceros hasta registrar movimientos (Fase 2) o cargar datos semilla (Fase 4).
- Registro de la ficha en el Word: código de caso de uso, necesidad, stakeholder y prioridad.

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | ADMIN o INVENTARIO crea un producto con código único (409 si se repite) y valores por defecto de reposición | Cumplido | `ProductoServiceTest`, `ProductoControllerTest`, `ProductoRepositoryTest`, `CatalogoIntegracionTest` |
| CA-2 | El listado filtra por texto, categoría y estado, pagina y rechaza campos de orden fuera de la lista blanca (400) | Cumplido | `ProductoRepositoryTest`, `ProductoControllerTest`, `PaginaJsonTest`, `OrdenamientoTest`, `ProductosPage.test.tsx` |
| CA-3 | Los campos inmutables (código, SKU, talla, color) no se modifican | Cumplido | `ProductoServiceTest`, `VarianteServiceTest`, `VarianteTest` |
| CA-4 | El SKU se genera en el servidor con sufijo ante colisión (`NEG`, `NEG2`) y sin tildes (`MARRÓN` → `MAR`) | Cumplido | `GeneradorSkuTest`, `VarianteServiceTest` |
| CA-5 | La generación talla × color es idempotente, rechaza `UNICA` mezclada y producto inactivo (422) | Cumplido | `VarianteServiceTest`, `VarianteControllerTest`, `GeneradorVariantes.test.tsx`, `lib/variantes.test.ts` |
| CA-6 | Generar 3 tallas × 2 colores produce una matriz de 6 celdas en 0 con estado `AGOTADO` | Cumplido | `CatalogoIntegracionTest`, `dominio.test.tsx` |
| CA-7 | Desactivar un producto desactiva sus variantes; reactivarlo no las reactiva; con reservado > 0 responde 409 | Cumplido | `ProductoServiceTest`, `VarianteServiceTest`, `ProductoDetallePage.test.tsx` |
| CA-8 | `estadoStock`: disponible 0 → `AGOTADO`; ≤ punto de reorden (o stock mínimo sin reorden) → `BAJO`; si no, `OK` | Cumplido | `VarianteTest`, `dominio.test.tsx` |
| CA-9 | `precioCompra` no se expone al VENDEDOR | Cumplido | `ProductoServiceTest`, `ProductoControllerTest`, `CatalogoIntegracionTest` |
| CA-10 | VENDEDOR y CONTABILIDAD consultan (200) pero reciben 403 al escribir; la interfaz oculta las acciones | Cumplido | `CategoriaControllerTest`, `ProductoControllerTest`, `VarianteControllerTest`, `lib/permisos.test.ts`, `ProductosPage.test.tsx` |
| CA-11 | La categoría solo se elimina sin productos (409 en otro caso); nombre único sin distinguir tildes ni mayúsculas | Cumplido | `CategoriaServiceTest`, `CategoriaControllerTest`, `CategoriaMigracionTest`, `CategoriasPage.test.tsx` |
| CA-12 | Las migraciones V3–V5 validan contra las entidades; existen las 6 categorías base y la restricción `stock_reservado <= stock_fisico` | Cumplido | `CategoriaMigracionTest`, `VarianteRepositoryTest` |
| CA-13 | El formulario valida precios y advierte si el precio de venta es menor al costo | Cumplido | `esquemaProducto.test.ts`, `ProductoFormPage.test.tsx` |

## Observaciones

- La numeración de `docs/dominio.md` asigna REQ-11 a "Gestión de productos"; el código de caso de uso, la necesidad y el
  stakeholder del Word no se conocen y se dejan por asignar. La vista de la matriz se registra bajo REQ-01.
- Inconsistencias previas del documento Word (pendientes de corregir): nombre y descripción cruzados entre REQ-08 y REQ-09;
  NEC-010 debería ser NEC-10.
- Diagrama: `diagramas/clases-fase1-catalogo.puml`.
