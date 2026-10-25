package co.livorno.controlstock.comun.web;

import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Construye la respuesta de descarga de un archivo exportado (ADR-011, R2): adjunto, tipo explícito y sin caché. */
public final class RespuestaArchivo {

    private RespuestaArchivo() {}

    public static ResponseEntity<byte[]> de(ArchivoExportado archivo) {
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(archivo.nombre(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .contentLength(archivo.contenido().length)
                .cacheControl(CacheControl.noStore())
                .body(archivo.contenido());
    }
}
