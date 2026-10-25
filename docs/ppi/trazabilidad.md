# Trazabilidad final (cierre de la fase 7)

Matriz requisito → caso de uso → ficha → módulos → diagramas → estado, para el documento PPI. Los títulos de los casos
de uso son los del Word (CU-01 a CU-12) y los nuevos de este proyecto (CU-13 a CU-18; convención en `docs/dominio.md`).
Diagrama de contexto de todos los CU: [`casos-uso-general.puml`](diagramas/casos-uso-general.puml).

## Requisitos funcionales

| Requisito | Caso de uso | Ficha | Módulos | Diagramas | Fase | Estado |
|---|---|---|---|---|---|---|
| REQ-01 Visualizar Stock y Atributos en Tiempo Real | CU-04 Visualizar inventario en tiempo real | [REQ-01](requisitos/REQ-01.md) | catalogo, inventario, reporte (panel) | clases-fase1-catalogo, clases-fase2-operacion, clases-general | 1, 2, 7 | Implementado |
| REQ-02 Registrar Automáticamente Operaciones de Stock | CU-03 Registrar entradas y salidas | [REQ-02](requisitos/REQ-02.md) | inventario (`MovimientoService`, kardex) | clases-fase2-operacion, secuencia-registrar-venta | 2 | Implementado |
| REQ-03 Generar Reportes de Rentabilidad | CU-06 Generar reportes de ventas y rentabilidad | [REQ-03](requisitos/REQ-03.md) | reporte, comun.exportacion | secuencia-exportar-reporte, componentes-sistema | 7 | Implementado |
| REQ-04 Emitir Alertas de Nivel Mínimo de Stock | CU-10 Alertas automáticas de stock bajo (extensión: CU-01 «crear solicitud de compra directa») | [REQ-04](requisitos/REQ-04.md) | inventario (alertas), analitica (ROP), compra (posición) | clases-fase5-analitica, clases-general | 3, 5, 6, 7 | Implementado |
| REQ-05 Identificar Productos de Mayor Venta | CU-08 Identificar productos más vendidos | [REQ-05](requisitos/REQ-05.md) | analitica | casos-uso-cu08-mas-vendidos | 5 | Implementado |
| REQ-06 Controlar el Acceso y Roles de Usuario | CU-02 Gestión de usuarios y roles (incluye CU-13) | [REQ-06](requisitos/REQ-06.md) | auth, usuario, comun | casos-uso-fase0-auth, clases-fase0-auth-usuario, secuencia-login | 0, 1 | Implementado |
| REQ-07 Elaborar Reportes Contables Automáticos | CU-07 Generar reportes contables automáticos | [REQ-07](requisitos/REQ-07.md) | reporte, comun.exportacion | secuencia-exportar-reporte, componentes-sistema | 7 | Implementado |
| REQ-08 Gestionar el Registro de Devoluciones | CU-09 Registrar devoluciones | [REQ-08](requisitos/REQ-08.md) | venta, inventario | clases-fase2-operacion | 2 | Implementado |
| REQ-09 Analizar Tendencias Históricas de Venta | CU-05 Visualizar tendencias históricas de ventas (extensión «Comparar periodos») | [REQ-09](requisitos/REQ-09.md) | analitica, comun.exportacion | secuencia-exportar-reporte | 6, 7 | Implementado |
| REQ-10 Optimizar la Planeación de Compras | CU-01 Optimizar planeación de compras (incluye CU-16, CU-17, CU-18) | [REQ-10](requisitos/REQ-10.md) | compra, analitica, inventario | clases-fase6-compra, estados-plan-compra, secuencia-plan-compra | 6 | Implementado |
| REQ-11 Gestión de Productos | CU-11 Registrar y gestionar productos | [REQ-11](requisitos/REQ-11.md) | catalogo | clases-fase1-catalogo | 1 | Implementado |
| REQ-12 Registrar y Gestionar Usuarios | CU-12 Registrar y gestionar usuarios | [REQ-12](requisitos/REQ-12.md) | usuario | clases-fase0-auth-usuario | 0, 1 | Implementado |
| REQ-13 Gestionar Apartados (nuevo) | CU-14 Gestionar apartados | [REQ-13](requisitos/REQ-13.md) | inventario (reservas), venta | estados-reserva, secuencia-convertir-reservas | 3 | Implementado |
| REQ-14 Clasificación ABC (nuevo) | CU-15 Clasificar productos (ABC) | [REQ-14](requisitos/REQ-14.md) | analitica | casos-uso-cu15-clasificacion-abc, clases-fase5-analitica | 5 | Implementado |

## Requisitos no funcionales

| Requisito | Ficha | Cómo se cumple | Estado |
|---|---|---|---|
| RNF-01 Disponibilidad en el sistema | — | `/actuator/health`, Docker Compose (MySQL), despliegue objetivo de ADR-001 (`despliegue-objetivo`) | Parcial: sin despliegue productivo (ADR-011, D6) |
| RNF-02 Seguridad y control | (REQ-06) | JWT, BCrypt, `@PreAuthorize` por endpoint, matriz de roles probada por HTTP, Ley 1581 (anonimización de reservas, reportes sin datos de clientes) | Implementado |
| RNF-03 Rendimiento de consultas | [RNF-03](requisitos/RNF-03.md) | SQL agregado de solo lectura, índices por fecha, `RendimientoReportesTest` (< 0,5 s con ~12.000 ventas) | Implementado; medición con la semilla de 36 meses en la T2 |
| RNF-04 Usabilidad de la interfaz | — | SPA React con estados de carga, vacío y error; accesibilidad con roles ARIA (pruebas con Testing Library); exportar con respaldo de formato | Implementado |
| RNF-05 Escalabilidad del sistema | — | Monolito modular con dependencias verificadas por ArchUnit (`paquetes-modulos`, `componentes-sistema`) | Diseño; sin pruebas de carga |

## Vistas generales

- Paquetes: [`paquetes-modulos.puml`](diagramas/paquetes-modulos.puml). Componentes:
  [`componentes-sistema.puml`](diagramas/componentes-sistema.puml). Despliegue:
  [`despliegue-objetivo.puml`](diagramas/despliegue-objetivo.puml).
- Clases general: [`clases-general.puml`](diagramas/clases-general.puml) (entidades de todos los módulos; los detalles
  por módulo están en `clases-fase*.puml`).
- Secuencia de exportar un reporte: [`secuencia-exportar-reporte.puml`](diagramas/secuencia-exportar-reporte.puml).

## Correcciones al Word registradas en las fichas

- REQ-08 y REQ-09 tienen nombre y descripción cruzados (REQ-09 es «Analizar Tendencias Históricas de Venta»).
- NEC-010 debería ser NEC-10. Los no funcionales del Word (REQ-01..REQ-05) se nombran RNF-01..RNF-05.
- Fuera de alcance, como observación sin cambiar el texto del Word: gastos y validación fiscal (CU-07), correo (CU-06),
  registrar proveedor (CU-01), almacenamiento de reportes generados y bloqueo por inconsistencias (CU-07, alt. 1).
