package co.livorno.controlstock.comun.web;

import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Valida el orden solicitado contra una lista blanca de campos. */
public final class Ordenamiento {

    private Ordenamiento() {}

    public static void validar(Pageable pageable, Set<String> permitidos) {
        for (Sort.Order orden : pageable.getSort()) {
            if (!permitidos.contains(orden.getProperty())) {
                throw new SolicitudInvalidaException("Campo de orden no permitido: " + orden.getProperty());
            }
        }
    }
}
