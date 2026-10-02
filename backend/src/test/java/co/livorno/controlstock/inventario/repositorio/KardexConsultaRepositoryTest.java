package co.livorno.controlstock.inventario.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class KardexConsultaRepositoryTest {

    @Autowired MovimientoRepository movimientos;
    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;

    Variante a;
    Variante b;

    @BeforeEach
    void preparar() {
        Producto p = productos.saveAndFlush(new Producto("LIV001", "Camiseta", categorias.findAllByOrderByNombreAsc().get(0),
                new BigDecimal("20000"), new BigDecimal("59900"), null, null));
        a = variantes.saveAndFlush(new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 2));
        b = variantes.saveAndFlush(new Variante(p, "LIV001-L-NEG", Talla.L, "NEGRO", 2));
        mov(a, TipoMovimiento.ENTRADA, "2026-10-01T09:00");
        mov(a, TipoMovimiento.AJUSTE_POSITIVO, "2026-10-02T10:00");
        mov(a, TipoMovimiento.AJUSTE_POSITIVO, "2026-10-02T10:00"); // misma fecha: desempata por id desc
        mov(b, TipoMovimiento.ENTRADA, "2026-10-03T23:59");
    }

    private void mov(Variante v, TipoMovimiento tipo, String fecha) {
        BigDecimal costo = tipo == TipoMovimiento.ENTRADA ? new BigDecimal("1000") : null;
        movimientos.save(Movimiento.de(v, tipo, 1, 0, 0, costo, null, LocalDateTime.parse(fecha), null, null,
                tipo == TipoMovimiento.ENTRADA ? null : "conteo"));
    }

    private List<Movimiento> buscar(Long varianteId, TipoMovimiento tipo, String desde, String hasta) {
        return movimientos.buscar(varianteId, tipo, desde == null ? null : LocalDateTime.parse(desde),
                hasta == null ? null : LocalDateTime.parse(hasta), PageRequest.of(0, 20)).getContent();
    }

    @Test
    void buscar_sinFiltros_ordenaPorFechaDescEIdDesc() {
        List<Movimiento> r = buscar(null, null, null, null);

        assertThat(r).hasSize(4);
        assertThat(r.get(0).getVariante().getId()).isEqualTo(b.getId());
        assertThat(r.get(1).getId()).isGreaterThan(r.get(2).getId());
        assertThat(r.get(3).getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
    }

    @Test
    void buscar_porVarianteYTipo_filtra() {
        assertThat(buscar(a.getId(), null, null, null)).hasSize(3);
        assertThat(buscar(null, TipoMovimiento.ENTRADA, null, null)).hasSize(2);
        assertThat(buscar(a.getId(), TipoMovimiento.AJUSTE_POSITIVO, null, null)).hasSize(2);
        assertThat(buscar(a.getId(), TipoMovimiento.VENTA, null, null)).isEmpty();
        assertThat(buscar(999999L, null, null, null)).isEmpty();
    }

    @Test
    void buscar_rangoDeFechas_esSemiabierto() {
        assertThat(buscar(null, null, "2026-10-02T00:00", "2026-10-03T00:00")).hasSize(2);
        assertThat(buscar(null, null, "2026-10-03T00:00", "2026-10-04T00:00")).hasSize(1);
        assertThat(buscar(null, null, "2026-10-04T00:00", null)).isEmpty();
        assertThat(buscar(null, null, null, "2026-10-02T00:00")).hasSize(1);
    }

    @Test
    void buscar_pagina_cuentaElTotal() {
        var pagina = movimientos.buscar(null, null, null, null, PageRequest.of(1, 3));

        assertThat(pagina.getTotalElements()).isEqualTo(4);
        assertThat(pagina.getContent()).hasSize(1);
    }
}
