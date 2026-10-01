package co.livorno.controlstock.auth.servicio;

import co.livorno.controlstock.auth.config.PropiedadesLogin;
import co.livorno.controlstock.comun.excepcion.DemasiadosIntentosException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limitador en memoria de intentos fallidos de login. Clave = email normalizado + IP. Tras maxIntentos fallos
 * dentro de la ventana, la clave queda bloqueada hasta que la ventana (contada desde el primer fallo) expire.
 * El estado se pierde al reiniciar y no se comparte entre instancias.
 */
@Component
public class LimitadorIntentosLogin {

    private record Intentos(int fallos, Instant inicioVentana) {}

    private final ConcurrentHashMap<String, Intentos> intentos = new ConcurrentHashMap<>();
    private final PropiedadesLogin propiedades;
    private final Clock clock;

    public LimitadorIntentosLogin(PropiedadesLogin propiedades, Clock clock) {
        this.propiedades = propiedades;
        this.clock = clock;
    }

    /** Lanza DemasiadosIntentosException si la clave esta bloqueada. Debe llamarse antes de verificar la contrasena. */
    public void verificarNoBloqueado(String email, String ip) {
        Intentos actual = intentos.get(clave(email, ip));
        if (actual == null) {
            return;
        }
        Instant ahora = clock.instant();
        Instant fin = actual.inicioVentana().plus(propiedades.ventana());
        if (actual.fallos() >= propiedades.maxIntentos() && ahora.isBefore(fin)) {
            throw new DemasiadosIntentosException(Duration.between(ahora, fin).plusMillis(999).toSeconds());
        }
    }

    public void registrarFallo(String email, String ip) {
        Instant ahora = clock.instant();
        limpiarVencidas(ahora);
        intentos.merge(clave(email, ip), new Intentos(1, ahora), (previo, nuevo) ->
                vencida(previo, ahora) ? nuevo : new Intentos(previo.fallos() + 1, previo.inicioVentana()));
    }

    public void registrarExito(String email, String ip) {
        intentos.remove(clave(email, ip));
    }

    /** Vacia todo el estado (uso en pruebas). */
    public void limpiar() {
        intentos.clear();
    }

    int tamano() {
        return intentos.size();
    }

    private void limpiarVencidas(Instant ahora) {
        intentos.entrySet().removeIf(e -> vencida(e.getValue(), ahora));
    }

    private boolean vencida(Intentos i, Instant ahora) {
        return !ahora.isBefore(i.inicioVentana().plus(propiedades.ventana()));
    }

    private static String clave(String email, String ip) {
        return email.trim().toLowerCase(Locale.ROOT) + "|" + ip;
    }
}
