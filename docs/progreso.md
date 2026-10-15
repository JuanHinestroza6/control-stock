# Progreso

Bitácora viva del proyecto. Se actualiza con `/cerrar-sesion`. Mantener corto: estado actual, no historia completa.

## Estado actual

- Semana: 3
- Fase en curso: 4 — Datos semilla terminada (rama `fase-4-datos-semilla` publicada); siguiente: Fase 5 (analítica base)
- Última actualización: 2026-10-13

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
- [x] Fase 4 — T1: corrida real completa y correcta (2026-10-13): carga 107,4 s (Maven 1:54); 12.148 ventas (83 anuladas),
      18.592 unidades, ingreso $2.196.015.600; pérdidas evitables 3,0 %; 666 devoluciones; 91 reservas (13 activas);
      alertas AGOTADO 30 y STOCK_BAJO 111; la segunda ejecución no creó nada
- [x] Fase 4 — PPI: sección "Datos de demostración" (docs/ppi/datos-demostracion.md) con los números de T1

## En curso

- [ ] PR de `fase-4-datos-semilla` hacia main (lo abre el usuario cuando lo decida)

## Próximos pasos

1. PR y merge de `fase-4-datos-semilla`; luego `git switch main && git pull`.
2. Iniciar `/fase 5`: alertas nocturnas y SIN_ROTACION, clasificación ABC, punto de reorden dinámico, más vendidos.
3. Tras el ROP de la fase 5, revisar el umbral de alertas (ver "Bloqueos y decisiones").

## Bloqueos y decisiones pendientes (aceptados, sin acción)

- Validación del body antes de `@PreAuthorize`: un rol sin permiso con body inválido recibe 400 en vez de 403.
- Revocación por `iat` con precisión de segundos: un token emitido en el mismo segundo del cambio sigue válido (ADR-005).
- Desactivación simultánea de los dos últimos ADMIN: el perdedor del deadlock de MySQL recibe 409.
- Semilla: la analítica (ABC, ROP y alertas) corre tras el commit de la carga; si falla, la carga queda confirmada y se
  recupera con "Recalcular" como ADMIN (una nueva ejecución de la semilla dice "ya hay datos").
- Umbral de alertas: hoy 141 de 299 variantes quedan en alerta tras la carga (AGOTADO 30, STOCK_BAJO 111). No se ajusta
  `stockMinimo` ahora: se revisa después del punto de reorden dinámico de la fase 5, con meta de ~10–15 % de variantes en
  alerta (decisión del usuario, 2026-10-13).
- Para fases futuras: ventas ANULADAS fuera de demanda, ABC y rentabilidad (fases 5–7); SIN_ROTACION y re-evaluación
  nocturna de alertas en la fase 5; la demanda observada está censurada por las ventas perdidas (2,5 %, fase 6).

## Prueba T1 — fase 4 (PowerShell 5, desde la raíz del repo) — completada 2026-10-13

**Atención: la carga exige una base vacía. El paso 2 BORRA todos los datos de tu base de desarrollo local** (productos,
ventas y usuarios creados en las pruebas T3). El ADMIN de `ADMIN_EMAIL` se vuelve a crear solo al migrar.

1. En `.env` agrega `SEED_PASSWORD=` con una contraseña de 12–72 caracteres que no empiece por "cambiar". Es la
   contraseña de los 4 usuarios demo.
2. Base vacía: `docker compose down -v` y luego `docker compose up -d mysql` (esperar `healthy` con `docker compose ps`).
3. Carga: `cd backend` y `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,seed"`. No levanta servidor web;
   tarda ~2–3 min, imprime el resumen y termina sola. Anota el tiempo y el resumen.
4. Arranque normal: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` y en otra terminal `cd frontend`,
   `npm run dev`. Abre http://localhost:5173.
5. Verificación con cada usuario demo. Contraseñas: los 4 usuarios demo (incluido `admin@livorno.co`) usan la de
   `SEED_PASSWORD` del `.env`; el ADMIN inicial (`ADMIN_EMAIL`) usa la de `ADMIN_PASSWORD`. Nunca se escriben en el repo.
   - [x] `admin@livorno.co`: inventario con stock y estados variados; ~30 productos en 6 categorías; LIV017 inactivo.
   - [x] `ventas@livorno.co`: ~12 mil ventas en 24 meses, con descuentos, devoluciones y algunas anuladas; reservas en
         los 4 estados (unas pocas ACTIVAS que vencen en los próximos días).
   - [x] `inventario@livorno.co`: kardex de una variante con entradas, ventas y ajustes encadenados; alertas activas.
   - [x] `contabilidad@livorno.co`: ve ventas con costo; no ve reservas ni alertas.
   - [x] La campana: 141 alertas activas; el umbral se revisa tras el ROP de la fase 5 (meta ~10–15 % de variantes).
6. Volver a ejecutar el paso 3 sin borrar: debe decir que ya hay datos y no crear nada.

## Notas para la próxima sesión

- Maven en el sandbox de Claude Code: usar `./mvnw -o` (offline). Si se agregan dependencias, ejecutar `./mvnw` con red
  (MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT).
- Docker Desktop debe estar arriba: sin él se omiten las pruebas con Testcontainers (no hacer commit en ese caso).
- Al encadenar verificación y commit, cortar ante cualquier fallo (`verify && commit`).
- Las pruebas que borran variantes deben borrar antes `alerta` y `reserva`; las de semilla usan `LimpiezaSemilla`.
- Las reservas ACTIVAS de la semilla vencen a los pocos días: regenerar la víspera de una demo.
- CI fijado en `ubuntu-24.04`; el limitador de login es en memoria (`getRemoteAddr()`).
