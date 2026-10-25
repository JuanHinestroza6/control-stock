# Guion de sustentación — Control Stock (T3, fase 7)

Duración objetivo: **15 minutos** de exposición y demostración, más preguntas. Cinco expositores (E1 a E5): cada uno
dirige un bloque y prepara las preguntas de su tema. Las cifras marcadas «T2-f7» se actualizan con la corrida real de la
guía T2 de la fase 7 (`docs/progreso.md`); las demás son de la T2 de la fase 6 y de la T1 de la fase 7.

## Antes de entrar (la víspera y 30 min antes)

- [ ] Semilla regenerada la víspera (pasos 1–2 de la guía T2): reservas ACTIVAS y SIN_ROTACION alineadas con la fecha.
- [ ] Backend arriba (`dev,local` en el portátil) y `npm run dev`; navegador con 4 pestañas ya autenticadas:
      `admin@livorno.co`, `inventario@livorno.co`, `ventas@livorno.co`, `contabilidad@livorno.co` (claves fuera del
      repo).
- [ ] Un PDF y un Excel ya descargados (rentabilidad y movimientos del mes anterior) por si falla la red o el equipo.
- [ ] Diagramas renderizados en el documento: casos de uso general, paquetes, clases general, secuencia de exportar.
- [ ] Plan B: capturas del panel, del pronóstico de LIV001 y de un reporte, por si la demo en vivo falla.

## Bloques

| Tiempo | Quién | Bloque | Qué mostrar o decir |
|---|---|---|---|
| 0:00–1:30 | E1 | Problema | Livorno Clothing vende por Instagram, TikTok y WhatsApp; el inventario por talla y color se llevaba a mano: ventas perdidas por agotados, apartados sin control, compras a ojo. Necesidades del Word (NEC-01 a NEC-10) y la nueva NEC-11 (apartados) |
| 1:30–3:00 | E1 | Solución y arquitectura | Monolito modular Spring Boot + React (diagrama de paquetes): `catalogo`, `inventario`, `venta`, `analitica`, `compra`, `reporte`. Cuatro reglas que sostienen todo: el stock solo cambia por `MovimientoService`; el kardex es inmutable; cada endpoint tiene rol; los reportes solo leen. ArchUnit hace cumplir las dependencias |
| 3:00–4:30 | E2 | Demo: inicio y operación | ADMIN: panel (ventas del día y del mes, margen, inventario, «Alertas más urgentes»). VENDEDOR: registrar una venta de una variante; luego, como ADMIN, el inventario y el kardex de esa variante (saldos antes/después; el VENDEDOR no ve el kardex). Mencionar el bloqueo optimista (`@Version`) ante ventas simultáneas |
| 4:30–5:30 | E2 | Demo: apartados | VENDEDOR: apartar una prenda (stock reservado vs. disponible), convertirla en venta. Ley 1581: una tarea nocturna anonimiza las reservas cerradas hace 90 días o más; ningún reporte ni exportación incluye datos del cliente |
| 5:30–7:30 | E3 | Demo: analítica | Clasificación ABC por variante; punto de reorden (normal / Poisson); pestaña Pronóstico: LIV001 con Holt-Winters (serie observada y corregida por agotados, backtest, reparto por variante) frente a un producto con SES y su motivo |
| 7:30–9:00 | E3 | Demo: compras | INVENTARIO: «Generar sugerencia» (política (s, S), lote mínimo, justificación por línea); ADMIN: aprobar (queda «en camino» y la alerta STOCK_BAJO usa la posición); INVENTARIO: recibir → ENTRADAS `PLAN_COMPRA/{id}` en el kardex |
| 9:00–11:00 | E4 | Demo: reportes | CONTABILIDAD: Rentabilidad (margen por producto y gráfico del top 10); Libro de ventas por mes (la devolución cuenta en su fecha y concilia con la rentabilidad); Valorización (en camino informativo); **Movimientos** del periodo (saldo inicial + flujos = saldo final). Exportar a PDF y Excel; mostrar la hoja de detalle. INVENTARIO intenta `/reportes` → acceso denegado |
| 11:00–12:30 | E5 | Calidad | Pruebas: ~1.400 del backend (unitarias, WebMvc por rol, integración con MySQL real en Testcontainers) y 642 del frontend; CI en cada PR. RNF-03: todos los reportes < 0,5 s con 12.000 ventas (exige < 3 s) y EXPLAIN sin recorridos completos. Matriz de roles probada por HTTP |
| 12:30–14:00 | E5 | Resultados | Semilla de 36 meses (~20.000 ventas): 14,4 % de variantes en alerta de stock (meta 10–15 %; sin contar lo en camino 38,8 %); WAPE global del pronóstico 18,3 % (Holt-Winters 17,1 %); recálculo analítico en 6,5 s. Corrección por agotados: «T2-f7» |
| 14:00–15:00 | E1 | Cierre | Limitaciones honestas y trabajo futuro (abajo); agradecimiento y paso a preguntas |

