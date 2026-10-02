ALTER TABLE usuario
    ADD COLUMN password_cambiado_en DATETIME(6) NULL,
    ADD COLUMN debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;
