package co.livorno.controlstock.catalogo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.dto.CategoriaRequest;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock CategoriaRepository repositorio;
    @InjectMocks CategoriaService servicio;

    @Test
    void crear_nombreNuevo_guardaConNombreSinEspacios() {
        // dado
        when(repositorio.existsByNombre("Gorras")).thenReturn(false);
        when(repositorio.saveAndFlush(any(Categoria.class))).thenAnswer(i -> i.getArgument(0));
        // cuando
        var respuesta = servicio.crear(new CategoriaRequest("  Gorras "));
        // entonces
        assertThat(respuesta.nombre()).isEqualTo("Gorras");
        assertThat(respuesta.totalProductos()).isZero();
    }

    @Test
    void crear_nombreDuplicado_lanzaConflicto() {
        when(repositorio.existsByNombre("Jeans")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CategoriaRequest("Jeans")))
                .isInstanceOf(ConflictoException.class);
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void actualizar_nombreDeOtraCategoria_lanzaConflicto() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(new Categoria("Jeans")));
        when(repositorio.existsByNombreAndIdNot("Hoodies", 1L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(1L, new CategoriaRequest("Hoodies")))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void actualizar_inexistente_lanza404() {
        when(repositorio.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(9L, new CategoriaRequest("X")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizar_nombreValido_renombra() {
        Categoria c = new Categoria("Jeans");
        when(repositorio.findById(1L)).thenReturn(Optional.of(c));
        when(repositorio.existsByNombreAndIdNot("Denim", 1L)).thenReturn(false);
        when(repositorio.saveAndFlush(c)).thenReturn(c);

        assertThat(servicio.actualizar(1L, new CategoriaRequest("Denim")).nombre()).isEqualTo("Denim");
    }

    @Test
    void eliminar_conProductos_lanzaConflicto() {
        Categoria c = new Categoria("Jeans");
        when(repositorio.findById(1L)).thenReturn(Optional.of(c));
        doThrow(new DataIntegrityViolationException("fk")).when(repositorio).flush();

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("La categoría tiene productos asociados");
    }

    @Test
    void eliminar_sinProductos_borra() {
        Categoria c = new Categoria("Jeans");
        when(repositorio.findById(1L)).thenReturn(Optional.of(c));

        servicio.eliminar(1L);

        verify(repositorio).delete(c);
    }
}
