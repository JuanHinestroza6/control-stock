package co.livorno.controlstock.comun.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class PasswordValidaValidator implements ConstraintValidator<PasswordValida, String> {

    static final int MIN_CARACTERES = 12;
    static final int MAX_CARACTERES = 72;
    static final int MAX_BYTES = 72; // límite de BCrypt

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        String error = error(valor);
        if (error == null) {
            return true;
        }
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }

    /** Mensaje del primer incumplimiento, o null si la contraseña es válida (sin llaves ni $ para la plantilla). */
    static String error(String valor) {
        if (valor == null || valor.isBlank()) {
            return "La contraseña es obligatoria";
        }
        int caracteres = valor.codePointCount(0, valor.length());
        if (caracteres < MIN_CARACTERES) {
            return "La contraseña debe tener al menos " + MIN_CARACTERES + " caracteres";
        }
        if (caracteres > MAX_CARACTERES || valor.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "La contraseña no puede superar " + MAX_CARACTERES + " caracteres ni " + MAX_BYTES + " bytes";
        }
        if (valor.toLowerCase(Locale.ROOT).startsWith("cambiar")) {
            return "La contraseña no puede empezar por la palabra cambiar";
        }
        return null;
    }
}
