# ADR-006: Operación de inventario — punto único de stock, anulación, concurrencia, kardex y costos

- Estado: aceptada
- Fecha: 2026-10-02

## Contexto
La Fase 2 introduce el kardex (`Movimiento`), las entradas, los ajustes, las ventas, las anulaciones y las devoluciones.
Todas cambian el stock de `Variante`. Las reglas de oro 1, 2 y 7 exigen un único punto de escritura del stock, un kardex
inmutable y bloqueo optimista. Había cinco cuestiones abiertas:
1. La versión inicial de `docs/especificaciones/reglas-inventario.md` pedía un `Variante.aplicar()` paquete-privado,
   pero `Variante` vive en `catalogo.modelo` y `MovimientoService` en `inventario.servicio`: Java no permite esa
   visibilidad entre paquetes.
2. El dominio no tenía un tipo de movimiento para la anulación de una venta.
3. Qué hacer cuando dos operaciones chocan sobre la misma variante.
4. Cómo garantizar la inmutabilidad del kardex.
5. De dónde sale el costo de una venta y si las entradas registran costo.

## Decisión
1. **Punto único de escritura**: `Variante.aplicarMovimiento(int deltaFisico, int deltaReservado)` es público y valida los
   invariantes (físico ≥ 0, reservado ≥ 0, reservado ≤ físico; si no, `IllegalStateException`). Una prueba **ArchUnit**
   (`archunit-junit5`, dependencia de test) falla si alguna clase distinta de `inventario.servicio.MovimientoService`
   invoca ese método. Los CHECK de la tabla `variante` (V5) son la última defensa.
2. **ANULACION_VENTA** (+físico, reservado sin cambio): solo ADMIN, motivo obligatorio, un movimiento por línea con
   referencia `VENTA/{id}`. Anular dos veces → 409. Venta con devoluciones → 422 (se corrige con ajustes). Si la venta vino
   de una reserva (Fase 3), solo se devuelve el físico; la reserva sigue CONVERTIDA. Las ventas ANULADAS se excluyen de la
   demanda, los más vendidos, ABC, el pronóstico y los reportes (Fases 5–7).
3. **Concurrencia**: `@Version` en `Variante`. `OptimisticLockingFailureException` y `PessimisticLockingFailureException`
   → 409 "Los datos cambiaron mientras registrabas la operación. Revisa y vuelve a confirmar." **Sin reintento en el
   servidor**: el cliente conserva el formulario. Las operaciones de varias líneas procesan las variantes ordenadas por id
   y Hibernate ordena los UPDATE (`hibernate.order_updates=true`) para evitar interbloqueos. Anular y devolver bloquean la
   fila de la venta (`PESSIMISTIC_WRITE`), porque una devolución no apta no toca `Variante` y su `@Version` no la protege.
4. **Kardex inmutable sin triggers**: `Movimiento` con `@org.hibernate.annotations.Immutable`, sin setters, creado por una
   fábrica; `MovimientoRepository` extiende `Repository` (solo `save` y consultas), sin borrado ni actualización.
5. **Costos**:
   - Cada línea de entrada lleva `costoUnitario` (≥ 0, escala 2) que se guarda en `movimiento.costo_unitario` (obligatorio
     en ENTRADA, null en los demás tipos) y **actualiza `precioCompra` del producto** (último costo de entrada mayor que 0). Las líneas
     de un mismo producto en una entrada deben traer el mismo costo (422), porque el costo vive en el producto.
   - La venta guarda en `DetalleVenta` el `precioLista` (precio de venta del producto en ese momento), el `precioUnitario`
     (opcional en la solicitud, `0 < precio ≤ lista`; si falta, igual a la lista) y el `costoUnitario` (`precioCompra` en
     ese momento). Así los reportes muestran margen y descuentos otorgados aunque los precios cambien después.

## Alternativas consideradas
- Método paquete-privado — imposible entre paquetes distintos.
- Un `EfectoMovimiento` de inventario como parámetro de `Variante` — crea un ciclo de dependencias catalogo → inventario.
- Prueba que escanea el texto de las fuentes — frágil (comentarios, nombres parecidos); ArchUnit analiza el bytecode.
- Reutilizar DEVOLUCION o AJUSTE_POSITIVO para anular — mezcla devoluciones reales con correcciones y distorsiona las
  métricas de devoluciones y mermas.
- Reintento automático con `@Retryable` de Spring 7 — disponible sin dependencias, pero exige una fachada fuera de la
  transacción y oculta conflictos; con pocos usuarios simultáneos el 409 es suficiente. Se revisará si los 409 son frecuentes.
- Bloqueo pesimista de `Variante` en todas las operaciones — contradice la regla de oro 7 y serializa lecturas.
- Triggers MySQL que rechacen UPDATE/DELETE en `movimiento` — requieren `log_bin_trust_function_creators` en Docker
  Compose y Testcontainers; la protección en la aplicación basta para el alcance.
- Costo promedio ponderado — contablemente más preciso, pero excede el alcance; el último costo es simple y explicable.
- Entradas sin costo — el margen dependería de que alguien edite el producto a mano.

## Consecuencias
- Nueva dependencia de test `com.tngtech.archunit:archunit-junit5` 1.5.1: el usuario debe descargarla una vez con
  `.\mvnw.cmd test-compile` desde su terminal (el entorno de compilación automatizado trabaja sin red).
- `dominio.md` agrega ANULACION_VENTA, `Movimiento.costoUnitario`, `Movimiento.usuarioId` nullable,
  `Venta.anuladaEn/anuladaPor/motivoAnulacion` y `DetalleVenta.precioLista`.
- Un costo de entrada 0 (muestra o regalo) se guarda en el movimiento pero NO actualiza `precioCompra` (decisión del usuario), para no generar márgenes del 100 % en las ventas siguientes.
- Dos entradas simultáneas del mismo producto: gana la última en confirmar (`Producto` no tiene `@Version`); aceptado.
- El usuario puede ver 409 si dos personas venden la misma prenda en el mismo instante; debe volver a confirmar.
- Las analíticas y reportes deben filtrar `venta.estado = 'REGISTRADA'`.
