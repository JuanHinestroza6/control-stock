package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.dto.EjecucionAnaliticaResponse;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Recálculo manual de la analítica (solo ADMIN, síncrono). Deliberadamente sin {@code @Transactional}: el orquestador
 * abre una transacción por paso.
 */
@Service
@RequiredArgsConstructor
public class RecalculoAnaliticoService {

    private final EjecucionAnaliticaService ejecucion;
    private final ConsultaAnaliticaService consulta;
    private final UsuarioActual usuarioActual;

    /** @throws EjecucionEnCursoException si ya hay otro recálculo en curso (409) */
    public EjecucionAnaliticaResponse recalcular() {
        EjecucionAnalitica terminada = ejecucion.ejecutar(OrigenEjecucion.MANUAL, usuarioActual.id());
        return consulta.respuesta(terminada);
    }
}
