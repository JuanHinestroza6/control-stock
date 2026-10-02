# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 2
- Fase en curso: 2 — Operación (rama `fase-2-operacion`, sin push): código, revisión y PPI terminados; falta la prueba
  manual T3 del usuario
- Última actualización: 2026-10-02

## Hecho

- [x] Fase 0 — Setup: monorepo, infra, CI, auth JWT, frontend base; PPI REQ-06/REQ-12, convención RNF (PR #1–#3)
- [x] Fase 1 — Núcleo: catálogo (categorías, productos, variantes, SKU, matriz) y usuarios (ADR-004, ADR-005); PPI
      REQ-11; T3 manual sin errores (PR #4)
- [x] Fase 2 — plan (docs/fases/fase-2-plan.md) y ADR-006 (punto único de stock con ArchUnit, ANULACION_VENTA,
      409 sin reintento, kardex inmutable, entradas con costo; costo 0 no actualiza precioCompra)
- [x] Fase 2 — backend: V8 kardex, MovimientoService, inventario, entradas/ajustes, kardex, V9 ventas (precioLista,
      costoUnitario), anulación, V10 devoluciones; concurrencia probada. 440/440 pruebas
- [x] Fase 2 — frontend: inventario, entrada por matriz, ajuste, kardex, nueva venta, listado/detalle, devolución,
      anulación, rutas por rol. 304/304 pruebas, lint y build OK
- [x] Fase 2 — revisión final: sin críticos; 1 importante (ArchUnit) y 7 menores corregidos (6705c7c, 898bb19)
- [x] Fase 2 — PPI: fichas REQ-01, REQ-02, REQ-08; diagramas de clases de operación y secuencia de registrar venta

## En curso

- [ ] T3 de la fase 2: prueba manual con el backend real (la hace el usuario; ver "Prueba manual T3 — fase 2")

## Próximos pasos

1. Ejecutar la prueba manual T3 de la fase 2 y reportar lo que falle.
2. Push de `fase-2-operacion` y PR solo cuando el usuario lo pida.
3. Asignar stakeholder, NEC, CU y prioridad de REQ-01, REQ-02 y REQ-08 contra el Word; revisar diagramas con Alt+D.

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409 (decisión del usuario).
- Reglas para fases futuras: las ventas ANULADAS se excluyen de demanda, ABC y rentabilidad (fases 5–7); anular una venta
  que vino de una reserva solo devuelve físico y la reserva queda CONVERTIDA (fase 3).

## Prueba manual T3 — fase 2 (PowerShell 5, desde la raíz del repo)

1. Arranque igual que en la fase 1: `.env` completo, `docker compose up -d mysql` (esperar `healthy`), en `backend`
   `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` (Flyway aplica V8–V10 solo) y en `frontend`
   `npm run dev`. Abre http://localhost:5173 y entra con `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
2. Verificación (crea antes un usuario INVENTARIO, uno VENDEDOR y uno CONTABILIDAD si no existen):
   - [ ] Inventario: el producto con variantes de la fase 1 aparece en 0 y AGOTADO; filtros y búsqueda funcionan.
   - [ ] Como INVENTARIO, "Nueva entrada" con cantidades en la matriz y costo 25000 → el stock sube y el detalle del
         producto muestra precioCompra 25000. Otra entrada con costo 0 → stock sube y precioCompra no cambia.
   - [ ] Ajuste negativo con observación → el stock baja; el kardex de esa variante muestra ENTRADA y AJUSTE con los
         saldos antes → después encadenados.
   - [ ] Como VENDEDOR, nueva venta con 2 variantes, una con precio menor que la lista → el detalle muestra el
         descuento y no muestra el costo; el stock baja. Pedir más que el disponible no se permite.
   - [ ] Devolver 1 unidad apta → stock +1; devolver 1 no apta → stock igual; ambas aparecen en el detalle.
   - [ ] Como ADMIN, anular esa venta → 422 (tiene devoluciones). Anular otra venta sin devoluciones con motivo →
         stock repuesto y el kardex muestra ANULACION_VENTA.
   - [ ] Como CONTABILIDAD: ve inventario, kardex y ventas con costo, sin botones para registrar ni anular.
   - [ ] Como INVENTARIO no ve Ventas; como VENDEDOR no ve Kardex ni entradas.

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). ArchUnit 1.5.1 ya está en `~/.m2`. Si se agregan
  dependencias, ejecutar `./mvnw` con red (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`), sin `grep` intermedios que
  cambien el código de salida.
- El limitador de login es en memoria y usa `getRemoteAddr()`; detrás de un proxy inverso habrá que revisarlo.
- CI fijado en `ubuntu-24.04` (ubuntu-latest pasa a Ubuntu 26 el 2026-10-19); migrar a propósito cuando convenga.
