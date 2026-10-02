CREATE TABLE categoria (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    nombre         VARCHAR(60) NOT NULL,
    creado_en      DATETIME(6) NOT NULL,
    actualizado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categoria_nombre UNIQUE (nombre)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO categoria (nombre, creado_en, actualizado_en) VALUES
    ('Camisetas', NOW(6), NOW(6)),
    ('Hoodies', NOW(6), NOW(6)),
    ('Jeans', NOW(6), NOW(6)),
    ('Joggers', NOW(6), NOW(6)),
    ('Chaquetas', NOW(6), NOW(6)),
    ('Accesorios', NOW(6), NOW(6));
