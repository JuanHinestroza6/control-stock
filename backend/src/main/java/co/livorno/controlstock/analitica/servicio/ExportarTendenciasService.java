package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.dto.PuntoTendencia;
import co.livorno.controlstock.analitica.dto.TendenciaResponse;
import co.livorno.controlstock.analitica.repositorio.ConsultasTendencias;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.ColumnaReporte;
import co.livorno.controlstock.comun.exportacion.DocumentoTabular;
import co.livorno.controlstock.comun.exportacion.ExportadorDocumentos;
import co.livorno.controlstock.comun.exportacion.FiltroReporte;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.comun.exportacion.SeccionTabular;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportación de las tendencias históricas a PDF y Excel (CU-05, REQ-09, ADR-011). Usa la misma respuesta que
 * {@link TendenciasService#consultar}; agrega un resumen calculado (sin pronóstico) y, en el PDF, un gráfico de líneas.
 * Solo lectura; sin costos.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ExportarTendenciasService {

    static final String MENSAJE_SIN_DATOS = "No hay datos disponibles para el rango";

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter MES_LARGO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES_CO);

    private final TendenciasService tendencias;
    private final ConsultasTendencias consultas;
    private final ExportadorDocumentos exportador;
    private final Clock clock;

    public ExportarTendenciasService(TendenciasService tendencias, ConsultasTendencias consultas,
            ExportadorDocumentos exportador, Clock clock) {
        this.tendencias = tendencias;
        this.consultas = consultas;
        this.exportador = exportador;
        this.clock = clock;
    }

    /** Los filtros se validan y completan igual que en {@link TendenciasService#consultar} (400 si son inválidos). */
    public ArchivoExportado exportar(String desde, String hasta, Long categoriaId, Long productoId,
            FormatoExportacion formato, String generadoPor) {
        TendenciaResponse r = tendencias.consultar(desde, hasta, categoriaId, productoId);
        String nombreBase = "tendencias_" + r.desde() + "_" + r.hasta();
        return exportador.exportar(documento(r, generadoPor, formato == FormatoExportacion.PDF), formato, nombreBase);
    }

    private DocumentoTabular documento(TendenciaResponse r, String generadoPor, boolean conGrafico) {
        boolean sinDatos = r.totalUnidades() == 0 && r.totalIngreso().signum() == 0;

        List<FiltroReporte> filtros = new ArrayList<>();
        filtros.add(new FiltroReporte("Desde", r.desde()));
        filtros.add(new FiltroReporte("Hasta", r.hasta()));
        filtros.add(new FiltroReporte("Categoría", r.categoriaId() == null ? "Todas" : orId(
                consultas.nombreCategoria(r.categoriaId()), r.categoriaId())));
        filtros.add(new FiltroReporte("Producto", r.productoId() == null ? "Todos" : orId(
                consultas.nombreProducto(r.productoId()), r.productoId())));

        List<ColumnaReporte> columnas = List.of(ColumnaReporte.texto("Mes", 10), ColumnaReporte.entero("Unidades", 11),
                ColumnaReporte.dinero("Ingreso neto", 16), ColumnaReporte.entero("Uds. año anterior", 13),
                ColumnaReporte.dinero("Ingreso año anterior", 16), ColumnaReporte.porcentaje("Var. unidades %", 12),
                ColumnaReporte.porcentaje("Var. ingreso %", 12));

        List<List<Object>> filas = new ArrayList<>();
        long unidadesPrevias = 0;
        BigDecimal ingresoPrevio = BigDecimal.ZERO;
        for (PuntoTendencia p : r.puntos()) {
            filas.add(java.util.Arrays.asList(p.periodo(), p.unidades(), p.ingreso(), p.unidadesAnioAnterior(),
                    p.ingresoAnioAnterior(), p.variacionUnidades(), p.variacionIngreso()));
            unidadesPrevias += p.unidadesAnioAnterior();
            ingresoPrevio = ingresoPrevio.add(p.ingresoAnioAnterior());
        }
        SeccionTabular seccion;
        List<String> notas = new ArrayList<>();
        if (sinDatos) {
            seccion = SeccionTabular.de("Tendencia mensual", columnas, List.of()).conMensaje(MENSAJE_SIN_DATOS);
        } else {
            List<Object> totales = java.util.Arrays.asList("Total", r.totalUnidades(), r.totalIngreso(), unidadesPrevias,
                    ingresoPrevio, variacion(BigDecimal.valueOf(r.totalUnidades()), BigDecimal.valueOf(unidadesPrevias)),
                    variacion(r.totalIngreso(), ingresoPrevio));
            seccion = SeccionTabular.de("Tendencia mensual", columnas, filas).conTotales(totales);
            if (conGrafico) {
                byte[] png = grafico(r.puntos());
                if (png != null) {
                    seccion = seccion.conImagenPdf(png);
                }
            }
            notas.add(resumen(r, unidadesPrevias, ingresoPrevio));
        }
        notas.add("Demanda comercial: unidades e ingreso netos de devoluciones; las ventas anuladas no cuentan. No"
                + " incluye pronóstico ni costos.");
        return new DocumentoTabular("Tendencias históricas de ventas", filtros, generadoPor, LocalDateTime.now(clock),
                List.of(seccion), notas);
    }

    /** Un fallo al dibujar (p. ej. sin fuentes en el servidor) no impide entregar el documento sin gráfico. */
    private static byte[] grafico(List<PuntoTendencia> puntos) {
        try {
            return GraficoTendencias.png(puntos);
        } catch (RuntimeException | LinkageError e) {
            log.warn("No se pudo dibujar el gráfico de tendencias; el PDF se entrega solo con la tabla", e);
            return null;
        }
    }

    /** Párrafo de resumen del CU-05 (paso 6): total, variación frente al año anterior, mejor y peor mes. */
    static String resumen(TendenciaResponse r, long unidadesPrevias, BigDecimal ingresoPrevio) {
        Comparator<PuntoTendencia> porIngreso = Comparator.comparing(PuntoTendencia::ingreso);
        PuntoTendencia mayor = r.puntos().stream().max(porIngreso.thenComparing(
                Comparator.comparing(PuntoTendencia::periodo).reversed())).orElseThrow();
        PuntoTendencia menor = r.puntos().stream().min(porIngreso.thenComparing(PuntoTendencia::periodo)).orElseThrow();

        StringBuilder t = new StringBuilder("Resumen: entre ").append(r.desde()).append(" y ").append(r.hasta())
                .append(" se vendieron ").append(entero(r.totalUnidades())).append(" unidades con un ingreso neto de $")
                .append(dinero(r.totalIngreso())).append(". ");
        BigDecimal var = variacion(r.totalIngreso(), ingresoPrevio);
        if (var == null) {
            t.append("El mismo periodo del año anterior no tuvo ventas, por lo que no hay variación para comparar. ");
        } else {
            t.append("Frente al mismo periodo del año anterior (").append(entero(unidadesPrevias))
                    .append(" unidades, $").append(dinero(ingresoPrevio)).append(") el ingreso ")
                    .append(var.signum() >= 0 ? "creció " : "disminuyó ").append(porcentaje(var.abs())).append(" %. ");
        }
        t.append("El mes de mayor venta fue ").append(mes(mayor)).append(" ($").append(dinero(mayor.ingreso()))
                .append(") y el de menor venta, ").append(mes(menor)).append(" ($").append(dinero(menor.ingreso()))
                .append(").");
        return t.toString();
    }

    private static String mes(PuntoTendencia p) {
        return YearMonth.parse(p.periodo()).format(MES_LARGO);
    }

    private static String orId(String nombre, Long id) {
        return nombre != null ? nombre : "#" + id;
    }

    private static BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return actual.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static String entero(long v) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(ES_CO)).format(v);
    }

    private static String dinero(BigDecimal v) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ES_CO)).format(v);
    }

    private static String porcentaje(BigDecimal v) {
        return new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(ES_CO)).format(v);
    }
}
