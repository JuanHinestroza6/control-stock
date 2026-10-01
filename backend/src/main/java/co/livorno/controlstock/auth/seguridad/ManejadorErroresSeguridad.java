package co.livorno.controlstock.auth.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/** Respuestas 401/403 de los filtros de seguridad, como ProblemDetail (RFC 9457) en espanol. */
public class ManejadorErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper mapper = JsonMapper.builder()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .build();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escribir(request, response, HttpStatus.UNAUTHORIZED, "No autenticado",
                "Tu sesión no es válida o expiró. Inicia sesión de nuevo.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escribir(request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permisos para realizar esta acción.");
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado,
            String titulo, String detalle) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        pd.setTitle(titulo);
        try {
            pd.setInstance(new URI(request.getRequestURI()));
        } catch (URISyntaxException e) {
            // sin instance si la ruta no es un URI valido
        }
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(pd));
    }
}
