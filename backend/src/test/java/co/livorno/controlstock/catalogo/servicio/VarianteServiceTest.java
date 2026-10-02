package co.livorno.controlstock.catalogo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.dto.GenerarVariantesRequest;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse.Motivo;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VarianteServiceTest {

    @Mock ProductoRepository productos;
    @Mock VarianteRepository variantes;
    @InjectMocks VarianteService servicio;

    private Producto producto(boolean activo) {
        Producto p = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        ReflectionTestUtils.setField(p, "id", 10L);
        if (!activo) {
            p.desactivar();
        }
        return p;
    }

    private Variante existente(Producto p, String sku, Talla t, String color, boolean activa) {
        Variante v = new Variante(p, sku, t, color, 1);
        if (!activa) {
            v.desactivar();
        }
        return v;
    }

    private GenerarVariantesRequest req(List<Talla> tallas, List<String> colores) {
        return new GenerarVariantesRequest(tallas, colores, 3);
    }

    @Test
    void generar_3x2_creaSeisConSkuYStockEnCero() {
        Producto p = producto(true);
        when(productos.findById(10L)).thenReturn(Optional.of(p));
        when(variantes.findByProductoId(10L)).thenReturn(List.of());

        var r = servicio.generar(10L, req(List.of(Talla.M, Talla.S, Talla.L), List.of("Negro", "marrón")));

        assertThat(r.creadas()).hasSize(6);
        assertThat(r.omitidas()).isEmpty();
        assertThat(r.creadas()).extracting("sku").containsExactly("LIV001-S-MAR", "LIV001-S-NEG", "LIV001-M-MAR",
                "LIV001-M-NEG", "LIV001-L-MAR", "LIV001-L-NEG");
        assertThat(r.creadas()).allSatisfy(v -> {
            assertThat(v.stockFisico()).isZero();
            assertThat(v.stockMinimo()).isEqualTo(3);
            assertThat(v.estadoStock()).isEqualTo(EstadoStock.AGOTADO);
        });
    }

    @Test
    void generar_coloresQueColisionanEnElLote_usanSufijoNumerico() {
        Producto p = producto(true);
        when(productos.findById(10L)).thenReturn(Optional.of(p));
        when(variantes.findByProductoId(10L)).thenReturn(List.of());

        var r = servicio.generar(10L, req(List.of(Talla.M), List.of("Negro", "Neón")));

        assertThat(r.creadas()).extracting("sku").containsExactlyInAnyOrder("LIV001-M-NEG", "LIV001-M-NEO");
        var r2 = servicio.generar(10L, req(List.of(Talla.L), List.of("Negro mate", "Negro")));
        assertThat(r2.creadas()).extracting("sku").containsExactlyInAnyOrder("LIV001-L-NEG", "LIV001-L-NEG2");
    }

    @Test
    void generar_colisionConSkuEnBaseDeDatos_usaSufijo() {
        when(productos.findById(10L)).thenReturn(Optional.of(producto(true)));
        when(variantes.findByProductoId(10L)).thenReturn(List.of());
        when(variantes.existsBySku("LIV001-M-NEG")).thenReturn(true);

        var r = servicio.generar(10L, req(List.of(Talla.M), List.of("Negro")));

        assertThat(r.creadas()).extracting("sku").containsExactly("LIV001-M-NEG2");
    }

    @Test
    void generar_idempotente_omiteExistentesYExistentesInactivas() {
        Producto p = producto(true);
        when(productos.findById(10L)).thenReturn(Optional.of(p));
        when(variantes.findByProductoId(10L)).thenReturn(List.of(
                existente(p, "LIV001-M-NEG", Talla.M, "NEGRO", true),
                existente(p, "LIV001-M-ROJ", Talla.M, "ROJO", false)));

        var r = servicio.generar(10L, req(List.of(Talla.M), List.of("negro", "ROJO", "Azul")));

        assertThat(r.creadas()).extracting("sku").containsExactly("LIV001-M-AZU");
        assertThat(r.omitidas()).extracting("color", "sku", "motivo").containsExactly(
                org.assertj.core.groups.Tuple.tuple("NEGRO", "LIV001-M-NEG", Motivo.EXISTE),
                org.assertj.core.groups.Tuple.tuple("ROJO", "LIV001-M-ROJ", Motivo.EXISTE_INACTIVA));
    }

    @Test
    void generar_duplicadosTrasNormalizar_seIgnoran() {
        when(productos.findById(10L)).thenReturn(Optional.of(producto(true)));
        when(variantes.findByProductoId(10L)).thenReturn(List.of());

        var r = servicio.generar(10L, req(List.of(Talla.M, Talla.M), List.of("negro", " NEGRO ", "Negro")));

        assertThat(r.creadas()).hasSize(1);
    }

    @Test
    void generar_unicaConOtrasTallas_lanza422() {
        when(productos.findById(10L)).thenReturn(Optional.of(producto(true)));
        when(variantes.findByProductoId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.generar(10L, req(List.of(Talla.UNICA, Talla.M), List.of("Negro"))))
                .isInstanceOf(ExcepcionNegocio.class);
        verify(variantes, never()).saveAllAndFlush(any());
    }

    @Test
    void generar_unicaCuandoYaExistenOtrasTallas_lanza422() {
        Producto p = producto(true);
        when(productos.findById(10L)).thenReturn(Optional.of(p));
        when(variantes.findByProductoId(10L)).thenReturn(List.of(existente(p, "X", Talla.M, "NEGRO", true)));

        assertThatThrownBy(() -> servicio.generar(10L, req(List.of(Talla.UNICA), List.of("Negro"))))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void generar_unicaSola_funciona() {
        when(productos.findById(10L)).thenReturn(Optional.of(producto(true)));
        when(variantes.findByProductoId(10L)).thenReturn(List.of());

        assertThat(servicio.generar(10L, req(List.of(Talla.UNICA), List.of("Negro"))).creadas())
                .extracting("sku").containsExactly("LIV001-UNICA-NEG");
    }

    @Test
    void generar_productoInactivo_lanza422() {
        when(productos.findById(10L)).thenReturn(Optional.of(producto(false)));

        assertThatThrownBy(() -> servicio.generar(10L, req(List.of(Talla.M), List.of("Negro"))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("inactivo");
        verify(variantes, never()).saveAllAndFlush(any());
    }

    @Test
    void generar_productoInexistente_lanza404() {
        when(productos.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.generar(99L, req(List.of(Talla.M), List.of("Negro"))))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(variantes, never()).existsBySku(anyString());
    }

    @Test
    void listar_ordenaPorTallaYColor_yFiltraInactivasPorDefecto() {
        Producto p = producto(true);
        when(productos.existsById(10L)).thenReturn(true);
        when(variantes.findByProductoIdAndActivoTrue(10L)).thenReturn(List.of(
                existente(p, "B", Talla.L, "AZUL", true), existente(p, "A", Talla.S, "ROJO", true),
                existente(p, "C", Talla.S, "AZUL", true)));

        assertThat(servicio.listar(10L, false)).extracting("sku").containsExactly("C", "A", "B");
        verify(variantes, never()).findByProductoId(10L);
    }

    @Test
    void listar_conInactivas_usaTodas() {
        when(productos.existsById(10L)).thenReturn(true);
        when(variantes.findByProductoId(10L)).thenReturn(List.of());

        assertThat(servicio.listar(10L, true)).isEmpty();
    }

    private Variante conStock(Variante v, int fisico, int reservado) {
        ReflectionTestUtils.setField(v, "stockFisico", fisico);
        ReflectionTestUtils.setField(v, "stockReservado", reservado);
        return v;
    }

    @Test
    void actualizar_cambiaSoloElStockMinimo() {
        Variante v = existente(producto(true), "A", Talla.M, "NEGRO", true);
        when(variantes.findById(5L)).thenReturn(Optional.of(v));
        when(variantes.saveAndFlush(v)).thenReturn(v);

        var r = servicio.actualizar(5L, new co.livorno.controlstock.catalogo.dto.ActualizarVarianteRequest(9));

        assertThat(r.stockMinimo()).isEqualTo(9);
        assertThat(r.sku()).isEqualTo("A");
    }

    @Test
    void actualizar_inexistente_lanza404() {
        when(variantes.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(5L,
                new co.livorno.controlstock.catalogo.dto.ActualizarVarianteRequest(1)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void cambiarEstado_desactivarConReservado_lanza409() {
        Variante v = conStock(existente(producto(true), "LIV001-M-NEG", Talla.M, "NEGRO", true), 10, 3);
        when(variantes.findById(5L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> servicio.cambiarEstado(5L, false))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.ConflictoException.class)
                .hasMessage("La variante LIV001-M-NEG tiene 3 unidades en apartados activos.");
        assertThat(v.isActivo()).isTrue();
    }

    @Test
    void cambiarEstado_desactivarSinReservado_desactiva() {
        Variante v = existente(producto(true), "A", Talla.M, "NEGRO", true);
        when(variantes.findById(5L)).thenReturn(Optional.of(v));

        assertThat(servicio.cambiarEstado(5L, false).activo()).isFalse();
    }

    @Test
    void cambiarEstado_activarConProductoInactivo_lanza422() {
        Variante v = existente(producto(false), "A", Talla.M, "NEGRO", false);
        when(variantes.findById(5L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> servicio.cambiarEstado(5L, true))
                .isInstanceOf(ExcepcionNegocio.class).hasMessage("Activa primero el producto LIV001.");
        assertThat(v.isActivo()).isFalse();
    }

    @Test
    void cambiarEstado_activarConProductoActivo_activa() {
        Variante v = existente(producto(true), "A", Talla.M, "NEGRO", false);
        when(variantes.findById(5L)).thenReturn(Optional.of(v));

        assertThat(servicio.cambiarEstado(5L, true).activo()).isTrue();
    }

    @Test
    void cambiarEstado_igualAlActual_esNoOpAunqueHayaReservado() {
        Variante v = conStock(existente(producto(true), "A", Talla.M, "NEGRO", true), 10, 3);
        when(variantes.findById(5L)).thenReturn(Optional.of(v));

        assertThat(servicio.cambiarEstado(5L, true).activo()).isTrue();
        verify(variantes, never()).saveAndFlush(any());
    }

    @Test
    void matriz_ordenaTallasPorEnumColoresAlfabeticosYDejaCeldasAusentes() {
        Producto p = producto(true);
        when(productos.existsById(10L)).thenReturn(true);
        when(variantes.findByProductoIdAndActivoTrue(10L)).thenReturn(List.of(
                conStock(existente(p, "L-AZU", Talla.L, "AZUL", true), 2, 1),
                existente(p, "S-ROJ", Talla.S, "ROJO", true)));

        var m = servicio.matriz(10L, false);

        assertThat(m.tallas()).containsExactly(Talla.S, Talla.L);
        assertThat(m.colores()).containsExactly("AZUL", "ROJO");
        assertThat(m.celdas()).hasSize(2); // S-AZUL y L-ROJO no existen: celda ausente
        assertThat(m.celdas()).extracting("sku").containsExactly("S-ROJ", "L-AZU");
        assertThat(m.celdas().get(1).stockDisponible()).isEqualTo(1);
        assertThat(m.celdas().get(1).estadoStock()).isEqualTo(EstadoStock.BAJO);
    }

    @Test
    void matriz_productoSinVariantes_devuelveListasVacias() {
        when(productos.existsById(10L)).thenReturn(true);
        when(variantes.findByProductoIdAndActivoTrue(10L)).thenReturn(List.of());

        var m = servicio.matriz(10L, false);

        assertThat(m.tallas()).isEmpty();
        assertThat(m.celdas()).isEmpty();
    }

    @Test
    void listar_productoInexistente_lanza404() {
        when(productos.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.listar(99L, false)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
