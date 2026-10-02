package co.livorno.controlstock.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Contraseña: no vacía, 12 a 72 caracteres, máximo 72 bytes en UTF-8 y que no empiece por "cambiar". */
@Documented
@Constraint(validatedBy = PasswordValidaValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordValida {

    String message() default "La contraseña no es válida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
