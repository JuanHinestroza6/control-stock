-- Fase 5 (ADR-009): registro de ejecuciones de la analitica, instantanea por variante (ABC + punto de reorden) e
-- indice del kardex para buscar la ultima venta de un producto (SIN_ROTACION).
CREATE TABLE ejecucion_analitica (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    origen               VARCHAR(10)  NOT NULL,
    usuario_id           BIGINT       NULL,
    iniciada_en          DATETIME(6)  NOT NULL,
    terminada_en         DATETIME(6)  NULL,
    estado               VARCHAR(10)  NOT NULL,
    fecha_corte          DATE         NOT NULL,
    variantes_analizadas INT          NULL,
    clase_a              INT          NULL,
    clase_b              INT          NULL,
    clase_c              INT          NULL,
    con_punto_reorden    INT          NULL,
    alertas_evaluadas    INT          NULL,
    alertas_fallidas     INT          NULL,
    variantes_activas    INT          NULL,
    en_alerta_stock      INT          NULL,
    sin_rotacion_activas INT          NULL,
    duracion_ms          BIGINT       NULL,
    mensaje              VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ejecucion_analitica_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ejecucion_analitica_origen CHECK (origen IN ('NOCTURNA', 'MANUAL', 'SEMILLA')),
    CONSTRAINT ck_ejecucion_analitica_estado CHECK (estado IN ('EN_CURSO', 'EXITOSA', 'FALLIDA')),
    CONSTRAINT ck_ejecucion_analitica_terminada CHECK ((estado = 'EN_CURSO') = (terminada_en IS NULL)),
    CONSTRAINT ck_ejecucion_analitica_usuario CHECK ((origen = 'MANUAL') = (usuario_id IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_ejecucion_estado_inicio ON ejecucion_analitica (estado, iniciada_en);
CREATE INDEX idx_ejecucion_usuario ON ejecucion_analitica (usuario_id);

CREATE TABLE analitica_variante (
    variante_id     BIGINT        NOT NULL,
    ejecucion_id    BIGINT        NOT NULL,
    unidades_netas  INT           NOT NULL,
    ingreso_neto    DECIMAL(14,2) NOT NULL,
    participacion   DECIMAL(9,6)  NOT NULL,
    acumulado       DECIMAL(9,6)  NOT NULL,
    posicion        INT           NOT NULL,
    clase_abc       CHAR(1)       NOT NULL,
    dias_historia   INT           NOT NULL,
    demanda_media   DECIMAL(10,4) NOT NULL,
    desviacion      DECIMAL(10,4) NOT NULL,
    lead_time_dias  INT           NOT NULL,
    nivel_servicio  DECIMAL(4,3)  NOT NULL,
    z               DECIMAL(4,2)  NOT NULL,
    stock_seguridad DECIMAL(10,2) NULL,
    punto_reorden   INT           NULL,
    metodo_reorden  VARCHAR(12)   NOT NULL,
    calculado_en    DATETIME(6)   NOT NULL,
    PRIMARY KEY (variante_id),
    CONSTRAINT fk_analitica_variante_variante FOREIGN KEY (variante_id) REFERENCES variante (id) ON DELETE RESTRICT,
    CONSTRAINT fk_analitica_variante_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion_analitica (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_analitica_variante_clase CHECK (clase_abc IN ('A', 'B', 'C')),
    CONSTRAINT ck_analitica_variante_metodo CHECK (metodo_reorden IN ('NORMAL', 'POISSON', 'SIN_DEMANDA', 'SIN_HISTORIA')),
    CONSTRAINT ck_analitica_variante_sin_historia CHECK ((metodo_reorden = 'SIN_HISTORIA') = (punto_reorden IS NULL)),
    CONSTRAINT ck_analitica_variante_ingreso CHECK (ingreso_neto >= 0),
    CONSTRAINT ck_analitica_variante_unidades CHECK (unidades_netas >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_analitica_variante_ejecucion ON analitica_variante (ejecucion_id);
CREATE INDEX idx_analitica_variante_clase ON analitica_variante (clase_abc, posicion);

-- Ultima venta por producto sin recorrer todo el kardex de la variante. Solo indice: la tabla no cambia.
CREATE INDEX idx_movimiento_variante_tipo_fecha ON movimiento (variante_id, tipo, fecha);
