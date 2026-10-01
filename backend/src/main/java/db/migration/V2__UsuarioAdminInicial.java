package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Crea el ADMIN inicial si no existe ninguno. Email y contrasena vienen de los placeholders de Flyway
 * (spring.flyway.placeholders.admin-email / admin-password, alimentados por ADMIN_EMAIL / ADMIN_PASSWORD).
 */
public class V2__UsuarioAdminInicial extends BaseJavaMigration {

    static final String PH_EMAIL = "admin-email";
    static final String PH_PASSWORD = "admin-password";
    static final int MIN_LONGITUD_PASSWORD = 12;

    @Override
    public void migrate(Context context) throws Exception {
        insertarAdminSiNoExiste(context.getConnection(), context.getConfiguration().getPlaceholders(),
                new BCryptPasswordEncoder());
    }

    public static void insertarAdminSiNoExiste(Connection conexion, Map<String, String> placeholders,
            PasswordEncoder encoder) throws SQLException {
        if (existeAdmin(conexion)) {
            return;
        }
        String email = placeholders.getOrDefault(PH_EMAIL, "").trim().toLowerCase(java.util.Locale.ROOT);
        String password = placeholders.getOrDefault(PH_PASSWORD, "");
        if (email.isEmpty() || password.isBlank()) {
            throw new IllegalStateException("No existe ningun usuario ADMIN y faltan las variables de entorno "
                    + "ADMIN_EMAIL y/o ADMIN_PASSWORD para crear el administrador inicial.");
        }
        if (password.length() < MIN_LONGITUD_PASSWORD
                || password.toLowerCase(Locale.ROOT).startsWith("cambiar")) {
            throw new IllegalStateException("ADMIN_PASSWORD debe tener al menos " + MIN_LONGITUD_PASSWORD
                    + " caracteres y no puede ser el valor de ejemplo (empieza por 'cambiar').");
        }
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Bogota"));
        String sql = "INSERT INTO usuario (nombre, email, password_hash, rol, activo, creado_en, actualizado_en) "
                + "VALUES (?, ?, ?, 'ADMIN', TRUE, ?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, "Administrador");
            ps.setString(2, email);
            ps.setString(3, encoder.encode(password));
            ps.setObject(4, ahora);
            ps.setObject(5, ahora);
            ps.executeUpdate();
        }
    }

    private static boolean existeAdmin(Connection conexion) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("SELECT COUNT(*) FROM usuario WHERE rol = 'ADMIN'");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }
}
