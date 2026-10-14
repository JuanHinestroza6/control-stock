# Plan APROBADO — Fase 4 (Datos semilla)

Rama `fase-4-datos-semilla`. Base para REQ-05, REQ-09, REQ-10 y la clasificación ABC (Fases 5–6); ejercita REQ-01, REQ-02,
REQ-04, REQ-08 y Apartados con volumen real. Reglas: `docs/dominio.md` sección "Datos semilla", ADR-006, ADR-007 y
ADR-008. Especificación de la demanda: skill `datos-semilla`.

**Regla de oro 1:** el generador solo usa servicios reales; el stock cambia solo con `MovimientoService.registrar(...)`.

## Decisiones confirmadas por el usuario (2026-10-04)
- D1 Fechas pasadas: `semilla.RelojSimulado extends Clock`, `@Primary` + `@Profile("seed")`. `avanzarA(LocalDateTime)`
  solo avanza (retroceder → `IllegalStateException`); `volverAlSistema()` al terminar. Servicios y `DateTimeProvider`
  toman el Clock primario sin cambios. Las operaciones de cada día se ordenan por hora antes de ejecutarlas.
- D2 Usuario que actúa: `ComoUsuario.ejecutar(usuario, accion)` fija un `UsuarioAutenticado` en el `SecurityContext` y lo
  limpia en `finally`. VENDEDOR: ventas, devoluciones, conversión. INVENTARIO: entradas, ajustes, reservas, cancelaciones.
  ADMIN: anulaciones y cambio de precios. Vencimientos: `ReservaService.vencer(id, ahora)` (`usuarioId` null).
- D3 Una sola transacción (`TransactionTemplate`) para toda la carga, `flush()` + `clear()` por día simulado; el generador
  guarda solo ids entre días. Una `ExcepcionNegocio` dentro de un servicio marca rollback-only: el generador valida el
  disponible antes de vender o reservar y cualquier error aborta todo (la base queda como estaba).
- D4 Alertas al final: `AlertaListener` con `@ConditionalOnProperty(name = "app.alertas.por-evento", havingValue = "true",
  matchIfMissing = true)`; `false` en `application-seed.yml`. Al final, con el reloj real, `AlertaService.evaluar(id)` por
  cada variante (orden por id).
- D5 Idempotencia y protección: con ventas → log "ya hay datos" y no hace nada (código 0); con productos o movimientos sin
  ventas → aborta (código 1, mensaje "base no vacía: docker compose down -v"); perfil `prod` activo → aborta. Sin borrado en
  código.
- D6 Salir al terminar: `SpringApplication.exit` + `System.exit` (0 éxito, 1 fallo) si `app.semilla.salir-al-terminar`.
- D7 Determinismo: `Random` con `app.semilla.semilla-aleatoria` (42), sin iterar `HashMap`/`HashSet` en decisiones
  aleatorias, Poisson implementado a mano. `app.semilla.fecha-fin` (vacía = ayer; debe ser anterior a hoy, si no
  aborta): la historia termina ese día 23:59; las reservas ACTIVAS se crean en sus últimas 48 h. El cierre (alertas) usa
  el reloj real.
- D8 Usuarios demo `admin@`, `inventario@`, `ventas@`, `contabilidad@livorno.co` con `UsuarioRepository` +
  `PasswordEncoder` (no `UsuarioService.crear`, que marca temporal); `SEED_PASSWORD` obligatoria, validada con la política
  (12–72 caracteres, ≤ 72 bytes, no empieza por `cambiar`). Si el correo existe, se reutiliza.
- D9 Módulo nuevo `semilla`; ArchUnit: ninguna clase fuera de `co.livorno.controlstock.semilla..` depende de él.
- D10 Sin migraciones y sin frontend.

## Configuración (`application-seed.yml`)
```yaml
app:
  tareas:
    habilitadas: false
  alertas:
    por-evento: false
  semilla:
    ejecutar: true
    salir-al-terminar: true
    semilla-aleatoria: 42
    meses: 24
    fecha-fin:            # vacía = ayer (America/Bogota); debe ser anterior a hoy
    tickets-dia-base: 12  # se calibra en T1 para unas 12 mil ventas
    password: ${SEED_PASSWORD:}
```
`.env.example`: agregar `SEED_PASSWORD=` con el comentario de la política. Pruebas: `ejecutar=false`,
`salir-al-terminar=false`.

