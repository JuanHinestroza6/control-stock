# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 3
- Fase en curso: 3 — Apartados (rama `fase-3-apartados`, sin push): código, revisión y PPI terminados; falta la prueba
  manual T3 del usuario
- Última actualización: 2026-10-04

## Hecho

- [x] Fase 0 — Setup: monorepo, infra, CI, auth JWT, frontend base; PPI REQ-06/REQ-12, convención RNF (PR #1–#3)
- [x] Fase 1 — Núcleo: catálogo y usuarios (ADR-004, ADR-005); PPI REQ-11; T3 sin errores (PR #4)
- [x] Fase 2 — Operación: kardex, MovimientoService, entradas, ajustes, ventas, anulación, devoluciones (ADR-006); PPI
      REQ-01, REQ-02, REQ-08; T3 sin errores (PR #5)
- [x] Fase 3 — plan (docs/fases/fase-3-plan.md) y ADR-007 (reservas y alertas)
- [x] Fase 3 — backend: V11 reservas (crear, cancelar, extender, vencimiento cada 15 min), conversión de varias reservas
      del mismo cliente en una venta, desactivación bloqueada por apartados, V12 alertas STOCK_BAJO/AGOTADO excluyentes
      por evento, V13 + anonimización nocturna (Ley 1581, 90 días); concurrencia probada. 558/558 pruebas
- [x] Fase 3 — frontend: reservas (listado, nueva, cancelar, extender, convertir), indicadores de reservado, alertas y
      campana, rutas por rol. 359/359 pruebas, lint y build OK
- [x] Fase 3 — revisión final: sin críticos; 1 importante (alertas con bloqueo de variante) y 7 menores corregidos
- [x] Fase 3 — PPI: fichas REQ-04 y REQ-13 (Apartados, propuesto); diagramas de estados de Reserva y de conversión

## En curso

- [ ] T3 de la fase 3: prueba manual con el backend real (la hace el usuario; ver "Prueba manual T3 — fase 3")

## Próximos pasos

1. Ejecutar la prueba manual T3 de la fase 3 y reportar lo que falle.
2. Push de `fase-3-apartados` y PR solo cuando el usuario lo pida.
3. Asignar stakeholder, NEC, CU y prioridad de REQ-04 y REQ-13 contra el Word; revisar diagramas con Alt+D.

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409 (decisión del usuario).
- Reservas: hasta 15 min de reservado de más tras vencer (la tarea corre cada 15 min); atender a mano una alerta vigente
  la reabre con el siguiente movimiento (regla del dominio).
- Para fases futuras: ventas ANULADAS fuera de demanda, ABC y rentabilidad (fases 5–7); SIN_ROTACION y re-evaluación
  nocturna de alertas en la fase 5; el generador de datos semilla (fase 4) corre con el perfil `seed`, que apaga las tareas.

## Prueba manual T3 — fase 3 (PowerShell 5, desde la raíz del repo)

1. Arranque igual que en fases anteriores: `.env` completo, `docker compose up -d mysql` (esperar `healthy`), en
   `backend` `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` (Flyway aplica V11–V13 solo) y en `frontend`
   `npm run dev`. Abre http://localhost:5173. Usa los usuarios ADMIN, INVENTARIO, VENDEDOR y CONTABILIDAD de la fase 2.
2. Verificación:
   - [ ] Como VENDEDOR, "Nuevo apartado" con 2 variantes para un cliente (nombre y teléfono) y vigencia 24 h → aparecen
         2 reservas ACTIVAS; el inventario y la matriz muestran el reservado y el disponible baja.
   - [ ] Apartar más que el disponible no se permite.
   - [ ] Como INVENTARIO, extender una reserva 24 h → cambia el vencimiento; pedir más de 7 días desde la creación → error.
   - [ ] Cancelar una reserva con motivo → pasa a CANCELADA y el reservado baja; el kardex muestra LIBERACION "Cancelada".
   - [ ] Como VENDEDOR, seleccionar las reservas ACTIVAS del cliente y "Convertir en venta" con un precio menor que la
         lista → se crea la venta con el descuento y "Origen: apartados R-…"; las reservas quedan CONVERTIDAS.
   - [ ] Intentar convertir reservas de dos clientes distintos no se permite.
   - [ ] Intentar desactivar una variante con un apartado ACTIVO → mensaje con apartados y unidades.
   - [ ] Alertas: vender hasta dejar una variante en 0 → aparece AGOTADO en /alertas y la campana suma; reponer con una
         entrada → la alerta se cierra sola ("Automático"). Con stock bajo el mínimo aparece STOCK_BAJO (nunca ambas).
   - [ ] Como INVENTARIO, "Atender" una alerta activa; como VENDEDOR solo se ven (sin botón).
   - [ ] Como CONTABILIDAD no ve Reservas ni Alertas ni la campana.
   - [ ] (Opcional) Crear una reserva con vigencia 1 h y esperar a que venza: en ≤ 15 min pasa a VENCIDA con LIBERACION.

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT).
- Docker Desktop debe estar arriba: sin él se omiten las pruebas con Testcontainers (no hacer commit en ese caso).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`).
- Las pruebas de integración que borran variantes deben borrar antes `alerta` y `reserva` (claves foráneas).
- CI fijado en `ubuntu-24.04`; el limitador de login es en memoria (`getRemoteAddr()`).
