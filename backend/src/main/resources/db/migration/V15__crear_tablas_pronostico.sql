-- Fase 6 (ADR-010): pronostico de demanda mensual por producto y variante, instantaneas del pronostico por producto
-- (para explicar el metodo elegido) y columnas nuevas en analitica_variante y ejecucion_analitica.
CREATE TABLE pronostico (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    producto_id         BIGINT        NOT NULL,
    variante_id         BIGINT        NULL,
    periodo             DATE          NOT NULL,
    cantidad_proyectada DECIMAL(10,2) NOT NULL,
    metodo              VARCHAR(12)   NOT NULL,
    error_wape          DECIMAL(7,4)  NULL,
    calculado_en        DATETIME(6)   NOT NULL,
    ejecucion_id        BIGINT        NOT NULL,
    -- variante_id NULL = total del producto; la clave generada hace unico (producto, variante, periodo) tambien ahi.
    variante_clave      BIGINT AS (COALESCE(variante_id, 0)) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_pronostico_producto FOREIGN KEY (producto_id) REFERENCES producto (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pronostico_variante FOREIGN KEY (variante_id) REFERENCES variante (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pronostico_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion_analitica (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_pronostico_metodo CHECK (metodo IN ('SES', 'HOLT_WINTERS')),
    CONSTRAINT ck_pronostico_cantidad CHECK (cantidad_proyectada >= 0),
    CONSTRAINT ck_pronostico_periodo CHECK (DAY(periodo) = 1),
    CONSTRAINT uq_pronostico_producto_variante_periodo UNIQUE (producto_id, variante_clave, periodo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_pronostico_variante_periodo ON pronostico (variante_id, periodo);
CREATE INDEX idx_pronostico_ejecucion ON pronostico (ejecucion_id);

CREATE TABLE analitica_producto (
    producto_id             BIGINT        NOT NULL,
    ejecucion_id            BIGINT        NOT NULL,
    meses_historia          INT           NOT NULL,
    metodo                  VARCHAR(12)   NOT NULL,
    alfa                    DECIMAL(3,2)  NULL,
    beta                    DECIMAL(3,2)  NULL,
    gamma                   DECIMAL(3,2)  NULL,
    wape_ses                DECIMAL(7,4)  NULL,
    wape_hw                 DECIMAL(7,4)  NULL,
    error_wape              DECIMAL(7,4)  NULL,
    motivo_sin_hw           VARCHAR(20)   NULL,
    unidades_observadas_12m INT           NOT NULL,
    unidades_corregidas_12m DECIMAL(10,2) NOT NULL,
    calculado_en            DATETIME(6)   NOT NULL,
    PRIMARY KEY (producto_id),
    CONSTRAINT fk_analitica_producto_producto FOREIGN KEY (producto_id) REFERENCES producto (id) ON DELETE RESTRICT,
    CONSTRAINT fk_analitica_producto_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion_analitica (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_analitica_producto_metodo CHECK (metodo IN ('SES', 'HOLT_WINTERS', 'SIN_HISTORIA')),
    CONSTRAINT ck_analitica_producto_beta CHECK ((metodo = 'HOLT_WINTERS') = (beta IS NOT NULL)),
    CONSTRAINT ck_analitica_producto_motivo CHECK (motivo_sin_hw IN ('POCA_HISTORIA', 'MES_EN_CERO', 'NIVEL_NO_POSITIVO')),
    CONSTRAINT ck_analitica_producto_motivo_hw CHECK (metodo <> 'HOLT_WINTERS' OR motivo_sin_hw IS NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_analitica_producto_ejecucion ON analitica_producto (ejecucion_id);

CREATE TABLE analitica_producto_mes (
    producto_id         BIGINT        NOT NULL,
    periodo             DATE          NOT NULL,
    ejecucion_id        BIGINT        NOT NULL,
    unidades_observadas INT           NOT NULL,
    disponibilidad      DECIMAL(5,4)  NOT NULL,
    unidades_corregidas DECIMAL(10,2) NOT NULL,
    backtest_ses        DECIMAL(10,2) NULL,
    backtest_hw         DECIMAL(10,2) NULL,
    PRIMARY KEY (producto_id, periodo),
    CONSTRAINT fk_analitica_producto_mes_producto FOREIGN KEY (producto_id) REFERENCES producto (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_analitica_producto_mes_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion_analitica (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_analitica_producto_mes_disponibilidad CHECK (disponibilidad BETWEEN 0 AND 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_analitica_producto_mes_ejecucion ON analitica_producto_mes (ejecucion_id);

ALTER TABLE analitica_variante
    ADD COLUMN fuente_demanda    VARCHAR(10)   NOT NULL DEFAULT 'HISTORICA',
    ADD COLUMN demanda_lead_time DECIMAL(10,2) NULL,
    ADD COLUMN tasa_pronosticada DECIMAL(10,4) NULL,
    ADD CONSTRAINT ck_analitica_variante_fuente CHECK (fuente_demanda IN ('PRONOSTICO', 'HISTORICA'));

ALTER TABLE ejecucion_analitica
    ADD COLUMN productos_pronosticados INT          NULL,
    ADD COLUMN con_holt_winters        INT          NULL,
    ADD COLUMN con_ses                 INT          NULL,
    ADD COLUMN wape_global             DECIMAL(7,4) NULL,
    ADD COLUMN en_alerta_sin_transito  INT          NULL,
    ADD COLUMN con_pedido_en_camino    INT          NULL;
