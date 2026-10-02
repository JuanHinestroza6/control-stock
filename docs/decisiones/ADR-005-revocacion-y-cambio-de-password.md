# ADR-005: Revocación de tokens por cambio de contraseña y contraseña temporal obligatoria

- Estado: aceptada
- Fecha: 2026-10-01
- Modifica: ADR-003 (sesión JWT sin revocación)

## Contexto
ADR-003 dejó los tokens válidos hasta su vencimiento (8 h) sin revocación. En la Fase 1 el ADMIN crea usuarios y restablece
contraseñas (REQ-12). Sin revocación, quien tenga un token robado o una sesión abierta seguiría dentro hasta 8 h después
del restablecimiento, y la contraseña que define el ADMIN (que él conoce) quedaría como definitiva.

## Decisión
- Nueva columna `usuario.password_cambiado_en` (DATETIME(6), nullable). Al restablecer (ADMIN) o cambiar (el propio usuario)
  la contraseña se guarda el instante del `Clock`, **truncado a segundos**.
- El convertidor de JWT, que ya recarga el usuario en cada petición, rechaza con 401 un token cuyo `iat` sea **anterior** a
  `passwordCambiadoEn` (comparación estricta, ambos en segundos y en la misma zona del `Clock`). Un token sin `iat` se rechaza
  si el usuario tiene `passwordCambiadoEn`.
- `PUT /api/auth/yo/password` cambia la propia contraseña y devuelve un `LoginResponse` con un token nuevo (el anterior queda
  revocado). Contraseña actual incorrecta → 422, no 401 (un 401 haría que el frontend cierre la sesión).
- Nueva columna `usuario.debe_cambiar_password` (BOOLEAN, default FALSE). La contraseña asignada por el ADMIN al crear o al
  restablecer es temporal y la pone en TRUE; cambiar la propia la pone en FALSE. El ADMIN inicial de V2 no queda forzado.
- Mientras `debeCambiarPassword` sea TRUE, la cadena de filtros solo permite `GET /api/auth/yo` y `PUT /api/auth/yo/password`;
  el resto responde 403 con `detail` "Debes cambiar tu contraseña temporal antes de continuar." y propiedad
  `codigo: "CAMBIO_PASSWORD_REQUERIDO"`. `LoginResponse.usuario` y `GET /api/auth/yo` incluyen `debeCambiarPassword` para que
  el frontend redirija a `/cuenta`.
- Migración `V6__agregar_credenciales_usuario.sql` (V1 y V2 no se editan).

## Alternativas consideradas
- Claim de versión de credenciales (contador en BD y en el token) — sin la ventana de 1 s, pero agrega un claim y una
  columna menos legible; la fecha además sirve de auditoría.
- Lista negra de tokens (jti) — estado en servidor y limpieza periódica; demasiado para el alcance.
- Comparar con precisión de microsegundos — rompe el caso normal: `iat` se emite en segundos y el token nuevo emitido justo
  después del cambio quedaría con `iat` menor que el instante del cambio y sería rechazado.
- Forzar el cambio solo en el frontend — se evade llamando la API directamente.

## Consecuencias
- Restablecer la contraseña cierra de inmediato las sesiones abiertas de ese usuario (siguiente petición → 401).
- Ventana aceptada: un token emitido en el mismo segundo pero antes del cambio sigue siendo válido.
- El ADMIN no puede restablecer su propia contraseña desde la administración de usuarios (lo dejaría forzado y sin sesión);
  usa "Mi cuenta".
- `UsuarioAutenticado`, `UsuarioSesionResponse` y el tipo `UsuarioSesion` del frontend agregan `debeCambiarPassword`;
  las sesiones guardadas antes del cambio (sin el campo) se tratan como `false`.
- El cambio de la propia contraseña no pasa por el limitador de intentos del login (requiere un token válido); revisar si
  se observa abuso.
