package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.Talla;
import java.util.List;

public record GenerarVariantesResponse(List<VarianteResponse> creadas, List<Omitida> omitidas) {

    public enum Motivo { EXISTE, EXISTE_INACTIVA }

    public record Omitida(Talla talla, String color, String sku, Motivo motivo) {
    }
}
