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
- La demanda observada está censurada por los agotados (ventas perdidas); lo corrige el pronóstico (§4, corrección por agotados).

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
- **ROP con pronóstico (Fase 6, ADR-010)**: si el producto tiene pronóstico, `μL` = Σ tasa_v(día) para los `L` días desde
  el corte, con `tasa_v(día) = pronóstico_v(mes del día) / días del mes` (más allá del horizonte, la tasa del último mes);
  `s` sigue siendo la desviación de la serie diaria observada de 90 días; misma regla NORMAL/POISSON/SIN_HISTORIA/
  SIN_DEMANDA y mismos z. Sin pronóstico → fórmula anterior. Se guarda la fuente (`PRONOSTICO` | `HISTORICA`).
  Ejemplo: octubre 31 (1/día), noviembre 60 (2/día); corte 25-oct, L=15 → μL = 7×1 + 8×2 = 23; s=1,2, clase A →
  SS = 1,65×1,2×√15 = 7,67 → ROP = ceil(30,67) = 31.
- Poisson: suma acumulada iterativa `término_k = término_{k−1} × λ / k` (λ < 5, sin problemas numéricos).
- Ejemplos a mano: d̄=2, s=1, L=16, A → μL=32, SS=6,6, ROP=39. μL=0,46, C → ROP=1. μL=0,14, C → ROP=0. μL=2, A → ROP=5.
- Escritura: UPDATE dirigido desde `analitica.repositorio`, sin tocar `version` (ver dominio, "Analítica").

## 4. Pronóstico (por PRODUCTO, mensual)

La demanda por variante es muy dispersa; se pronostica por producto y luego se desagrega. Todo en `BigDecimal` escala 2
HALF_UP al guardar; las rejillas, SSE y WAPE se calculan en `double` solo dentro de la función pura (no es dinero).

### Serie mensual y corrección por agotados
```
Meses completos desde el primer mes completo del producto hasta el mes anterior al actual (máx. 36; el mes en curso nunca entra).
y_m = Σ vendidas (fecha de la venta, REGISTRADA) − Σ devueltas aptas (fecha de la devolución), recorte a 0 por mes.
Disponibilidad diaria (kardex ordenado por fecha, id): día "sin stock" si abre en 0 y ningún movimiento lo deja > 0;
  frac_{v,m} = días con stock / días existentes de v en el mes. Solo variantes activas.
disp_m = Σ_v w_v·frac_{v,m} / Σ_{v existentes} w_v      (w_v = participación de v en la demanda del producto; total 0 → iguales)
corregida_m = y_m = 0 ? 0 : y_m / max(disp_m, 0,5)
```
Ejemplo: w = 0,75 / 0,25; la segunda sin stock 15 de 30 días → disp = 0,875; observada 70 → corregida 80. Mes sin stock
en todas → piso 0,5 → ×2. La corrección sobreestima si el quiebre fue falta de demanda (por eso el piso).

### Suavizado exponencial simple (SES)
```
nivel_1 = y_1
nivel_t = α·y_t + (1−α)·nivel_{t−1}
pronóstico_{t+h} = nivel_n   (constante)
```
α por rejilla 0.1..0.9 (paso 0.1) minimizando SSE de un paso adelante, Σ_{t=2..n}(y_t − nivel_{t−1})²; empate → menor α.
Ejemplo: α=0,5 sobre 10, 12, 14 → niveles 10, 11, 12,5 → pronóstico 12,5.

### Holt-Winters MULTIPLICATIVO (tendencia + estacionalidad, m = 12)
La estacionalidad de la moda (y de la semilla: `λ = base × factorMes × 1,015^mes`) es multiplicativa: el pico de
diciembre crece con la empresa; un modelo aditivo lo subestimaría.

**Cuándo compite** (si no, el producto usa SES y se guarda el motivo en `motivo_sin_hw`):
- `POCA_HISTORIA`: n < 30 (el entrenamiento más corto del backtest, n − 6, debe tener ≥ 24 meses = 2 temporadas);
- `MES_EN_CERO`: algún mes de la serie corregida es 0 (un índice 0 hace inestable `y / s_{t−12}` y no es estacionalidad
  sino falta de demanda; los entrenamientos del backtest son prefijos de la serie);
- `NIVEL_NO_POSITIVO`: el nivel inicial no es positivo en los 12 meses del primer año, o todas las combinaciones de la
  rejilla llevan el nivel a ≤ 0.

```
Inicialización con dos años; índices como RAZONES respecto de la tendencia, normalizados a media 1:
  ȳ1, ȳ2 = promedios del año 1 y 2 ; tend_0 = (ȳ2 − ȳ1)/12 ; nivel_0 = ȳ1 − 6,5·tend_0
  r_i = y_i / (nivel_0 + i·tend_0), i = 1..12 ; s_i = r_i × 12 / Σ r_j          (media 1)
Para t = 1..n (s_{t−12} = s_i inicial si t ≤ 12):
  ŷ_t     = (nivel_{t−1} + tend_{t−1}) · s_{t−12}              (error de un paso → SSE)
  nivel_t = α(y_t / s_{t−12}) + (1−α)(nivel_{t−1} + tend_{t−1})
  tend_t  = β(nivel_t − nivel_{t−1}) + (1−β)·tend_{t−1}
  s_t     = γ(y_t / nivel_t) + (1−γ)·s_{t−12}
  pronóstico_{n+h} = max(0, (nivel_n + h·tend_n) · s_{n−12+((h−1) mod 12)+1})
```
(α, β, γ) por rejilla {0.1, 0.3, 0.5, 0.7, 0.9}³ minimizando SSE; empate → orden lexicográfico ascendente. Una
combinación cuyo nivel llega a ≤ 0 durante el ajuste se descarta.

