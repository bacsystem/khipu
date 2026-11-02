# Migraciones de Flyway

Cada archivo `V<n>__<descripcion>.sql` se aplica una sola vez, en orden de versión. Flyway guarda la suma de verificación de cada
uno: un archivo ya aplicado no se edita (ni sus comentarios); lo que cambie va en una migración nueva.

## Versiones reservadas: no usar

- **V67** queda reservada y **no debe usarse nunca**. La pila de ramas que agregó la liquidación de compra (V62), la retención y la
  percepción (V68) y las guías de remisión (V69) se reconcilió sin una V67. Una vez aplicadas V68 o V69 en una base, Flyway rechaza
  una V67 nueva por estar fuera de orden (`Detected resolved migration not applied to database`), así que la siguiente migración es
  la que sigue a la más alta existente (V70 en adelante). No se renumeran V68 ni V69: ya las referencian PRs abiertas y bases que las
  aplicaron.
