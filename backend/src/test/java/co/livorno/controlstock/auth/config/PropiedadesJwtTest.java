package co.livorno.controlstock.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PropiedadesJwtTest {

    @Test
    void crear_secretoAusente_falla() {
        assertThatThrownBy(() -> new PropiedadesJwt(null, Duration.ofHours(8)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new PropiedadesJwt("", Duration.ofHours(8)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void crear_secretoDe31Bytes_falla() {
        assertThatThrownBy(() -> new PropiedadesJwt("a".repeat(31), Duration.ofHours(8)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void crear_secretoDe32Bytes_ok() {
        assertThat(new PropiedadesJwt("a".repeat(32), Duration.ofHours(8)).secret()).hasSize(32);
    }

    @Test
    void crear_secretoCortoConCaracteresMultibyte_cuentaBytesUtf8() {
        // 16 letras 'ñ' = 16 caracteres pero 32 bytes en UTF-8
        assertThat(new PropiedadesJwt("ñ".repeat(16), Duration.ofHours(1))).isNotNull();
        assertThatThrownBy(() -> new PropiedadesJwt("ñ".repeat(15), Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void crear_secretoDeEjemploQueEmpiezaPorCambiar_falla() {
        assertThatThrownBy(() -> new PropiedadesJwt("cambiar-por-un-secreto-aleatorio-de-minimo-32-bytes", Duration.ofHours(8)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("valor de ejemplo");
        assertThatThrownBy(() -> new PropiedadesJwt("CAMBIAR-por-un-secreto-aleatorio-de-minimo-32-bytes", Duration.ofHours(8)))
                .isInstanceOf(IllegalStateException.class);
    }
}
