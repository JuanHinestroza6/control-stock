package co.livorno.controlstock.inventario.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.modelo.Reserva;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class ReservaRepositoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 2, 10, 0);
    private static final String C = "2026-10-02 10:00:00";
    private static final String V = "2026-10-04 10:00:00";

    @Autowired ReservaRepository reservas;
    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;

    Variante variante;
    Usuario usuario;

    @BeforeEach
    void preparar() {
        Producto p = productos.saveAndFlush(new Producto("LIV001", "Camiseta",
                categorias.findAllByOrderByNombreAsc().get(0), new BigDecimal("20000"), new BigDecimal("59900"), null,
                null));
        variante = variantes.saveAndFlush(new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1));
        usuario = usuarios.saveAndFlush(new Usuario("Vendedor", "vend@livorno.co", "hash", Rol.VENDEDOR));
    }

    private Reserva guardar(LocalDateTime vence) {
        return reservas.saveAndFlush(Reserva.de(variante, 2, "Ana", "3001234567", T0, vence, usuario.getId()));
    }

    private int insertar(String estado, int cantidad, String creada, String vence, String cerrada, String motivo) {
        return jdbc.update("""
                insert into reserva (variante_id, cantidad, cliente_nombre, cliente_contacto, creada_en, vence_en,
                    estado, usuario_id, cerrada_en, motivo_cancelacion)
                values (?, ?, 'Ana', '300', ?, ?, ?, ?, ?, ?)""", variante.getId(), cantidad, creada, vence, estado,
                usuario.getId(), cerrada, motivo);
    }

    @Test
    void save_reservaActiva_persisteYSeLeeConValidate() {
        Reserva r = guardar(T0.plusHours(48));

        Reserva leida = reservas.findById(r.getId()).orElseThrow();

        assertThat(leida.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(leida.getCantidad()).isEqualTo(2);
        assertThat(leida.getVentaId()).isNull();
    }

    @Test
    void save_transicionCancelar_cumpleChecks() {
        Reserva r = guardar(T0.plusHours(48));
        r.cancelar("Cliente desistió", T0.plusHours(1), usuario.getId());

        reservas.saveAndFlush(r);

        assertThat(reservas.findById(r.getId()).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void check_filasValidas_seAceptan() {
        assertThat(insertar("ACTIVA", 1, C, V, null, null)).isEqualTo(1);
        assertThat(insertar("VENCIDA", 1, C, V, "2026-10-04 10:15:00", null)).isEqualTo(1);
        assertThat(insertar("CANCELADA", 1, C, V, "2026-10-03 10:00:00", "Desistió")).isEqualTo(1);
    }

    @Test
    void check_violaciones_seRechazan() {
        // cantidad 0
        assertThatThrownBy(() -> insertar("ACTIVA", 0, C, V, null, null)).isInstanceOf(DataAccessException.class);
        // estado desconocido
        assertThatThrownBy(() -> insertar("OTRA", 1, C, V, C, null)).isInstanceOf(DataAccessException.class);
        // vence antes de crear
        assertThatThrownBy(() -> insertar("ACTIVA", 1, V, C, null, null)).isInstanceOf(DataAccessException.class);
        // CONVERTIDA sin venta
        assertThatThrownBy(() -> insertar("CONVERTIDA", 1, C, V, C, null)).isInstanceOf(DataAccessException.class);
        // ACTIVA con cerrada_en
        assertThatThrownBy(() -> insertar("ACTIVA", 1, C, V, C, null)).isInstanceOf(DataAccessException.class);
        // VENCIDA sin cerrada_en
        assertThatThrownBy(() -> insertar("VENCIDA", 1, C, V, null, null)).isInstanceOf(DataAccessException.class);
        // CANCELADA sin motivo
        assertThatThrownBy(() -> insertar("CANCELADA", 1, C, V, C, null)).isInstanceOf(DataAccessException.class);
        // motivo en una no cancelada
        assertThatThrownBy(() -> insertar("VENCIDA", 1, C, V, C, "x")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void idsVencidas_soloActivasVencidasOrdenadasYLimitadas() {
        Reserva tarde = guardar(T0.plusHours(5));
        Reserva temprano = guardar(T0.plusHours(2));
        guardar(T0.plusHours(30)); // no vencida
        Reserva cerrada = guardar(T0.plusHours(1));
        cerrada.vencer(T0.plusHours(3));
        reservas.saveAndFlush(cerrada);

        LocalDateTime ahora = T0.plusHours(10);

        assertThat(reservas.idsVencidas(ahora, PageRequest.of(0, 500)))
                .containsExactly(temprano.getId(), tarde.getId());
        assertThat(reservas.idsVencidas(ahora, PageRequest.of(0, 1))).containsExactly(temprano.getId());
    }

    @Test
    void buscar_filtrosCombinados_devuelveSoloLasQueCoinciden() {
        Reserva a = guardar(T0.plusHours(5));
        Reserva b = reservas.saveAndFlush(Reserva.de(variante, 1, "Luis_100%", "luis@mail.co", T0,
                T0.plusHours(40), usuario.getId()));
        Reserva c = guardar(T0.plusHours(1));
        c.vencer(T0.plusHours(2));
        reservas.saveAndFlush(c);
        PageRequest pag = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("venceEn"));

        assertThat(reservas.buscar(null, null, null, null, pag).getContent())
                .extracting(Reserva::getId).containsExactly(c.getId(), a.getId(), b.getId());
        assertThat(reservas.buscar(EstadoReserva.ACTIVA, null, null, null, pag).getTotalElements()).isEqualTo(2);
        assertThat(reservas.buscar(null, variante.getId(), "%LIV001-M%", null, pag).getTotalElements()).isEqualTo(3);
        assertThat(reservas.buscar(null, null, "%luis!_100!%%", null, pag).getContent())
                .extracting(Reserva::getId).containsExactly(b.getId());
        assertThat(reservas.buscar(null, null, "%3001234567%", null, pag).getTotalElements()).isEqualTo(2);
        // porVencer: ACTIVA con venceEn < límite (c está VENCIDA y no cuenta)
        assertThat(reservas.buscar(null, null, null, T0.plusHours(12), pag).getContent())
                .extracting(Reserva::getId).containsExactly(a.getId());
    }

    @Test
    void bloquear_variasReservas_devuelveFilasOrdenadasPorId() {
        Reserva a = guardar(T0.plusHours(48));
        Reserva b = guardar(T0.plusHours(24));

        List<Reserva> bloqueadas = reservas.bloquearVarias(List.of(b.getId(), a.getId()));

        assertThat(reservas.bloquear(a.getId())).isPresent();
        assertThat(reservas.bloquear(-1L)).isEmpty();
        assertThat(bloqueadas).extracting(Reserva::getId).containsExactly(a.getId(), b.getId());
    }
}
