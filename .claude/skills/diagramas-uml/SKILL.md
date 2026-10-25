---
name: diagramas-uml
description: Convenciones para generar diagramas UML en PlantUML a partir del código de Control Stock (clases, secuencia, casos de uso, paquetes, componentes, despliegue) para el documento académico PPI. Úsalo siempre que se pida crear, actualizar o revisar un diagrama o la documentación del PPI.
---

# Diagramas UML (PlantUML)

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

- **Clases**: solo entidades de dominio con atributos clave y tipos; multiplicidades en las relaciones; enums como `enum`. Leer las entidades reales en `*/modelo/`. Un diagrama general + uno detallado por módulo si crece.
- **Secuencia**: un diagrama por caso de uso principal (registrar venta, convertir reserva, generar plan de compras). Participantes: Actor, Frontend, Controller, Service, MovimientoService, Repository, BD. Mostrar la transacción y el evento de alerta.
- **Casos de uso**: actores = roles (Administrador, Encargado de inventario, Vendedor, Contabilidad, Sistema como actor temporal). Códigos CU-xx del documento en cada caso. Usar `<<include>>` y `<<extend>>` según los puntos de extensión de las fichas.
- **Paquetes**: módulos del backend y sus dependencias (`comun` sin dependencias hacia otros módulos).
- **Componentes**: SPA React, API REST Spring Boot (con sus módulos), MySQL, tareas programadas, servicio de reportes.
- **Despliegue**: nodo cliente (navegador), servidor de aplicación (JAR Spring Boot + estáticos), servidor MySQL; protocolo HTTPS y JDBC.

## Reglas

- Generar desde el código, nunca de memoria. Si algo del documento no existe en el código, señalarlo.
- Nombres en español, iguales a los del código.
- Validar sintaxis: si `plantuml` está instalado, `plantuml -checkonly archivo.puml`.

## Validación local (verificada)

`plantuml` no está en el PATH; se usa el JAR de la extensión de VS Code (Git Bash, desde la raíz):

```
java -jar "$USERPROFILE/.vscode/extensions/jebbs.plantuml-2.18.1/plantuml.jar" -checkonly docs/ppi/diagramas/*.puml
```

Sin salida y código 0 = sintaxis correcta. Graphviz no está instalado: para renderizar agregar `!pragma layout smetana`.

Sin la extensión de VS Code (p. ej. el portátil), el mismo JAR desde Maven Central (queda en `~/.m2`, desde `backend/`):
`./mvnw dependency:get -Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7` y luego
`java -jar ~/.m2/repository/net/sourceforge/plantuml/plantuml/1.2024.7/plantuml-1.2024.7.jar -checkonly docs/ppi/diagramas/*.puml`.
