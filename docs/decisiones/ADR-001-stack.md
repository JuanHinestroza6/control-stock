# ADR-001: Stack tecnológico

- Estado: aceptada
- Fecha: 2026-10-01

## Contexto
El documento PPI (sección 4.1) define Java + Spring Boot, MySQL y un frontend web responsivo con React o Angular.
El equipo domina Java, SQL y UML. Hay 5 semanas hasta la primera entrega.

## Decisión
- Backend: Java 21 + Spring Boot 3.x + Maven Wrapper, arquitectura en capas organizada por módulo.
- Persistencia: MySQL 8 + Spring Data JPA + Flyway para migraciones versionadas.
- Seguridad: Spring Security con JWT (stateless) y autorización por rol con `@PreAuthorize`.
- Frontend: React + Vite + TypeScript + Tailwind + TanStack Query + Recharts.
- Predicción: algoritmos implementados en Java puro (SES, Holt-Winters), sin servicio de ML aparte.

## Alternativas consideradas
- Angular: más estructura, pero curva mayor para el tiempo disponible.
- Microservicio Python para ML: más potente, pero agrega despliegue y comunicación entre servicios que no aportan al alcance.
- JavaScript sin TypeScript: menos fricción inicial, pero más errores en contratos con la API.

## Consecuencias
- Un solo lenguaje en backend, despliegue simple (un JAR + MySQL + estáticos del frontend).
- Los algoritmos de predicción quedan como funciones puras fáciles de probar y de explicar en la sustentación.
