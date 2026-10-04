CREATE TABLE reserva (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    variante_id        BIGINT       NOT NULL,
    cantidad           INT          NOT NULL,
    cliente_nombre     VARCHAR(120) NOT NULL,
    cliente_contacto   VARCHAR(120) NOT NULL,
    creada_en          DATETIME(6)  NOT NULL,
    vence_en           DATETIME(6)  NOT NULL,
    estado             VARCHAR(12)  NOT NULL,
    venta_id           BIGINT       NULL,
    usuario_id         BIGINT       NOT NULL,
    cerrada_en         DATETIME(6)  NULL,
    cerrada_por        BIGINT       NULL,
    motivo_cancelacion VARCHAR(200) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reserva_variante FOREIGN KEY (variante_id) REFERENCES variante (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reserva_venta FOREIGN KEY (venta_id) REFERENCES venta (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reserva_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reserva_cerrada_por FOREIGN KEY (cerrada_por) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reserva_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_reserva_estado CHECK (estado IN ('ACTIVA', 'CONVERTIDA', 'CANCELADA', 'VENCIDA')),
    CONSTRAINT ck_reserva_vigencia CHECK (vence_en > creada_en),
    CONSTRAINT ck_reserva_venta CHECK ((estado = 'CONVERTIDA') = (venta_id IS NOT NULL)),
    CONSTRAINT ck_reserva_cierre CHECK ((estado = 'ACTIVA') = (cerrada_en IS NULL)),
    CONSTRAINT ck_reserva_motivo CHECK ((estado = 'CANCELADA') = (motivo_cancelacion IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_reserva_estado_vence ON reserva (estado, vence_en);
CREATE INDEX idx_reserva_variante_estado ON reserva (variante_id, estado);
CREATE INDEX idx_reserva_venta ON reserva (venta_id);
CREATE INDEX idx_reserva_usuario ON reserva (usuario_id);
CREATE INDEX idx_reserva_cerrada_por ON reserva (cerrada_por);
