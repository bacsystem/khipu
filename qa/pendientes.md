# Pendientes de certificación · no bloquean

Todo lo que las auditorías dejaron abierto a propósito, en un solo lugar. Cada línea: qué, dónde, arreglo. El issue de GitHub, cuando existe, es solo el enlace.

## Emisión (certificada `37e1a75`) · issue #117

| # | Qué | Dónde | Arreglo |
|---|---|---|---|
| 1 | Esc descarta el formulario sin confirmar (el clic fuera sí está protegido) | `nuevo-comprobante-dialog.tsx` | interceptar `onOpenChange` y confirmar si hay datos |
| 2 | Sin timeout en `apiRequest`: un POST que nunca responde deja «Emitiendo…» | `lib/api/browser.ts` | `AbortSignal.timeout(…)` → rama de error que ya avisa |
| 3 | Etiqueta «Precio unit. (con IGV)» también en exonerado/inafecto/gratuito; pie «IGV (18 %) S/ 0.00» en factura 100 % exonerada | `nuevo-comprobante-form.tsx` | etiqueta según afectación |
| 4 | «siguiente N.º» obsoleto entre pestañas del mismo tenant (el backend asigna bien) | diálogo | «último emitido: N» |
| 5 | `calcularTotales` devuelve 0.00 en silencio para 17/40 (hoy inalcanzable desde el diálogo) | `totales.ts` | fallar o marcar «no soportado», con test |
| 6 | Enter sobre un campo incompleto no reacciona (antes al menos salía la burbuja nativa) | `formularios.ts` | `reportValidity()` o foco al botón |
| 7 | Botones ± del stepper no alcanzables por Tab (`tabindex=-1`, default de base-ui) | design system `stepper-numerico.tsx` | decidir: exponer o documentar |
| — | Verificación manual pendiente: popup nativo del `<select>` Moneda en macOS (Espacio, flechas, Enter → cambia moneda y **no** emite). No automatizable bajo CDP | — | 30 s a mano |

## Emisión · backend · issue #115

- Idempotencia en `POST /v1/facturas`: un reintento tras corte de red no debe emitir dos facturas (clave de idempotencia por tenant). Hoy el portal avisa «pudo haberse emitido»; el backend no protege.

## Notas (recert #10: 0 bloqueantes · 2 importantes, corregido; recert #11 pendiente)

