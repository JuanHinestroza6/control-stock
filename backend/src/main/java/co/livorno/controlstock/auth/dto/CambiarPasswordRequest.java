package co.livorno.controlstock.auth.dto;

import co.livorno.controlstock.comun.validacion.PasswordValida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 72, message = "La contraseña actual admite máximo 72 caracteres") String actual,
        @PasswordValida String nueva) {}
