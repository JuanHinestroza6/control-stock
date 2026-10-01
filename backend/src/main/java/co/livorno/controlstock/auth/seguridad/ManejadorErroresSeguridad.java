package co.livorno.controlstock.auth.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Respuestas 401/403 de los filtros de seguridad, como ProblemDetail (RFC 9457) en espanol. Textos fijos. */
public class ManejadorErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escribir(response, HttpStatus.UNAUTHORIZED, "No autenticado",
                "Tu sesión no es válida o expiró. Inicia sesión de nuevo.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escribir(response, HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permisos para realizar esta acción.");
    }

    private static void escribir(HttpServletResponse response, HttpStatus estado, String titulo, String detalle)
            throws IOException {
        response.setStatus(estado.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":"
                + estado.value() + ",\"detail\":\"" + detalle + "\"}");
    }
}
