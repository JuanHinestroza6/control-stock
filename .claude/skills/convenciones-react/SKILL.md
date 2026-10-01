---
name: convenciones-react
description: Convenciones del frontend de Control Stock en React + TypeScript + Vite + Tailwind - estructura de carpetas, sistema de diseño del PPI (colores, tipografías, layout de dashboard), cliente API con Axios y JWT, TanStack Query, formularios, tablas, gráficos con Recharts y manejo de roles. Úsalo siempre que escribas o revises código del frontend o diseñes una pantalla.
---

# Convenciones React

## Estructura

```
frontend/src/
├── api/          cliente.ts (axios + interceptores), productos.ts, ventas.ts ...
├── tipos/        interfaces que reflejan los DTOs del backend
├── hooks/        useProductos, useCrearVenta ... (TanStack Query)
├── componentes/  ui/ (Boton, Tarjeta, Tabla, Badge, Modal, Campo) y de dominio (MatrizTallaColor, BadgeStock)
├── paginas/      una carpeta por módulo: inventario/, ventas/, analitica/ ...
├── lib/          formato.ts (moneda COP, fechas), auth.ts (rol, token)
└── App.tsx       rutas con React Router y guardas por rol
```

## Sistema de diseño (del documento PPI, sección 4.6)

Configurar en `tailwind.config` como tokens; no usar colores hex sueltos en componentes.

| Token | Valor | Uso |
|---|---|---|
| `fondo` | #FFFFFF | Tarjetas, contenedores (60%) |
| `fondo-suave` | #F9FAFB | Fondo de página, filas zebra |
| `oxford` | #111827 | Sidebar, textos principales, encabezados de tabla, botón primario (30%) |
| `acento` | #0288D1 | Enlaces, botones secundarios, navegación activa, secciones de IA/predicción |
| `ok` | #10B981 | Stock óptimo, éxito |
| `alerta` | #F59E0B | Stock bajo / reorden |
| `critico` | #EF4444 | Agotado, errores |

Tipografía: Montserrat (títulos: H1 24px bold, H2 18px medium) e Inter (cuerpo 14px regular, captions 12px medium).
Tarjetas con `rounded-lg` (8px). Tablas zebra. Inputs con etiqueta arriba y borde `acento` al enfocar.

Layout fijo: sidebar izquierda oxford (Inicio, Productos, Inventario, Ventas, Apartados, Analítica IA, Compras, Reportes, Usuarios),
topbar (buscador global, campana de alertas con contador, usuario y rol), área central. En móvil la sidebar es un drawer.

Badge de stock: `ok` si disponible > ROP, `alerta` si 0 < disponible ≤ ROP, `critico` si 0.

## Datos

- Cliente Axios con `baseURL` desde `import.meta.env.VITE_API_URL`, interceptor que agrega `Authorization: Bearer` y redirige a login en 401.
- Toda lectura con `useQuery`, toda escritura con `useMutation` + `invalidateQueries` de las claves afectadas (una venta invalida `inventario`, `kardex`, `alertas`, `dashboard`).
- Claves de query como arreglos: `['productos', filtros]`.
- Errores del backend llegan como `ProblemDetail`; un helper `mensajeError(error)` extrae `detail` o los errores por campo.

## Componentes

- Componentes funcionales con props tipadas; prohibido `any`.
- Formularios con `react-hook-form` + `zod`.
- Moneda: `Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })`.
- Gráficos con Recharts dentro de `ResponsiveContainer`. Pronósticos: serie histórica en `oxford`, proyección punteada en `acento`.
- Cada página: estado cargando (skeleton), error (mensaje + reintentar) y vacío (mensaje + acción sugerida).

## Roles

`lib/auth.ts` expone `tieneRol(...roles)`. Las rutas usan `<RutaProtegida roles={[...]}>` y los botones se ocultan por rol,
siguiendo la matriz de `docs/dominio.md`.
