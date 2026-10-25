package co.livorno.controlstock.reporte.servicio;

import static co.livorno.controlstock.reporte.servicio.CalculosReporte.MENSAJE_SIN_VENTAS;
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
import co.livorno.controlstock.reporte.dto.CriterioRentabilidad;
import co.livorno.controlstock.reporte.dto.FilaRentabilidad;
import co.livorno.controlstock.reporte.dto.FiltroRentabilidad;
import co.livorno.controlstock.reporte.dto.RentabilidadResponse;
import co.livorno.controlstock.reporte.dto.TotalesRentabilidad;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.AcumuladoRentabilidad;
import co.livorno.controlstock.reporte.servicio.ValidadorRangoReporte.RangoReporte;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporte de rentabilidad (REQ-03, CU-06, ADR-011). Solo ventas REGISTRADA por fecha de venta; las devoluciones
 * (de cualquier fecha) restan en la fecha de la venta, igual que Más vendidos. Solo lectura.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentabilidadService {

    private final ConsultasReporte consultas;
    private final ValidadorRangoReporte validador;
    private final ExportadorDocumentos exportador;
    private final Clock clock;

    /** El filtro puede traer fechas, agrupación u orden nulos: se completan con los valores por defecto. */
    public RentabilidadResponse consultar(FiltroRentabilidad filtro) {
        RangoReporte rango = validador.validar(filtro.desde(), filtro.hasta());
        AgrupacionReporte agrupacion = filtro.agrupacion() != null ? filtro.agrupacion() : AgrupacionReporte.PRODUCTO;
        CriterioRentabilidad orden = filtro.orden() != null ? filtro.orden() : CriterioRentabilidad.MARGEN;
        Long categoriaId = filtro.categoriaId();
        String categoria = categoriaId != null ? consultas.nombreCategoria(categoriaId) : null;

        List<AcumuladoRentabilidad> acumulados = consultas.rentabilidad(rango.desde(), rango.hasta(), categoriaId,
                agrupacion);
        BigDecimal margenTotal = dinero(suma(acumulados.stream().map(RentabilidadService::margen).toList()));
        List<FilaRentabilidad> filas = new ArrayList<>(acumulados.stream().map(a -> fila(a, margenTotal)).toList());
        filas.sort(comparador(orden));
        TotalesRentabilidad totales = totales(filas);

        List<String> notas = new ArrayList<>();
        notas.add("El costo de ventas usa el último costo de entrada vigente al vender (ADR-006): el margen es aproximado.");
        notas.add("Las devoluciones se atribuyen a la fecha de la venta; la merma por devoluciones no aptas se informa"
                + " aparte (resultado después de mermas).");
        long lineasCostoCero = acumulados.stream().mapToLong(AcumuladoRentabilidad::lineasCostoCero).sum();
        if (lineasCostoCero > 0) {
            notas.add(lineasCostoCero + " líneas con costo 0: su margen es igual al ingreso.");
        }
        boolean sinVentas = filas.isEmpty();
        return new RentabilidadResponse(rango.desde(), rango.hasta(), categoriaId, categoria, agrupacion, orden,
                sinVentas, sinVentas ? MENSAJE_SIN_VENTAS : null, List.copyOf(filas), totales, List.copyOf(notas),
                LocalDateTime.now(clock));
    }

    /** Convierte la misma respuesta de {@link #consultar} en PDF o Excel (lo exportado es lo mostrado). */
    public ArchivoExportado exportar(FiltroRentabilidad filtro, FormatoExportacion formato, String generadoPor) {
        RentabilidadResponse r = consultar(filtro);
        String nombreBase = "rentabilidad_" + r.desde() + "_" + r.hasta();
        return exportador.exportar(documento(r, generadoPor), formato, nombreBase);
    }

    private static BigDecimal margen(AcumuladoRentabilidad a) {
        return a.facturado().subtract(a.devoluciones()).subtract(a.costoVentas());
    }

    private static FilaRentabilidad fila(AcumuladoRentabilidad a, BigDecimal margenTotal) {
        BigDecimal neto = dinero(a.facturado().subtract(a.devoluciones()));
        BigDecimal costo = dinero(a.costoVentas());
        BigDecimal margen = neto.subtract(costo);
        BigDecimal merma = dinero(a.mermaNoApta());
        return new FilaRentabilidad(a.id(), a.codigo(), a.nombre(), a.categoria(), dinero(a.ventasLista()),
                dinero(a.descuentos()), dinero(a.facturado()), dinero(a.devoluciones()), neto, costo, margen,
                porcentaje(margen, neto), merma, margen.subtract(merma), a.vendidas(), a.devueltas(),
                a.vendidas() - a.devueltas(), porcentaje(margen, margenTotal));
    }

    private static Comparator<FilaRentabilidad> comparador(CriterioRentabilidad orden) {
        Comparator<FilaRentabilidad> principal = switch (orden) {
            case MARGEN -> Comparator.comparing(FilaRentabilidad::margenBruto).reversed();
            case INGRESO -> Comparator.comparing(FilaRentabilidad::ingresoNeto).reversed();
            case UNIDADES -> Comparator.comparingLong(FilaRentabilidad::unidadesNetas).reversed();
        };
        return principal.thenComparing(FilaRentabilidad::id);
    }

    /** Totales sumados en Java desde las filas ya redondeadas. */
    private static TotalesRentabilidad totales(List<FilaRentabilidad> filas) {
        BigDecimal lista = dinero(suma(filas.stream().map(FilaRentabilidad::ventasLista).toList()));
        BigDecimal descuentos = dinero(suma(filas.stream().map(FilaRentabilidad::descuentos).toList()));
        BigDecimal facturado = dinero(suma(filas.stream().map(FilaRentabilidad::facturado).toList()));
        BigDecimal devoluciones = dinero(suma(filas.stream().map(FilaRentabilidad::devoluciones).toList()));
        BigDecimal neto = dinero(suma(filas.stream().map(FilaRentabilidad::ingresoNeto).toList()));
        BigDecimal costo = dinero(suma(filas.stream().map(FilaRentabilidad::costoVentas).toList()));
        BigDecimal merma = dinero(suma(filas.stream().map(FilaRentabilidad::mermaNoApta).toList()));
        BigDecimal margen = neto.subtract(costo);
        long vendidas = filas.stream().mapToLong(FilaRentabilidad::unidadesVendidas).sum();
        long devueltas = filas.stream().mapToLong(FilaRentabilidad::unidadesDevueltas).sum();
        return new TotalesRentabilidad(lista, descuentos, facturado, devoluciones, neto, costo, margen,
                porcentaje(margen, neto), merma, margen.subtract(merma), vendidas, devueltas, vendidas - devueltas);
    }

    private static DocumentoTabular documento(RentabilidadResponse r, String generadoPor) {
        boolean porCategoria = r.agrupacion() == AgrupacionReporte.CATEGORIA;
        List<ColumnaReporte> columnas = new ArrayList<>();
        if (!porCategoria) {
            columnas.add(ColumnaReporte.texto(r.agrupacion() == AgrupacionReporte.VARIANTE ? "SKU" : "Código", 14));
        }
        columnas.add(ColumnaReporte.texto(switch (r.agrupacion()) {
            case CATEGORIA -> "Categoría";
            case PRODUCTO -> "Producto";
            case VARIANTE -> "Variante";
        }, 32));
        if (!porCategoria) {
            columnas.add(ColumnaReporte.texto("Categoría", 16));
        }
        columnas.addAll(List.of(ColumnaReporte.dinero("Ventas a lista", 15), ColumnaReporte.dinero("Descuentos", 14),
                ColumnaReporte.dinero("Facturado", 15), ColumnaReporte.dinero("Devoluciones", 14),
                ColumnaReporte.dinero("Ingreso neto", 15), ColumnaReporte.dinero("Costo de ventas", 15),
                ColumnaReporte.dinero("Margen bruto", 15), ColumnaReporte.porcentaje("Margen %", 9),
                ColumnaReporte.dinero("Merma no apta", 14), ColumnaReporte.dinero("Resultado tras mermas", 16),
                ColumnaReporte.entero("Uds. vendidas", 9), ColumnaReporte.entero("Uds. devueltas", 9),
                ColumnaReporte.entero("Uds. netas", 9), ColumnaReporte.porcentaje("% del margen", 10)));

        List<List<Object>> filas = new ArrayList<>();
        for (FilaRentabilidad f : r.filas()) {
            List<Object> celdas = new ArrayList<>();
            if (!porCategoria) {
                celdas.add(f.codigo());
            }
            celdas.add(f.nombre());
            if (!porCategoria) {
                celdas.add(f.categoria());
            }
            celdas.addAll(List.of(f.ventasLista(), f.descuentos(), f.facturado(), f.devoluciones(), f.ingresoNeto(),
                    f.costoVentas(), f.margenBruto()));
            celdas.add(f.margenPorcentaje());
            celdas.add(f.mermaNoApta());
            celdas.add(f.resultadoDespuesMermas());
            celdas.addAll(List.of(f.unidadesVendidas(), f.unidadesDevueltas(), f.unidadesNetas()));
            celdas.add(f.participacionMargen());
            filas.add(celdas);
        }
        TotalesRentabilidad t = r.totales();
        List<Object> totales = new ArrayList<>();
        if (porCategoria) {
            totales.add("Total");
        } else {
            totales.add(null);
            totales.add("Total");
            totales.add(null);
        }
        totales.addAll(List.of(t.ventasLista(), t.descuentos(), t.facturado(), t.devoluciones(), t.ingresoNeto(),
                t.costoVentas(), t.margenBruto()));
        totales.add(t.margenPorcentaje());
        totales.add(t.mermaNoApta());
        totales.add(t.resultadoDespuesMermas());
        totales.addAll(List.of(t.unidadesVendidas(), t.unidadesDevueltas(), t.unidadesNetas()));
        totales.add(r.sinVentas() ? null : new BigDecimal("100.0"));
        SeccionTabular seccion = SeccionTabular.de("Rentabilidad", columnas, filas).conTotales(totales);
        if (r.sinVentas()) {
            seccion = seccion.conMensaje(r.mensaje());
        }
        List<FiltroReporte> filtros = List.of(new FiltroReporte("Desde", r.desde().toString()),
                new FiltroReporte("Hasta", r.hasta().toString()),
                new FiltroReporte("Categoría", r.categoria() != null ? r.categoria() : "Todas"),
                new FiltroReporte("Agrupación", r.agrupacion().name()),
                new FiltroReporte("Orden", r.orden().name()));
        return new DocumentoTabular("Reporte de rentabilidad", filtros, generadoPor, r.generadoEn(),
                List.of(seccion), r.notas());
    }
}
