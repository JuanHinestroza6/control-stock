package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.servicio.ConsultaReservasActivas;
import co.livorno.controlstock.inventario.repositorio.ApartadosVariante;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Implementa el puerto de catalogo consultando la tabla {@code reserva}. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaReservasActivasJpa implements ConsultaReservasActivas {

    private final ReservaRepository reservas;

    @Override
    public Resumen resumenDeVariante(Long varianteId) {
        return reservas.apartadosActivosDeVariante(varianteId)
                .map(a -> new Resumen(a.apartados(), a.unidades()))
                .orElse(new Resumen(0, 0));
    }

    @Override
    public Optional<VarianteConApartados> primeraVarianteConApartados(Long productoId) {
        return reservas.apartadosActivosPorProducto(productoId, PageRequest.of(0, 1)).stream().findFirst()
                .map(a -> new VarianteConApartados(a.varianteId(), a.sku(), a.apartados(), a.unidades()));
    }
}
