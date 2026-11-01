# Pruebas de carga (k6)

Cubre el ítem #15 de `docs/spec/spec.md`: "Pruebas de carga k6 con umbrales en CI" (ya no corren en cada PR, ver «Fuera de CI»). Escenarios:

| Script | Umbral del spec | Resultado (laptop local) |
|---|---|---|
| `emision.js` | p95 < 500 ms sin SUNAT, 50 doc/s | ✅ **p95 = 29 ms**, 50 doc/s sostenidos sin iteraciones descartadas (2026-09-22, con caché de certificado; ver hallazgo abajo). Antes: ❌ p95 = 3.6–4.3 s a ~40 doc/s |
| `descargas.js` | p95 < 300 ms | ✅ p95 = 31 ms, 20 descargas/s durante un minuto (2026-10-18) |
| `boletas.js` | p95 < 500 ms sin SUNAT, 50 doc/s (#287) | ✅ **p95 = 20.5 ms**, 4 074 boletas (mitad con DNI, mitad «sin documento»), 0 errores, serie B001 sin huecos ni duplicados (2026-10-18) |
| `emision.js` (facturas) | idem | ✅ **p95 = 24.5 ms**, 4 074 facturas, 0 errores, serie F001 sin huecos (2026-10-18) |
| Baja de boleta en el resumen diario (#287) | meta acordada: p95 ≤ 100 ms por resumen, ≥ 50/s | ✅ `RendimientoDeBajasDeBoletaTest`: generar el SummaryDocuments + firmar + validar contra el XSD cuesta **p50 4 ms · p95 6 ms · 237 resúmenes/s** en un hilo; 10 000 bajas, unos 45 s de CPU |
| RC de 10 000 boletas < 5 min | < 5 min (#301) | ✅ `RendimientoDeBajasDeBoletaTest`: 10 000 boletas de una misma fecha son **20 resúmenes de 500 líneas**; generar + firmar + validar contra el XSD los 20 toma **~1,2 s** en un hilo (p50 58 ms por resumen), sin red. La base de datos de un resumen de 500 líneas (recoger las hermanas con su bloqueo, marcarlas ENVIADO, y luego aplicarles el CDR una por una) cuesta, con Postgres real y un hilo, **~1,5 s al informar y ~3 s al resolver** (`JdbcResumenDiarioPorLotesTest`); ×20 resúmenes, ~1,5 min de base para las 10 000. SUNAT es lo único que falta sumar y no lo controla khipu. Una **anulación** (`BAJA`) sigue siendo un resumen de una línea (fila de arriba); lo que se agrupa son las **altas** de boletas que pasaron el envío individual |

## Preparar un tenant de carga

No uses un tenant real: `preparar-tenant.sh` da de alta una empresa aislada con la clave de la plataforma (`PLATFORM_ADMIN_KEY`), le guarda credenciales SOL de prueba, le sube un
certificado autofirmado (con el RUC en el `OU`, que es lo único que valida el backend), crea las series que pidas y devuelve su API key. No pasa por el registro de cuentas: está
cerrado por defecto (`REGISTRO_ABIERTO`) y pide teléfono y correo verificado, y una carga no tiene por qué pasar por el portal.

```bash
API_BASE_URL=http://localhost:8001 PLATFORM_ADMIN_KEY=... SERIES="01:F001 03:B001" ./k6/preparar-tenant.sh
# imprime la API key en stdout (los pasos intermedios van a stderr); SERIES por defecto es solo 01:F001
```

En Windows con Git Bash, `openssl` necesita `MSYS_NO_PATHCONV=1` y un `TMPDIR` con ruta de Windows; en Linux no hace falta nada.

## Correr los escenarios

```bash
API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/emision.js
API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/boletas.js      # necesita la serie B001 (tipo 03)
API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/descargas.js
```

Sin k6 instalado: `docker run --rm -i -e API_BASE_URL=http://host.docker.internal:8001 -e API_KEY=fk_... grafana/k6:0.54.0 run - < k6/boletas.js`.

La fecha de emisión de los scripts es la de **Lima** (UTC-5): con la fecha UTC, entre las 19:00 y las 24:00 de Lima la API respondía `422 FECHA_INVALIDA` (fecha futura) y la siembra de
`descargas.js` fallaba.

## Fuera de CI

Las pruebas de carga **no corren solas en GitHub Actions**: se lanzan a mano, con los comandos de arriba, contra un backend local o un entorno de pruebas, o desde
Actions → «Carga (k6)» → *Run workflow* (`.github/workflows/k6.yml`, solo `workflow_dispatch`: levanta Postgres y el backend, corre los tres escenarios y la comprobación de
numeración, con p95 < 1 500 ms porque el runner es más lento). Un runner compartido mide mucho más lento que una laptop (allí ~25 ms de p95) y con ruido de una corrida
a otra, así que como check de cada PR daba poca señal.

Qué mirar después de una corrida:

| Escenario | Umbral |
|---|---|
| `descargas.js` | p95 < 300 ms, errores < 1 % |
| `emision.js` (facturas) y `boletas.js` | todos los `check` (201 del tipo y estado pedidos) > 99 %, errores < 1 %, menos de 20 iteraciones descartadas (o sea, se sostienen los 50 doc/s) y p95 < 500 ms (`UMBRAL_P95_MS` lo cambia) |
| Numeración después de la carga | por serie: documentos = número más alto = números distintos |

Para la numeración, contra la base del backend que recibió la carga:

```sql
SELECT serie, count(*), max(numero), count(DISTINCT numero) FROM documento GROUP BY serie ORDER BY serie;
```

Las tres columnas tienen que coincidir en cada serie; si no, hay huecos o duplicados.

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

### Garantías que se verifican con pruebas (issue #9)

La decisión de arriba (acortar la sección crítica, no cambiar la numeración) mantiene el bloqueo de la serie durante todo el trabajo, y eso es lo que da la numeración sin
huecos. Esas garantías ya no dependen de leer el código:

- `JdbcSerieRepositoryTest.concurrenciaNoDuplicaNumeros`: ocho hilos sobre una serie, sin duplicados.
- `NumeracionDeLaEmisionE2ETest` (pila real, Postgres y HTTP): **16 emisiones a la vez** sobre `F001` devuelven exactamente los números 1 a 16 y la serie queda en 16; y si guardar el
  XML falla **después** de asignar el número, la serie no avanza, no queda un documento a medias y la siguiente emisión recibe ese mismo número.
- `emision.js` pasa el umbral (p95 29 ms a 50 doc/s; tabla de arriba).

### El listado ya no hace una consulta por comprobante

El comentario de la revisión de #71 pedía resolver junto con el cuello de la emisión que `JdbcComprobanteRepository.mapear` hiciera 5 consultas **por comprobante** (cargos, ítems,
anticipos, documentos relacionados y cuotas), y que la tabla del portal pagara 1 + 5·N. Las consultas de varias filas (`listar`, `notasDe`, `pendientesQueVencenEmitidosHasta`,
`pendientesDeCdr`, `firmadosEmitidosEntre`) ahora leen primero los ids, después los hijos de todos juntos con `IN (...)` de a 500 y por último las filas. `JdbcComprobanteConsultasTest`
lo mide contando las sentencias que llegan a la base: una página de **30 comprobantes pasaba de 136 sentencias a 7**, el mismo número que con 3, y cada comprobante vuelve igual que con
`buscar`. Las lecturas de una sola fila (`buscar`, `bloquear`) no cambian.

## Panel de inicio con 100 000 comprobantes (#24)

El issue pide que el panel cargue en menos de 1 s con datos reales. Medido el 2026-10-20 en un Windows 11 de desarrollo, con otros procesos compartiendo la CPU (al medir, 10–20 %
de carga; horas antes estaba al 100 % con otras corridas): Postgres 16 en Docker (contenedor desechable), el backend con `bootRun` y el portal compilado (`next build` + `next start`)
contra ese backend, sin mocks. Una empresa con **100 000 comprobantes** en los últimos 300 días (la siembra del test de volumen de `JdbcResumenDeComprobantesRepositoryTest`, por SQL),
6 679 de ellos en el mes. Cinco lecturas después de una de calentamiento, con `curl -w %{time_total}`:

| Lectura | Tiempos (s) |
|---|---|
| `GET /v1/facturas/resumen` del mes | 0.071 0.050 0.047 0.059 0.048 |
| `GET /v1/facturas/resumen` sin rango (historial completo) | 0.111 0.112 0.095 0.098 0.093 |
| `GET /v1/facturas/resumen/por-dia` del mes | 0.045 0.029 0.041 0.039 0.029 |
| `GET /v1/facturas/resumen/por-dia` de 62 días (el máximo) | 0.063 0.061 0.052 0.056 0.070 |
| Portal `/inicio` completo (layout + sus lecturas en paralelo, HTML del servidor) | 0.089 0.079 0.080 0.075 0.072 (la primera, en frío: 0.241) |
| Portal `/comprobantes`, de referencia | 0.116 0.081 0.072 0.092 0.073 |

En un Chromium (Playwright), `/inicio` hasta el evento `load`: 182, 122, 105, 123 y 106 ms (TTFB 73–138 ms). Muy por debajo del segundo. En `next dev` con la máquina al 100 % el
mismo `/inicio` (con mocks) tardaba ~3 s, igual que cualquier otra página: es el costo del modo desarrollo, no del panel.

## Pendiente

- Resumen diario con muchas **altas en un mismo envío**: hecho en #301 (hasta 500 líneas por fecha de emisión; medido en la tabla de arriba). Las **bajas** siguen yendo una por resumen a propósito: se piden de a una y una línea con error rechaza todo el resumen (2987).
- Medir el envío a SUNAT de las bajas: no se carga contra e-beta (es un servicio ajeno); su latencia no la controla khipu.
