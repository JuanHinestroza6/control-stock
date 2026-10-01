CREATE TABLE usuario (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    nombre         VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en      DATETIME(6)  NOT NULL,
    actualizado_en DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_usuario_rol ON usuario (rol);
