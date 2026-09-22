-- Celular de contacto en Perú, exigido al registrarse; nulo en las cuentas creadas antes de este campo.
ALTER TABLE cuenta ADD COLUMN telefono VARCHAR(9);
