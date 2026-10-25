# Convenciones de los diagramas UML (PlantUML)

Convenciones de los diagramas UML del documento PPI de Control Stock (clases, secuencia, casos de uso, estados,
paquetes, componentes y despliegue). El índice de los diagramas y su relación con los requisitos están en
`docs/ppi/README.md` y `docs/ppi/trazabilidad.md`.

Ubicación: `docs/ppi/diagramas/<tipo>-<tema>.puml`. Un diagrama por archivo, legible en una página.

## Estilo común (al inicio de cada archivo)

```
@startuml
skinparam shadowing false
skinparam defaultFontName Arial
skinparam classAttributeIconSize 0
skinparam linetype ortho
title <Título en español>
...
@enduml
```

## Por tipo

- **Clases**: solo entidades de dominio con sus atributos clave y tipos; multiplicidades en las relaciones; enums como
  `enum`. Se toman de las entidades reales en `*/modelo/`. Un diagrama general (`clases-general.puml`) y uno detallado
  por módulo (`clases-fase*-*.puml`).
- **Secuencia**: uno por caso de uso principal (registrar venta, convertir reservas, plan de compra, exportar un
  reporte). Participantes: actor, frontend, controller, servicio, `MovimientoService` cuando hay stock, repositorio o
  consultas, base de datos. Se muestran la transacción y el evento de alerta.
- **Casos de uso**: actores = roles (Administrador, Encargado de inventario, Vendedor, Contabilidad y Sistema como actor
  temporal). Códigos CU-xx y títulos del documento Word en cada caso; `<<include>>` y `<<extend>>` según los puntos de
  extensión de las fichas (por ejemplo, CU-01 incluye CU-16 a CU-18 y extiende CU-10; «Comparar periodos» extiende
  CU-05).
- **Estados**: ciclos de vida con sus transiciones y quién las dispara (reserva, plan de compra).
- **Paquetes**: módulos del backend y sus dependencias, iguales a las reglas de ArchUnit (`comun` no depende de ningún
  otro módulo).
- **Componentes**: SPA React, API REST Spring Boot con sus módulos, MySQL, tareas programadas y generadores de PDF y
  Excel.
- **Despliegue**: nodo cliente (navegador), servidor de aplicación (JAR Spring Boot + estáticos) y servidor MySQL, con
  HTTPS y JDBC; se distingue el objetivo de ADR-001 del uso local actual.

## Reglas

- Se generan desde el código, nunca de memoria. Si algo del documento Word no existe en el código, se señala.
- Nombres en español, iguales a los del código.
- Antes de hacer commit se valida la sintaxis de todos los diagramas.

## Validación

Graphviz no es obligatorio. La sintaxis se valida con el JAR de PlantUML, desde la raíz del repositorio (Git Bash):

```
# Con la extensión PlantUML de VS Code instalada:
java -jar "$USERPROFILE/.vscode/extensions/jebbs.plantuml-2.18.1/plantuml.jar" -checkonly docs/ppi/diagramas/*.puml

# Sin la extensión: el mismo JAR desde Maven Central (queda en ~/.m2; requiere red la primera vez)
cd backend && ./mvnw dependency:get -Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7 && cd ..
java -jar ~/.m2/repository/net/sourceforge/plantuml/plantuml/1.2024.7/plantuml-1.2024.7.jar -checkonly docs/ppi/diagramas/*.puml
```

Sin salida y código 0 significa que la sintaxis es correcta. Para renderizar sin Graphviz se agrega
`-Playout=smetana` (o `!pragma layout smetana` en el archivo); el acomodo es peor que con Graphviz, sobre todo en el
diagrama de casos de uso general, así que para el documento Word conviene renderizar con Graphviz o con el servidor de
PlantUML.
