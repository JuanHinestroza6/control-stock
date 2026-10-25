package co.livorno.controlstock.reporte.servicio;

import static co.livorno.controlstock.reporte.servicio.CalculosReporte.MENSAJE_SIN_VENTAS;
import static co.livorno.controlstock.reporte.servicio.CalculosReporte.dinero;
import static co.livorno.controlstock.reporte.servicio.CalculosReporte.suma;

import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.ColumnaReporte;
import co.livorno.controlstock.comun.exportacion.DocumentoTabular;
import co.livorno.controlstock.comun.exportacion.ExportadorDocumentos;
import co.livorno.controlstock.comun.exportacion.FiltroReporte;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.comun.exportacion.SeccionTabular;
import co.livorno.controlstock.reporte.dto.FilaLibro;
import co.livorno.controlstock.reporte.dto.FiltroLibroVentas;
import co.livorno.controlstock.reporte.dto.LibroVentasResponse;
import co.livorno.controlstock.reporte.dto.PeriodoLibro;
import co.livorno.controlstock.reporte.dto.VentaAnuladaItem;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.DevolucionDetalle;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.DevolucionesPeriodo;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.LineaDetalle;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.VentasPeriodo;
import co.livorno.controlstock.reporte.servicio.ValidadorRangoReporte.RangoReporte;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Libro de ventas (REQ-07, CU-07, ADR-011). Criterio contable: las ventas cuentan en la fecha de la venta y las
 * devoluciones, con el reverso de su costo, en la fecha de la devolución; así un periodo cerrado no cambia por una
 * devolución posterior. Sobre un rango que cubra la venta y sus devoluciones concilia con la rentabilidad. Las
 * ventas anuladas solo aparecen en su sección. Solo lectura.
 */
@Service
@Transactional(readOnly = true)
public class LibroVentasService {

    public static final int LIMITE_DETALLE_POR_DEFECTO = 50_000;

    private final ConsultasReporte consultas;
    private final ValidadorRangoReporte validador;
    private final ExportadorDocumentos exportador;
    private final Clock clock;
    private final int limiteDetalle;

    public LibroVentasService(ConsultasReporte consultas, ValidadorRangoReporte validador,
            ExportadorDocumentos exportador, Clock clock,
            @Value("${app.reportes.libro.max-lineas-detalle:" + LIMITE_DETALLE_POR_DEFECTO + "}") int limiteDetalle) {
        this.consultas = consultas;
        this.validador = validador;
        this.exportador = exportador;
        this.clock = clock;
        this.limiteDetalle = limiteDetalle;
    }

    /** El filtro puede traer fechas o agrupación nulas: se completan con los valores por defecto (día). */
    public LibroVentasResponse consultar(FiltroLibroVentas filtro) {
        RangoReporte rango = validador.validar(filtro.desde(), filtro.hasta());
        PeriodoLibro agrupacion = filtro.agrupacion() != null ? filtro.agrupacion() : PeriodoLibro.DIA;

        Map<String, FilaLibro> porPeriodo = new TreeMap<>();
        for (VentasPeriodo v : consultas.ventasPorPeriodo(rango.desde(), rango.hasta(), agrupacion)) {
            porPeriodo.put(v.periodo(), new FilaLibro(v.periodo(), v.numeroVentas(), dinero(v.ventasLista()),
                    dinero(v.descuentos()), dinero(v.facturado()), dinero(null), dinero(v.facturado()),
                    dinero(v.costo()), dinero(v.facturado().subtract(v.costo())), dinero(null), v.unidades(), 0));
        }
        for (DevolucionesPeriodo d : consultas.devolucionesPorPeriodo(rango.desde(), rango.hasta(), agrupacion)) {
            FilaLibro f = porPeriodo.getOrDefault(d.periodo(), new FilaLibro(d.periodo(), 0, dinero(null), dinero(null),
                    dinero(null), dinero(null), dinero(null), dinero(null), dinero(null), dinero(null), 0, 0));
            BigDecimal valor = dinero(d.valor());
            BigDecimal neto = f.facturado().subtract(valor);
            BigDecimal costo = f.costoVentas().subtract(dinero(d.costo()));
            porPeriodo.put(d.periodo(), new FilaLibro(f.periodo(), f.numeroVentas(), f.ventasLista(), f.descuentos(),
                    f.facturado(), valor, neto, costo, neto.subtract(costo), dinero(d.mermaNoApta()),
                    f.unidadesVendidas(), d.unidades()));
        }
        List<FilaLibro> filas = List.copyOf(porPeriodo.values());
        FilaLibro totales = totales(filas);

        List<VentaAnuladaItem> anuladas = consultas.ventasAnuladas(rango.desde(), rango.hasta());
        BigDecimal montoAnuladas = dinero(suma(anuladas.stream().map(VentaAnuladaItem::total).toList()));
        long lineas = consultas.contarLineasVenta(rango.desde(), rango.hasta());

        List<String> notas = new ArrayList<>();
        notas.add("Las ventas cuentan en la fecha de la venta y las devoluciones, con el reverso de su costo, en la"
                + " fecha de la devolución: el ingreso neto, el costo y el margen de un periodo pueden ser negativos.");
        notas.add("Solo se listan los periodos con movimiento (ventas o devoluciones); los demás se omiten.");
        notas.add("El costo de ventas usa el último costo de entrada vigente al vender (ADR-006): el margen es aproximado.");
        notas.add("Las ventas anuladas no están en las cifras; se listan aparte.");
        if (agrupacion == PeriodoLibro.MES && !rangoDeMesesCompletos(rango)) {
            notas.add("Los meses de los extremos del rango pueden estar incompletos.");
        }
        boolean sinVentas = filas.isEmpty();
        return new LibroVentasResponse(rango.desde(), rango.hasta(), agrupacion, sinVentas,
                sinVentas ? MENSAJE_SIN_VENTAS : null, filas, totales, anuladas, anuladas.size(), montoAnuladas,
                lineas, List.copyOf(notas), LocalDateTime.now(clock));
    }

