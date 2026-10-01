package db.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class V2__UsuarioAdminInicialTest {

    private Connection conexionConAdmins(int cantidad, PreparedStatement insert) throws Exception {
        Connection con = mock(Connection.class);
        PreparedStatement consulta = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(con.prepareStatement(anyString())).thenAnswer(inv ->
                ((String) inv.getArgument(0)).startsWith("SELECT") ? consulta : insert);
        when(consulta.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(cantidad);
        return con;
    }

    @Test
    void insertar_sinAdminYSinPlaceholders_lanzaExcepcionClara() throws Exception {
        PreparedStatement insert = mock(PreparedStatement.class);
        Connection con = conexionConAdmins(0, insert);

        assertThatThrownBy(() -> V2__UsuarioAdminInicial.insertarAdminSiNoExiste(
                con, Map.of("admin-email", "", "admin-password", ""), new BCryptPasswordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL");
        verify(insert, never()).executeUpdate();
    }

    @Test
    void insertar_passwordMenorA12Caracteres_lanzaExcepcion() throws Exception {
        PreparedStatement insert = mock(PreparedStatement.class);
        Connection con = conexionConAdmins(0, insert);

        assertThatThrownBy(() -> V2__UsuarioAdminInicial.insertarAdminSiNoExiste(
                con, Map.of("admin-email", "a@livorno.test", "admin-password", "Corta123!!!"), new BCryptPasswordEncoder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("12");
        verify(insert, never()).executeUpdate();
    }

    @Test
    void insertar_passwordDeEjemploQueEmpiezaPorCambiar_lanzaExcepcion() throws Exception {
        PreparedStatement insert = mock(PreparedStatement.class);
        Connection con = conexionConAdmins(0, insert);

        assertThatThrownBy(() -> V2__UsuarioAdminInicial.insertarAdminSiNoExiste(
                con, Map.of("admin-email", "a@livorno.test", "admin-password", "cambiar-password-admin"),
                new BCryptPasswordEncoder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cambiar");
        verify(insert, never()).executeUpdate();
    }

    @Test
    void insertar_passwordDe12Caracteres_insertaAdmin() throws Exception {
        PreparedStatement insert = mock(PreparedStatement.class);
        Connection con = conexionConAdmins(0, insert);

        V2__UsuarioAdminInicial.insertarAdminSiNoExiste(
                con, Map.of("admin-email", " Admin@Livorno.TEST ", "admin-password", "Doce12345678"),
                new BCryptPasswordEncoder());

        verify(insert).setString(2, "admin@livorno.test");
        verify(insert).executeUpdate();
    }

    @Test
    void insertar_conAdminExistenteYSinPlaceholders_noHaceNadaNiFalla() throws Exception {
        PreparedStatement insert = mock(PreparedStatement.class);
        Connection con = conexionConAdmins(1, insert);

        assertThatCode(() -> V2__UsuarioAdminInicial.insertarAdminSiNoExiste(
                con, Map.of(), new BCryptPasswordEncoder())).doesNotThrowAnyException();
        verify(insert, never()).executeUpdate();
    }
}
