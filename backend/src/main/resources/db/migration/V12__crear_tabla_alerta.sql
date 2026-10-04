CREATE TABLE alerta (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    variante_id     BIGINT      NOT NULL,
    tipo            VARCHAR(15) NOT NULL,
    estado          VARCHAR(10) NOT NULL,
    creada_en       DATETIME(6) NOT NULL,
    revisada_en     DATETIME(6) NOT NULL,
    atendida_en     DATETIME(6) NULL,
    atendida_por    BIGINT      NULL,
    -- Columna generada, no se mapea en JPA. Solo las alertas ACTIVAS la llenan: el UNIQUE impide dos activas del
    -- mismo tipo para una variante y permite varias ATENDIDAS (NULL no choca en un indice unico).
    variante_activa BIGINT AS (CASE WHEN estado = 'ACTIVA' THEN variante_id END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_alerta_variante FOREIGN KEY (variante_id) REFERENCES variante (id) ON DELETE RESTRICT,
    CONSTRAINT fk_alerta_atendida_por FOREIGN KEY (atendida_por) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT ck_alerta_tipo CHECK (tipo IN ('STOCK_BAJO', 'AGOTADO', 'SIN_ROTACION')),
    CONSTRAINT ck_alerta_estado CHECK (estado IN ('ACTIVA', 'ATENDIDA')),
    CONSTRAINT ck_alerta_atendida CHECK ((estado = 'ATENDIDA') = (atendida_en IS NOT NULL)),
    CONSTRAINT uk_alerta_activa UNIQUE (variante_activa, tipo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_alerta_estado_tipo ON alerta (estado, tipo, creada_en);
CREATE INDEX idx_alerta_variante ON alerta (variante_id);
CREATE INDEX idx_alerta_atendida_por ON alerta (atendida_por);