    /**
     * Convierte la misma respuesta de {@link #consultar} en PDF o Excel. El PDF lleva el resumen y las anuladas; el
     * Excel agrega las hojas "Detalle de ventas" y "Devoluciones". Solo el Excel lleva detalle, por eso el límite de
     * líneas (422 "Acote el rango") se aplica solo a ese formato.
     */
    public ArchivoExportado exportar(FiltroLibroVentas filtro, FormatoExportacion formato, String generadoPor) {
        LibroVentasResponse r = consultar(filtro);
        boolean conDetalle = formato == FormatoExportacion.XLSX;
        if (conDetalle && r.lineasDetalle() > limiteDetalle) {
            throw new ExcepcionNegocio("El libro tiene " + r.lineasDetalle() + " líneas de venta y el Excel admite"
                    + " hasta " + limiteDetalle + ". Acote el rango.");
        }
        String nombreBase = "libro-ventas_" + r.desde() + "_" + r.hasta();
        return exportador.exportar(documento(r, generadoPor, conDetalle), formato, nombreBase);
    }

    private static boolean rangoDeMesesCompletos(RangoReporte rango) {
        return rango.desde().getDayOfMonth() == 1
                && rango.hasta().equals(rango.hasta().withDayOfMonth(rango.hasta().lengthOfMonth()));
    }

    private static FilaLibro totales(List<FilaLibro> filas) {
        return new FilaLibro(null, filas.stream().mapToLong(FilaLibro::numeroVentas).sum(),
                dinero(suma(filas.stream().map(FilaLibro::ventasLista).toList())),
                dinero(suma(filas.stream().map(FilaLibro::descuentos).toList())),
                dinero(suma(filas.stream().map(FilaLibro::facturado).toList())),
                dinero(suma(filas.stream().map(FilaLibro::devoluciones).toList())),
                dinero(suma(filas.stream().map(FilaLibro::ingresoNeto).toList())),
                dinero(suma(filas.stream().map(FilaLibro::costoVentas).toList())),
                dinero(suma(filas.stream().map(FilaLibro::margenBruto).toList())),
                dinero(suma(filas.stream().map(FilaLibro::mermaNoApta).toList())),
                filas.stream().mapToLong(FilaLibro::unidadesVendidas).sum(),
                filas.stream().mapToLong(FilaLibro::unidadesDevueltas).sum());
    }

    private DocumentoTabular documento(LibroVentasResponse r, String generadoPor, boolean conDetalle) {
        List<SeccionTabular> secciones = new ArrayList<>();
        secciones.add(resumen(r));
        secciones.add(anuladas(r));
        secciones.add(detalleVentas(r, conDetalle));
        secciones.add(devoluciones(r, conDetalle));
        List<FiltroReporte> filtros = List.of(new FiltroReporte("Desde", r.desde().toString()),
                new FiltroReporte("Hasta", r.hasta().toString()),
                new FiltroReporte("Agrupación", r.agrupacion() == PeriodoLibro.MES ? "Mes" : "Día"));
        return new DocumentoTabular("Libro de ventas", filtros, generadoPor, r.generadoEn(), secciones, r.notas());
    }

    private static SeccionTabular resumen(LibroVentasResponse r) {
        List<ColumnaReporte> columnas = List.of(ColumnaReporte.texto("Periodo", 12),
                ColumnaReporte.entero("N.º ventas", 9), ColumnaReporte.entero("Uds. vendidas", 9),
                ColumnaReporte.entero("Uds. devueltas", 9), ColumnaReporte.dinero("Ventas a lista", 15),
                ColumnaReporte.dinero("Descuentos", 14), ColumnaReporte.dinero("Facturado", 15),
                ColumnaReporte.dinero("Devoluciones", 14), ColumnaReporte.dinero("Ingreso neto", 15),
                ColumnaReporte.dinero("Costo de ventas", 15), ColumnaReporte.dinero("Margen bruto", 15),
                ColumnaReporte.dinero("Merma no apta", 14));
        List<List<Object>> filas = new ArrayList<>();
        for (FilaLibro f : r.filas()) {
            filas.add(celdas(f.periodo(), f));
        }
        SeccionTabular s = SeccionTabular.de("Resumen", columnas, filas).conTotales(celdas("Total", r.totales()));
        return r.sinVentas() ? s.conMensaje(r.mensaje()) : s;
    }

