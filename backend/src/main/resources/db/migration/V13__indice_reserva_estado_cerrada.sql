-- Anonimización nocturna de reservas cerradas (Ley 1581, ADR-007): filtra por estado y cerrada_en.
CREATE INDEX idx_reserva_estado_cerrada ON reserva (estado, cerrada_en);
