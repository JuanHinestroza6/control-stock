package co.livorno.controlstock.reporte.servicio;

import static co.livorno.controlstock.reporte.servicio.CalculosReporte.dinero;
import static co.livorno.controlstock.reporte.servicio.CalculosReporte.porcentaje;
import static co.livorno.controlstock.reporte.servicio.CalculosReporte.suma;

import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.ColumnaReporte;
import co.livorno.controlstock.comun.exportacion.DocumentoTabular;
import co.livorno.controlstock.comun.exportacion.ExportadorDocumentos;
import co.livorno.controlstock.comun.exportacion.FiltroReporte;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.comun.exportacion.SeccionTabular;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.FilaValorizacion;
import co.livorno.controlstock.reporte.dto.FiltroValorizacion;
import co.livorno.controlstock.reporte.dto.ValorizacionResponse;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.AcumuladoValorizacion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valorización del inventario al momento de la consulta (REQ-07, ADR-011): físico por el precio de compra actual del
 * producto (incluye lo reservado), valor a precio de venta y margen potencial. Lo en camino (planes APROBADO) es solo
 * informativo y no suma al valor. Solo lectura.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValorizacionService {

    static final String MENSAJE_SIN_EXISTENCIAS = "No hay existencias en el inventario";

    private final ConsultasReporte consultas;
    private final ExportadorDocumentos exportador;
    private final Clock clock;

    /** La agrupación nula se completa con PRODUCTO. */
    public ValorizacionResponse consultar(FiltroValorizacion filtro) {
        AgrupacionReporte agrupacion = filtro.agrupacion() != null ? filtro.agrupacion() : AgrupacionReporte.PRODUCTO;
        Long categoriaId = filtro.categoriaId();
        boolean incluirInactivas = filtro.incluirInactivas();
        String categoria = categoriaId != null ? consultas.nombreCategoria(categoriaId) : null;

        List<FilaValorizacion> filas = new ArrayList<>(consultas.valorizacion(categoriaId, agrupacion, incluirInactivas)
                .stream().map(ValorizacionService::fila).toList());
        filas.sort(Comparator.comparing(FilaValorizacion::valorCosto).reversed()
                .thenComparing(FilaValorizacion::id));
        FilaValorizacion totales = totales(filas);

        List<String> notas = new ArrayList<>();
        notas.add("Valorizado al costo actual (último costo de entrada, ADR-006); incluye las unidades reservadas.");
        notas.add("Lo en camino (pedidos aprobados, a costo estimado) es informativo y no suma al valor del inventario.");
        notas.add(incluirInactivas ? "Incluye las variantes inactivas que aún tienen existencias."
                : "No incluye las variantes ni productos inactivos.");
        long conCostoCero = consultas.productosConCostoCero(categoriaId, incluirInactivas);
        if (conCostoCero > 0) {
            notas.add(conCostoCero + " productos con precio de compra 0: su valor a costo es 0.");
        }
        boolean sinExistencias = totales.unidadesFisicas() == 0;
        return new ValorizacionResponse(categoriaId, categoria, agrupacion, incluirInactivas, sinExistencias,
                sinExistencias ? MENSAJE_SIN_EXISTENCIAS : null, List.copyOf(filas), totales, List.copyOf(notas),
                LocalDateTime.now(clock));
    }

    /** Convierte la misma respuesta de {@link #consultar} en PDF o Excel (lo exportado es lo mostrado). */
    public ArchivoExportado exportar(FiltroValorizacion filtro, FormatoExportacion formato, String generadoPor) {
        ValorizacionResponse r = consultar(filtro);
        String nombreBase = "valorizacion_" + LocalDate.now(clock);
        return exportador.exportar(documento(r, generadoPor), formato, nombreBase);
    }

    private static FilaValorizacion fila(AcumuladoValorizacion a) {
        BigDecimal costo = dinero(a.valorCosto());
        BigDecimal venta = dinero(a.valorVenta());
        BigDecimal margen = venta.subtract(costo);
        return new FilaValorizacion(a.id(), a.codigo(), a.nombre(), a.categoria(), a.fisicas(), a.reservadas(), costo,
                venta, margen, porcentaje(margen, venta), a.enCamino(), dinero(a.valorEnCamino()));
    }

    /** Totales sumados en Java desde las filas. */
    private static FilaValorizacion totales(List<FilaValorizacion> filas) {
        BigDecimal costo = dinero(suma(filas.stream().map(FilaValorizacion::valorCosto).toList()));
        BigDecimal venta = dinero(suma(filas.stream().map(FilaValorizacion::valorVenta).toList()));
        BigDecimal margen = venta.subtract(costo);
        return new FilaValorizacion(null, null, "Total", null,
                filas.stream().mapToLong(FilaValorizacion::unidadesFisicas).sum(),
                filas.stream().mapToLong(FilaValorizacion::unidadesReservadas).sum(), costo, venta, margen,
                porcentaje(margen, venta), filas.stream().mapToLong(FilaValorizacion::unidadesEnCamino).sum(),
                dinero(suma(filas.stream().map(FilaValorizacion::valorEnCamino).toList())));
    }

    private static DocumentoTabular documento(ValorizacionResponse r, String generadoPor) {
        boolean porCategoria = r.agrupacion() == AgrupacionReporte.CATEGORIA;
        List<ColumnaReporte> columnas = new ArrayList<>();
        if (!porCategoria) {
            columnas.add(ColumnaReporte.texto(r.agrupacion() == AgrupacionReporte.VARIANTE ? "SKU" : "Código", 16));
        }
        columnas.add(ColumnaReporte.texto(switch (r.agrupacion()) {
            case CATEGORIA -> "Categoría";
            case PRODUCTO -> "Producto";
            case VARIANTE -> "Variante";
        }, 32));
        if (!porCategoria) {
            columnas.add(ColumnaReporte.texto("Categoría", 16));
        }
        columnas.addAll(List.of(ColumnaReporte.entero("Uds. físicas", 10), ColumnaReporte.entero("Uds. reservadas", 10),
                ColumnaReporte.dinero("Valor a costo", 16), ColumnaReporte.dinero("Valor a precio de venta", 16),
                ColumnaReporte.dinero("Margen potencial", 16), ColumnaReporte.porcentaje("Margen %", 9),
                ColumnaReporte.entero("En camino (uds.)", 10), ColumnaReporte.dinero("Valor en camino", 16)));

        List<List<Object>> filas = new ArrayList<>();
        for (FilaValorizacion f : r.filas()) {
            List<Object> c = new ArrayList<>();
            if (!porCategoria) {
                c.add(f.codigo());
            }
            c.add(f.nombre());
            if (!porCategoria) {
                c.add(f.categoria());
            }
            agregarCifras(c, f);
            filas.add(c);
        }
        List<Object> totales = new ArrayList<>();
        if (porCategoria) {
            totales.add("Total");
        } else {
            totales.add(null);
            totales.add("Total");
            totales.add(null);
        }
        agregarCifras(totales, r.totales());
        SeccionTabular seccion = SeccionTabular.de("Valorización", columnas, filas).conTotales(totales);
        if (r.sinExistencias()) {
            seccion = seccion.conMensaje(r.mensaje());
        }
        List<FiltroReporte> filtros = List.of(
                new FiltroReporte("Categoría", r.categoria() != null ? r.categoria() : "Todas"),
                new FiltroReporte("Agrupación", r.agrupacion().name()),
                new FiltroReporte("Incluye inactivas", r.incluirInactivas() ? "Sí" : "No"));
        return new DocumentoTabular("Valorización del inventario", filtros, generadoPor, r.generadoEn(),
                List.of(seccion), r.notas());
    }

    private static void agregarCifras(List<Object> celdas, FilaValorizacion f) {
        celdas.add(f.unidadesFisicas());
        celdas.add(f.unidadesReservadas());
        celdas.add(f.valorCosto());
        celdas.add(f.valorVenta());
        celdas.add(f.margenPotencial());
        celdas.add(f.margenPotencialPorcentaje());
        celdas.add(f.unidadesEnCamino());
        celdas.add(f.valorEnCamino());
    }
}
