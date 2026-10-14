package co.livorno.controlstock.semilla;

import co.livorno.controlstock.catalogo.dto.CrearProductoRequest;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesRequest;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse;
import co.livorno.controlstock.catalogo.dto.ProductoDetalleResponse;
import co.livorno.controlstock.catalogo.dto.VarianteResponse;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.servicio.ProductoService;
import co.livorno.controlstock.catalogo.servicio.VarianteService;
import co.livorno.controlstock.inventario.dto.EntradaRequest;
import co.livorno.controlstock.inventario.dto.LineaEntradaRequest;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Crea el catálogo de demostración y su entrada inicial usando solo los servicios reales (ADR-008). Se ejecuta dentro
 * de la transacción de la carga y como un usuario con permiso de catálogo e inventario.
 */
@Component
@Profile("seed")
public class CreadorCatalogo {

    /** Días de demanda que cubre el stock del día 0. */
    public static final int DIAS_STOCK_INICIAL = 45;
    /** Días de demanda que cubre el stock mínimo. */
    public static final int DIAS_STOCK_MINIMO = 14;
    public static final int STOCK_MINIMO_MINIMO = 2;
    static final double TENDENCIA_COSTO_MENSUAL = 1.004;
    static final double VARIACION_COSTO = 0.02;

    /** Variante creada, con lo que la simulación necesita para elegirla. */
    public record VarianteCreada(Long id, Long productoId, ProductoSemilla producto, Talla talla, String color,
                                 int indiceColor, int stockMinimo) {
    }

    /** Resultado: ids por código de producto y todas las variantes (orden: producto, talla, color). */
    public record CatalogoCreado(Map<String, Long> productoIds, List<VarianteCreada> variantes) {
    }

    private final ProductoService productos;
    private final VarianteService variantes;
    private final OperacionInventarioService operaciones;
    private final CategoriaRepository categorias;

    public CreadorCatalogo(ProductoService productos, VarianteService variantes,
                           OperacionInventarioService operaciones, CategoriaRepository categorias) {
        this.productos = productos;
        this.variantes = variantes;
        this.operaciones = operaciones;
        this.categorias = categorias;
    }

    /** Crea productos y variantes con {@code stockMinimo} de dos semanas de demanda (mínimo 2). */
    public CatalogoCreado crearCatalogo(List<ProductoSemilla> catalogo, ModeloDemanda modelo, LocalDate inicio) {
        Map<String, Long> categoriaIds = categorias.findAll().stream()
                .collect(Collectors.toMap(Categoria::getNombre, Categoria::getId));
        Map<String, Long> productoIds = new LinkedHashMap<>();
        List<VarianteCreada> creadas = new ArrayList<>();
        for (ProductoSemilla p : catalogo) {
            Long categoriaId = categoriaIds.get(p.categoria());
            if (categoriaId == null) {
                throw new SemillaAbortadaException("No existe la categoría base '" + p.categoria()
                        + "' (migración V3) para el producto " + p.codigo() + ".");
            }
            ProductoDetalleResponse producto = productos.crear(new CrearProductoRequest(p.codigo(), p.nombre(),
                    categoriaId, p.costoBase(), p.precioVenta(), p.leadTimeDias(), p.loteMinimo()));
            int minimo = stockMinimo(p, modelo, inicio);
            GenerarVariantesResponse generadas = variantes.generar(producto.id(),
                    new GenerarVariantesRequest(p.tallas(), p.colores(), minimo));
            productoIds.put(p.codigo(), producto.id());
            for (VarianteResponse v : generadas.creadas()) {
                creadas.add(new VarianteCreada(v.id(), producto.id(), p, v.talla(), v.color(),
                        indiceColor(p, v.color()), minimo));
            }
        }
        return new CatalogoCreado(productoIds, creadas);
    }

    /** Una entrada por producto con stock de 45 días de demanda; devuelve las unidades ingresadas. */
    public long entradaInicial(CatalogoCreado catalogo, ModeloDemanda modelo, LocalDate inicio, Random random) {
        Map<String, List<VarianteCreada>> porProducto = catalogo.variantes().stream()
                .collect(Collectors.groupingBy(v -> v.producto().codigo(), LinkedHashMap::new, Collectors.toList()));
        long unidades = 0;
        for (List<VarianteCreada> deProducto : porProducto.values()) {
            ProductoSemilla p = deProducto.get(0).producto();
            double demanda = modelo.demandaEsperada(p, inicio, DIAS_STOCK_INICIAL);
            BigDecimal costo = costoEntrada(p.costoBase(), 0, random);
            List<LineaEntradaRequest> lineas = new ArrayList<>();
            for (VarianteCreada v : deProducto) {
                int cantidad = Math.max(p.loteMinimo(), (int) Math.ceil(
                        demanda * ModeloDemanda.participacionVariante(p, v.talla(), v.indiceColor())));
                lineas.add(new LineaEntradaRequest(v.id(), cantidad, costo));
                unidades += cantidad;
            }
            operaciones.registrarEntrada(new EntradaRequest("Stock inicial", lineas));
        }
        return unidades;
    }

    /** Dos semanas de demanda esperada del producto repartidas entre sus variantes (mínimo 2). */
    static int stockMinimo(ProductoSemilla p, ModeloDemanda modelo, LocalDate inicio) {
        double porVariante = modelo.demandaEsperada(p, inicio, DIAS_STOCK_MINIMO) / p.totalVariantes();
        return Math.max(STOCK_MINIMO_MINIMO, (int) Math.ceil(porVariante));
    }

    /** Costo = base × 1,004^mes ± 2 %, redondeado a la centena (escala 2). */
    public static BigDecimal costoEntrada(BigDecimal costoBase, int mesesDesdeInicio, Random random) {
        double factor = Math.pow(TENDENCIA_COSTO_MENSUAL, mesesDesdeInicio)
                * (1 + (random.nextDouble() * 2 - 1) * VARIACION_COSTO);
        long centenas = Math.max(1, Math.round(costoBase.doubleValue() * factor / 100.0));
        return BigDecimal.valueOf(centenas * 100).setScale(2);
    }

    private static String clave(String color) {
        return Normalizer.normalize(color.strip().toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static int indiceColor(ProductoSemilla p, String color) {
        String buscado = clave(color);
        for (int i = 0; i < p.colores().size(); i++) {
            if (clave(p.colores().get(i)).equals(buscado)) {
                return i;
            }
        }
        throw new SemillaAbortadaException("Color no reconocido en " + p.codigo() + ": " + color);
    }
}
