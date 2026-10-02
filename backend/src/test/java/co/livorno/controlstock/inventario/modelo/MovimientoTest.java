package co.livorno.controlstock.inventario.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

class MovimientoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Test
    void de_ventaConReserva_calculaSaldosDespuesYGuardaReferencia() {
        Movimiento m = Movimiento.de(null, TipoMovimiento.VENTA_RESERVA, 3, 10, 5, null, 7L, AHORA, "VENTA", 42L,
                null);

        assertThat(m.getFisicoAntes()).isEqualTo(10);
        assertThat(m.getFisicoDespues()).isEqualTo(7);
        assertThat(m.getReservadoAntes()).isEqualTo(5);
        assertThat(m.getReservadoDespues()).isEqualTo(2);
        assertThat(m.getReferenciaTipo()).isEqualTo("VENTA");
        assertThat(m.getFecha()).isEqualTo(AHORA);
    }

    @Test
    void de_entrada_normalizaCostoAEscala2YAceptaUsuarioNulo() {
        Movimiento m = Movimiento.de(null, TipoMovimiento.ENTRADA, 2, 0, 0, new BigDecimal("12000.555"), null, AHORA,
                null, null, null);

        assertThat(m.getCostoUnitario()).isEqualByComparingTo("12000.56");
        assertThat(m.getCostoUnitario().scale()).isEqualTo(2);
        assertThat(m.getUsuarioId()).isNull();
    }

    @Test
    void de_datosIncoherentes_lanza() {
        assertThatThrownBy(() -> Movimiento.de(null, TipoMovimiento.VENTA, 0, 5, 0, null, 1L, AHORA, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Movimiento.de(null, TipoMovimiento.ENTRADA, 1, 0, 0, null, 1L, AHORA, null, null,
                null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Movimiento.de(null, TipoMovimiento.VENTA, 1, 5, 0, BigDecimal.ONE, 1L, AHORA, null,
                null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Movimiento.de(null, TipoMovimiento.VENTA, 1, 5, 0, null, 1L, AHORA, "VENTA", null,
                null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reglaDeOro2_esInmutableYSinSetters() {
        assertThat(Movimiento.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertThat(Arrays.stream(Movimiento.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName))
                .noneMatch(n -> n.startsWith("set"));
    }
}
