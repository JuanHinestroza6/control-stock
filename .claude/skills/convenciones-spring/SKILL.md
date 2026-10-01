---
name: convenciones-spring
description: Convenciones de código backend de Control Stock en Java 21 y Spring Boot 3 - estructura de paquetes por módulo y capa, entidades JPA, repositorios, servicios, DTOs record, controllers, manejo de errores con ProblemDetail, seguridad con @PreAuthorize, migraciones Flyway y pruebas. Úsalo siempre que escribas, revises o pruebes código Java del backend.
---

# Convenciones Spring Boot

## Paquetes

```
co.livorno.controlstock
├── comun/        config, seguridad base, excepciones, utilidades
└── <modulo>/
    ├── web/          XxxController
    ├── servicio/     XxxService (clase concreta, sin interfaz salvo que haya 2+ implementaciones)
    ├── repositorio/  XxxRepository extends JpaRepository
    ├── modelo/       entidades y enums
    └── dto/          records de request/response
```

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

- Lombok: solo `@Getter` y `@NoArgsConstructor(access = PROTECTED)` en entidades. Prohibido `@Data` y `@Setter` en entidades.
- Relaciones `LAZY` siempre. Nombres de tabla y columna en snake_case.
- Borrado lógico (`activo = false`) para productos, variantes y usuarios.

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

Mapeo manual con métodos estáticos `de(...)`. Sin MapStruct.

## Servicios

- `@Service`, `@Transactional(readOnly = true)` a nivel de clase; `@Transactional` en métodos que escriben.
- Lanzan excepciones de `comun.excepcion`: `RecursoNoEncontradoException` (404), `ExcepcionNegocio` (422), `ConflictoException` (409).
- Obtienen el usuario actual con `UsuarioActual.id()` (helper en `comun.seguridad`).

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

- Rutas en plural y español: `/api/productos`, `/api/variantes/{id}/movimientos`.
- Listados siempre paginados (`Page<T>`), tamaño por defecto 20, máximo 100.
- Roles según la matriz de `docs/dominio.md`.

## Errores

`@RestControllerAdvice` devuelve `ProblemDetail` (RFC 9457) con `title`, `detail`, `status` y, para validaciones, `errores: [{campo, mensaje}]`. Mensajes en español y comprensibles para el usuario final.

## Flyway

- `src/main/resources/db/migration/V{n}__{descripcion_snake}.sql`. Numeración secuencial, nunca editar una existente.
- Incluir índices en FK y columnas de filtro (`sku`, `fecha`, `estado`).
- Datos de referencia (roles, admin inicial, categorías base) en migraciones; datos de demostración en el perfil `seed`, no en migraciones.

## Configuración

- `application.yml` + `application-dev.yml` + `application-test.yml`. Secretos por variables de entorno: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.
- `spring.jpa.hibernate.ddl-auto=validate` (el esquema lo controla Flyway). `spring.jpa.open-in-view=false`.

## Pruebas

| Qué | Herramienta |
|---|---|
| Servicio con lógica | JUnit 5 + Mockito |
| Cálculos analíticos | JUnit 5 puro con valores calculados a mano |
| Repositorio / consultas | `@DataJpaTest` + Testcontainers MySQL |
| Controller y seguridad | `@WebMvcTest` + `@WithMockUser(roles = ...)` |
| Flujo completo | `@SpringBootTest` + Testcontainers |

- Nombres: `metodo_condicion_resultadoEsperado`. Estructura dado/cuando/entonces con comentarios.
- Usa AssertJ (`assertThat`).
- Si Docker no está disponible, las pruebas con Testcontainers se marcan con `@Testcontainers(disabledWithoutDocker = true)`.
