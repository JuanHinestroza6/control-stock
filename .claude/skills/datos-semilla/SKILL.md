---
name: datos-semilla
description: Especificación del generador de datos de demostración de Control Stock (perfil Spring "seed") - catálogo de prendas, 36 meses de ventas con estacionalidad colombiana, distribución Pareto, devoluciones, reservas y entradas coherentes con el kardex. Úsalo cuando se cree, modifique o depure el generador de datos, o cuando se necesiten datos realistas para probar analítica o preparar la sustentación.
---

# Generador de datos semilla

Objetivo: que la analítica (ABC, ROP, Holt-Winters) muestre resultados creíbles en la sustentación.
Implementación (ADR-008): módulo `semilla` (`co.livorno.controlstock.semilla`, todo con `@Profile("seed")`; ningún otro
módulo depende de él). `GeneradorDatosSemilla` hace la carga y `EjecutorSemilla` (`CommandLineRunner`) la lanza, imprime
el resumen y termina la aplicación. Idempotente (si ya hay ventas, no hace nada; si hay productos o movimientos sin
ventas, aborta) y reproducible (`new Random(42)` y `app.semilla.fecha-fin` fija).

## Regla crítica

Las ventas, devoluciones, entradas y reservas se crean a través de los servicios reales (`MovimientoService`, `VentaService`),
NO con inserts directos. Así el kardex y el stock quedan consistentes y el generador también sirve como prueba de integración.
Para fechar en el pasado, los servicios usan el `Clock` inyectado; en el perfil `seed` el bean `RelojSimulado`
(`@Primary`, solo avanza, vuelve al reloj del sistema al terminar) lo reemplaza. Los servicios leen el usuario del
contexto de seguridad: el generador lo fija por operación (VENDEDOR vende, INVENTARIO ingresa/ajusta/reserva, ADMIN anula).

## Catálogo

- 6 categorías: Camisetas, Hoodies, Jeans, Joggers, Chaquetas, Accesorios. Ya las crea la migración
  `V3__crear_tabla_categoria.sql` (Fase 1): el generador las busca por nombre y las reutiliza, NO las crea.
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
- 3 productos "estancados" (LIV004, LIV026, LIV030): demanda baja antes, y **ninguna venta ni reserva en los últimos 90 días**
  de la historia (`ModeloDemanda.DIAS_ESTANCAMIENTO`; en historias cortas arranca 30 días después del inicio). Conservan stock
  disponible para que se vea la alerta SIN_ROTACION (60 días sin ventas).

## Operación

- Historia de 36 meses (35 completos al cerrar) hasta la fecha final (ADR-010 D2): Holt-Winters necesita ≥ 30 meses y el backtest evalúa 6 meses fuera de muestra.
- Reposición por **planes de compra reales** (ADR-010 D8): cuando la posición (disponible + en camino) de una variante cae bajo el punto de pedido propio de la semilla (demanda del lead time + 7 días), se crea un plan por producto y día (INVENTARIO), se aprueba a la misma hora (ADMIN) y se recibe al llegar (INVENTARIO; la recepción es la ENTRADA `PLAN_COMPRA/{id}`) por 30–60 días de demanda. No usa el ROP ni la sugerencia del sistema. Los pedidos que llegan después de `fin` quedan APROBADOS (en camino); el verificador exige que Σ cantidad aprobada de planes APROBADO coincida con el simulador. Algunas variantes se dejan agotar a propósito.
- Devoluciones: ~4% de las ventas, dentro de los 15 días siguientes, 80% aptas para venta.
- Reservas: en los últimos 30 días, ~10 activas, algunas vencidas y otras convertidas.
- Usuarios demo: uno por rol (`admin@livorno.co`, `inventario@livorno.co`, `ventas@livorno.co`, `contabilidad@livorno.co`) con contraseña desde variable de entorno `SEED_PASSWORD` (obligatoria), sin cambio de contraseña obligatorio.

## Rendimiento

Una sola transacción para toda la carga (todo o nada) con `flush()` + `clear()` por día simulado; el generador guarda
solo ids entre días. Las entidades usan `GenerationType.IDENTITY`, así que Hibernate no agrupa los INSERT
(`batch_size` solo ayuda en los UPDATE): el ahorro viene de la transacción única y de apagar el listener de alertas
(`app.alertas.por-evento=false` en `seed`); al final se evalúan las alertas de todas las variantes con el reloj real.
Meta: menos de 2 minutos (si se pasa, bajar el volumen de tickets diarios).
Al final verifica la consistencia (stock = suma del kardex, reservado = suma de reservas ACTIVAS, nada negativo) e
imprime un resumen: productos, variantes, ventas, ventas perdidas, movimientos, ingreso total, reservas por estado, alertas.
