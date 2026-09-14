# Pruebas de carga (k6)

Cubre el ítem #15 de `docs/spec/spec.md`: "Pruebas de carga k6 con umbrales en CI". Escenarios:

| Script | Umbral del spec | Resultado (2026-09-14, laptop local) |
|---|---|---|
| `emision.js` | p95 < 500 ms sin SUNAT, 50 doc/s | ❌ **p95 = 3.6 s** a solo ~40 doc/s reales (ver hallazgo abajo) |
| `descargas.js` | p95 < 300 ms | ✅ p95 = 17.7 ms |
| RC de 10 000 boletas < 5 min | — | ⏸️ No se puede probar: boletas y resumen diario (RC) no están implementados todavía (backlog 1B) |

## Preparar un tenant de carga

No uses un tenant real: `preparar-tenant.sh` crea una cuenta + empresa + certificado
autofirmado (con el RUC en el `OU`, que es lo único que valida el backend) + serie
`F001` + API key, todo aislado.

```bash
API_BASE_URL=http://localhost:8080 ./k6/preparar-tenant.sh
# imprime la API key en stdout (los pasos intermedios van a stderr)
```

## Correr los escenarios

```bash
API_BASE_URL=http://localhost:8080 API_KEY=fk_... k6 run k6/emision.js
API_BASE_URL=http://localhost:8080 API_KEY=fk_... k6 run k6/descargas.js
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

**No lo corregí**: separar "asignar número" de "firmar y guardar" cambia una
garantía deliberada del diseño actual (evitar números correlativos huecos si la
firma o el guardado fallan luego de asignar el número — SUNAT exige correlativos
sin huecos en los aceptados). Es un cambio de diseño con implicancias de
cumplimiento, no algo para tocar sin decidirlo con el resto del equipo.

Opciones a evaluar (fuera de este cambio):
- Reducir la sección crítica: asignar el número en una transacción corta, y
  firmar/validar/guardar en otra, aceptando y manejando el caso donde falle
  después de tener el número (ya existe `ERROR_FORMATO`/reintento para algo
  parecido en outbox).
- Cachear el certificado descifrado por tenant (ítem #10 de `docs/spec/spec.md`)
  para que firmar sea más barato dentro del lock.
- Medir cuánto del tiempo dentro del lock es señal real (firma+XSD) vs. overhead
  de Hikari/conexión, con el pool dimensionado para el caso de carga.

## Pendiente

- Escenario de RC (resumen diario de boletas) — bloqueado hasta que exista boletas
  + agrupación (backlog 1B).
- Umbrales en CI (el ítem del spec pide "con umbrales en CI"): falta wiring en
  GitHub Actions; hoy son scripts para correr a mano.
