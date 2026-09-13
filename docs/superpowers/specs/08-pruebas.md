# 08 · Estrategia de pruebas

**Fecha:** 2026-09-13 · **Estado:** aprobado · Índice: [README](README.md)

---

## 1. Estrategia de pruebas

| Nivel | Qué | Cómo |
|---|---|---|
| Unitarias `domain` | Reglas, totales, máquina de estados, series | JUnit 5, sin Spring; property-based para totales |
| Unitarias `application` | Casos de uso con puertos fake | Mockito / fakes en memoria |
| `out-ubl` | XML generado válido contra XSD oficiales de SUNAT (`docs/sunat/ref`) y comparado con ejemplos de las guías | Snapshot + validación XSD |
| `out-signing` | Firma verificable con `XMLSignature.validate`; hash = DigestValue | JUnit |
| `out-sunat-soap` | Contrato contra WSDL; mocks con WireMock (CDR aceptado, rechazado, SOAPFault, timeout) | WireMock |
| `out-sunat-rest` | Token cache, expiración, 422/401, base64/SHA-256 | WireMock |
| `out-persistence` | Repositorios, unicidad, `SKIP LOCKED`, migraciones | Testcontainers PostgreSQL |
| Integración | `POST /facturas` extremo a extremo con SUNAT simulado; boleta → RC → ticket vía outbox real | SpringBootTest + Testcontainers PostgreSQL + WireMock |
| Homologación | Suite contra **e-beta** con RUC/credenciales `MODDATOS` (facturas, notas, RC, RA) | Perfil `beta`, ejecución manual/CI nocturna |
| Seguridad | Ningún repositorio sin `tenant_id`; XXE; secretos no aparecen en logs ni respuestas | ArchUnit + tests dedicados |
| Arquitectura | Reglas de dependencia entre módulos | ArchUnit |
| Carga / rendimiento | Escenarios k6 en `tests/k6/`: emisión de facturas (p95 < 500 ms sin SUNAT, 50 doc/s por instancia), descargas XML/CDR/PDF (p95 < 300 ms), rate limit (429 al superar 20 req/s), resumen diario de 10 000 boletas (< 5 min). Umbrales (`thresholds`) fallan el job si no se cumplen | k6 contra entorno `test` con SUNAT simulado (WireMock); nocturno en CI y antes de cada release |
| Instalación self-hosted | `docker compose up` desde cero + CLI `init` + emisión de una factura contra SUNAT simulado; actualización de versión N-1 → N | Job de CI con el paquete `dist/` |

