package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.dto.FiltroMasVendidos;
import co.livorno.controlstock.analitica.dto.MasVendidoItem;
import co.livorno.controlstock.analitica.dto.MasVendidosResponse;
import co.livorno.controlstock.analitica.repositorio.ConsultasVentaAnalitica;
import co.livorno.controlstock.analitica.repositorio.MasVendidoFila;
import co.livorno.controlstock.analitica.repositorio.TotalVentaNeta;
import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ranking de productos o variantes más vendidos (REQ-05, R10). Solo lectura, sin costos ni margen. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasVendidosService {

    public static final int DIAS_POR_DEFECTO = 30;
    public static final int RANGO_MAXIMO_DIAS = 731;
    public static final int LIMITE_POR_DEFECTO = 10;
    public static final int LIMITE_MAXIMO = 50;

    private final ConsultasVentaAnalitica consultas;
    private final Clock clock;

    public MasVendidosResponse consultar(LocalDate desde, LocalDate hasta, Long categoriaId, CriterioRanking criterio,
            AgrupacionRanking agrupacion, Integer limite) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now(clock);
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
        if (inicio.isAfter(fin)) {
            throw new SolicitudInvalidaException("desde", "La fecha inicial no puede ser posterior a la final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > RANGO_MAXIMO_DIAS) {
            throw new SolicitudInvalidaException("desde", "El rango no puede superar " + RANGO_MAXIMO_DIAS + " días.");
        }
        int n = limite != null ? limite : LIMITE_POR_DEFECTO;
        if (n < 1 || n > LIMITE_MAXIMO) {
            throw new SolicitudInvalidaException("limite", "El límite debe estar entre 1 y " + LIMITE_MAXIMO + ".");
        }
        FiltroMasVendidos filtro = new FiltroMasVendidos(inicio, fin, categoriaId,
                criterio != null ? criterio : CriterioRanking.UNIDADES,
                agrupacion != null ? agrupacion : AgrupacionRanking.PRODUCTO, n);

        TotalVentaNeta total = consultas.totalVentaNeta(filtro);
        List<MasVendidoFila> filas = consultas.masVendidos(filtro);
        List<MasVendidoItem> items = new ArrayList<>(filas.size());
        for (int i = 0; i < filas.size(); i++) {
            MasVendidoFila f = filas.get(i);
            BigDecimal participacion = filtro.criterio() == CriterioRanking.UNIDADES
                    ? porcentaje(BigDecimal.valueOf(f.unidades()), BigDecimal.valueOf(total.unidades()))
                    : porcentaje(f.ingreso(), total.ingreso());
            items.add(new MasVendidoItem(i + 1, f.productoId(), f.productoCodigo(), f.productoNombre(),
                    new CategoriaRef(f.categoriaId(), f.categoriaNombre()), f.varianteId(), f.sku(),
                    f.talla() != null ? Talla.valueOf(f.talla()) : null, f.color(), f.unidades(), f.ingreso(),
                    participacion, f.activo()));
        }
        return new MasVendidosResponse(inicio, fin, filtro.criterio(), filtro.agrupacion(), categoriaId,
                total.unidades(), total.ingreso(), items);
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