## Backend: piezas (`co.livorno.controlstock.semilla`, todo `@Profile("seed")`)
- `PropiedadesSemilla` (record `@ConfigurationProperties("app.semilla")`) con validación de rangos.
- `RelojSimulado`, `ComoUsuario`.
- `CatalogoSemilla`: datos fijos de unos 30 productos (código `LIV001…`, nombre, categoría de V3 por nombre, precio,
  costo base, colores, lead time, lote mínimo, marcas `estancado` / `descontinuado`). 6 categorías: se buscan, no se crean.
- `ModeloDemanda` (puro, sin Spring): λ diaria, Poisson, pesos Zipf, factores de mes y día, tendencia, mezcla
  talla/color, elección de líneas y cantidades.
- `GeneradorDatosSemilla.generar(): ResumenSemilla`: guardas, usuarios, catálogo, simulación día a día, reservas, cierre.
- `VerificadorConsistencia` (JdbcTemplate, solo lectura): invariantes; lanza excepción si alguna falla.
- `EjecutorSemilla` (`CommandLineRunner`, `@ConditionalOnProperty(app.semilla.ejecutar)`): protecciones, ejecución,
  resumen en el log, salida.

## Datos y distribución
- Catálogo con `ProductoService.crear` y `VarianteService.generar`: 2–4 colores × S–XL (UNICA en accesorios), unas 300
  variantes; precios en COP del skill; costo 40–55 %; lead time 10–30 días; `stockMinimo` por producto ≈ 2 semanas de
  demanda esperada (mínimo 2). Fecha de creación = inicio de la historia.
- Ventas (`VentaService.registrar`), unas 12 mil: tickets/día ~ Poisson(base × mes × día × 1,015^mes) con los factores del
  skill; líneas por ticket 1/2/3 (70/22/8 %); producto por pesos Zipf calibrados (20 % de productos ≈ 75–80 % de
  unidades); talla S/M/L/XL 20/35/30/15, primer color 50 %; cantidad 1 (85 %) o 2; hora 09:00–21:00; 15 % de las líneas
  con descuento 5–20 % (redondeado a 100). Sin disponible se vende lo que haya; el resto cuenta como venta perdida.
- Estancados: 2–3 productos con demanda ≈ 0 los últimos 3 meses y físico > 0. Descontinuado: 1 producto deja de
  reponerse hace 6 meses, se agota y se desactiva (`ProductoService.cambiarEstado`).
- Entradas (`OperacionInventarioService.registrarEntrada`, una por producto que llega, 08:00): día 0 stock de 45 días de
  demanda; cada día 07:00, si disponible + en camino ≤ `stockMinimo` → llegada en `leadTimeDias` por 30–60 días de demanda
  (≥ lote mínimo). Costo = base × 1,004^mes ± 2 %, redondeado a 100. Algunas variantes se dejan agotar.
- Precios: cada enero `precioVenta` +5 % con terminación 900 (`ProductoService.actualizar`).
- Ajustes (`registrarAjuste`): unos 2 por mes, positivos y negativos, con observación ("Conteo físico", "Merma").
- Devoluciones (`DevolucionService.registrar`): 4 % de las líneas, 1–15 días después, 80 % aptas, motivos de una lista;
  nunca sobre ventas anuladas.
- Anulaciones (`VentaService.anular`): 0,7 % de las ventas, mismo día, solo ventas sin devolución programada.
- Reservas (últimos 30 días de la historia, unas 3/día, nombres y contactos ficticios): 55 % convertidas en 1–48 h
  (`registrarDesdeReservas`, a veces 2 del mismo cliente), 15 % canceladas con motivo, 20 % vencidas
  (`vencer(id, venceEn + ≤15 min)`), unas 10 ACTIVAS creadas en las últimas 48 h de la historia con vigencia 72–168 h.
- Cierre: reloj real, alertas de todas las variantes, verificación, resumen (productos, variantes, ventas, perdidas,
  anuladas, devoluciones, movimientos, ingreso, reservas por estado, alertas activas por tipo, duración).

## Tareas (cada una cierra con un commit y pruebas en verde)
Paso 0 (coordinador): copiar este plan, ADR-008, `docs/dominio.md`, el skill `datos-semilla` y `CLAUDE.md`.

Backend (`backend-dev`):
- B1 `RelojSimulado`, `PropiedadesSemilla`, `ComoUsuario`, `application-seed.yml`, `.env.example`, listener condicional,
  regla ArchUnit del módulo. Pruebas: reloj (avanza, no retrocede, vuelve al sistema); contexto sin `seed` usa el reloj
  del sistema y registra `AlertaListener`; con `por-evento=false` no lo registra.
