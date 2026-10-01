package co.livorno.controlstock.comun.excepcion;

/** Error de validación de un campo; forma fija consumida por el frontend. */
public record ErrorCampo(String campo, String mensaje) {}