    private static List<Object> celdas(String periodo, FilaLibro f) {
        List<Object> c = new ArrayList<>();
        c.add(periodo);
        c.add(f.numeroVentas());
        c.add(f.unidadesVendidas());
        c.add(f.unidadesDevueltas());
        c.add(f.ventasLista());
        c.add(f.descuentos());
        c.add(f.facturado());
        c.add(f.devoluciones());
        c.add(f.ingresoNeto());
        c.add(f.costoVentas());
        c.add(f.margenBruto());
        c.add(f.mermaNoApta());
        return c;
    }

    private static SeccionTabular anuladas(LibroVentasResponse r) {
        List<ColumnaReporte> columnas = List.of(ColumnaReporte.texto("Venta", 11),
                ColumnaReporte.fechaHora("Fecha de la venta", 18), ColumnaReporte.dinero("Total", 15),
                ColumnaReporte.fechaHora("Anulada en", 18), ColumnaReporte.texto("Anulada por", 22),
                ColumnaReporte.texto("Motivo", 40));
        List<List<Object>> filas = new ArrayList<>();
        for (VentaAnuladaItem a : r.anuladas()) {
            filas.add(java.util.Arrays.asList(a.numero(), a.fecha(), a.total(), a.anuladaEn(), a.anuladaPor(),
                    a.motivoAnulacion()));
        }
        SeccionTabular s = SeccionTabular.de("Ventas anuladas", columnas, filas);
        if (r.anuladas().isEmpty()) {
            return s.conMensaje("No hay ventas anuladas en el periodo");
        }
        return s.conTotales(java.util.Arrays.asList("Total (" + r.totalAnuladas() + ")", null, r.montoAnuladas(), null,
                null, null));
    }

    private SeccionTabular detalleVentas(LibroVentasResponse r, boolean conDetalle) {
        List<ColumnaReporte> columnas = List.of(ColumnaReporte.texto("Venta", 11),
                ColumnaReporte.fechaHora("Fecha", 18), ColumnaReporte.texto("SKU", 22),
                ColumnaReporte.texto("Producto", 32), ColumnaReporte.entero("Cantidad", 9),
                ColumnaReporte.dinero("Precio de lista", 15), ColumnaReporte.dinero("Precio unitario", 15),
                ColumnaReporte.dinero("Costo unitario", 15), ColumnaReporte.dinero("Subtotal", 15));
        List<List<Object>> filas = new ArrayList<>();
        if (conDetalle) {
            for (LineaDetalle l : consultas.detalleVentas(r.desde(), r.hasta())) {
                filas.add(java.util.Arrays.asList(numeroVenta(l.ventaId()), l.fecha(), l.sku(), l.producto(),
                        l.cantidad(), l.precioLista(), l.precioUnitario(), l.costoUnitario(),
                        dinero(l.precioUnitario().multiply(BigDecimal.valueOf(l.cantidad())))));
            }
        }
        return SeccionTabular.de("Detalle de ventas", columnas, filas).soloEnExcel();
    }

    private SeccionTabular devoluciones(LibroVentasResponse r, boolean conDetalle) {
        List<ColumnaReporte> columnas = List.of(ColumnaReporte.fechaHora("Fecha de la devolución", 18),
                ColumnaReporte.texto("Venta", 11), ColumnaReporte.texto("SKU", 22),
                ColumnaReporte.texto("Producto", 32), ColumnaReporte.entero("Cantidad", 9),
                ColumnaReporte.texto("Apta para la venta", 10), ColumnaReporte.dinero("Precio unitario", 15),
                ColumnaReporte.dinero("Valor devuelto", 15), ColumnaReporte.dinero("Costo unitario", 15));
        List<List<Object>> filas = new ArrayList<>();
        if (conDetalle) {
            for (DevolucionDetalle d : consultas.detalleDevoluciones(r.desde(), r.hasta())) {
                filas.add(java.util.Arrays.asList(d.fecha(), numeroVenta(d.ventaId()), d.sku(), d.producto(),
                        d.cantidad(), d.aptaParaVenta() ? "Sí" : "No", d.precioUnitario(),
                        dinero(d.precioUnitario().multiply(BigDecimal.valueOf(d.cantidad()))), d.costoUnitario()));
            }
        }
        return SeccionTabular.de("Devoluciones", columnas, filas).soloEnExcel();
    }

    private static String numeroVenta(Long id) {
        return String.format("V-%06d", id);
    }
}
