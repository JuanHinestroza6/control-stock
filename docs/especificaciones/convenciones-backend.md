# Convenciones del backend

Convenciones de código del backend de Control Stock en Java 21 y Spring Boot 4.1 (ADR-001 y ADR-002): estructura de
paquetes por módulo y capa, entidades JPA, DTOs, servicios, controllers, errores, seguridad, migraciones y pruebas.

## Paquetes

```
co.livorno.controlstock
├── comun/        config, seguridad base, excepciones, exportación (PDF/Excel), utilidades
└── <modulo>/
    ├── web/          XxxController
    ├── servicio/     XxxService (clase concreta, sin interfaz salvo que haya 2+ implementaciones o un puerto)
    ├── repositorio/  XxxRepository extends JpaRepository, o consultas JDBC de solo lectura
    ├── modelo/       entidades y enums
    └── dto/          records de request y response
```

Módulos: `comun`, `auth`, `usuario`, `catalogo`, `inventario`, `venta`, `analitica`, `compra`, `reporte` y `semilla`.
Las dependencias entre módulos las verifica ArchUnit (`DependenciasEntreModulosTest`, `ReglasArquitecturaTest`); el
diagrama está en `docs/ppi/diagramas/paquetes-modulos.puml`. Cuando un módulo necesita algo de otro del que no puede
depender, se usa un puerto (interfaz en el que consume, implementación en el que provee), p. ej.
`inventario.servicio.ConsultaPedidosEnCamino` implementado en `compra`.

## Entidades

```java
@Entity
@Table(name = "producto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Producto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    // Constructor o fábrica con validaciones; métodos de negocio con nombre (actualizarPrecios, desactivar)
}
```

- Lombok: solo `@Getter` y `@NoArgsConstructor(access = PROTECTED)` en entidades. Prohibidos `@Data` y `@Setter`.
- Relaciones `LAZY` siempre. Nombres de tabla y columna en snake_case.
- Borrado lógico (`activo = false`) para productos, variantes y usuarios.
- Dinero en `BigDecimal` (escala 2, `RoundingMode.HALF_UP`); fechas con `LocalDate`/`LocalDateTime` en America/Bogota.

## DTOs

```java
public record CrearProductoRequest(
    @NotBlank @Size(max = 20) String codigo,
    @NotBlank @Size(max = 120) String nombre,
    @NotNull Long categoriaId,
    @NotNull @DecimalMin("0.00") BigDecimal precioCompra,
    @NotNull @DecimalMin("0.00") BigDecimal precioVenta,
    @Min(1) Integer leadTimeDias
) {}

public record ProductoResponse(Long id, String codigo, String nombre, String categoria,
                               BigDecimal precioVenta, int totalVariantes) {
    public static ProductoResponse de(Producto p) { ... }
}
```

Mapeo manual con métodos estáticos `de(...)`, sin MapStruct. Los controllers nunca exponen entidades JPA.

## Servicios

- `@Service`, `@Transactional(readOnly = true)` a nivel de clase; `@Transactional` en los métodos que escriben.
- Lanzan excepciones de `comun.excepcion`: `RecursoNoEncontradoException` (404), `SolicitudInvalidaException` (400),
  `ConflictoException` (409) y `ExcepcionNegocio` (422).
- El usuario actual se obtiene con `UsuarioActual` (`comun.seguridad`): `id()` u `obtener()`.
- El stock solo cambia por `MovimientoService` (ver [`reglas-inventario.md`](reglas-inventario.md)).
- Los reportes y el panel solo leen: consultas SQL agregadas con `NamedParameterJdbcTemplate` y `readOnly`; ArchUnit
  impide que `reporte` use `MovimientoService` o escriba por JDBC.

## Controllers

```java
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {
    private final ProductoService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<ProductoResponse> listar(FiltroProducto filtro, Pageable pageable) { ... }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody CrearProductoRequest req) { ... }
}
```

- Sin lógica de negocio. `@PreAuthorize` en cada endpoint, según la matriz de roles de `docs/dominio.md`.
- Rutas en plural y español: `/api/productos`, `/api/variantes/{id}/movimientos`.
- Listados siempre paginados (`Page<T>`), tamaño por defecto 20, máximo 100; el orden solo por campos permitidos.
- **Exportación de reportes** (ADR-011): endpoint hermano `GET …/exportar?formato=PDF|XLSX` con los mismos filtros y el
  mismo servicio que el JSON. El servicio arma un `DocumentoTabular` (`comun.exportacion`) y `ExportadorDocumentos` lo
  convierte en `ArchivoExportado`; el controller responde con `RespuestaArchivo.de(...)`: `ResponseEntity<byte[]>` con
  `Content-Type` explícito, `Content-Disposition: attachment; filename="..."` y `Cache-Control: no-store`. Los errores de
  filtros siguen siendo 400 `ProblemDetail` (no se usa negociación por `Accept`).

## Errores

`@RestControllerAdvice` (`ManejadorGlobalExcepciones`) devuelve `ProblemDetail` (RFC 9457) con `title`, `detail`,
`status` y, en las validaciones, `errores: [{campo, mensaje}]`. Mensajes en español y comprensibles para el usuario
final.

## Flyway

- `src/main/resources/db/migration/V{n}__{descripcion_snake}.sql`, numeración secuencial; nunca se edita una migración
  que ya está en `main`.
- Índices en las FK y en las columnas de filtro (`sku`, `fecha`, `estado`).
- Datos de referencia (admin inicial, categorías base) en migraciones; datos de demostración en el perfil `seed`
  (ver [`datos-semilla.md`](datos-semilla.md)), nunca en migraciones.

## Configuración

- `application.yml` + `application-dev.yml` + `application-seed.yml`; en pruebas, `src/test/resources/application-test.yml`.
- Secretos por variables de entorno (`.env`, ver `.env.example`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
  `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `SEED_PASSWORD`, `CORS_ORIGENES`… Cero secretos en el repositorio.
- Equipo sin Docker: perfil `local` con `backend/config/application-local.properties` (fuera de Git), que Spring Boot
  carga desde `./config/` al ejecutar desde `backend/`.
- `spring.jpa.hibernate.ddl-auto=validate` (el esquema lo controla Flyway) y `spring.jpa.open-in-view=false`.

## Pruebas

| Qué | Herramienta |
|---|---|
| Servicio con lógica | JUnit 5 + Mockito |
| Cálculos analíticos | JUnit 5 puro con valores calculados a mano |
| Repositorio y consultas | `@DataJpaTest` o `@SpringBootTest` + Testcontainers (MySQL 8.4) |
| Controller y seguridad | `@WebMvcTest` con `authentication(...)` de un `UsuarioAutenticado` por rol |
| Flujo completo | `@SpringBootTest` + Testcontainers, por HTTP con el JWT de cada rol |
| Arquitectura | ArchUnit |

- Nombres: `metodo_condicion_resultadoEsperado`. Estructura dado / cuando / entonces. AssertJ (`assertThat`).
- Las pruebas con Testcontainers llevan `@Testcontainers(disabledWithoutDocker = true)`: sin Docker se omiten y las
  valida la CI del PR.
- La caché de contextos de prueba está limitada a 8 (`src/test/resources/spring.properties`): cada contexto con
  Testcontainers mantiene un MySQL propio. Evitar `@MockitoBean` nuevos en las pruebas con contenedor.
- Boot 4 (ADR-002): mocks de beans con `@MockitoBean` (`@MockBean` no existe). Testcontainers 2:
  `org.testcontainers.mysql.MySQLContainer` con `@ServiceConnection`, imagen `mysql:8.4`. Jackson 3 (`tools.jackson.*`).
