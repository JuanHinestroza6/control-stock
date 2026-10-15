---
name: algoritmos-analitica
description: Fórmulas, pseudocódigo y casos borde de los algoritmos analíticos de Control Stock - demanda diaria, clasificación ABC (Pareto), stock de seguridad, punto de reorden, suavizado exponencial simple, Holt-Winters, backtesting con WAPE, desagregación por variante y sugerencia de cantidad de compra. Úsalo siempre que se implemente, pruebe, depure o explique cualquier cálculo de analítica, predicción o planeación de compras.
---

# Algoritmos de analítica

Todo vive en `analitica.calculo` como funciones puras con pruebas a mano. Los servicios (`AbcService`, `ReordenService`,
`PronosticoService`, `PlanCompraService`) solo consultan datos y llaman a estas funciones.

## 1. Demanda (ADR-009)

Solo ventas REGISTRADA (las ANULADAS nunca cuentan). Hay dos definiciones, una por propósito:
- **Comercial** (ABC, más vendidos): unidades netas = vendidas − devueltas (todas), atribuidas a la fecha de la venta;
  ingreso neto = Σ (cantidad − devuelta) × precioUnitario.
- **Inventario** (ROP): por día, vendidas (fecha de la venta, incluidas VENTA_RESERVA) − devueltas **aptas** (fecha de la
  devolución), recortada a 0. Una devolución no apta no vuelve al estante: es consumo. Las reservas ACTIVAS no son demanda.
- Ventana del ROP: últimos 90 días, `[corte − 90 d, corte)` con corte = hoy (excluye el día en curso).
  `n = min(90, días desde la creación de la variante)`. Los días sin ventas cuentan como 0 (rellenar la serie, error
  frecuente).
- `dPromedio` = Σ serie / n; `dDesviacion` = desviación estándar muestral (n = 1 → 0).
- La demanda observada está censurada por los agotados (ventas perdidas); lo corrige el pronóstico de la Fase 6.

## 2. Clasificación ABC (por variante, ingreso neto de los últimos 12 meses)

1. Universo: variantes activas de productos activos. Ventana `[corte − 365 d, corte)` (un ciclo estacional completo:
   diciembre ×1,6 no voltea las clases).
2. Ordenar por ingreso neto descendente, desempate por id de variante; acumulado en BigDecimal escala 6 HALF_UP.
3. A: acumulado ≤ 80 %. B: ≤ 95 %. C: el resto. Ingreso 0 → C; total 0 → todas C. Inactivas → clase null.
4. El primer ítem siempre es A aunque por sí solo supere el 80 %.

Ejemplo de prueba: 500, 200, 150, 100, 50 → acumulados 0,50 / 0,70 / 0,85 / 0,95 / 1,00 → A, A, B, B, C.

Recalcular en la ejecución nocturna (`app.analitica.cron = "0 0 2 * * *"`, zona America/Bogota) y bajo demanda (ADMIN).

## 3. Stock de seguridad y punto de reorden (ROP híbrido, sin piso)

```
Clase A z=1,65 p=0,95 | B z=1,28 p=0,90 | C z=0,84 p=0,80 ; sin clase → C
L = leadTimeDias del producto
n < 30 días de historia  → ROP = null (umbral = stockMinimo)          método SIN_HISTORIA
dPromedio == 0           → ROP = 0 (solo AGOTADO; SIN_ROTACION cubre)  método SIN_DEMANDA
μL = dPromedio × L
μL ≥ 5 → SS = z × dDesviacion × √L ; ROP = ceil(μL + SS)              método NORMAL
μL < 5 → ROP = mín r con P(Poisson(μL) ≤ r) ≥ p ; SS = ROP − μL        método POISSON (demanda intermitente)
```
- **Sin piso**: `stockMinimo` es solo el respaldo cuando `puntoReorden` es null (dominio). No usar `max(ROP, stockMinimo)`.
- `loteMinimo` no interviene en el ROP (se usa en la sugerencia de compra).
- Poisson: suma acumulada iterativa `término_k = término_{k−1} × λ / k` (λ < 5, sin problemas numéricos).
- Ejemplos a mano: d̄=2, s=1, L=16, A → μL=32, SS=6,6, ROP=39. μL=0,46, C → ROP=1. μL=0,14, C → ROP=0. μL=2, A → ROP=5.
- Escritura: UPDATE dirigido desde `analitica.repositorio`, sin tocar `version` (ver dominio, "Analítica").

## 4. Pronóstico (por PRODUCTO, mensual)

La demanda por variante es muy dispersa; se pronostica por producto y luego se desagrega.

### Suavizado exponencial simple (SES)
```
nivel_1 = y_1
nivel_t = α·y_t + (1−α)·nivel_{t−1}
pronóstico_{t+h} = nivel_t   (constante)
```
α por búsqueda en rejilla 0.1..0.9 (paso 0.1) minimizando SSE de un paso adelante.

### Holt-Winters aditivo (tendencia + estacionalidad, m = 12)
Requiere al menos 24 meses (2 temporadas). Si hay menos, usar SES.
```
Inicialización:
  nivel_0     = promedio del primer año
  tendencia_0 = (promedio año 2 − promedio año 1) / 12
  estacional_i = y_i − promedio año 1, i = 1..12
Actualización:
  nivel_t     = α(y_t − s_{t−m}) + (1−α)(nivel_{t−1} + tend_{t−1})
  tend_t      = β(nivel_t − nivel_{t−1}) + (1−β)·tend_{t−1}
  s_t         = γ(y_t − nivel_t) + (1−γ)·s_{t−m}
  pronóstico_{t+h} = nivel_t + h·tend_t + s_{t−m+((h−1) mod m)+1}
```
α, β, γ por rejilla {0.1, 0.3, 0.5, 0.7, 0.9}³ minimizando SSE. Recortar pronósticos negativos a 0.

### Selección del método (backtesting)
1. Reservar los últimos 3 meses como prueba.
2. Ajustar SES y HW con el resto; pronosticar 3 meses.
3. `WAPE = Σ|real − pronóstico| / Σ real`. Elegir el menor. Guardar `metodo` y `errorWape` en `Pronostico`.
4. Reajustar el elegido con toda la serie y pronosticar los próximos 3 meses.
Mostrar el WAPE en la interfaz: es la evidencia de que la "IA" funciona.

### Desagregación a variantes
`participación_variante = ventas de la variante / ventas del producto` en los últimos 180 días.
`pronóstico_variante = pronóstico_producto × participación` (variantes sin ventas → participación 0).

## 5. Sugerencia de compra

```
R = 30 días (periodo de revisión)
demandaCobertura = pronóstico diario × (L + R)        (pronóstico mensual / 30)
cantidad = demandaCobertura + SS − disponible − enTransito
cantidad = max(0, cantidad) redondeado hacia arriba al múltiplo de loteMinimo
```
`enTransito` = cantidades de planes APROBADOS no recibidos. Incluir una `justificacion` legible:
"Pronóstico 42 u/mes, lead time 15 d, SS 6, disponible 10 → sugerido 59".

## Casos borde que las pruebas deben cubrir

Serie vacía, serie de un solo valor, serie toda en cero, serie constante, menos de 24 meses (cae a SES),
devoluciones mayores que ventas en un día (demanda neta negativa → recortar a 0), producto nuevo sin historia.
