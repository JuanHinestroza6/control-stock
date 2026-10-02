package co.livorno.controlstock.usuario.dto;

import co.livorno.controlstock.comun.validacion.PasswordValida;

public record RestablecerPasswordRequest(@PasswordValida String passwordTemporal) {}
