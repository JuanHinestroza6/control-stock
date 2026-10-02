package co.livorno.controlstock.usuario.dto;

import co.livorno.controlstock.comun.validacion.PasswordValida;
import co.livorno.controlstock.usuario.modelo.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres") String nombre,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo admite máximo 150 caracteres") String email,
        @NotNull(message = "El rol es obligatorio") Rol rol,
        @PasswordValida String passwordTemporal) {}
