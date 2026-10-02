CREATE TABLE movimiento (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    variante_id       BIGINT        NOT NULL,
    tipo              VARCHAR(20)   NOT NULL,
    cantidad          INT           NOT NULL,
    fisico_antes      INT           NOT NULL,
    fisico_despues    INT           NOT NULL,
    reservado_antes   INT           NOT NULL,
    reservado_despues INT           NOT NULL,
    costo_unitario    DECIMAL(12,2) NULL,
    usuario_id        BIGINT        NULL,
    fecha             DATETIME(6)   NOT NULL,
    referencia_tipo   VARCHAR(20)   NULL,
    referencia_id     BIGINT        NULL,
    observacion       VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_movimiento_variante FOREIGN KEY (variante_id) REFERENCES variante (id) ON DELETE RESTRICT,
    CONSTRAINT fk_movimiento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT ck_movimiento_tipo CHECK (tipo IN ('ENTRADA', 'VENTA', 'VENTA_RESERVA', 'DEVOLUCION',
        'AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO', 'RESERVA', 'LIBERACION', 'ANULACION_VENTA')),
    CONSTRAINT ck_movimiento_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_movimiento_saldos CHECK (fisico_antes >= 0 AND fisico_despues >= 0 AND reservado_antes >= 0
        AND reservado_despues >= 0),
    CONSTRAINT ck_movimiento_reservado_lte_fisico CHECK (reservado_despues <= fisico_despues),
    CONSTRAINT ck_movimiento_costo_entrada CHECK ((tipo = 'ENTRADA') = (costo_unitario IS NOT NULL)),
    CONSTRAINT ck_movimiento_costo_no_negativo CHECK (costo_unitario IS NULL OR costo_unitario >= 0),
    CONSTRAINT ck_movimiento_referencia CHECK ((referencia_tipo IS NULL) = (referencia_id IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_movimiento_variante_fecha ON movimiento (variante_id, fecha, id);
CREATE INDEX idx_movimiento_fecha ON movimiento (fecha);
CREATE INDEX idx_movimiento_tipo_fecha ON movimiento (tipo, fecha);
CREATE INDEX idx_movimiento_referencia ON movimiento (referencia_tipo, referencia_id);
