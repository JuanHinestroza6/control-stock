package co.livorno.controlstock.auth.seguridad;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public static final String CODIGO_CAMBIO_PASSWORD = "CAMBIO_PASSWORD_REQUERIDO";

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u && u.debeCambiarPassword()) {
            escribir(request, response, HttpStatus.FORBIDDEN, "Cambio de contraseña requerido",
                    "Debes cambiar tu contraseña temporal antes de continuar.", CODIGO_CAMBIO_PASSWORD);
            return;
        }
        escribir(request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permisos para realizar esta acción.", null);
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado,
            String titulo, String detalle) throws IOException {
        escribir(request, response, estado, titulo, detalle, null);
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado,
            String titulo, String detalle, String codigo) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        pd.setTitle(titulo);
        if (codigo != null) {
            pd.setProperty("codigo", codigo);
        }
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
