# ADR-002: Versiones de Spring Boot y MySQL

- Estado: aceptada
- Fecha: 2026-10-01
- Modifica: ADR-001 (línea "Spring Boot 3.x" y "MySQL 8")

## Contexto
ADR-001 fijó Spring Boot 3.x y MySQL 8. Al iniciar la Fase 0 (2026-10-01): start.spring.io ya no ofrece 3.x (solo 4.0.x y 4.1.x),
la última 3.5 (3.5.16) terminó su soporte OSS el 2026-06-30, y MySQL 8.0 llegó a fin de vida el 2026-04-30.

## Decisión
Backend con Spring Boot 4.1.1 (Java 21) y base de datos MySQL 8.4 LTS (imagen `mysql:8.4` en Docker Compose y Testcontainers).

## Alternativas consideradas
- Spring Boot 3.5.16 — coincide con tutoriales y guías de convenciones, pero sin parches de seguridad públicos y no generable desde Initializr.
- Spring Boot 4.0.x — soporte OSS solo hasta 2026-12-31, antes de terminar el semestre siguiente.
- MySQL 8.0 — fuera de soporte. MySQL 9.x Innovation — ciclos cortos de soporte; 8.4 es la LTS (hasta 2032).

## Consecuencias
- Versiones con soporte durante todo el proyecto; defendible en la sustentación.
- Cambios de Boot 4 a tener en cuenta: Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3 (paquete `tools.jackson`),
  starters modulares (p. ej. `spring-boot-starter-flyway`, `spring-boot-starter-webmvc`), módulos de prueba separados
  (`@WebMvcTest` en `spring-boot-webmvc-test`), Testcontainers 2 (`org.testcontainers.mysql.MySQLContainer`),
  `@MockitoBean` en lugar de `@MockBean` (eliminado).
- Muchos ejemplos en internet son de Boot 3: compilar y correr pruebas tras cada incremento (JDK local o CI).
