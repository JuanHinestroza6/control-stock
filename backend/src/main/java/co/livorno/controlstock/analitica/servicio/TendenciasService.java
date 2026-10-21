package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.dto.PuntoTendencia;
import co.livorno.controlstock.analitica.dto.TendenciaResponse;
import co.livorno.controlstock.analitica.repositorio.ConsultasTendencias;
import co.livorno.controlstock.analitica.repositorio.FilaTendencia;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tendencias históricas (REQ-09): demanda comercial mensual con el mismo mes del año anterior. Sin costos. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TendenciasService {

    public static final int MESES_POR_DEFECTO = 24;
    public static final int MESES_MAXIMO = 36;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ConsultasTendencias consultas;
    private final Clock clock;

    /**
     * @param desde primer mes {@code YYYY-MM} (por defecto 24 meses completos hasta {@code hasta})
     * @param hasta último mes {@code YYYY-MM}, completo (por defecto el mes anterior al actual)
     */
    public TendenciaResponse consultar(String desde, String hasta, Long categoriaId, Long productoId) {
        YearMonth ultimoCompleto = YearMonth.now(clock).minusMonths(1);
        YearMonth fin = hasta == null || hasta.isBlank() ? ultimoCompleto : mes("hasta", hasta);
        YearMonth inicio = desde == null || desde.isBlank() ? fin.minusMonths(MESES_POR_DEFECTO - 1L)
                : mes("desde", desde);
        if (fin.isAfter(ultimoCompleto)) {
            throw new SolicitudInvalidaException("hasta",
                    "Solo se muestran meses completos: el último es " + ultimoCompleto + ".");
        }
        if (inicio.isAfter(fin)) {
            throw new SolicitudInvalidaException("desde", "El mes inicial no puede ser posterior al final.");
        }
        long meses = ChronoUnit.MONTHS.between(inicio, fin) + 1;
        if (meses > MESES_MAXIMO) {
            throw new SolicitudInvalidaException("desde", "El rango no puede superar " + MESES_MAXIMO + " meses.");
        }

        Map<YearMonth, FilaTendencia> porMes = new HashMap<>();
        consultas.porMes(inicio.minusMonths(12), fin, categoriaId, productoId).forEach(f -> porMes.put(f.mes(), f));

        List<PuntoTendencia> puntos = new ArrayList<>();
        long totalUnidades = 0;
        BigDecimal totalIngreso = BigDecimal.ZERO.setScale(2);
        for (YearMonth m = inicio; !m.isAfter(fin); m = m.plusMonths(1)) {
            FilaTendencia actual = porMes.get(m);
            FilaTendencia previa = porMes.get(m.minusYears(1));
            long u = actual != null ? actual.unidades() : 0L;
            BigDecimal i = actual != null ? actual.ingreso() : BigDecimal.ZERO.setScale(2);
            long ua = previa != null ? previa.unidades() : 0L;
            BigDecimal ia = previa != null ? previa.ingreso() : BigDecimal.ZERO.setScale(2);
            puntos.add(new PuntoTendencia(m.toString(), u, i, ua, ia,
                    variacion(BigDecimal.valueOf(u), BigDecimal.valueOf(ua)), variacion(i, ia)));
            totalUnidades += u;
            totalIngreso = totalIngreso.add(i);
        }
        return new TendenciaResponse(inicio.toString(), fin.toString(), categoriaId, productoId, puntos,
                totalUnidades, totalIngreso);
    }

    private static BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return actual.subtract(anterior).multiply(CIEN).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static YearMonth mes(String campo, String valor) {
        try {
            return YearMonth.parse(valor.strip());
        } catch (DateTimeParseException e) {
            throw new SolicitudInvalidaException(campo, "El mes debe tener el formato AAAA-MM.");
        }
    }
}
