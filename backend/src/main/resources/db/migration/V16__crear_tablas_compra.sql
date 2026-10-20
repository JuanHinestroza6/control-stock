-- Fase 6 (ADR-010): plan de compras. Un plan tiene lineas por variante; "en camino" = suma de las lineas de planes
-- APROBADO. No toca movimiento: la recepcion genera ENTRADAS con referencia PLAN_COMPRA/{id}.
CREATE TABLE plan_compra (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    estado             VARCHAR(10)  NOT NULL,
    origen             VARCHAR(10)  NOT NULL,
    observacion        VARCHAR(255) NULL,
    creado_en          DATETIME(6)  NOT NULL,
    creado_por         BIGINT       NOT NULL,
    aprobado_en        DATETIME(6)  NULL,
    aprobado_por       BIGINT       NULL,
    recibido_en        DATETIME(6)  NULL,
    recibido_por       BIGINT       NULL,
    cancelado_en       DATETIME(6)  NULL,
    cancelado_por      BIGINT       NULL,
    motivo_cancelacion VARCHAR(200) NULL,
    ejecucion_id       BIGINT       NULL,
    version            INT          NOT NULL,
    actualizado_en     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_plan_compra_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_compra_aprobado_por FOREIGN KEY (aprobado_por) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_compra_recibido_por FOREIGN KEY (recibido_por) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_compra_cancelado_por FOREIGN KEY (cancelado_por) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_compra_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion_analitica (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_plan_compra_estado CHECK (estado IN ('BORRADOR', 'APROBADO', 'RECIBIDO', 'CANCELADO')),
    CONSTRAINT ck_plan_compra_origen CHECK (origen IN ('SUGERIDO', 'MANUAL')),
    CONSTRAINT ck_plan_compra_recibido CHECK ((estado = 'RECIBIDO') = (recibido_en IS NOT NULL)),
    CONSTRAINT ck_plan_compra_cancelado CHECK ((estado = 'CANCELADO')
        = (cancelado_en IS NOT NULL AND motivo_cancelacion IS NOT NULL)),
    CONSTRAINT ck_plan_compra_aprobado CHECK (estado NOT IN ('APROBADO', 'RECIBIDO') OR aprobado_en IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_plan_compra_estado_creado ON plan_compra (estado, creado_en);
CREATE INDEX idx_plan_compra_creado_por ON plan_compra (creado_por);
CREATE INDEX idx_plan_compra_aprobado_por ON plan_compra (aprobado_por);
CREATE INDEX idx_plan_compra_recibido_por ON plan_compra (recibido_por);
CREATE INDEX idx_plan_compra_cancelado_por ON plan_compra (cancelado_por);
CREATE INDEX idx_plan_compra_ejecucion ON plan_compra (ejecucion_id);

CREATE TABLE detalle_plan (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    plan_id           BIGINT        NOT NULL,
    variante_id       BIGINT        NOT NULL,
    cantidad_sugerida INT           NULL,
    cantidad_aprobada INT           NOT NULL,
    costo_estimado    DECIMAL(12,2) NOT NULL,
    justificacion     VARCHAR(255)  NULL,
    cantidad_recibida INT           NULL,
    costo_recibido    DECIMAL(12,2) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_detalle_plan_plan FOREIGN KEY (plan_id) REFERENCES plan_compra (id) ON DELETE RESTRICT,
    CONSTRAINT fk_detalle_plan_variante FOREIGN KEY (variante_id) REFERENCES variante (id) ON DELETE RESTRICT,
    CONSTRAINT ck_detalle_plan_aprobada CHECK (cantidad_aprobada BETWEEN 1 AND 100000),
    CONSTRAINT ck_detalle_plan_sugerida CHECK (cantidad_sugerida IS NULL OR cantidad_sugerida >= 1),
    CONSTRAINT ck_detalle_plan_costo CHECK (costo_estimado >= 0),
    CONSTRAINT ck_detalle_plan_recibida CHECK (cantidad_recibida IS NULL OR cantidad_recibida BETWEEN 0 AND 100000),
    CONSTRAINT ck_detalle_plan_costo_recibido CHECK (costo_recibido IS NULL OR costo_recibido >= 0),
    CONSTRAINT ck_detalle_plan_recepcion CHECK ((cantidad_recibida IS NULL) = (costo_recibido IS NULL)),
    CONSTRAINT uq_detalle_plan_plan_variante UNIQUE (plan_id, variante_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Consulta de "en camino" por variante.
CREATE INDEX idx_detalle_plan_variante_plan ON detalle_plan (variante_id, plan_id);