| Qué | Nota |
|---|---|
| El tope de la NC **parcial** anticipa 3286, no 3503 por tributo (la NC por importe sí, desde A8) | el backend lo rechaza dentro de la transacción, sin gastar correlativo |
| Descuento de monto fijo en parcial acredita por encima de lo proporcional | simétrico al cargo; decisión de producto, el texto de ayuda lo dice |
| «Cancelar» sin confirmar | `<Link>` directo |
| Motivo 03 «corrección por error en la descripción» obliga a mover cantidades | cae en parcial; SUNAT no define importe 0 para el 03 |
| Errores por campo del 422 descartados (`res.errores`) | transversal, ver [validaciones.md](validaciones.md) |
| Mock sin dos guardas de la nota total (anticipos; descuento/cargos globales sin ítems) ni 2642/2644 por motivo | inalcanzables desde el formulario |
| Base del 2955 del mock aproximada (/1.18 gravadas, /1.04 IVAP) | `[POSIBLE]` con cantidad subcentavo en IVAP con cargo % |
| `importeLineaNota` se desvía ≤ 0.02 del dominio en parciales con ISC | 20 000 líneas aleatorias: 91 % exacto, máx. 0.02; exacto con la cantidad facturada |
| `afectacionPredominante` elige 10 en una factura mixta: el descuento acredita IGV sobre todo el importe aunque parte sea exonerada | SUNAT lo acepta dentro del 3503; riesgo fiscal del emisor, no rechazo. Partir la nota por afectación sería otra iteración |
| `Acreditado` suma también las NC de motivo 10 al acumulado | política deliberada (#83), más estricta que SUNAT |
| Mock: 3503 sin el límite de gratuitas; descuento de línea no restado del total; 3111 solo en gravadas onerosas (la fila del 9996 es inalcanzable con IGV ≥ 10.5 %) | sin fixture con gratuitas ni descuento de línea; inalcanzables hoy |
| Backend: el 3503 compara `gravado` sin mirar el tributo (NC 12 con líneas 17 sobre factura no IVAP pasaría) | solo por API; el formulario no ofrece el 12 sin IVAP |
| 2885: SUNAT lo exime en el motivo 10 (NC) y en el 03 (ND); khipu lo aplica siempre | más estricto, inalcanzable desde el portal (el formulario manda siempre la fecha de hoy) |
| Boletas fuera de alcance | issue #20 |

## Bajas (auditoría #1: 2 bloqueantes · 7 importantes, corregido; recert #1 pendiente)

| Qué | Nota |
|---|---|
| `DarDeBajaService.continuar` corre sin lock de la baja: un `GET /v1/bajas/{id}` de un integrador y el outbox a la vez podrían enviar dos veces el mismo RA | `[POSIBLE]`, no alcanzable desde el portal |
| `getStatus` 99 con `content` que no es ZIP (SUNAT devuelve texto en algunos errores) | `[POSIBLE]`; con el arreglo del worker ya no se agota, se reintenta |
| El outbox ordena por `d.fecha_emision NULLS LAST` y una fila `BAJA` (cuyo `agregado_id` no es un documento) queda siempre al final | con ≥50 envíos pendientes la consulta del ticket se posterga |
| El CDR de la baja se rehidrata con `observaciones = List.of()` | se pierden tras un reinicio |
| El ZIP del RA lleva solo el XML; el manual describe además una carpeta dummy | `[POSIBLE]`, igual que en emisión (certificada); SUNAT lo acepta |
| 2957 se mide contra la fecha de generación, SUNAT contra la de recepción | el diálogo avisa cuando es el último día del plazo |
| La ficha no muestra `intentos` salvo cuando no hay CDR | cosmético |
## Registro + onboarding (auditoría #1: 1 bloqueante · 12 importantes, corregido; recert #1 pendiente)

| Qué | Nota |
|---|---|
| No existe endpoint para editar la razón social ni el entorno de una empresa | por eso la validación al crear es la única defensa; un cambio posterior exige intervención manual |
| Un RUC tecleado por error queda tomado en toda la plataforma | denegación de registro, no impersonación: emitir exige que el OU del certificado traiga ese RUC |
| El correo no se verifica y no hay límite de empresas por cuenta | — |
| El wizard no tiene «Volver» | un tipeo solo se corrige antes de enviar el paso |
| `POST /api/auth/registro` con cuerpo no-JSON da 500 en vez de 400 | mismo patrón que el resto de los route handlers |
| El mock revienta con cuerpo vacío en `POST /v1/empresas` | `[POSIBLE]` en cuanto al disparador |
## Login / sesión (aud. #1, ficha [login.md](login.md))

| Qué | Dónde | Arreglo |
|---|---|---|
| Enumeración de cuentas por tiempo: 324 ms con bcrypt cuando el correo existe vs ~1–5 ms cuando no | backend, autenticación | hash dummy en la rama de correo inexistente |
| Sin rate limiting en login, recuperar ni restablecer | backend | trabajo propio; decide si va antes de abrir el autoservicio |
| Logout no revoca el refresh cuando el access ya venció | `api/auth/logout` | revocar por refresh |
| Paridad del mock: no verifica firma ni `exp` del JWT, falta el handler de `/v1/auth/restablecer`, y el correo se compara respetando mayúsculas | `src/mocks/handlers.ts` | tres huecos independientes |

## Empresa: fiscales, certificado, SOL (aud. #1, ficha [empresa.md](empresa.md))

| Qué | Dónde | Arreglo |
|---|---|---|
| Paridad del mock (4, el del padrón ya corregido): acepta cualquier «certificado» sin parsearlo, acepta SOL en blanco, solo valida el ubigeo, y deja registrar dos empresas con el mismo RUC | `src/mocks/handlers.ts` | |
| Cobertura: OU↔RUC y SOL en blanco sin test; `empresa.spec.ts` no toca el formulario de certificado ni el de SOL; el test de cifrado solo cubre `sol_clave_enc` | tests | |
| Menores: cookie `factura_empresa` sin comprobar pertenencia (el backend corta con 403, no hay fuga); fault SOAP de credenciales clasificado como transitorio y sin señal en pantalla; `cert_vigencia_hasta` nullable se muestra «Sin certificado»; RUC sin dígito verificador en cliente; cita equivocada de la regla 3034. *(El 401 de `CREDENCIALES_INVALIDAS` y las claves de mensaje faltantes: corregidos en #162.)* | varios | |

## Series (aud. #1, ficha [series.md](series.md))

| Qué | Dónde | Arreglo |
|---|---|---|
| Una serie asignada a un anexo lo bloquea para siempre: el error pide reasignarlas, pero no existe endpoint para reasignar, desactivar ni borrar una serie | backend | falta el endpoint; también deja estados «Inactiva» inalcanzables en el portal |
| Establecimientos sin estado de carga ni de error: el combo ofrece solo «0000 · Domicilio fiscal» sin aviso, y como la serie no se puede editar, todos sus comprobantes saldrían con el domicilio fiscal (regla 3030) | `nueva-serie-form.tsx` | el patrón de catálogos de notas (#120) |
| Paridad del mock: no replica `serieValida` (una `BQQ1` queda ofrecida como serie de factura en emisión) ni la unicidad `(tipo, codigo)` | `src/mocks/handlers.ts` | |
| Cobertura: no existe `e2e/series.spec.ts`; las cuatro anotaciones de `SerieRequest` no están atadas por ningún test REST | tests | |
| Menores: el alta no da señal si la fila cae en la página 2 (buscador deshabilitado); filtro «Inactivas» inalcanzable; series `BC##` legítimas invisibles en notas; orden distinto entre mock y backend; `codigo` sin normalizar en el dominio | varios | |

## Establecimientos (aud. #1, ficha [establecimientos.md](establecimientos.md))

**Dos bloqueantes abiertos**, ver la ficha: tres distritos válidos del catálogo 13 imposibles de registrar (el dominio rechaza lo que él mismo derivó; SUNAT dice truncar a 30, no rechazar), y el PDF que cambia si se edita el domicilio del anexo.

| Qué | Dónde | Arreglo |
|---|---|---|
| La baja de un anexo no tiene vuelta atrás: re-registrarlo responde 201 con `activo:false` y el portal no ofrece ni editar ni reactivar | `AdministrarTenantService`, `establecimientos-table.tsx` | endpoint de reactivación, o rechazar el alta sobre uno de baja |
| El `AddressTypeCode` del domicilio fiscal es texto libre: con un código de anexo ahí, **todas** las series en `0000` emiten con ese código. Si no está declarado en el RUC, SUNAT devuelve 3239 en cada comprobante | `datos-fiscales-form.tsx`, `Domicilio.java` | quitarlo del formulario o validarlo contra los anexos |
| Un código de anexo mal tipeado deja la serie inservible sin forma de corregirlo (3239 con el correlativo gastado) | varios | ligado al hueco de series |
| **[POSIBLE]** Lost update: el `PUT` lee `activo` fuera de la transacción y sin bloqueo, así que puede deshacer una baja en silencio | `AdministrarTenantService` | usar el `buscarConBloqueo` que ya existe |
| Menores: el formato de 4 dígitos se cita como 3030 cuando es **4242**, y el 3239 no se menciona en ninguna parte; el `PUT` sobre un código inexistente lo **crea** y responde 200; el tope de 100 del nombre sin test; la acción principal del top bar dice «Nuevo comprobante»; foco perdido tras confirmar una baja y sin región viva; espacios Unicode que `isISOControl` no filtra; el mock con 5 ubigeos, ninguno de Tacna | varios | |

## Listado (aud. #1, ficha [listado.md](listado.md))

**Un bloqueante abierto**: al cambiar de empresa el listado sigue mostrando los comprobantes de la anterior, porque la clave de caché no incluye la empresa.

| Qué | Dónde | Arreglo |
|---|---|---|
| El refresco falla en silencio: datos viejos, el icono girando para siempre y ningún aviso. La página que sondea puede quedar congelada mostrando «Enviado» mientras SUNAT ya rechazó | `comprobantes-table.tsx` | leer el estado de error |
| Un `hasta` anterior al `desde` **ensancha** la lista: el cliente borra el campo en silencio. La API devuelve 400 y el portal lo tapa | `lib/api/facturas.ts` | propagar el 400 |
| El número de página llega sin validar: contadores negativos, «todavía no emitiste ningún comprobante» habiendo 14 y sin salida, y 400 contra el backend real → pantalla de error | `(privado)/comprobantes/page.tsx` | el saneado que ya tiene `por_pagina` |
| Los importes del listado son un recálculo: las cinco columnas de totales se escriben y **nunca se leen**. Nada contrasta lo que se muestra con lo que se firmó | `JdbcComprobanteRepository` | leer los totales persistidos |
| Cobertura del dinero: cambiar la columna de importe por la base gravada deja 150 tests y 49 e2e en verde. Tampoco hay test del color ni de la etiqueta del estado, ni de la paginación, ni del orden | tests | |
| Paridad del mock: acepta seis clases de parámetro que el backend rechaza con 400, y no verifica la pertenencia de la empresa | `src/mocks/handlers.ts` | |
| Menores: «IGV S/ 0.00» en un IVAP que sí lleva impuesto; tres estados del filtro inalcanzables; botón de PDF deshabilitado que es código muerto; el conteo sin el JOIN del listado; notas mezcladas con facturas sin poder separarlas; baja en curso invisible; orden sin desempate | varios | |

## Detalle y descargas (aud. #1, ficha [detalle.md](detalle.md))

**Tres bloqueantes abiertos**: «Reenviar» sin ningún manejo de error, el PDF sin las leyendas del catálogo 52 que declaró el emisor, y el PDF armado con los datos fiscales de hoy en vez de los firmados.

| Qué | Dónde | Arreglo |
|---|---|---|
| «Copiar» de la vista previa entrega un XML reindentado: la firma no valida y SUNAT responde 2336. El panel se titula «XML firmado», así que invita a guardarlo | `lib/xml.ts`, `vista-previa.tsx` | copiar el original |
| Las descargas son anclas sin manejo de error: se termina viendo un JSON crudo, y el ZIP sin constancia **navega la pestaña actual** y pierde la ficha | `[id]/page.tsx` | pedir por fetch y avisar |
| El PDF de un rechazado o anulado se imprime como válido, sin ninguna marca | `comun.ftl` | marca de estado |
| La versión del PDF quedó en 1 y la plantilla cambió cuatro veces: la próxima corrección de importes no llegará a los ya cacheados | `ConsultarComprobanteService` | test que ate el bump |
| Paridad del mock: los tres endpoints de descarga no validan nada (ni empresa, ni id, ni estado), y los fixtures no felices omiten enlaces que el backend siempre manda | `src/mocks/*` | |
| Menores: `colSpan` de 9 en una tabla de 10; el monto en letras revienta con importes de diez cifras que SUNAT admite, y el golpe cae en la **emisión** con un 500; falta la página de no encontrado; «Total a pagar» sin la percepción y otro número más abajo que sí la lleva; un estado terminal de fallo en caja gris; dos posibles sin confirmar (el IGV del QR en IVAP, una lectura sin guarda de nulo) | varios | |

## Transversal

- El portal descarta los errores por campo del 422 (`errores`) y muestra solo `mensaje` — 1 PR; ver [validaciones.md](validaciones.md).
