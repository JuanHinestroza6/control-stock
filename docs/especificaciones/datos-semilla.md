# Especificación del generador de datos semilla

Generador de datos de demostración de Control Stock: catálogo de prendas, 36 meses de ventas con estacionalidad
colombiana, distribución tipo Pareto, devoluciones, reservas y reposición por planes de compra, coherentes con el kardex.
Objetivo: que la analítica (ABC, punto de reorden, Holt-Winters) muestre resultados creíbles en la sustentación.
Decisiones: ADR-008 y ADR-010 (D2 y D8). Resultados de las corridas reales: `docs/ppi/datos-demostracion.md`.

## Implementación

- Módulo `semilla` (`co.livorno.controlstock.semilla`): todas sus clases llevan `@Profile("seed")` y ningún otro módulo
  depende de él (ArchUnit). `GeneradorDatosSemilla` hace la carga y `EjecutorSemilla` (`CommandLineRunner`) la lanza,
  imprime el resumen y termina la aplicación (código 0 si todo salió bien, 1 si falló).
- Se ejecuta con los perfiles `dev,seed` (PC con Docker) o `dev,local,seed` (MySQL nativo con
  `backend/config/application-local.properties`). Después se arranca normal con `dev` (o `dev,local`).
- **Idempotente**: si ya hay ventas no hace nada; si hay productos o movimientos sin ventas, aborta.
- **Reproducible**: `app.semilla.semilla-aleatoria` (42 por defecto) y `app.semilla.fecha-fin` (vacía = ayer en
  America/Bogota; debe ser anterior a hoy). Volumen con `app.semilla.tickets-dia-base` (15: unas 20 mil ventas en 36
  meses).

## Regla crítica

Las ventas, devoluciones, entradas, ajustes, anulaciones, reservas y planes de compra se crean con los **servicios
reales** (`MovimientoService`, `VentaService`, `ReservaService`, `PlanCompraService`…), nunca con inserts directos. Así el
kardex y el stock quedan consistentes y el generador también sirve como prueba de integración.

- Para fechar en el pasado, los servicios usan el `Clock` inyectado; en el perfil `seed` el bean `RelojSimulado`
  (`@Primary`, solo avanza, vuelve al reloj del sistema al terminar) lo reemplaza.
- Los servicios leen el usuario del contexto de seguridad: el generador lo fija por operación (VENDEDOR vende,
  INVENTARIO ingresa, ajusta, reserva, crea y recibe planes, ADMIN anula y aprueba).

## Catálogo

- 6 categorías: Camisetas, Hoodies, Jeans, Joggers, Chaquetas, Accesorios. Las crea la migración
  `V3__crear_tabla_categoria.sql`: el generador las busca por nombre y las reutiliza.
- 30 productos (LIV001 a LIV030), cada uno con 2–4 colores y tallas S–XL (accesorios: UNICA) → 299 variantes.
- Precios realistas en COP (camiseta 59.900–89.900, hoodie 129.900–179.900, jean 119.900–169.900). Costo = 40–55 % del
  precio. Lead time 10–30 días. `stockMinimo` por producto ≈ 2 semanas de demanda.
- Un producto descontinuado (LIV017) se desactiva cerca del final de la historia.

## Demanda

```
unidades(día, producto) ~ Poisson(λ)
λ = base_producto × factor_mes × factor_día_semana × tendencia
```

- `base_producto`: distribución tipo Pareto (pesos Zipf). El 20 % de los productos concentra cerca del 80 % de la
  demanda (garantiza un ABC visible).
- `factor_mes` (estacionalidad de la moda en Colombia): ene 0,8; feb 0,7; mar 0,8; abr 0,85; may 1,0 (día de la madre);
  jun 1,25 (prima); jul 1,0; ago 0,9; sep 0,95 (amor y amistad); oct 1,0; nov 1,3 (Black Friday); dic 1,6 (prima y
  navidad).
- `factor_día_semana`: lun–jue 0,85; vie 1,1; sáb 1,3; dom 1,0.
- `tendencia`: crecimiento de ~1,5 % mensual.
- Distribución por talla: S 20 %, M 35 %, L 30 %, XL 15 %. Por color: el primero de cada producto 50 %, el resto se
  reparte.
