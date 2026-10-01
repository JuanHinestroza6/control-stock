---
name: datos-semilla
description: Especificación del generador de datos de demostración de Control Stock (perfil Spring "seed") - catálogo de prendas, 24 meses de ventas con estacionalidad colombiana, distribución Pareto, devoluciones, reservas y entradas coherentes con el kardex. Úsalo cuando se cree, modifique o depure el generador de datos, o cuando se necesiten datos realistas para probar analítica o preparar la sustentación.
---

# Generador de datos semilla

Objetivo: que la analítica (ABC, ROP, Holt-Winters) muestre resultados creíbles en la sustentación.
Implementación: `comun/semilla/GeneradorDatosSemilla` (`CommandLineRunner` con `@Profile("seed")`), idempotente
(si ya hay ventas, no hace nada) y reproducible (`new Random(42)`).

## Regla crítica

Las ventas, devoluciones, entradas y reservas se crean a través de los servicios reales (`MovimientoService`, `VentaService`),
NO con inserts directos. Así el kardex y el stock quedan consistentes y el generador también sirve como prueba de integración.
Para fechar en el pasado, los servicios aceptan un `Clock` inyectable; el generador usa un reloj ajustable.

## Catálogo

- 6 categorías: Camisetas, Hoodies, Jeans, Joggers, Chaquetas, Accesorios.
- ~30 productos, cada uno con 2–4 colores y tallas S–XL (accesorios: UNICA) → ~300 variantes.
- Precios realistas en COP (camiseta 59.900–89.900, hoodie 129.900–179.900, jean 119.900–169.900). Costo = 40–55% del precio.
- Lead time 10–30 días.

## Demanda

```
unidades(día, producto) ~ Poisson(λ)
λ = base_producto × factor_mes × factor_día_semana × tendencia
```
- `base_producto`: distribución Pareto. El 20% de los productos concentra ~80% de la demanda (garantiza ABC visible).
- `factor_mes` (estacionalidad Colombia, moda): ene 0.8, feb 0.7, mar 0.8, abr 0.85, may 1.0 (día de la madre), jun 1.25 (prima),
  jul 1.0, ago 0.9, sep 0.95 (amor y amistad), oct 1.0, nov 1.3 (Black Friday), dic 1.6 (prima + navidad).
- `factor_día_semana`: lun–jue 0.85, vie 1.1, sáb 1.3, dom 1.0.
- `tendencia`: crecimiento de ~1.5% mensual (la empresa está creciendo, como dice el PPI).
- Distribución por talla: S 20%, M 35%, L 30%, XL 15%. Por color: el primero de cada producto 50%, el resto se reparte.
- 2–3 productos "estancados" con demanda casi nula en los últimos 3 meses (para demostrar SIN_ROTACION).

## Operación

- Historia de 24 meses hasta la fecha actual (necesario para Holt-Winters con m = 12).
- Entradas: cuando el disponible de una variante cae bajo un umbral, se programa una ENTRADA tras su lead time por 1–2 meses de demanda. Algunas variantes se dejan agotar a propósito.
- Devoluciones: ~4% de las ventas, dentro de los 15 días siguientes, 80% aptas para venta.
- Reservas: en los últimos 30 días, ~10 activas, algunas vencidas y otras convertidas.
- Usuarios demo: uno por rol (`admin@livorno.co`, `inventario@livorno.co`, `ventas@livorno.co`, `contabilidad@livorno.co`) con contraseña desde variable de entorno `SEED_PASSWORD`.

## Rendimiento

Insertar en lotes (`hibernate.jdbc.batch_size=50`), transacción por mes simulado. Meta: menos de 2 minutos.
Al final imprime un resumen: productos, variantes, ventas, movimientos, ingreso total.
