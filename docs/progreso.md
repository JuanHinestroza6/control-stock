# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 3
- Fase en curso: 4 — Datos semilla (rama `fase-4-datos-semilla`, sin push): generador, revisión y pruebas terminados;
  falta la corrida real T1 del usuario y la sección PPI "Datos de demostración"
- Última actualización: 2026-10-04

## Hecho

- [x] Fase 0 — Setup: monorepo, infra, CI, auth JWT, frontend base; PPI REQ-06/REQ-12, convención RNF (PR #1–#3)
- [x] Fase 1 — Núcleo: catálogo y usuarios (ADR-004, ADR-005); PPI REQ-11; T3 sin errores (PR #4)
- [x] Fase 2 — Operación: kardex, MovimientoService, ventas, anulación, devoluciones (ADR-006); PPI REQ-01/02/08 (PR #5)
- [x] Fase 3 — Apartados: reservas, conversión en venta, alertas por evento, anonimización Ley 1581 (ADR-007); PPI REQ-04
      y REQ-13 (nuevo, NEC-11, CU-14); T3 sin errores (PR #6)
- [x] Fase 4 — plan (docs/fases/fase-4-plan.md) y ADR-008 (reloj simulado, una transacción, alertas al final, salir)
- [x] Fase 4 — módulo `semilla`: 30 productos / 299 variantes, 24 meses con estacionalidad colombiana y Pareto por
      grupos (20 % superior ≈ 77,6 %, mayor ≈ 15,7 %), reposición por lead time (pérdidas evitables 2,5 %), devoluciones,
      anulaciones, reservas en todos los estados, verificador de consistencia, usuarios demo, protecciones. 644/644 pruebas
- [x] Fase 4 — revisión final: sin críticos; 2 importantes (solo dev/test, sin servidor web en seed) y 5 menores corregidos

## En curso

- [ ] T1: corrida real del generador y revisión en la interfaz (la hace el usuario; ver "Prueba T1 — fase 4")

## Próximos pasos

1. Ejecutar la prueba T1 y reportar tiempos, resumen y lo que se vea raro en la interfaz (p. ej. cantidad de alertas).
2. Sección PPI "Datos de demostración" (documentador) con los números reales de T1.
3. Push de `fase-4-datos-semilla` y PR solo cuando el usuario lo pida.

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409.
- Semilla: las alertas se evalúan tras el commit de la carga (si fallan, una nueva ejecución las re-evalúa); ~120
  STOCK_BAJO activas al final (revisar en T1 si es demasiado ruido para la campana).
- Para fases futuras: ventas ANULADAS fuera de demanda, ABC y rentabilidad (fases 5–7); SIN_ROTACION y re-evaluación
  nocturna de alertas en la fase 5; la demanda observada está censurada por las ventas perdidas (2,5 %, fase 6).

## Prueba T1 — fase 4 (PowerShell 5, desde la raíz del repo)

**Atención: la carga exige una base vacía. El paso 2 BORRA todos los datos de tu base de desarrollo local** (productos,
ventas y usuarios creados en las pruebas T3). El ADMIN de `ADMIN_EMAIL` se vuelve a crear solo al migrar.

1. En `.env` agrega `SEED_PASSWORD=` con una contraseña de 12–72 caracteres que no empiece por "cambiar". Es la
   contraseña de los 4 usuarios demo.
2. Base vacía: `docker compose down -v` y luego `docker compose up -d mysql` (esperar `healthy` con `docker compose ps`).
3. Carga: `cd backend` y `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,seed"`. No levanta servidor web;
   tarda ~2–3 min, imprime el resumen y termina sola. Anota el tiempo y el resumen.
4. Arranque normal: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` y en otra terminal `cd frontend`,
   `npm run dev`. Abre http://localhost:5173.
5. Verificación con cada usuario demo (contraseña = `SEED_PASSWORD`):
   - [ ] `admin@livorno.co`: inventario con stock y estados variados; ~30 productos en 6 categorías; LIV017 inactivo.
   - [ ] `ventas@livorno.co`: ~12 mil ventas en 24 meses, con descuentos, devoluciones y algunas anuladas; reservas en
         los 4 estados (unas pocas ACTIVAS que vencen en los próximos días).
   - [ ] `inventario@livorno.co`: kardex de una variante con entradas, ventas y ajustes encadenados; alertas activas.
   - [ ] `contabilidad@livorno.co`: ve ventas con costo; no ve reservas ni alertas.
   - [ ] La campana: ¿la cantidad de alertas (~150) te parece razonable para la demo?
6. Volver a ejecutar el paso 3 sin borrar: debe decir que ya hay datos y no crear nada.

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT).
- Docker Desktop debe estar arriba: sin él se omiten las pruebas con Testcontainers (no hacer commit en ese caso).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`).
- Las pruebas que borran variantes deben borrar antes `alerta` y `reserva`; las de semilla usan `LimpiezaSemilla`.
- Las reservas ACTIVAS de la semilla vencen a los pocos días: regenerar la víspera de una demo.
- CI fijado en `ubuntu-24.04`; el limitador de login es en memoria (`getRemoteAddr()`).
