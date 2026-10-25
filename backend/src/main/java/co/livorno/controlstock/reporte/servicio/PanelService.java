package co.livorno.controlstock.reporte.servicio;

import static co.livorno.controlstock.reporte.servicio.CalculosReporte.dinero;
import static co.livorno.controlstock.reporte.servicio.CalculosReporte.porcentaje;

import co.livorno.controlstock.reporte.dto.PanelResponse;
import co.livorno.controlstock.reporte.dto.PuntoDia;
import co.livorno.controlstock.reporte.dto.ResumenCompras;
import co.livorno.controlstock.reporte.dto.ResumenInventario;
import co.livorno.controlstock.reporte.dto.ResumenReservas;
import co.livorno.controlstock.reporte.dto.ResumenVentas;
import co.livorno.controlstock.reporte.repositorio.ConsultasPanel;
import co.livorno.controlstock.reporte.repositorio.ConsultasPanel.AlertasActivas;
import co.livorno.controlstock.reporte.repositorio.ConsultasPanel.Compras;
import co.livorno.controlstock.reporte.repositorio.ConsultasPanel.Existencias;
import co.livorno.controlstock.reporte.repositorio.ConsultasPanel.ReservasActivas;
import co.livorno.controlstock.reporte.repositorio.ConsultasPanel.VentasDia;
import co.livorno.controlstock.reporte.repositorio.ConsultasPanel.VentasRango;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panel de inicio por rol (ADR-011, R5). Cada sección se calcula solo si el rol la puede ver; las demás llegan null y
 * ni siquiera se consultan. Solo lectura, sin datos de clientes (Ley 1581). Fechas con el {@code Clock}
 * (America/Bogota).
 *
 * <ul>
 *   <li>ventas: ADMIN, VENDEDOR, CONTABILIDAD (margen solo ADMIN y CONTABILIDAD)
 *   <li>inventario: todos (valores solo ADMIN y CONTABILIDAD)
 *   <li>reservas: ADMIN, INVENTARIO, VENDEDOR
 *   <li>compras: ADMIN, INVENTARIO, CONTABILIDAD (valor en camino solo ADMIN y CONTABILIDAD)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PanelService {

    static final int DIAS_SERIE = 7;
    static final int HORAS_POR_VENCER = 24;

    private static final Set<String> VE_VENTAS = Set.of("ADMIN", "VENDEDOR", "CONTABILIDAD");
    private static final Set<String> VE_COSTOS = Set.of("ADMIN", "CONTABILIDAD");
    private static final Set<String> VE_RESERVAS = Set.of("ADMIN", "INVENTARIO", "VENDEDOR");
    private static final Set<String> VE_COMPRAS = Set.of("ADMIN", "INVENTARIO", "CONTABILIDAD");

    private final ConsultasPanel consultas;
    private final Clock clock;

    /** @param rol nombre del rol del usuario autenticado (ADMIN, INVENTARIO, VENDEDOR o CONTABILIDAD) */
    public PanelResponse obtener(String rol) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        boolean costos = VE_COSTOS.contains(rol);
        return new PanelResponse(ahora,
                VE_VENTAS.contains(rol) ? ventas(ahora.toLocalDate(), costos) : null,
                inventario(costos),
                VE_RESERVAS.contains(rol) ? reservas(ahora) : null,
                VE_COMPRAS.contains(rol) ? compras(ahora, costos) : null);
    }

    private ResumenVentas ventas(LocalDate hoy, boolean costos) {
        LocalDate inicioSerie = hoy.minusDays(DIAS_SERIE - 1L);
        Map<LocalDate, VentasDia> porDia = new HashMap<>();
        consultas.ventasPorDia(inicioSerie, hoy).forEach(v -> porDia.put(v.fecha(), v));
        List<PuntoDia> serie = new ArrayList<>();
        for (LocalDate d = inicioSerie; !d.isAfter(hoy); d = d.plusDays(1)) {
            VentasDia v = porDia.get(d);
            serie.add(v == null ? new PuntoDia(d, dinero(null), 0)
                    : new PuntoDia(d, dinero(v.ingresoNeto()), v.numeroVentas()));
        }
        PuntoDia dia = serie.get(serie.size() - 1);

        VentasRango mes = consultas.ventasRango(hoy.withDayOfMonth(1), hoy);
        BigDecimal neto = dinero(mes.ingresoNeto());
        BigDecimal margen = costos ? neto.subtract(dinero(mes.costoVentas())) : null;
        BigDecimal margenPct = costos ? porcentaje(margen, neto) : null;
        return new ResumenVentas(dia.total(), dia.numeroVentas(), neto, mes.numeroVentas(), margen, margenPct,
                List.copyOf(serie));
    }

    private ResumenInventario inventario(boolean costos) {
        Existencias e = consultas.existencias();
        AlertasActivas a = consultas.alertasActivas();
        BigDecimal valorCosto = null;
        BigDecimal valorVenta = null;
        if (costos) {
            BigDecimal[] valores = consultas.valorInventario();
            valorCosto = dinero(valores[0]);
            valorVenta = dinero(valores[1]);
        }
        return new ResumenInventario(e.variantes(), e.fisicas(), e.disponibles(), a.agotado(), a.stockBajo(),
                a.sinRotacion(), valorCosto, valorVenta);
    }

    private ResumenReservas reservas(LocalDateTime ahora) {
        ReservasActivas r = consultas.reservasActivas(ahora.plusHours(HORAS_POR_VENCER));
        return new ResumenReservas(r.activas(), r.unidades(), r.porVencer());
    }

    private ResumenCompras compras(LocalDateTime ahora, boolean costos) {
        Compras c = consultas.compras(ahora);
        return new ResumenCompras(c.borradores(), c.enCamino(), c.unidadesEnCamino(), c.atrasados(),
                costos ? dinero(c.valorEnCamino()) : null);
    }
}
