package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropiedadesSemillaTest {

    @Test
    void toString_enmascaraLaPassword() {
        var p = new PropiedadesSemilla(true, true, 42, 24, null, 15, "ClaveDemo-2026-ok");

        assertThat(p.toString()).doesNotContain("ClaveDemo-2026-ok").contains("password=****").contains("meses=24");
    }

    @Test
    void toString_sinPassword_indicaNull() {
        var p = new PropiedadesSemilla(true, true, 42, 24, null, 15, " ");

        assertThat(p.toString()).contains("password=null");
    }
}
