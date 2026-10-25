package co.livorno.controlstock.reporte.servicio;

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
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.FilaMovimientos;
import co.livorno.controlstock.reporte.dto.FiltroMovimientos;
import co.livorno.controlstock.reporte.dto.MovimientosResponse;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.AcumuladoMovimientos;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.MovimientoDetalle;
import co.livorno.controlstock.reporte.servicio.ValidadorRangoReporte.RangoReporte;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Movimientos del periodo (REQ-07, CU-03; ADR-011, después del corte): estado del físico por grupo con saldo inicial,
 * entradas (y su valor a costo), ventas, anulaciones, devoluciones, ajustes y saldo final, sacado del kardex. Solo
 * lectura; el detalle de movimientos solo va en el Excel.
 */
@Service
@Transactional(readOnly = true)
public class MovimientosPeriodoService {

    public static final int LIMITE_DETALLE_POR_DEFECTO = 50_000;
    static final String MENSAJE_SIN_MOVIMIENTOS = "No hay movimientos en el periodo";

    private static final Map<String, String> TIPOS = Map.of("ENTRADA", "Entrada", "VENTA", "Venta",
            "VENTA_RESERVA", "Venta de apartado", "DEVOLUCION", "Devolución", "AJUSTE_POSITIVO", "Ajuste positivo",
            "AJUSTE_NEGATIVO", "Ajuste negativo", "ANULACION_VENTA", "Anulación de venta");

    private final ConsultasReporte consultas;
    private final ValidadorRangoReporte validador;
    private final ExportadorDocumentos exportador;
    private final Clock clock;
    private final int limiteDetalle;

    public MovimientosPeriodoService(ConsultasReporte consultas, ValidadorRangoReporte validador,
            ExportadorDocumentos exportador, Clock clock,
            @Value("${app.reportes.movimientos.max-detalle:" + LIMITE_DETALLE_POR_DEFECTO + "}") int limiteDetalle) {
        this.consultas = consultas;
        this.validador = validador;
        this.exportador = exportador;
        this.clock = clock;
        this.limiteDetalle = limiteDetalle;
    }

    /** Fechas nulas: del día 1 del mes hasta hoy; agrupación nula: PRODUCTO. */
    public MovimientosResponse consultar(FiltroMovimientos filtro) {
        RangoReporte rango = validador.validar(filtro.desde(), filtro.hasta());
        AgrupacionReporte agrupacion = filtro.agrupacion() != null ? filtro.agrupacion() : AgrupacionReporte.PRODUCTO;
        Long categoriaId = filtro.categoriaId();
        String categoria = categoriaId != null ? consultas.nombreCategoria(categoriaId) : null;

        List<FilaMovimientos> filas = new ArrayList<>(consultas.movimientos(rango.desde(), rango.hasta(), categoriaId,
                agrupacion).stream().map(MovimientosPeriodoService::fila).toList());
        filas.sort(Comparator.comparing(FilaMovimientos::codigo, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FilaMovimientos::nombre).thenComparing(FilaMovimientos::id));
        long movimientos = consultas.contarMovimientos(rango.desde(), rango.hasta(), categoriaId);

        List<String> notas = List.of(
                "Unidades físicas (incluyen lo reservado). Saldo final = saldo inicial + entradas − ventas + anulaciones"
                        + " + devoluciones + ajustes positivos − ajustes negativos.",
                "Las reservas y liberaciones no cambian el físico y no se incluyen.",
                "Las devoluciones no aptas para la venta no regresan al inventario y no son un movimiento; su costo"
                        + " está en la merma no apta del libro de ventas.",
                "El valor de las entradas es la cantidad por el costo unitario registrado en cada entrada.",
                "Se listan los grupos con movimientos en el periodo o con existencias al cierre, incluidas las"
                        + " variantes inactivas.");
        boolean sinMovimientos = movimientos == 0;
        return new MovimientosResponse(rango.desde(), rango.hasta(), categoriaId, categoria, agrupacion,
                sinMovimientos, sinMovimientos ? MENSAJE_SIN_MOVIMIENTOS : null, List.copyOf(filas), totales(filas),
                movimientos, notas, LocalDateTime.now(clock));
    }

    /**
     * Convierte la misma respuesta de {@link #consultar} en PDF o Excel. El Excel agrega la hoja "Detalle de
     * movimientos"; por eso el límite (422 "Acote el rango") se aplica solo a ese formato.
     */
    public ArchivoExportado exportar(FiltroMovimientos filtro, FormatoExportacion formato, String generadoPor) {
        MovimientosResponse r = consultar(filtro);
        boolean conDetalle = formato == FormatoExportacion.XLSX;
        if (conDetalle && r.movimientosDetalle() > limiteDetalle) {
            throw new ExcepcionNegocio("El reporte tiene " + r.movimientosDetalle() + " movimientos y el Excel admite"
                    + " hasta " + limiteDetalle + ". Acote el rango.");
        }
        String nombreBase = "movimientos_" + r.desde() + "_" + r.hasta();
        return exportador.exportar(documento(r, generadoPor, conDetalle), formato, nombreBase);
    }

    private static FilaMovimientos fila(AcumuladoMovimientos a) {
        return new FilaMovimientos(a.id(), a.codigo(), a.nombre(), a.categoria(), a.saldoInicial(), a.entradas(),
                dinero(a.valorEntradas()), a.ventas(), a.anulaciones(), a.devoluciones(), a.ajustesPositivos(),
                a.ajustesNegativos(), a.saldoFinal());
    }

