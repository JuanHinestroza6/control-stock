---
name: algoritmos-analitica
description: Fórmulas, pseudocódigo y casos borde de los algoritmos analíticos de Control Stock - demanda diaria, clasificación ABC (Pareto), stock de seguridad, punto de reorden, suavizado exponencial simple, Holt-Winters, backtesting con WAPE, desagregación por variante y sugerencia de cantidad de compra. Úsalo siempre que se implemente, pruebe, depure o explique cualquier cálculo de analítica, predicción o planeación de compras.
---

# Algoritmos de analítica

Todo vive en `analitica.calculo` como funciones puras con pruebas a mano. Los servicios (`AbcService`, `ReordenService`,
`PronosticoService`, `PlanCompraService`) solo consultan datos y llaman a estas funciones.

## 1. Demanda

- Demanda neta diaria por variante = unidades vendidas − unidades devueltas aptas, por día.
- Ventana: últimos 90 días. Los días sin ventas cuentan como 0 (rellenar la serie, error frecuente).
- `dPromedio` = media de la serie; `dDesviacion` = desviación estándar muestral.

## 2. Clasificación ABC (por ingresos de los últimos 90 días)

1. Ingreso por variante = Σ cantidad × precioUnitario de sus ventas.
2. Ordenar de mayor a menor, calcular porcentaje acumulado.
3. A: acumulado ≤ 80%. B: ≤ 95%. C: el resto. Variantes sin ventas → C.
4. El primer ítem siempre es A aunque por sí solo supere el 80%.

Recalcular en la tarea nocturna (`@Scheduled(cron = "0 0 2 * * *", zone = "America/Bogota")`) y bajo demanda (ADMIN).

## 3. Stock de seguridad y punto de reorden

```
Z = 1.65 (clase A, 95%) | 1.28 (B, 90%) | 0.84 (C, 80%)
L = leadTimeDias del producto
SS  = Z × dDesviacion × √L
ROP = ceil(dPromedio × L + SS)
```
- Si hay menos de 30 días con historia → no calcular, usar `stockMinimo` manual.
- Si `dPromedio == 0` → ROP = 0 (no alertar por stock bajo; la alerta SIN_ROTACION cubre el caso).
- ROP nunca menor que `stockMinimo` si este fue configurado: `ROP = max(ROP, stockMinimo)`.

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
