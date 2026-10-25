# Plan de desarrollo — 5 semanas hasta la primera entrega

Cada fase termina con: pruebas en verde, revisión de código sin críticos, `docs/progreso.md` actualizado y demo funcional.
Backend y frontend avanzan en paralelo dentro de cada semana.

## Semana 1 — Fase 0 (setup) + Fase 1 (núcleo)

Fase 0:
- Monorepo, `docker-compose.yml` con MySQL 8, `.env.example`, `.gitignore`.
- Backend generado con Spring Initializr (Web, JPA, Security, Validation, Flyway, MySQL, Actuator, Testcontainers).
- Módulo `comun`: `ManejadorGlobalExcepciones` (`@RestControllerAdvice` con `ProblemDetail`), `ExcepcionNegocio`, auditoría de fechas.
- Auth JWT: login, filtro, `@EnableMethodSecurity`, usuario ADMIN inicial por migración.
- Frontend con Vite + TS + Tailwind, layout (sidebar, topbar, área central) según el sistema de diseño del PPI, login.

Fase 1:
- CRUD de categorías, productos y variantes (generación de variantes a partir de tallas × colores).
- Vista matriz talla × color con stock por celda.
- CRUD de usuarios y roles (REQ-06, REQ-12).

## Semana 2 — Fase 2 (operación)

- `MovimientoService` con todos los tipos, validaciones y bloqueo optimista. Pruebas de concurrencia.
- Entradas y ajustes (REQ-02). Kardex consultable por variante con filtros (REQ-01).
- Ventas con detalle (guarda `costoUnitario`), anulación.
- Devoluciones (REQ-08).
- Frontend: inventario actual con filtros, formulario de entradas, registro de venta, kardex.

## Semana 3 — Fase 3 (apartados) + Fase 4 (datos semilla) + Fase 5 (analítica base)

- Reservas: crear, cancelar, convertir en venta, vencimiento automático (`@Scheduled`).
- Generador de datos semilla (perfil `seed`): 24 meses de historia con estacionalidad.
- Alertas por evento STOCK_BAJO y AGOTADO (REQ-04): hechas en la fase 3 (ADR-007). Pendiente para la fase 5: tarea
  nocturna de re-evaluación y SIN_ROTACION.
- Clasificación ABC y punto de reorden dinámico (tarea nocturna + recálculo manual).
- Productos más vendidos (REQ-05).

## Semana 4 — Fase 6 (predicción y compras)

- Pronóstico por producto (SES y Holt-Winters con selección por backtesting), desagregado a variantes por mezcla histórica.
- Tendencias históricas con gráficos (REQ-09).
- Plan de compras: sugerencia, edición, aprobación y recepción (REQ-10).

## Semana 5 — Fase 7 (cierre de entrega)

- Dashboard (tarjetas resumen, alertas, ventas del día, top productos).
- Reportes de ventas y rentabilidad, reportes contables, exportación PDF y Excel (REQ-03, REQ-07).
- Pruebas de rendimiento básicas (consultas < 3 s con 10.000 registros).
- Documento PPI actualizado: requisitos nuevos, diagramas (clases, secuencia, casos de uso, despliegue, componentes, paquetes).
- Ensayo de sustentación con datos semilla.

## Fuera de alcance por ahora (candidatos si sobra tiempo)

Ventas por canal (el campo ya existe), detección avanzada de productos estancados, dashboard en tiempo real por WebSockets,
escaneo QR, asistente en lenguaje natural.
