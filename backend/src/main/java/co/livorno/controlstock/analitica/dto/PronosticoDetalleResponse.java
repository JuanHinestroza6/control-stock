package co.livorno.controlstock.analitica.dto;

import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Detalle del pronóstico de un producto: serie histórica, pronóstico del producto y desagregación por variante. */
public record PronosticoDetalleResponse(long productoId, String codigo, String nombre, String categoria,
        MetodoPronostico metodo, BigDecimal errorWape, BigDecimal wapeSes, BigDecimal wapeHw,
        MotivoSinHoltWinters motivoSinHw, int mesesHistoria, int unidadesUltimos12m,
        BigDecimal unidadesCorregidas12m, List<PuntoPronostico> proximos, ParametrosModelo parametros,
        List<PuntoSerie> serie, List<PuntoPronostico> pronostico, List<PronosticoVarianteResponse> variantes,
        LocalDateTime calculadoEn) {}
