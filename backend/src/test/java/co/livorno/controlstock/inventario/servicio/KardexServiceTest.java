package co.livorno.controlstock.inventario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.inventario.dto.MovimientoResponse;
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.MovimientoRepository;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class KardexServiceTest {

    MovimientoRepository movimientos = mock(MovimientoRepository.class);
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    KardexService servicio = new KardexService(movimientos, usuarios);

    @Test
    void consultar_desdeMayorQueHasta_400() {
        assertThatThrownBy(() -> servicio.consultar(null, null, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 2),
                PageRequest.of(0, 20)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La fecha inicial no puede ser posterior a la final.");
    }

    @Test
    void consultar_convierteFechasInclusivasEIgnoraElSortDelCliente() {
        when(movimientos.buscar(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        servicio.consultar(5L, TipoMovimiento.ENTRADA, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2),
                PageRequest.of(2, 10, Sort.by("tipo")));

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(movimientos).buscar(eq(5L), eq(TipoMovimiento.ENTRADA), eq(LocalDateTime.of(2026, 10, 2, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 3, 0, 0)), p.capture());
        assertThat(p.getValue().getPageNumber()).isEqualTo(2);
        assertThat(p.getValue().getPageSize()).isEqualTo(10);
        assertThat(p.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void consultar_resuelveNombreDeUsuarioYNullParaElSistema() {
        Producto prod = new Producto("LIV001", "Camiseta", mock(Categoria.class), BigDecimal.TEN, BigDecimal.TEN, null, null);
        Variante v = new Variante(prod, "LIV001-M-NEG", Talla.M, "NEGRO", 2);
        LocalDateTime f = LocalDateTime.of(2026, 10, 2, 10, 0);
        Movimiento porUsuario = Movimiento.de(v, TipoMovimiento.AJUSTE_POSITIVO, 1, 0, 0, null, 7L, f, null, null, "x");
        Movimiento delSistema = Movimiento.de(v, TipoMovimiento.LIBERACION, 1, 1, 1, null, null, f, null, null, null);
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(7L);
        when(u.getNombre()).thenReturn("Ines");
        when(movimientos.buscar(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(porUsuario, delSistema)));
        when(usuarios.findAllById(any())).thenReturn(List.of(u));

        List<MovimientoResponse> r = servicio.consultar(null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertThat(r).extracting(MovimientoResponse::usuarioNombre).containsExactly("Ines", null);
        assertThat(r.get(0).sku()).isEqualTo("LIV001-M-NEG");
        assertThat(r.get(0).productoNombre()).isEqualTo("Camiseta");
    }
}
