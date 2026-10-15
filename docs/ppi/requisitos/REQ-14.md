# REQ-14 — Clasificación ABC (FO-ERS)

> Requisito **nuevo**, no incluido en la versión actual del documento Word; se agrega con el código REQ-14 y el caso de
> uso CU-15 (también nuevo; ver la convención de códigos en `docs/dominio.md`).
> Estado a la fecha: implementado en la Fase 5 (backend y frontend), rama `fase-5-analitica`. Decisiones en ADR-009.

| Campo | Contenido |
|---|---|
| **Código** | REQ-14 (nuevo) |
| **Nombre** | Clasificación ABC |
| **Tipo** | Funcional |
| **Stakeholder** | Asistente de gerencia |
| **Necesidad** | NEC-10 |
| **Caso de uso** | CU-15 "Clasificar productos (ABC)" (nuevo) |
| **Relacionados** | REQ-04 (el punto de reorden usa la clase como nivel de servicio); REQ-05 (mismo ingreso neto por variante); REQ-01 (la clase se muestra en inventario); REQ-08 (las devoluciones restan del ingreso) |
| **Prioridad** | Media |
| **Módulos** | `analitica` (`ClasificadorAbc`, `CalculoAnaliticoService`, orquestador de ejecución, `AnaliticaNocturnaTarea`, `AnaliticaController`), `inventario` (columna Clase); frontend: `paginas/analitica` (pestaña ABC y panel de ejecución), `paginas/inventario` |

## Descripción

El sistema debe clasificar las variantes activas en clases A, B y C según su aporte al ingreso, de modo que la gerencia
identifique las que concentran las ventas y la reposición se priorice en consecuencia. La clase además determina el nivel
de servicio con el que se calcula el punto de reorden.

## Alcance implementado (Fase 5)

- **Unidad y criterio**: por variante, según el ingreso neto de los últimos 12 meses (`[corte − 365 días, corte)`, con
  corte igual a hoy, sin el día en curso). Ingreso neto = Σ (cantidad − devuelta) × `precioUnitario` de ventas REGISTRADA.
- **Cortes** sobre el acumulado, en orden descendente (desempate por id): A hasta 80 %, B hasta 95 %, C el resto. La
  primera variante siempre es A; sin ingreso, C. Universo: variantes activas de productos activos; las demás quedan con
  `claseAbc` nula.
- **Consumidor: punto de reorden**. El nivel de servicio depende de la clase: A 95 % (z = 1,65), B 90 % (z = 1,28), C
  80 % (z = 0,84); sin clase se usa C. Detalle en REQ-04.
- **Ejecución**: nocturna a las 02:00 (America/Bogota) y manual con "Recalcular" (solo ADMIN, síncrona). Una ejecución a la
  vez: el recálculo con otra en curso responde 409 y la nocturna se omite. Cada ejecución queda registrada (origen
  NOCTURNA, MANUAL o SEMILLA; estado; conteos; métrica de alertas).
- **Escritura**: `claseAbc` y `puntoReorden` se actualizan con un UPDATE dirigido desde `analitica.repositorio`, sin tocar
  `version` ni el stock (no provoca 409 en las ventas concurrentes).
- **Interfaz**: pestaña ABC (tarjetas por clase, ventana analizada, filtros por clase, categoría y texto, orden y
  paginación), panel "Última ejecución" y columna Clase en inventario (visible a todos los roles; no revela costos).

### Endpoints

| Método y ruta | Rol |
|---|---|
| `GET /api/analitica/abc`, `GET /api/analitica/abc/resumen` | ADMIN, INVENTARIO, CONTABILIDAD |
| `GET /api/analitica/reorden` | ADMIN, INVENTARIO, CONTABILIDAD |
| `GET /api/analitica/ejecuciones/ultima` | ADMIN, INVENTARIO, CONTABILIDAD |
| `POST /api/analitica/recalcular` | ADMIN |

### Flujo de clasificar productos (CU-15)

| Paso | Actor | Sistema |
|---|---|---|
| 1 | El administrador abre Analítica y pulsa "Recalcular" (o llega la ejecución nocturna) | Verifica que no haya otra ejecución en curso y la registra como EN_CURSO |
| 2 | Confirma | Calcula el ingreso neto de 12 meses, clasifica y calcula el ROP en una sola transacción |
| 3 | | Re-evalúa las alertas de todas las variantes y registra la ejecución EXITOSA con sus conteos y la métrica |
| 4 | El asistente de gerencia abre la pestaña ABC | Muestra las tarjetas por clase y el detalle por variante con ingreso y acumulado |

