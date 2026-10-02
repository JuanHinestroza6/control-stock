package co.livorno.controlstock.comun.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class OrdenamientoTest {

    private static final Set<String> PERMITIDOS = Set.of("nombre", "codigo");

    @Test
    void validar_campoPermitido_noLanza() {
        assertThatCode(() -> Ordenamiento.validar(PageRequest.of(0, 20, Sort.by("nombre")), PERMITIDOS))
                .doesNotThrowAnyException();
    }

    @Test
    void validar_sinOrden_noLanza() {
        assertThatCode(() -> Ordenamiento.validar(PageRequest.of(0, 20), PERMITIDOS)).doesNotThrowAnyException();
    }

    @Test
    void validar_campoNoPermitido_lanzaSolicitudInvalida() {
        assertThatThrownBy(() -> Ordenamiento.validar(PageRequest.of(0, 20, Sort.by("password")), PERMITIDOS))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("password");
    }
}
