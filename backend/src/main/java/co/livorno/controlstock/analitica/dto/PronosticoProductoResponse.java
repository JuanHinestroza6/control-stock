package co.livorno.controlstock.analitica.dto;

import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.math.BigDecimal;
import java.util.List;

/** Fila del listado de pronósticos. {@code metodo} null = SIN_HISTORIA. WAPE como fracción (0,1234 = 12,34 %). */
public record PronosticoProductoResponse(long productoId, String codigo, String nombre, String categoria,
        MetodoPronostico metodo, BigDecimal errorWape, BigDecimal wapeSes, BigDecimal wapeHw,
        MotivoSinHoltWinters motivoSinHw, int mesesHistoria, int unidadesUltimos12m,
        BigDecimal unidadesCorregidas12m, List<PuntoPronostico> proximos) {}
