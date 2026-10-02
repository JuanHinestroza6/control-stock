CREATE TABLE devolucion (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    detalle_venta_id BIGINT       NOT NULL,
    cantidad         INT          NOT NULL,
    motivo           VARCHAR(255) NOT NULL,
    apta_para_venta  BOOLEAN      NOT NULL,
    fecha            DATETIME(6)  NOT NULL,
    usuario_id       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_devolucion_detalle_venta FOREIGN KEY (detalle_venta_id) REFERENCES detalle_venta (id) ON DELETE RESTRICT,
    CONSTRAINT fk_devolucion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT ck_devolucion_cantidad CHECK (cantidad > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_devolucion_detalle_venta ON devolucion (detalle_venta_id);
CREATE INDEX idx_devolucion_fecha ON devolucion (fecha);
CREATE INDEX idx_devolucion_usuario ON devolucion (usuario_id);
