package co.livorno.controlstock.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, String tipo, Instant expiraEn, UsuarioSesionResponse usuario) {

    public static LoginResponse de(String token, Instant expiraEn, UsuarioSesionResponse usuario) {
        return new LoginResponse(token, "Bearer", expiraEn, usuario);
    }
}
