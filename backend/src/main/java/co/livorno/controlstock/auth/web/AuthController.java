package co.livorno.controlstock.auth.web;

import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.auth.dto.LoginResponse;
import co.livorno.controlstock.auth.dto.UsuarioSesionResponse;
import co.livorno.controlstock.auth.servicio.AuthService;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final UsuarioActual usuarioActual;

    public AuthController(AuthService service, UsuarioActual usuarioActual) {
        this.service = service;
        this.usuarioActual = usuarioActual;
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public LoginResponse login(@Valid @RequestBody LoginRequest solicitud, HttpServletRequest request) {
        return service.login(solicitud, request.getRemoteAddr());
    }

    @GetMapping("/yo")
    @PreAuthorize("isAuthenticated()")
    public UsuarioSesionResponse yo() {
        return UsuarioSesionResponse.de(usuarioActual.obtener());
    }
}
