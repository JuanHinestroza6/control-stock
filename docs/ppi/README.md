# Material para el documento PPI

El agente `documentador-ppi` deja aquí secciones en Markdown y diagramas PlantUML (`.puml`)
listos para renderizar y pegar en el documento Word del PPI.

- [`requisitos/`](requisitos/) — fichas FO-ERS individuales (`REQ-xx.md`), incluidos los requisitos nuevos: REQ-01, REQ-02, REQ-04, REQ-05, REQ-06, REQ-08, REQ-09 (Tendencias históricas), REQ-10 (Planeación de compras; CU-16, CU-17 y CU-18 nuevos), REQ-11, REQ-12, REQ-13 (Gestionar apartados, requisito nuevo) y REQ-14 (Clasificación ABC, requisito nuevo).
- `datos-demostracion.md` — sección "Datos de demostración" (Fases 4 a 6): propósito, generación, modelo de demanda, reposición por planes de compra y resultados de las corridas T1 (Fase 4), T2 (Fase 5, con la analítica) y T2 de la Fase 6 (pendiente).
- `diagramas/*.puml` — clases, secuencia, casos de uso, despliegue, componentes, paquetes. Fase 5 (analítica): `casos-uso-cu08-mas-vendidos.puml`, `casos-uso-cu15-clasificacion-abc.puml`, `clases-fase5-analitica.puml`.

  Fase 6 (compras): `estados-plan-compra.puml` (máquina de estados del plan), `clases-fase6-compra.puml` (módulo `compra` y sus puertos con `inventario` y `analitica`), `secuencia-plan-compra.puml` (sugerir, aprobar y recibir).

Renderizar: extensión PlantUML de VSCode (Alt+D para vista previa) o https://www.plantuml.com/plantuml