    private static FilaMovimientos totales(List<FilaMovimientos> filas) {
        return new FilaMovimientos(null, null, "Total", null, sumar(filas, FilaMovimientos::saldoInicial),
                sumar(filas, FilaMovimientos::entradas),
                dinero(suma(filas.stream().map(FilaMovimientos::valorEntradas).toList())),
                sumar(filas, FilaMovimientos::ventas), sumar(filas, FilaMovimientos::anulaciones),
                sumar(filas, FilaMovimientos::devoluciones), sumar(filas, FilaMovimientos::ajustesPositivos),
                sumar(filas, FilaMovimientos::ajustesNegativos), sumar(filas, FilaMovimientos::saldoFinal));
    }

    private static long sumar(List<FilaMovimientos> filas, ToLongFunction<FilaMovimientos> campo) {
        return filas.stream().mapToLong(campo).sum();
    }

    private DocumentoTabular documento(MovimientosResponse r, String generadoPor, boolean conDetalle) {
        List<FiltroReporte> filtros = List.of(new FiltroReporte("Desde", r.desde().toString()),
                new FiltroReporte("Hasta", r.hasta().toString()),
                new FiltroReporte("Categoría", r.categoria() != null ? r.categoria() : "Todas"),
                new FiltroReporte("Agrupación", r.agrupacion().name()));
        return new DocumentoTabular("Movimientos del periodo", filtros, generadoPor, r.generadoEn(),
                List.of(resumen(r), detalle(r, conDetalle)), r.notas());
    }

    private static SeccionTabular resumen(MovimientosResponse r) {
        boolean porCategoria = r.agrupacion() == AgrupacionReporte.CATEGORIA;
        List<ColumnaReporte> columnas = new ArrayList<>();
        if (!porCategoria) {
            columnas.add(ColumnaReporte.texto(r.agrupacion() == AgrupacionReporte.VARIANTE ? "SKU" : "Código", 16));
        }
        columnas.add(ColumnaReporte.texto(switch (r.agrupacion()) {
            case CATEGORIA -> "Categoría";
            case PRODUCTO -> "Producto";
            case VARIANTE -> "Variante";
        }, 30));
        if (!porCategoria) {
            columnas.add(ColumnaReporte.texto("Categoría", 14));
        }
        columnas.addAll(List.of(ColumnaReporte.entero("Saldo inicial", 9), ColumnaReporte.entero("Entradas", 9),
                ColumnaReporte.dinero("Valor de entradas", 15), ColumnaReporte.entero("Ventas", 9),
                ColumnaReporte.entero("Anulaciones", 9), ColumnaReporte.entero("Devoluciones", 9),
                ColumnaReporte.entero("Ajustes +", 9), ColumnaReporte.entero("Ajustes −", 9),
                ColumnaReporte.entero("Saldo final", 9)));

        List<List<Object>> filas = new ArrayList<>();
        for (FilaMovimientos f : r.filas()) {
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
        if (!porCategoria) {
            totales.add(null);
        }
        totales.add("Total");
        if (!porCategoria) {
            totales.add(null);
        }
        agregarCifras(totales, r.totales());
        SeccionTabular s = SeccionTabular.de("Resumen", columnas, filas).conTotales(totales);
        return r.sinMovimientos() ? s.conMensaje(r.mensaje()) : s;
    }

    private static void agregarCifras(List<Object> celdas, FilaMovimientos f) {
        celdas.add(f.saldoInicial());
        celdas.add(f.entradas());
        celdas.add(f.valorEntradas());
        celdas.add(f.ventas());
        celdas.add(f.anulaciones());
        celdas.add(f.devoluciones());
        celdas.add(f.ajustesPositivos());
        celdas.add(f.ajustesNegativos());
        celdas.add(f.saldoFinal());
    }

    private SeccionTabular detalle(MovimientosResponse r, boolean conDetalle) {
        List<ColumnaReporte> columnas = List.of(ColumnaReporte.fechaHora("Fecha", 18), ColumnaReporte.texto("SKU", 22),
                ColumnaReporte.texto("Producto", 30), ColumnaReporte.texto("Tipo", 18),
                ColumnaReporte.entero("Cantidad", 9), ColumnaReporte.entero("Físico antes", 9),
                ColumnaReporte.entero("Físico después", 9), ColumnaReporte.dinero("Costo unitario", 15),
                ColumnaReporte.texto("Referencia", 20), ColumnaReporte.texto("Usuario", 22));
        List<List<Object>> filas = new ArrayList<>();
        if (conDetalle) {
            for (MovimientoDetalle m : consultas.detalleMovimientos(r.desde(), r.hasta(), r.categoriaId())) {
                filas.add(Arrays.asList(m.fecha(), m.sku(), m.producto(), TIPOS.getOrDefault(m.tipo(), m.tipo()),
                        m.cantidad(), m.fisicoAntes(), m.fisicoDespues(), m.costoUnitario(),
                        m.referenciaTipo() != null ? m.referenciaTipo() + "/" + m.referenciaId() : null, m.usuario()));
            }
        }
        return SeccionTabular.de("Detalle de movimientos", columnas, filas).soloEnExcel();
    }
}
