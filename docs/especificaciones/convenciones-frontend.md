# Convenciones del frontend

Convenciones del frontend de Control Stock en React 18 + TypeScript + Vite + Tailwind (ADR-001): estructura, sistema de
diseño del documento PPI, cliente API, TanStack Query, formularios, gráficos, roles y pruebas.

## Estructura

```
frontend/src/
├── api/          cliente.ts (Axios + interceptores), productos.ts, ventas.ts, reportes.ts ...
├── tipos/        interfaces que reflejan los DTOs del backend
├── hooks/        useProductos, useVentas, useReportes ... (TanStack Query)
├── componentes/  ui/ (Boton, Tarjeta, Tabla, Badge, Modal, Campo, BotonExportar), layout/ (RutaProtegida, Topbar…)
│                 y de dominio (MatrizTallaColor, BadgeStock, TarjetaIndicador…)
├── paginas/      una carpeta por módulo: inicio/, inventario/, ventas/, analitica/, compras/, reportes/ ...
├── lib/          formato.ts (moneda COP, fechas), auth.ts, permisos.ts, errores.ts, descarga.ts ...
├── pruebas/      utilidades de prueba (render con sesión y rol, falsos de Axios)
└── App.tsx       rutas con React Router y guardas por rol
```

React fijado en 18.3.1, por eso React Router 7 (la 8 exige React 19). Linter: oxlint (`npm run lint`).

## Sistema de diseño (documento PPI, sección 4.6)

Tokens con `@theme` en `src/index.css` (Tailwind 4); no se usan colores hex sueltos en los componentes.

| Token | Valor | Uso |
|---|---|---|
| `fondo` | #FFFFFF | Tarjetas, contenedores (60 %) |
| `fondo-suave` | #F9FAFB | Fondo de página, filas zebra |
| `oxford` | #111827 | Sidebar, textos principales, encabezados de tabla, botón primario (30 %) |
| `acento` | #0288D1 | Enlaces, botones secundarios, navegación activa, secciones de analítica y predicción |
| `ok` | #10B981 | Stock óptimo, éxito |
| `alerta` | #F59E0B | Stock bajo / reorden |
| `critico` | #EF4444 | Agotado, errores |

- Tipografía: Montserrat (títulos: H1 24 px bold, H2 18 px medium) e Inter (cuerpo 14 px regular, captions 12 px medium).
- Tarjetas con `rounded-lg` (8 px). Tablas zebra. Inputs con la etiqueta arriba y borde `acento` al enfocar.
- Layout fijo: sidebar izquierda `oxford` (Inicio, Productos, Inventario, Ventas, Reservas, Alertas, Analítica, Compras,
  Reportes y Usuarios, según el rol; `componentes/layout/modulos.ts`), topbar (campana de alertas con contador, usuario
  y rol) y área central. En móvil la sidebar es un drawer.
- Badge de stock: `ok` si disponible > umbral, `alerta` si 0 < disponible ≤ umbral, `critico` si 0.

## Datos

- Cliente Axios con `baseURL` desde `import.meta.env.VITE_API_URL` (por defecto `/api`, con el proxy de Vite en
  desarrollo), interceptor que agrega `Authorization: Bearer` y
  redirige al login en 401.
- Toda lectura con `useQuery` y toda escritura con `useMutation` + `invalidateQueries` de las claves afectadas (una venta
  invalida inventario, kardex, alertas y panel).
- Claves de query como arreglos: `['productos', filtros]`.
- Los errores del backend llegan como `ProblemDetail`; `mensajeError(error)` (`lib/errores.ts`) extrae `detail` o los
  errores por campo.
- Descargas de PDF y Excel con `responseType: 'blob'`; `lib/descarga.ts` lee el nombre de `Content-Disposition` y
  convierte un error que llega como blob en `ProblemDetail`. `BotonExportar` ofrece el otro formato si uno falla.

## Componentes

- Componentes funcionales con props tipadas; prohibido `any`.
- Formularios con `react-hook-form` + `zod`.
- Moneda: `Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })`.
- Gráficos con Recharts dentro de `ResponsiveContainer`, cargados de forma diferida (`React.lazy`). Pronósticos: serie
  histórica en `oxford`, proyección punteada en `acento`.
- Cada página tiene estado cargando (skeleton), error (mensaje + reintentar) y vacío (mensaje + acción sugerida).

## Roles

- `lib/permisos.ts` expone funciones por permiso (`puedeVerCostos`, `puedeMoverStock`, `puedeVerKardex`…) y las rutas
  usan `<RutaProtegida roles={[...]}>`. Los botones y secciones se ocultan por rol siguiendo la matriz de
  `docs/dominio.md`; la autorización real la decide el servidor.

## Pruebas

- Vitest + Testing Library (`npm run test`), con `vi.mock` de `api/*` y consultas accesibles (`getByRole`,
  `findByText`). Las esperas asíncronas llegan a 3 s (`src/pruebas/setup.ts`) para evitar fallos por carga.
- Las pruebas que simulan el login con un adaptador de Axios usan `soloLogin` (`src/pruebas/http.ts`).
