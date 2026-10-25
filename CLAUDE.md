# Control Stock — Livorno Clothing

Sistema web de gestión de inventario para Livorno Clothing (moda, Medellín; vende por Instagram, TikTok y WhatsApp).
Proyecto académico PPI — Ingeniería en Software, Tecnológico de Antioquia. Equipo de 3 personas.

Diferenciadores del proyecto (no recortar sin consultar): inventario por variantes (talla × color), kardex inmutable,
apartados (stock reservado vs. disponible), clasificación ABC, punto de reorden dinámico y predicción de demanda.

## Stack (decidido — ver docs/decisiones/ADR-001-stack.md)

- Backend: Java 21, Spring Boot 4.1.1, Maven Wrapper, Spring Data JPA, Spring Security + JWT, Bean Validation, Flyway, MySQL 8.4 (ver ADR-002).
- Frontend: React 18 + Vite + TypeScript, React Router, TanStack Query, Axios, Tailwind CSS, Recharts.
- Pruebas: JUnit 5, Mockito, Spring Boot Test, Testcontainers (MySQL). Frontend: Vitest + Testing Library.
- Reportes: Apache POI (Excel) y OpenPDF (PDF).
- Infra local: Docker Compose con MySQL.

## Estructura del repositorio

```
control-stock/
├── backend/    paquete raíz co.livorno.controlstock, organizado por módulo y luego por capa
├── frontend/   src/{api,componentes,paginas,hooks,tipos,lib}
├── docs/       dominio.md, plan.md, progreso.md, decisiones/, ppi/
└── .claude/    agentes y skills del equipo
```

Módulos backend: `comun`, `auth`, `usuario`, `catalogo`, `inventario`, `venta`, `analitica`, `compra`, `reporte`,
`semilla` (generador de datos de demostración, solo perfil `seed`; ningún módulo depende de él).
Capas dentro de cada módulo: `web` (controllers), `servicio`, `repositorio`, `modelo` (entidades), `dto`.

## Comandos

| Acción | Linux/Mac/Git Bash | Windows (cmd/PowerShell) |
|---|---|---|
| Levantar MySQL | `docker compose up -d mysql` | igual |
| Correr backend | `./mvnw spring-boot:run` | `mvnw.cmd spring-boot:run` |
| Pruebas backend | `./mvnw test` | `mvnw.cmd test` |
| Verificación completa | `./mvnw verify` | `mvnw.cmd verify` |
| Cargar datos semilla (base vacía, `SEED_PASSWORD` en `.env`; carga y termina) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,seed` | `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,seed"` |
| Frontend | `npm run dev` / `npm run test` / `npm run build` / `npm run lint` (dentro de `frontend/`) | igual |

Detecta el sistema operativo antes de ejecutar comandos Maven y usa la variante correcta.

## Reglas de oro (no negociables)

1. El stock SOLO cambia a través de `MovimientoService`. Nunca asignes `stockFisico` o `stockReservado` en otro lugar.
2. El kardex (`Movimiento`) es inmutable: no hay UPDATE ni DELETE. Los errores se corrigen con un movimiento `AJUSTE`.
3. Migraciones Flyway: nunca edites una migración ya creada en `main`; crea una nueva `V{n}__descripcion.sql`.
4. Dinero siempre `BigDecimal` (escala 2, `RoundingMode.HALF_UP`). Nunca `double`/`float` para dinero.
5. Fechas con `LocalDate`/`LocalDateTime`, zona `America/Bogota`.
6. Los controllers no tienen lógica de negocio y nunca exponen entidades JPA: solo DTOs (`record`).
7. Toda operación que modifique stock es `@Transactional` y usa bloqueo optimista (`@Version` en `Variante`).
8. Seguridad por rol con `@PreAuthorize` en cada endpoint (matriz de permisos en docs/dominio.md).
9. Cero secretos en el repo: credenciales en variables de entorno / `.env` (está en `.gitignore`).
10. Ninguna tarea se da por terminada con pruebas en rojo o sin pruebas para la lógica nueva.

## Idioma y estilo

- Dominio en español: `Producto`, `Variante`, `registrarVenta()`, `stockDisponible`. Sufijos técnicos en inglés aceptados: `Controller`, `Service`, `Repository`, `Dto`.
- Responde al equipo en español.
- Commits en español con Conventional Commits: `feat(inventario): registrar movimiento de entrada`.

## Flujo de trabajo

1. Al iniciar sesión, lee `docs/progreso.md` para saber dónde quedó el trabajo.
2. Tareas no triviales (varias capas, modelo de datos, algoritmos): primero plan con el agente `arquitecto` o plan mode.
3. Implementa en incrementos pequeños y corre las pruebas tras cada incremento.
4. Al terminar un bloque: `/revisar`. Al terminar la sesión: `/cerrar-sesion`.
5. Si una decisión cambia el diseño, regístrala como ADR en `docs/decisiones/`.

## Agentes y skills disponibles

Agentes (`.claude/agents/`): `arquitecto`, `backend-dev`, `frontend-dev`, `analitica`, `tester`, `revisor`, `documentador-ppi`.
Skills automáticos: `reglas-inventario`, `convenciones-spring`, `convenciones-react`, `algoritmos-analitica`, `datos-semilla`, `diagramas-uml`.
Skills manuales: `/fase <n>`, `/nueva-entidad <Nombre>`, `/revisar`, `/cerrar-sesion`.

## Contexto del dominio

@docs/dominio.md
@docs/progreso.md

Plan por semanas: `docs/plan.md` (léelo al iniciar una fase).
