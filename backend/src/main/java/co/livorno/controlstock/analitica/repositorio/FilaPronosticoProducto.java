package co.livorno.controlstock.analitica.repositorio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Producto con su instantánea de pronóstico ({@code analitica_producto}); {@code metodo} en texto como se guarda. */
public record FilaPronosticoProducto(long productoId, String codigo, String nombre, String categoria, String metodo,
        BigDecimal alfa, BigDecimal beta, BigDecimal gamma, BigDecimal wapeSes, BigDecimal wapeHw,
        BigDecimal errorWape, String motivoSinHw, int mesesHistoria, int unidadesObservadas12m,
        BigDecimal unidadesCorregidas12m, LocalDateTime calculadoEn) {}