- B3 (tras B1 y B2) Protecciones, usuarios demo, catálogo, entrada inicial.
- B4 Simulación diaria: ventas, reposición, ajustes, precios, devoluciones, anulaciones, descontinuado.
- B5 Reservas, cierre (alertas, `VerificadorConsistencia`, resumen) y `EjecutorSemilla` con salida.

Analítica (`analitica`), en paralelo con B1:
- B2 `ModeloDemanda` + `CatalogoSemilla` con pruebas puras.

Pruebas (`tester`):
- B6 (tras B5) `SemillaIntegracionTest` con invariantes.
- T1 (usuario) Corrida completa con `dev,seed` sobre base vacía: medir tiempo, revisar el resumen y la interfaz con los
  cuatro usuarios demo (inventario, kardex, ventas, devoluciones, reservas, alertas). Ajustar `tickets-dia-base` si pasa
  de 2 min.

Documentación (`documentador-ppi`, tras T1):
- D1 Sección "Datos de demostración" para el documento PPI y el ensayo de sustentación: volumen real del resumen de T1,
  cómo regenerar (`docker compose down -v`, `SEED_PASSWORD`, fecha final fija) y usuarios demo.

Recorte si falta tiempo (en este orden): cambio de precios en enero, producto descontinuado, ajustes, conversión de dos
reservas en una venta.

## Pruebas
- `RelojSimuladoTest`: avanza, rechaza retroceder, `volverAlSistema`.
- `ModeloDemandaTest` (B2): misma semilla → misma secuencia; media de Poisson ±5 % en 10 mil muestras; top 20 % de
  productos ≥ 70 % de unidades; diciembre > junio > febrero en λ; estancados ≈ 0 en los últimos 3 meses; mezcla de tallas
  ±3 %.
- `SemillaIntegracionTest` (Testcontainers; perfiles `test,seed`; `ejecutar=false`; 6 productos, 3 meses,
  `fecha-fin` 2025-12-31; `SEED_PASSWORD` de prueba):
  - Por variante: físico = Σ Δ físico del kardex; reservado = Σ Δ reservado = Σ cantidades de reservas ACTIVAS.
  - Nada negativo: físico, reservado, disponible.
  - Kardex encadenado por variante (`fisicoDespues(n) = fisicoAntes(n+1)` ordenado por id), fecha no decreciente por id,
    fechas de movimientos, ventas y reservas dentro de [inicio, fecha-fin 23:59].
  - Total de cada venta = Σ cantidad × precioUnitario; hay líneas con `precioUnitario < precioLista`.
  - Devuelto ≤ vendido por línea; sin devoluciones en ventas ANULADAS; cada ANULADA tiene un ANULACION_VENTA por línea.
  - Al menos una reserva en cada estado; las CONVERTIDAS tienen `ventaId`; VENCIDAS con `cerradaPor` null.
  - Alertas: para cada variante activa, la alerta ACTIVA coincide con `AlertaService.tipoVigente`; ninguna en estado OK.
  - Usuarios demo: 4 roles, `debeCambiarPassword` false, login con `AuthService` y `SEED_PASSWORD`.
  - Unidades de diciembre > octubre.
  - Segunda llamada a `generar()` → no crea filas.
  - Sin `SEED_PASSWORD` → falla con mensaje claro y sin filas nuevas; un error forzado a mitad de carga → rollback total.
- ArchUnit: nadie depende de `semilla`; las reglas de oro existentes siguen en verde.

## Riesgos
- `RelojSimulado` fuera de la carga: solo en `seed`, la aplicación sale al terminar y el reloj vuelve al sistema.
- Tiempo: `IDENTITY` impide agrupar INSERT (unos 150 mil statements). Meta < 2 min; si se pasa, bajar `tickets-dia-base`.
- Transacción larga: `flush`/`clear` por día y solo ids entre días; si la memoria no alcanza, pasar a commit por mes
  (requiere nuevo acuerdo: rompe el todo o nada).
- Reservas ACTIVAS vencen pocos días después: regenerar la víspera con `fecha-fin` fija.
- Demanda censurada por agotados: documentarla para el pronóstico (Fase 6).
- `SecurityContext` olvidado entre operaciones: `ComoUsuario` con `finally`; un solo hilo.
- Fase 5: SIN_ROTACION verá los productos estancados; la re-evaluación nocturna repite la evaluación final.