- **Caminos alternativos**: otra ejecución en curso → 409; falla del cálculo → la ejecución queda FALLIDA, los valores
  previos se conservan y las alertas se re-evalúan igual; sin ejecuciones previas → la pestaña invita a recalcular (ADMIN)
  o muestra un aviso; variante ocupada por una venta → se salta y se reintenta (5 × 200 ms); rol VENDEDOR → 403.
- **Puntos de extensión**: consultar el punto de reorden de cada variante (REQ-04); planeación de compras (REQ-10, Fase 6).
- **Poscondición**: cada variante activa con clase A, B o C; stock y kardex sin cambios.

## Resultados de la prueba T2 (2026-10-15, semilla de 24 meses)

Ejecución SEMILLA exitosa en 4.951 ms; 291 variantes activas analizadas. Ingreso neto de 12 meses: $1.197.540.900.

| Clase | Variantes | Ingreso neto | Participación |
|---|---|---|---|
| A | 79 | $956.487.400 | 79,9 % |
| B | 86 | $181.121.400 | 15,1 % |
| C | 126 | $59.932.200 | 5,0 % |

## Criterios de aceptación

| # | Criterio | Estado | Pruebas |
|---|---|---|---|
| CA-1 | Cortes 80/95 sobre el acumulado, primera siempre A, sin ingreso C, desempate por id | Cumplido | `ClasificadorAbcTest` |
| CA-2 | El ingreso neto excluye anuladas y resta devoluciones; inventario muestra la clase | Cumplido | `AnaliticaFlujoIntegracionTest`, `ConsultasVentaAnaliticaIntegracionTest` |
| CA-3 | El nivel de servicio del ROP depende de la clase (sin clase usa C) | Cumplido | `CalculadoraReordenTest`, `CalculadoraReordenMonteCarloTest` |
| CA-4 | Consulta ABC con filtros, orden y paginación; excluye inactivas; orden inválido o clase inválida → 400 | Cumplido | `AnaliticaConsultasIntegracionTest`, `AnaliticaControllerConsultasTest` |
| CA-5 | La ejecución queda registrada con conteos y métrica; el fallo del cálculo no impide re-evaluar alertas | Cumplido | `EjecucionAnaliticaServiceIntegracionTest`, `EjecucionAnaliticaServiceGuardiaTest`, `EjecucionAnaliticaPorcentajeTest` |
| CA-6 | Una sola ejecución a la vez (409 al manual; la nocturna se omite) | Cumplido | `EjecucionAnaliticaServiceIntegracionTest`, `AnaliticaNocturnaTareaTest`, `AnaliticaControllerConsultasTest` |
| CA-7 | La tarea nocturna está programada a las 02:00 de Bogotá y no propaga errores | Cumplido | `AnaliticaNocturnaTareaTest` |
| CA-8 | El cálculo no modifica stock físico, reservado ni `version` de ninguna variante | Cumplido | `AnaliticaFlujoIntegracionTest` |
| CA-9 | Roles: lecturas para ADMIN, INVENTARIO y CONTABILIDAD; recalcular solo ADMIN; VENDEDOR 403; sin token 401 | Cumplido | `AnaliticaConsultasIntegracionTest`, `AnaliticaControllerConsultasTest`, `AnaliticaFlujoIntegracionTest` |
| CA-10 | La carga de la semilla cierra clasificando todas las variantes y guarda la ejecución SEMILLA | Cumplido | `SemillaIntegracionTest` |
| CA-11 | La pestaña ABC, el panel de ejecución y la columna Clase respetan el rol y los estados vacío y de error | Cumplido | `PestanaAbc.test.tsx`, `AnaliticaPage.test.tsx`, `InventarioPage.test.tsx` |

## Observaciones

- Requisito nuevo: no existe en el Word; incorporar REQ-14 y CU-15 en la próxima versión. Stakeholder (Asistente de
  gerencia), necesidad (NEC-10) y prioridad (Media) fueron aportados por el equipo.
- Inconsistencias del Word: REQ-08 y REQ-09 con nombre y descripción cruzados; NEC-010 debería ser NEC-10.
- Decisión D1 (ADR-009): ABC por variante y 12 meses, en lugar de 90 días o por producto, para evitar que la
  estacionalidad (diciembre × 1,6) reclasifique y haga oscilar el nivel de servicio.
- No se conserva historial de clases: solo la última instantánea; el historial de ejecuciones sí queda.
