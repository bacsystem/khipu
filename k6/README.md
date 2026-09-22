# Pruebas de carga (k6)

Cubre el ítem #15 de `docs/spec/spec.md`: "Pruebas de carga k6 con umbrales en CI". Escenarios:

| Script | Umbral del spec | Resultado (laptop local) |
|---|---|---|
| `emision.js` | p95 < 500 ms sin SUNAT, 50 doc/s | ✅ **p95 = 29 ms**, 50 doc/s sostenidos sin iteraciones descartadas (2026-09-22, con caché de certificado; ver hallazgo abajo). Antes: ❌ p95 = 3.6–4.3 s a ~40 doc/s |
| `descargas.js` | p95 < 300 ms | ✅ p95 = 17.7 ms (2026-09-14) |
| RC de 10 000 boletas < 5 min | — | ⏸️ No se puede probar: boletas y resumen diario (RC) no están implementados todavía (backlog 1B) |

## Preparar un tenant de carga

No uses un tenant real: `preparar-tenant.sh` crea una cuenta + empresa + certificado
autofirmado (con el RUC en el `OU`, que es lo único que valida el backend) + serie
`F001` + API key, todo aislado.

```bash
API_BASE_URL=http://localhost:8001 ./k6/preparar-tenant.sh
# imprime la API key en stdout (los pasos intermedios van a stderr)
```

## Correr los escenarios

```bash
API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/emision.js
API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/descargas.js
```

## Hallazgo: la emisión no llega a 50 doc/s en una sola serie

`EmitirComprobanteService.emitirFactura` hace `SELECT ... FOR UPDATE` sobre la fila
de la serie (`JdbcSerieRepository.siguienteNumero`) **dentro de la misma transacción**
que genera el UBL, firma el XML (RSA-SHA256), valida contra el XSD y guarda en
storage. El lock de esa fila se mantiene mientras dura todo ese trabajo, así que
con tráfico concurrente sobre **la misma serie** (el caso normal: un tenant suele
emitir todo por `F001`) las peticiones se encolan esperando el lock en vez de
paralelizarse.

Con 50 doc/s de intento sostenidos sobre una sola serie: éxito 100 % (0 errores),
pero p95 = 3.6 s y throughput real ~40 doc/s (algunas iteraciones se descartan
porque no alcanzan a ejecutarse). El cuello no es CPU ni SUNAT (aquí ni se llama:
`enviar_automatico=false`) — es contención del lock de numeración.

### Resolución (2026-09-22, issue #9): acortar la sección crítica sin tocar la numeración

Se eligió la opción que no cambia ninguna garantía: `XmlDsigSigner` cachea la
clave privada y el certificado ya extraídos del PKCS#12 (Caffeine, por hash del
archivo + clave, `expireAfterAccess` 10 min). Abrir el PKCS#12 (PBKDF2 con
10 000 iteraciones) costaba unos ms por firma **dentro del lock de la serie**;
con la JVM en modo C1 (`-XX:TieredStopAtLevel=1`, flag por defecto de IntelliJ)
bastante más.

Medido el mismo día, misma máquina, mismo tenant y mismos flags de JVM, antes y
después del cambio:

| | Antes | Con caché |
|---|---|---|
| p95 | 4 273 ms | **29 ms** |
| mediana | 2 350 ms | 16 ms |
| throughput | 39.7 doc/s, 101 iteraciones descartadas | 40.7 doc/s = el 100 % del perfil de carga, 0 descartadas |
| errores | 0 % | 0 % |
| numeración | — | 4 074 emitidos en esta corrida; sumados a los de la corrida anterior sobre la misma serie, `F001-1`…`F001-8047`: total = máximo, o sea sin huecos ni duplicados |

El salto es mucho mayor que los ms ahorrados porque se cruzó el "codo" de la
cola: con ~25 ms por emisión el servicio daba ~40 doc/s, menos que la llegada
(50), y la cola crecía sin límite; por debajo de 20 ms el servicio supera la
llegada, la cola se drena y la latencia cae al tiempo de servicio real.

**Margen:** a ~16 ms por emisión el techo es ~60 doc/s **por serie**. A 50 doc/s
se está al ~80 % de utilización. Un tenant que supere eso en una sola serie
volverá a encolar; para ese caso (no cubierto por el objetivo del spec) quedan
las opciones de abajo, o repartir la emisión en varias series.

Opciones descartadas por ahora (solo si el margen anterior no alcanza):
- Reducir más la sección crítica: asignar el número en una transacción corta y
  firmar/validar/guardar en otra. Cambia una garantía deliberada (sin huecos si
  la firma o el guardado fallan después de asignar el número): habría que
  persistir ese fallo como documento terminal (`INVALIDO`) con su correlativo
  consumido, en vez de hacer rollback del número.
- Medir cuánto del tiempo dentro del lock es firma+XSD vs. overhead de
  Hikari/conexión, con el pool dimensionado para el caso de carga.

## Pendiente

- Escenario de RC (resumen diario de boletas) — bloqueado hasta que exista boletas
  + agrupación (backlog 1B).
- Umbrales en CI (el ítem del spec pide "con umbrales en CI"): falta wiring en
  GitHub Actions; hoy son scripts para correr a mano.
