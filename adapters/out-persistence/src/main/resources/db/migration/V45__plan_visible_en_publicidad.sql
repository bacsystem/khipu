-- H20: si el plan sale en la página de precios. Aparte de `estado`: un plan a medida para un cliente está ACTIVO (se asigna y se cobra) pero no se publica.
-- Todos los planes que ya existían se siguen publicando. Agregar una columna NOT NULL con un DEFAULT constante no reescribe la tabla en Postgres 11+.
ALTER TABLE plan ADD COLUMN visible_en_publicidad BOOLEAN NOT NULL DEFAULT TRUE;