Pruebas a mano:
- `y_t = 100·S_t` (S de media 1, sin tendencia) → ȳ1 = 100, tend_0 = 0, s_i = S_i, pronóstico **exacto** (error < 1e-9)
  para cualquier (α, β, γ).
- Inicialización: año 1 = 10 salvo diciembre 16 (ȳ1 = 10,5); año 2 = 11 salvo diciembre 17,6 (ȳ2 = 11,55) →
  tend_0 = 0,0875, nivel_0 = 9,93125; verificar r_1, r_12 y media 1 de s.
- `y_t = (100 + 2t)·S_t`: no es exacto con esta inicialización; exigir WAPE < 2 % a 3 meses.
- Un mes en 0 → MES_EN_CERO; n = 23 → POCA_HISTORIA; tendencia que lleva el nivel a ≤ 0 → combinación descartada.

### Selección del método (backtesting con origen móvil)
```
n = meses de la serie corregida
n < 3        → SIN_HISTORIA (sin pronóstico; ROP y sugerencia usan la demanda histórica)
3 ≤ n < 9    → SES sin backtest (WAPE null)
9 ≤ n < 30   → solo SES, con WAPE del backtest (motivo POCA_HISTORIA)
n ≥ 30       → SES vs HW multiplicativo (salvo MES_EN_CERO / NIVEL_NO_POSITIVO → solo SES)
Backtest: orígenes o ∈ {n−6, n−3}; con y_1..y_o se ajusta (rejilla incluida) y se pronostican o+1..o+3.
WAPE = Σ|y − ŷ| / Σ y sobre los 6 puntos (Σ y = 0 → WAPE null → SES).
Menor WAPE gana; diferencia < 0,001 → SES (parsimonia).
Reajustar el elegido con toda la serie y pronosticar h = 1..3 (mes actual y dos siguientes). Guardar metodo y errorWape.
WAPE global de la ejecución = Σ|e| / Σ y de todos los productos con backtest.
```
Ejemplo WAPE: reales 10, 20, 30; pronóstico 12, 18, 33 → 7/60 = 0,1167. Mostrar el WAPE y el motivo sin HW en la
interfaz: es la evidencia de que la elección no es "a ojo". Limitación: el backtest (6 meses, abril–septiembre con
corte en octubre) no cubre noviembre–diciembre; el WAPE puede subestimar el error en los picos.

### Desagregación a variantes
Ventana 180 días `[corte − 180, corte)`: `part_v = demanda_v / max(fracConStock_v, 0,5)`, normalizada entre las variantes
activas del producto (una talla agotada no debe parecer impopular). Total 0 → todas 0.
`pronóstico_v,mes = pronóstico_producto,mes × part_v` (escala 2). Variantes inactivas: sin fila.

## 5. Sugerencia de compra (política (s, S), no revisión periódica)

Las alertas disparan por ROP (revisión continua); la sugerencia repone exactamente lo que está en alerta, no todo el catálogo.
```
umbral   = puntoReorden ?? stockMinimo ; posición = disponible + enCamino   (enCamino = Σ cantidadAprobada de planes APROBADO)
Candidata: variante y producto activos, posición ≤ umbral, y no está en otro BORRADOR (se informa como omitida).
R = 30 días (app.compras.periodo-revision-dias)
demandaR = Σ tasa_v en [corte + L, corte + L + R)        (sin pronóstico: d̄ observada × R)
S = umbral + demandaR ; sin pronóstico, sin demanda y ROP null: S = 2 × stockMinimo
q = ceil(S − posición) ; q ≤ 0 → no entra ; q = ceil(q / loteMinimo) × loteMinimo
```
Con ROP = ceil(μL + SS), `S` equivale a `μ(L+R) + SS`; solo cambia qué variantes entran. Justificación legible por línea:
"ROP 31 + demanda 30 d 68 − disponible 10 − en camino 0 = 89 → lote 6: 90".
Ejemplo: diciembre 93 (3/día); demanda del 9-nov al 8-dic = 22×2 + 8×3 = 68; S = 99; posición 10 → 89 → lote 6 → 90.
Variante SIN_DEMANDA agotada: S = 0 → no entra (no se compra lo que no se vende). Nunca toca stock: recibir un plan
genera ENTRADAS por `MovimientoService`.

## Casos borde que las pruebas deben cubrir

Serie vacía, serie de un solo valor, serie toda en cero (SES con pronóstico 0, WAPE null), serie constante, menos de 30
meses (cae a SES), mes en 0 (HW fuera), devoluciones mayores que ventas (demanda neta negativa → recortar a 0), producto
nuevo sin historia (< 3 meses), mes sin stock en todas las variantes (piso 0,5), variante creada a mitad de mes,
horizonte del pronóstico agotado dentro del lead time (última tasa), lote mínimo, candidata sin ROP.
