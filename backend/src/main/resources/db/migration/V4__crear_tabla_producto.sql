CREATE TABLE producto (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    codigo         VARCHAR(20)    NOT NULL,
    nombre         VARCHAR(120)   NOT NULL,
    categoria_id   BIGINT         NOT NULL,
    precio_compra  DECIMAL(12, 2) NOT NULL,
    precio_venta   DECIMAL(12, 2) NOT NULL,
    lead_time_dias INT            NOT NULL DEFAULT 15,
    lote_minimo    INT            NOT NULL DEFAULT 1,
    activo         BOOLEAN        NOT NULL DEFAULT TRUE,
    creado_en      DATETIME(6)    NOT NULL,
    actualizado_en DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_producto_codigo UNIQUE (codigo),
    CONSTRAINT fk_producto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE RESTRICT,
    CONSTRAINT ck_producto_precio_compra CHECK (precio_compra >= 0),
    CONSTRAINT ck_producto_precio_venta CHECK (precio_venta > 0),
    CONSTRAINT ck_producto_lead_time CHECK (lead_time_dias BETWEEN 1 AND 365),
    CONSTRAINT ck_producto_lote_minimo CHECK (lote_minimo >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_producto_categoria ON producto (categoria_id);
CREATE INDEX idx_producto_activo_nombre ON producto (activo, nombre);
