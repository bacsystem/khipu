# Diagramas de arquitectura

Fuente de cada diagrama: `.mmd` (Mermaid). Renderizados: `.svg` (vectorial) y `.png` (2000 px).
Especificación completa: [`../superpowers/specs/README.md`](../superpowers/specs/README.md).

Para regenerar tras editar un `.mmd`:

```bash
mmdc -i docs/architecture/<nombre>.mmd -o docs/architecture/<nombre>.svg -b white
mmdc -i docs/architecture/<nombre>.mmd -o docs/architecture/<nombre>.png -b white -w 2000
```

| # | Diagrama | Tipo | Archivo |
|---|---|---|---|
| 01 | Contexto del sistema | C4 nivel 1 | [01-contexto-c4](01-contexto-c4.svg) |
| 02 | Componentes (hexagonal) | Flowchart | [02-componentes-hexagonal](02-componentes-hexagonal.svg) |
| 03 | Modelo de dominio | Clases | [03-modelo-dominio-clases](03-modelo-dominio-clases.svg) |
| 04 | Ciclo de vida de un documento | Máquina de estados | [04-maquina-estados](04-maquina-estados.svg) |
| 05 | Modelo de datos | MER | [05-modelo-datos-mer](05-modelo-datos-mer.svg) |
| 06 | Factura — envío síncrono `sendBill` | Secuencia | [06-seq-factura-sincrona](06-seq-factura-sincrona.svg) |
| 07 | Boleta → Resumen Diario → ticket | Secuencia | [07-seq-boleta-resumen-diario](07-seq-boleta-resumen-diario.svg) |
| 08 | Comunicación de Baja (RA) | Secuencia | [08-seq-comunicacion-baja](08-seq-comunicacion-baja.svg) |
| 09 | Guía de Remisión — REST + OAuth2 | Secuencia | [09-seq-guia-remision-rest](09-seq-guia-remision-rest.svg) |
| 10 | Reintento vía outbox | Secuencia | [10-seq-reintento-outbox](10-seq-reintento-outbox.svg) |
| 11 | Despliegue | Flowchart | [11-despliegue](11-despliegue.svg) |
