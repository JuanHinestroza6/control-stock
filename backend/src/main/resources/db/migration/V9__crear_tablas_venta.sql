CREATE TABLE venta (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    fecha            DATETIME(6)   NOT NULL,
    usuario_id       BIGINT        NOT NULL,
    canal            VARCHAR(20)   NULL,
    total            DECIMAL(14,2) NOT NULL,
    estado           VARCHAR(12)   NOT NULL,
    anulada_en       DATETIME(6)   NULL,
    anulada_por      BIGINT        NULL,
    motivo_anulacion VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_venta_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT fk_venta_anulada_por FOREIGN KEY (anulada_por) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT ck_venta_total CHECK (total >= 0),
    CONSTRAINT ck_venta_estado CHECK (estado IN ('REGISTRADA', 'ANULADA')),
    CONSTRAINT ck_venta_anulacion CHECK ((estado = 'ANULADA') = (anulada_en IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_venta_fecha ON venta (fecha);
CREATE INDEX idx_venta_estado_fecha ON venta (estado, fecha);
CREATE INDEX idx_venta_usuario ON venta (usuario_id);
CREATE INDEX idx_venta_anulada_por ON venta (anulada_por);

CREATE TABLE detalle_venta (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    venta_id        BIGINT        NOT NULL,
    variante_id     BIGINT        NOT NULL,
    cantidad        INT           NOT NULL,
    precio_lista    DECIMAL(12,2) NOT NULL,
    precio_unitario DECIMAL(12,2) NOT NULL,
    costo_unitario  DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_detalle_venta_venta FOREIGN KEY (venta_id) REFERENCES venta (id) ON DELETE RESTRICT,
    CONSTRAINT fk_detalle_venta_variante FOREIGN KEY (variante_id) REFERENCES variante (id) ON DELETE RESTRICT,
    CONSTRAINT uk_detalle_venta_variante UNIQUE (venta_id, variante_id),
    CONSTRAINT ck_detalle_venta_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_venta_precio_lista CHECK (precio_lista > 0),
    CONSTRAINT ck_detalle_venta_precio_unitario CHECK (precio_unitario > 0 AND precio_unitario <= precio_lista),
    CONSTRAINT ck_detalle_venta_costo CHECK (costo_unitario >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_detalle_venta_variante ON detalle_venta (variante_id);
