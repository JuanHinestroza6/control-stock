package co.livorno.controlstock.inventario.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReservaTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 2, 10, 0);

    private Reserva nueva() {
        Reserva r = Reserva.de(null, 2, "Ana", "3001234567", T0, T0.plusHours(48), 1L);
        ReflectionTestUtils.setField(r, "id", 12L);
        return r;
    }

    @Test
    void de_datosValidos_quedaActiva() {
        Reserva r = nueva();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(r.estaActiva()).isTrue();
        assertThat(r.getCerradaEn()).isNull();
    }

    @Test
    void de_cantidadCeroOVencimientoNoPosterior_lanzaIllegalArgument() {
        assertThatThrownBy(() -> Reserva.de(null, 0, "Ana", "300", T0, T0.plusHours(1), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reserva.de(null, 1, "Ana", "300", T0, T0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numero_conId12_devuelveR000012() {
        assertThat(nueva().numero()).isEqualTo("R-000012");
    }

    @Test
    void cancelar_activa_guardaMotivoYUsuario() {
        Reserva r = nueva();
        r.cancelar("Cliente desistió", T0.plusHours(1), 5L);
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(r.getMotivoCancelacion()).isEqualTo("Cliente desistió");
        assertThat(r.getCerradaPor()).isEqualTo(5L);
        assertThat(r.getCerradaEn()).isEqualTo(T0.plusHours(1));
    }

    @Test
    void vencer_activa_cierraPorElSistema() {
        Reserva r = nueva();
        r.vencer(T0.plusHours(49));
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(r.getCerradaPor()).isNull();
        assertThat(r.getCerradaEn()).isNotNull();
    }

    @Test
    void convertir_activa_guardaVentaYUsuario() {
        Reserva r = nueva();
        r.convertir(99L, T0.plusHours(2), 7L);
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONVERTIDA);
        assertThat(r.getVentaId()).isEqualTo(99L);
        assertThat(r.getCerradaPor()).isEqualTo(7L);
    }

    @Test
    void extender_dentroDelTope_cambiaVenceEn() {
        Reserva r = nueva();
        r.extender(24, T0.plusHours(40));
        assertThat(r.getVenceEn()).isEqualTo(T0.plusHours(64));
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
    }

    @Test
    void extender_masDeSieteDiasDesdeCreacion_lanza422() {
        Reserva r = nueva();
        LocalDateTime venceAntes = r.getVenceEn();
        assertThatThrownBy(() -> r.extender(168, T0.plusHours(10)))
                .isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("Una reserva no puede durar más de 7 días.");
        assertThat(r.getVenceEn()).isEqualTo(venceAntes);
        // exactamente 7 días desde la creación sí se permite
        r.extender(158, T0.plusHours(10));
        assertThat(r.getVenceEn()).isEqualTo(T0.plusDays(7));
    }

    @Test
    void transiciones_sobreNoActiva_lanza409() {
        Reserva r = nueva();
        r.convertir(1L, T0, 1L);
        assertThatThrownBy(() -> r.cancelar("motivo largo", T0, 1L)).isInstanceOf(ConflictoException.class)
                .hasMessage("La reserva R-000012 ya está CONVERTIDA.");
        assertThatThrownBy(() -> r.vencer(T0)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> r.convertir(2L, T0, 1L)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> r.extender(1, T0)).isInstanceOf(ConflictoException.class);
    }
}
