CREATE TABLE variante (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    producto_id     BIGINT      NOT NULL,
    sku             VARCHAR(40) NOT NULL,
    talla           VARCHAR(5)  NOT NULL,
    color           VARCHAR(30) NOT NULL,
    stock_fisico    INT         NOT NULL DEFAULT 0,
    stock_reservado INT         NOT NULL DEFAULT 0,
    stock_minimo    INT         NOT NULL DEFAULT 0,
    punto_reorden   INT         NULL,
    clase_abc       CHAR(1)     NULL,
    activo          BOOLEAN     NOT NULL DEFAULT TRUE,
    version         BIGINT      NOT NULL DEFAULT 0,
    creado_en       DATETIME(6) NOT NULL,
    actualizado_en  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_variante_sku UNIQUE (sku),
    CONSTRAINT uk_variante_producto_talla_color UNIQUE (producto_id, talla, color),
    CONSTRAINT fk_variante_producto FOREIGN KEY (producto_id) REFERENCES producto (id) ON DELETE RESTRICT,
    CONSTRAINT ck_variante_talla CHECK (talla IN ('XS', 'S', 'M', 'L', 'XL', 'XXL', 'UNICA')),
    CONSTRAINT ck_variante_clase_abc CHECK (clase_abc IS NULL OR clase_abc IN ('A', 'B', 'C')),
    CONSTRAINT ck_variante_stock_fisico CHECK (stock_fisico >= 0),
    CONSTRAINT ck_variante_stock_reservado CHECK (stock_reservado >= 0),
    CONSTRAINT ck_variante_reservado_lte_fisico CHECK (stock_reservado <= stock_fisico),
    CONSTRAINT ck_variante_stock_minimo CHECK (stock_minimo >= 0),
    CONSTRAINT ck_variante_punto_reorden CHECK (punto_reorden IS NULL OR punto_reorden >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_variante_producto_activo ON variante (producto_id, activo);