## Mensajes clave (si el tiempo se acorta, que no falten)

1. **Variantes talla × color** con stock físico, reservado y disponible; el kardex inmutable explica cada unidad.
2. **Del dato a la decisión**: ABC → punto de reorden → pronóstico → sugerencia de compra → recepción, con roles.
3. **Reportes que cuadran**: el libro concilia con la rentabilidad y los movimientos cuadran con el kardex.
4. **Calidad medida**: pruebas automáticas, CI, rendimiento y seguridad verificados, no supuestos.

## Limitaciones y trabajo futuro (decirlas antes de que las pregunten)

- No hay despliegue productivo: se corre en local (Docker Compose o MySQL nativo); el objetivo está en ADR-001 y en el
  diagrama de despliegue.
- Fuera de alcance: correo de reportes (CU-06), gastos y validación fiscal (CU-07), registro de proveedores (CU-01).
- El margen usa el último costo de entrada (aproximado si el costo varía entre compras).
- El backtest no cubre noviembre y diciembre: el error en temporada alta puede ser mayor que el WAPE informado.
- La corrección por agotados recupera menos unidades de las perdidas (fase 6 ≈ 0,3×; ver «T2-f7» con la ventana
  comparable).

## Preguntas probables

| Pregunta | Respuesta corta | Quién |
|---|---|---|
| ¿Qué pasa si dos vendedores venden la última unidad a la vez? | Bloqueo optimista con `@Version` en la variante: una confirma, la otra recibe 409 y reintenta; el stock nunca queda negativo (restricciones en la base) | E2 |
| ¿Cómo corrigen un error de inventario si el kardex no se edita? | Con un AJUSTE positivo o negativo con observación; el historial queda completo | E2 |
| ¿Por qué Holt-Winters y no un modelo más complejo? | La serie es mensual, corta y estacional; HW multiplicativo captura nivel, tendencia y temporada, y compite contra SES por backtesting: se elige el de menor WAPE por producto | E3 |
| ¿Qué es la «posición» de inventario? | Disponible + lo que está en camino en planes aprobados; evita volver a alertar o sugerir lo que ya se pidió | E3 |
| ¿Cómo garantizan que el VENDEDOR no ve costos? | El servidor decide: `@PreAuthorize` por endpoint y secciones en null en el panel; hay pruebas que lo verifican por HTTP | E4 |
| ¿Los reportes son contables oficiales? | Son reportes de gestión compatibles con auditoría (marca de autor y fecha, totales, detalle); no reemplazan la contabilidad fiscal | E4 |
| ¿Cómo cumplen la Ley 1581? | Datos mínimos del cliente solo en apartados, anonimización nocturna de las reservas cerradas hace 90 días o más y ninguna exportación con datos personales (prueba que los busca en los bytes) | E2 / E4 |
| ¿Cómo saben que responde en menos de 3 s? | Prueba automática con 12.000 ventas y 40.000 movimientos (peor de 5 corridas) y medición sobre la semilla | E5 |
| ¿Qué harían con más tiempo? | Despliegue en la nube, proveedores y órdenes de compra formales, notificaciones por correo, pronóstico con eventos de temporada | E1 |

## Ensayo

- Cronometrar dos veces completas; recortar primero el bloque de apartados (dejar la conversión) y luego Valorización.
- Cada expositor dice su bloque sin leer; la demo sigue siempre el mismo orden de pestañas.
- Anotar en `docs/progreso.md` la duración de cada ensayo y lo que se recortó.
