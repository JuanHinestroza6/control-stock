# Material para el documento PPI

El agente `documentador-ppi` deja aquí secciones en Markdown y diagramas PlantUML (`.puml`)
listos para renderizar y pegar en el documento Word del PPI.

- [`requisitos/`](requisitos/) — fichas FO-ERS individuales: REQ-01 a REQ-14 (REQ-03 y REQ-07, reportes de la fase 7; REQ-09 «Analizar Tendencias Históricas de Venta»; REQ-10 con CU-01 del Word y CU-16, CU-17 y CU-18 nuevos; REQ-13 y REQ-14, requisitos nuevos) y RNF-03 (rendimiento).
- [`trazabilidad.md`](trazabilidad.md) — matriz final requisito → caso de uso → ficha → módulos → diagramas → estado, con los no funcionales y las correcciones al Word.
- `datos-demostracion.md` — sección "Datos de demostración" (Fases 4 a 6): propósito, generación, modelo de demanda, reposición por planes de compra y resultados de las corridas T1 (Fase 4), T2 (Fase 5, con la analítica) y T2 de la Fase 6 (2026-10-21, por API y base de datos).
- `diagramas/*.puml` — clases, secuencia, casos de uso, despliegue, componentes, paquetes. Fase 5 (analítica): `casos-uso-cu08-mas-vendidos.puml`, `casos-uso-cu15-clasificacion-abc.puml`, `clases-fase5-analitica.puml`.

  Fase 6 (compras): `estados-plan-compra.puml` (máquina de estados del plan), `clases-fase6-compra.puml` (módulo `compra` y sus puertos con `inventario` y `analitica`), `secuencia-plan-compra.puml` (sugerir, aprobar y recibir).

Renderizar: extensión PlantUML de VSCode (Alt+D para vista previa) o https://www.plantuml.com/plantuml

Fase 7 (vistas generales): `clases-general.puml` (entidades de todos los módulos), `secuencia-exportar-reporte.puml` (exportar un reporte a PDF o Excel), `paquetes-modulos.puml` (módulos y dependencias según ArchUnit y ADR-011, con los puertos), `componentes-sistema.puml` (SPA, API por módulos, MySQL, tareas programadas, generadores PDF/Excel), `despliegue-objetivo.puml` (objetivo de ADR-001; hoy MySQL en Docker Compose, backend con Maven y frontend con Vite) y `casos-uso-general.puml` (CU-01 a CU-18 por rol, con los títulos del Word de CU-03, CU-04, CU-09 y CU-10; CU-01 incluye CU-16 a CU-18 y extiende CU-10; «Comparar periodos» extiende CU-05).

Validar la sintaxis (no requiere Graphviz; para renderizar sin Graphviz use `!pragma layout smetana`), desde la raíz del repositorio en Git Bash:

```
java -jar "$USERPROFILE/.vscode/extensions/jebbs.plantuml-2.18.1/plantuml.jar" -checkonly docs/ppi/diagramas/*.puml
```

Sin salida y código 0 significa que la sintaxis es correcta.

En un equipo sin la extensión de VS Code (por ejemplo, el portátil), el mismo JAR se obtiene de Maven Central y queda en
`~/.m2` (desde `backend/`, con red):

```
./mvnw dependency:get -Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7
java -jar ~/.m2/repository/net/sourceforge/plantuml/plantuml/1.2024.7/plantuml-1.2024.7.jar -checkonly docs/ppi/diagramas/*.puml
```

Para renderizar sin Graphviz se agrega `-Playout=smetana` (el acomodo es peor que con Graphviz, sobre todo en el diagrama
de casos de uso general; para el Word conviene renderizar con Graphviz o con el servidor de PlantUML).
