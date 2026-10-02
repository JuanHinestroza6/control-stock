package co.livorno.controlstock.comun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/** Serializa Page como DTO estable: { content, page: { size, number, totalElements, totalPages } }. */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class ConfiguracionWeb {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    /**
     * @EnableSpringDataWebSupport hace que Boot no aplique spring.data.web.pageable.*, así que el tamaño por
     * defecto (20) y el máximo (100) se fijan aquí.
     */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer limitesDePagina() {
        return resolver -> {
            resolver.setFallbackPageable(org.springframework.data.domain.PageRequest.of(0, TAMANO_POR_DEFECTO));
            resolver.setMaxPageSize(TAMANO_MAXIMO);
        };
    }
}
