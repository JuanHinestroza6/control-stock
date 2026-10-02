# ADR-004: Catálogo — SKU generado e inmutable, borrado lógico y desactivación en cascada

- Estado: aceptada
- Fecha: 2026-10-01

## Contexto
La Fase 1 crea categorías, productos y variantes (talla × color). La variante es la unidad de inventario: desde la Fase 2
el kardex, las ventas, las reservas y los planes de compra la referencian por id y los usuarios la identifican por SKU.
Había que decidir cómo se genera el SKU, qué se puede editar, cómo se "borra" y qué pasa con las variantes de un producto
que deja de venderse, sin romper la trazabilidad del kardex.

## Decisión
- El servidor genera el SKU `{codigo}-{talla}-{COLOR3}` (COLOR3: tres primeros alfanuméricos del color sin tildes). Si
  colisiona, se agrega el menor sufijo numérico libre desde 2 (`LIV001-M-NEG2`). Único en toda la base.
- Son inmutables el `codigo` del producto y el `sku`, `talla` y `color` de la variante. Un error se corrige desactivando
  la variante y generando la correcta.
- Una variante por (producto, talla, color). La generación tallas × colores es idempotente. `UNICA` no se mezcla con otras tallas.
- Borrado lógico (`activo`) de producto y variante. La categoría se borra físicamente solo si no tiene productos (409).
- Desactivar un producto desactiva todas sus variantes; reactivarlo no las reactiva. No se desactiva una variante (ni el
  producto que la contiene) con reservas activas: 409. Hasta la Fase 3 se comprueba con `stockReservado > 0`.
- `precioCompra` no se expone al rol VENDEDOR. Se permite precio de venta menor al costo (la interfaz advierte).
- Las 6 categorías base se cargan en la migración V3 y el generador de datos semilla las reutiliza.

## Alternativas consideradas
- SKU libre escrito por el usuario — errores de digitación y duplicados; obliga a validar formato a mano.
- SKU editable / talla y color editables — el kardex y las etiquetas impresas quedarían apuntando a un código distinto
  del que se vendió; complicaría la explicación del historial.
- Borrado físico de productos y variantes — imposible cuando haya movimientos (FK) y destruye historia de ventas.
- Desactivar producto sin tocar variantes (propuesta inicial) — exige calcular "vendible = producto y variante activos" en
  cada consulta; el usuario prefirió la cascada explícita.
- Reactivación en cascada — reactivaría variantes que se habían desactivado a propósito antes.

## Consecuencias
- El kardex y los reportes siempre muestran el mismo SKU para una variante.
- Para corregir un color mal escrito hay que crear otra variante; la incorrecta queda inactiva (visible con un filtro).
- Las consultas de venta, reserva y entradas (Fase 2+) deben rechazar variantes inactivas.
- La regla de reservas activas se reforzará en la Fase 3 consultando la tabla `reserva`.
- `docs/dominio.md` (sección Catálogo) es la referencia de estas reglas.
