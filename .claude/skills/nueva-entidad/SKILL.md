---
name: nueva-entidad
description: Andamiaje completo de una entidad nueva del backend (migración, entidad, repositorio, servicio, DTOs, controller y pruebas) siguiendo las convenciones del proyecto.
disable-model-invocation: true
argument-hint: <NombreEntidad> <modulo> [descripción breve]
---

# Nueva entidad: $ARGUMENTS

1. Lee `docs/dominio.md` y localiza la entidad. Si no está definida ahí, detente y pide definirla primero (campos, reglas, permisos).
2. Revisa el último número de migración en `backend/src/main/resources/db/migration/`.
3. Crea, en este orden, dentro del módulo indicado:
   - `V{n+1}__crear_tabla_<entidad>.sql` con FK, índices y restricciones (`CHECK` para cantidades ≥ 0).
   - Entidad en `modelo/` (convenciones-spring: sin setters, LAZY, BigDecimal).
   - Repositorio en `repositorio/` con las consultas de filtro necesarias.
   - DTOs `Crear…Request`, `Actualizar…Request`, `…Response` en `dto/`.
   - Servicio en `servicio/` con validaciones de negocio.
   - Controller en `web/` con `@PreAuthorize` según la matriz de roles.
   - Pruebas: servicio (Mockito), controller (`@WebMvcTest` con roles permitido y denegado), repositorio si hay consultas propias.
4. Ejecuta las pruebas del módulo y luego la suite completa.
5. Resume: archivos creados, endpoints, resultado de pruebas.

Si la entidad modifica stock, aplica además el skill `reglas-inventario` y usa `MovimientoService`.
