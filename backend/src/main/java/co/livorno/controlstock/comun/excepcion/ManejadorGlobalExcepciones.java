package co.livorno.controlstock.comun.excepcion;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ManejadorGlobalExcepciones extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorCampo(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validacionProblema(errores));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorCampo> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorCampo(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validacionProblema(errores));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "El cuerpo de la solicitud no es válido o está mal formado."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail violaciones(ConstraintViolationException ex) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> {
                    String ruta = v.getPropertyPath().toString();
                    return new ErrorCampo(ruta.substring(ruta.lastIndexOf('.') + 1), v.getMessage());
                })
                .toList();
        return validacionProblema(errores);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(ExcepcionNegocio.class)
    public ProblemDetail negocio(ExcepcionNegocio ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio incumplida", ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail conflicto(ConflictoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail noAutenticado(AuthenticationException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "No autenticado", "Credenciales inválidas");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permisos para realizar esta acción.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail bloqueoOptimista(OptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto de concurrencia",
                "El registro fue modificado por otra operación. Actualiza e intenta de nuevo.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos", ex);
        return problema(HttpStatus.CONFLICT, "Conflicto de datos",
                "La operación viola una restricción de datos (registro duplicado o en uso).");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception ex) {
        log.error("Error no controlado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Intenta de nuevo más tarde.");
    }

    private static ProblemDetail validacionProblema(List<ErrorCampo> errores) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Hay campos con valores inválidos.");
        pd.setProperty("errores", errores);
        return pd;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        pd.setTitle(titulo);
        return pd;
    }
}
