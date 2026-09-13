# Especificación — API de Facturación Electrónica SUNAT

**Fecha:** 2026-09-13 · **Estado:** aprobado
**Referencias:** [`docs/sunat/notes.md`](../../sunat/notes.md) (documentación oficial SUNAT, fuente de verdad) · [`docs/architecture/`](../../architecture/README.md) (diagramas) · `docs/json/` (colección Postman, solo modelo de forma)

| # | Documento | Contenido |
|---|---|---|
| 00 | [Alcance y decisiones](00-alcance-y-decisiones.md) | Objetivo, alcance por fases, decisiones tomadas, fases de entrega, riesgos |
| 01 | [Requisitos no funcionales](01-requisitos-no-funcionales.md) | Rendimiento, disponibilidad, fiabilidad, escalabilidad, seguridad, cumplimiento, observabilidad, mantenibilidad, portabilidad, datos |
| 02 | [Arquitectura](02-arquitectura.md) | Contexto C4, componentes hexagonales, módulos Gradle, regla de dependencia, trabajo asíncrono |
| 03 | [Dominio y datos](03-dominio-y-datos.md) | Modelo de dominio, máquina de estados, reglas, MER, persistencia |
| 04 | [Flujos con SUNAT](04-flujos-sunat.md) | Secuencias: factura síncrona, boleta → RC, baja RA, guía REST, reintento outbox |
| 05 | [Contrato REST](05-contrato-rest.md) | Sobre de respuesta, códigos HTTP, endpoints, ejemplo de factura |
| 06 | [Seguridad y errores](06-seguridad-y-errores.md) | Autenticación, secretos, aislamiento, mapeo de errores SUNAT → estados |
| 07 | [Despliegue y operación](07-despliegue-y-operacion.md) | Topología, configuración, modo self-hosted, CLI, paquete `dist/`, métricas |
| 08 | [Pruebas](08-pruebas.md) | Niveles de prueba, homologación beta, k6, ArchUnit, instalación self-hosted |

**Orden de lectura sugerido:** 00 → 02 → 03 → 04 → 05; el resto según necesidad.

**Planes de implementación:** `docs/superpowers/plans/` (uno por sub-fase).
