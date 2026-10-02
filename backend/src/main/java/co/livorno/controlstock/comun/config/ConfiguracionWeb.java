package co.livorno.controlstock.comun.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/** Serializa Page como DTO estable: { content, page: { size, number, totalElements, totalPages } }. */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class ConfiguracionWeb {
}