- Tres productos «estancados» (LIV004, LIV026, LIV030): demanda baja antes y **ninguna venta ni reserva en los últimos 90
  días** de la historia (`ModeloDemanda.DIAS_ESTANCAMIENTO`; en historias cortas empieza 30 días después del inicio).
  Conservan stock disponible para que se vea la alerta SIN_ROTACION (60 días sin ventas).

## Operación

- Historia de **36 meses** (35 completos al cerrar) hasta la fecha final (ADR-010 D2): Holt-Winters necesita ≥ 30 meses
  y el backtest evalúa 6 meses fuera de muestra.
- **Reposición por planes de compra reales** (ADR-010 D8): cuando la posición (disponible + en camino) de una variante
  cae bajo el punto de pedido propio de la semilla (demanda del lead time + 7 días), se crea un plan por producto y día
  (INVENTARIO), se aprueba a la misma hora (ADMIN) y se recibe al llegar (INVENTARIO; la recepción es la ENTRADA
  `PLAN_COMPRA/{id}`) por 30–60 días de demanda. No usa el ROP ni la sugerencia del sistema. Los pedidos que llegan
  después de la fecha final quedan APROBADOS (en camino); el verificador exige que Σ cantidad aprobada de los planes
  APROBADO coincida con el simulador.
- Algunas variantes se dejan agotar a propósito (nunca se reponen); con el descontinuado, quedan fuera de las ventas
  perdidas «evitables».
- Devoluciones: ~4 % de las ventas, dentro de los 15 días siguientes, 80 % aptas para la venta.
- Reservas: en los últimos 30 días, ~10 activas, algunas vencidas, canceladas y convertidas. Vencen a los pocos días:
  conviene regenerar la víspera de una demostración.
- Usuarios demo, uno por rol: `admin@livorno.co`, `inventario@livorno.co`, `ventas@livorno.co`,
  `contabilidad@livorno.co`, con la contraseña `SEED_PASSWORD` (o `app.semilla.password` en el perfil `local`;
  obligatoria, 12–72 caracteres) y sin cambio de contraseña obligatorio. Si un correo ya existe con el mismo rol y activo,
  se reutiliza sin tocarlo: cuando el ADMIN inicial de la migración V2 es `admin@livorno.co`, ese usuario conserva su
  propia clave.

## Rendimiento

- Una sola transacción para toda la carga (todo o nada), con `flush()` + `clear()` por día simulado; el generador guarda
  solo ids entre días. Las entidades usan `GenerationType.IDENTITY`, así que Hibernate no agrupa los INSERT: el ahorro
  viene de la transacción única y de apagar el listener de alertas (`app.alertas.por-evento=false` en `seed`).
- Duración medida con 36 meses: 2,5–4,5 min según el equipo (la meta inicial de 2 min, pensada para 24 meses, se
  superó y se aceptó porque la carga corre una vez; ADR-010).

## Cierre y verificación

1. Con la carga ya confirmada y el reloj del sistema, corre la ejecución analítica completa de origen SEMILLA (ABC, ROP,
   pronóstico y re-evaluación de todas las alertas). Si falla, la carga queda confirmada y se recupera con «Recalcular»
   como ADMIN.
2. Verifica la consistencia: stock = suma del kardex, reservado = suma de reservas ACTIVAS, nada negativo, en camino del
   simulador = planes APROBADO, y la analítica coherente.
3. Imprime el resumen: usuarios, productos, variantes, ventas (anuladas), unidades e ingreso, ventas perdidas (líneas,
   % evitables, unidades), devoluciones, entradas, ajustes, alzas de precio, planes de compra (creados, recibidos, en
   camino), reservas por estado, alertas activas y, de la analítica de cierre: clases ABC, métodos de ROP, % de variantes
   en alerta de stock con y sin lo en camino, productos con Holt-Winters y SES, WAPE global y la **comparación de la
   corrección por agotados** (ADR-011, D5): lo que agrega la corrección en los 12 meses completos antes del mes de la
   fecha de corte de la analítica, frente a las unidades perdidas comparables de esa ventana (sin variantes agotables a
   propósito ni el descontinuado), y la razón entre ambas (esperado 0,5×–2×; la corrección también ve las variantes
   agotables, lo que sube algo la razón).
