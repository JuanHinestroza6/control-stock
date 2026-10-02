package co.livorno.controlstock.catalogo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.dto.ActualizarProductoRequest;
import co.livorno.controlstock.catalogo.dto.CrearProductoRequest;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock ProductoRepository productos;
    @Mock co.livorno.controlstock.catalogo.repositorio.VarianteRepository variantes;
    @Mock CategoriaRepository categorias;
    @Mock UsuarioActual usuarioActual;
    @InjectMocks ProductoService servicio;

    private Categoria categoria() {
        Categoria c = new Categoria("Camisetas");
        ReflectionTestUtils.setField(c, "id", 2L);
        return c;
    }

    private Producto producto() {
        Producto p = new Producto("LIV001", "Camiseta", categoria(), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        ReflectionTestUtils.setField(p, "id", 10L);
        return p;
    }

    private void comoRol(String rol) {
        when(usuarioActual.obtener()).thenReturn(new UsuarioAutenticado(1L, "u@x.co", "U", rol));
    }

    private CrearProductoRequest crearReq(String codigo) {
        return new CrearProductoRequest(codigo, "Camiseta", 2L, new BigDecimal("20000"), new BigDecimal("59900"),
                null, null);
    }

    @Test
    void crear_codigoEnMinusculas_guardaEnMayusculasConDefaults() {
        comoRol("ADMIN");
        when(categorias.findById(2L)).thenReturn(Optional.of(categoria()));
        when(productos.existsByCodigo("LIV001")).thenReturn(false);
        when(productos.saveAndFlush(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        var r = servicio.crear(crearReq(" liv001 "));

        assertThat(r.codigo()).isEqualTo("LIV001");
        assertThat(r.leadTimeDias()).isEqualTo(15);
        assertThat(r.loteMinimo()).isEqualTo(1);
        assertThat(r.activo()).isTrue();
        assertThat(r.precioCompra()).isEqualByComparingTo("20000.00");
    }

    @Test
    void crear_codigoDuplicado_lanzaConflicto() {
        when(categorias.findById(2L)).thenReturn(Optional.of(categoria()));
        when(productos.existsByCodigo("LIV001")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(crearReq("LIV001"))).isInstanceOf(ConflictoException.class);
        verify(productos, never()).saveAndFlush(any());
    }

    @Test
    void crear_categoriaInexistente_lanza404() {
        when(categorias.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(crearReq("LIV001")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtener_vendedor_noVeCosto() {
        comoRol("VENDEDOR");
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(producto()));

        var r = servicio.obtener(10L);

        assertThat(r.precioCompra()).isNull();
        assertThat(r.precioVenta()).isEqualByComparingTo("59900");
    }

    @Test
    void obtener_rolesConCosto_veCosto() {
        for (String rol : List.of("ADMIN", "INVENTARIO", "CONTABILIDAD")) {
            when(usuarioActual.obtener()).thenReturn(new UsuarioAutenticado(1L, "u@x.co", "U", rol));
            when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(producto()));

            assertThat(servicio.obtener(10L).precioCompra()).as(rol).isEqualByComparingTo("20000");
        }
    }

    @Test
    void obtener_inexistente_lanza404() {
        when(productos.buscarConCategoria(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizar_cambiaDatosPeroNoElCodigo() {
        comoRol("INVENTARIO");
        Producto p = producto();
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(p));
        when(categorias.findById(2L)).thenReturn(Optional.of(categoria()));
        when(productos.saveAndFlush(p)).thenReturn(p);

        var r = servicio.actualizar(10L, new ActualizarProductoRequest("Nuevo nombre", 2L, new BigDecimal("1000"),
                new BigDecimal("2500.5"), 30, 6));

        assertThat(r.codigo()).isEqualTo("LIV001");
        assertThat(r.nombre()).isEqualTo("Nuevo nombre");
        assertThat(r.precioVenta()).isEqualByComparingTo("2500.50");
        assertThat(r.leadTimeDias()).isEqualTo(30);
        assertThat(r.loteMinimo()).isEqualTo(6);
    }

    @Test
    void listar_ordenFueraDeListaBlanca_lanzaSolicitudInvalida() {
        var pag = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThatThrownBy(() -> servicio.listar(null, null, null, pag))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void listar_escapaElTextoYProductoSinVariantesTieneTotalesEnCero() {
        var pag = PageRequest.of(0, 20, Sort.by("nombre"));
        when(productos.buscar("%50!%%", null, true, pag)).thenReturn(new PageImpl<>(List.of(producto()), pag, 1));
        when(variantes.totalesPorProducto(List.of(10L))).thenReturn(List.of());

        var r = servicio.listar("50%", null, true, pag);

        assertThat(r.getContent()).hasSize(1);
        assertThat(r.getContent().get(0).totalVariantes()).isZero();
        assertThat(r.getContent().get(0).stockDisponibleTotal()).isZero();
    }

    @Test
    void listar_conVariantes_usaTotalesDeLaConsultaAgregada() {
        var pag = PageRequest.of(0, 20, Sort.by("nombre"));
        when(productos.buscar(null, null, null, pag)).thenReturn(new PageImpl<>(List.of(producto()), pag, 1));
        when(variantes.totalesPorProducto(List.of(10L)))
                .thenReturn(List.of(new co.livorno.controlstock.catalogo.repositorio.TotalesProducto(10L, 6L, 42L)));

        var r = servicio.listar(null, null, null, pag);

        assertThat(r.getContent().get(0).totalVariantes()).isEqualTo(6);
        assertThat(r.getContent().get(0).stockDisponibleTotal()).isEqualTo(42);
    }

    @Test
    void cambiarEstado_desactivar_inactivaElProducto() {
        comoRol("ADMIN");
        Producto p = producto();
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(p));
        when(productos.saveAndFlush(p)).thenReturn(p);

        assertThat(servicio.cambiarEstado(10L, false).activo()).isFalse();
    }

    @Test
    void cambiarEstado_igualAlActual_esNoOpSinGuardar() {
        comoRol("ADMIN");
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(producto()));

        assertThat(servicio.cambiarEstado(10L, true).activo()).isTrue();
        verify(productos, never()).saveAndFlush(any());
    }

    private co.livorno.controlstock.catalogo.modelo.Variante variante(Producto p, String sku, int reservado) {
        var v = new co.livorno.controlstock.catalogo.modelo.Variante(p, sku,
                co.livorno.controlstock.catalogo.modelo.Talla.M, "NEGRO", 1);
        ReflectionTestUtils.setField(v, "stockFisico", reservado + 5);
        ReflectionTestUtils.setField(v, "stockReservado", reservado);
        return v;
    }

    @Test
    void cambiarEstado_desactivar_cascadaDesactivaTodasLasVariantes() {
        comoRol("ADMIN");
        Producto p = producto();
        var a = variante(p, "LIV001-M-NEG", 0);
        var b = variante(p, "LIV001-M-ROJ", 0);
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(p));
        when(variantes.findByProductoId(10L)).thenReturn(List.of(a, b));
        when(productos.saveAndFlush(p)).thenReturn(p);

        servicio.cambiarEstado(10L, false);

        assertThat(a.isActivo()).isFalse();
        assertThat(b.isActivo()).isFalse();
    }

    @Test
    void cambiarEstado_desactivarConReservado_lanza409YNoCambiaNada() {
        Producto p = producto();
        var a = variante(p, "LIV001-M-NEG", 0);
        var b = variante(p, "LIV001-M-ROJ", 4);
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(p));
        when(variantes.findByProductoId(10L)).thenReturn(List.of(a, b));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.cambiarEstado(10L, false))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("No se puede desactivar el producto: la variante LIV001-M-ROJ tiene 4 unidades "
                        + "en apartados activos.");
        assertThat(p.isActivo()).isTrue();
        assertThat(a.isActivo()).isTrue();
        verify(productos, never()).saveAndFlush(any());
    }

    @Test
    void cambiarEstado_reactivar_noReactivaLasVariantes() {
        comoRol("ADMIN");
        Producto p = producto();
        p.desactivar();
        var a = variante(p, "LIV001-M-NEG", 0);
        a.desactivar();
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(p));
        when(productos.saveAndFlush(p)).thenReturn(p);

        assertThat(servicio.cambiarEstado(10L, true).activo()).isTrue();
        assertThat(a.isActivo()).isFalse();
        verify(variantes, never()).findByProductoId(any());
    }

    @Test
    void actualizar_violacionDeIntegridad_noDiceCodigoDuplicado() {
        Producto p = producto();
        when(productos.buscarConCategoria(10L)).thenReturn(Optional.of(p));
        when(categorias.findById(2L)).thenReturn(Optional.of(categoria()));
        when(productos.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> servicio.actualizar(10L, new ActualizarProductoRequest("Camiseta", 2L,
                new BigDecimal("20000"), new BigDecimal("59900"), 15, 1)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("categoría")
                .hasMessageNotContaining("código");
    }

    @Test
    void crear_violacionDeIntegridadPorCarrera_dicePorCodigoDuplicado() {
        Categoria c = categoria();
        when(categorias.findById(2L)).thenReturn(Optional.of(c));
        when(productos.existsByCodigo("LIV001")).thenReturn(false);
        when(productos.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> servicio.crear(crearReq("LIV001")))
                .isInstanceOf(ConflictoException.class).hasMessage("Ya existe un producto con ese código.");
    }
}
