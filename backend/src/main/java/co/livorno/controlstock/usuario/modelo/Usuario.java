package co.livorno.controlstock.usuario.modelo;

import co.livorno.controlstock.comun.modelo.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    /** Momento del último cambio o restablecimiento de contraseña, truncado a segundos (revoca tokens anteriores). */
    @Column(name = "password_cambiado_en")
    private LocalDateTime passwordCambiadoEn;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword;

    public Usuario(String nombre, String email, String passwordHash, Rol rol) {
        this.nombre = nombre;
        this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        this.passwordHash = passwordHash;
        this.rol = rol;
    }

    /** El ADMIN asigna una contraseña temporal: obliga a cambiarla y revoca los tokens vigentes. */
    public void asignarPasswordTemporal(String passwordHash, LocalDateTime ahora) {
        this.passwordHash = passwordHash;
        this.debeCambiarPassword = true;
        this.passwordCambiadoEn = ahora.truncatedTo(ChronoUnit.SECONDS);
    }

    /** El propio usuario cambia su contraseña: quita la obligación y revoca los tokens anteriores. */
    public void cambiarPassword(String passwordHash, LocalDateTime ahora) {
        this.passwordHash = passwordHash;
        this.debeCambiarPassword = false;
        this.passwordCambiadoEn = ahora.truncatedTo(ChronoUnit.SECONDS);
    }

    public void actualizarDatos(String nombre, String email) {
        this.nombre = nombre.strip();
        this.email = email.strip().toLowerCase(Locale.ROOT);
    }

    public void cambiarRol(Rol rol) {
        this.rol = rol;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }
}
