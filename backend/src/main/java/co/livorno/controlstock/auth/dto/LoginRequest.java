package co.livorno.controlstock.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres") String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres") String password) {}
