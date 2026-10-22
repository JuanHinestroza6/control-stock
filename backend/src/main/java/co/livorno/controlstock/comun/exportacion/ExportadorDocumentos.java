package co.livorno.controlstock.comun.exportacion;

import org.springframework.stereotype.Component;

/** Fachada de exportación (ADR-011): convierte un {@link DocumentoTabular} en el archivo del formato pedido. */
@Component
public class ExportadorDocumentos {

    public static final String TIPO_PDF = "application/pdf";
    public static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final GeneradorExcel generadorExcel;
    private final GeneradorPdf generadorPdf;

    public ExportadorDocumentos(GeneradorExcel generadorExcel, GeneradorPdf generadorPdf) {
        this.generadorExcel = generadorExcel;
        this.generadorPdf = generadorPdf;
    }

    /** @param nombreBase nombre sin extensión; se agrega {@code .pdf} o {@code .xlsx} */
    public ArchivoExportado exportar(DocumentoTabular doc, FormatoExportacion formato, String nombreBase) {
        return switch (formato) {
            case PDF -> new ArchivoExportado(generadorPdf.generar(doc), TIPO_PDF, nombreBase + ".pdf");
            case XLSX -> new ArchivoExportado(generadorExcel.generar(doc), TIPO_XLSX, nombreBase + ".xlsx");
        };
    }
}
