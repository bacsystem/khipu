# Flujo 6 · Establecimientos (anexos)

Auditoría independiente (contexto limpio, worktree `wt-establecimientos`, `E2E_PORT=3280`, `API_BASE_URL` a puerto muerto) sobre la rama de los bloqueantes de sesión/certificado/correlativo.
20 mutaciones dirigidas, arnés validado con un letal conocido y un no-op. Suites: Vitest 150/150 · `:domain` `:application` `:adapters:in-rest` `:adapters:out-ubl` `:adapters:out-persistence` `:bootstrap` verdes.
**Veredicto de la auditoría #1: NO CERTIFICADO — 2 bloqueantes, 5 importantes.**

Leyenda: 🔧 corregido, a la espera de recert · ✅ verificado por una recert posterior · ⬜ abierto, en [pendientes.md](pendientes.md).

## Bloqueantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | **Tres distritos válidos del catálogo 13 no se pueden registrar, ni como anexo ni como domicilio fiscal.** El dominio deriva el distrito del catálogo y lo pasa por un tope de 30 caracteres, así que rechaza el valor que él mismo puso: «CORONEL GREGORIO ALBARRACIN LANCHIPA» (Tacna, unos 120 000 habitantes, 36 caracteres), «SAN FRANCISCO DE ASIS DE YARUSYACAN» (35) y «ANDRES AVELINO CACERES DORREGARAY» (33). El selector del portal ofrece los 1892 distritos reales, el usuario elige uno de esos tres y recibe 422 por un campo que nunca escribió y no puede corregir. Queda sin punto de emisión | `domain/tenant/Domicilio.java` | lo que **deriva el catálogo** se recorta a 30; lo que **escribe el emisor** se sigue rechazando, que es su entrada. Las reglas 4096–4098 son OBSERV, no ERROR |
| ⬜ | **El PDF de un comprobante ya emitido cambia si se edita el domicilio del anexo: la impresa contradice al XML firmado.** La impresión lee la fila **actual** del establecimiento, y la clave de caché del PDF no incluye el domicilio. Reproducido: factura firmada con una dirección, `PUT` del anexo con otra, primera descarga del PDF → sale la dirección nueva mientras el XML y el CDR llevan la vieja. Si el PDF se había pedido antes de la edición queda cacheado y sí coincide, así que el resultado depende de cuándo se descargó. Aplica igual al domicilio fiscal `0000`: es sistémico | `ConsultarComprobanteService`, `EmisorDeSerie.paraImprimir` | congelar los datos del emisor en la emisión, o incluirlos en la clave de caché. El comentario del servicio afirma hoy lo contrario («los datos impresos no cambian nunca») |

## Importantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| ⬜ | La baja de un anexo no tiene vuelta atrás y el re-registro miente: un `POST`/`PUT` sobre un anexo dado de baja hereda `activo` del existente, así que lo actualiza pero lo deja de baja, y responde **201 CREATED con `activo:false`**. El portal muestra `—` en las acciones de esas filas: ni editar ni reactivar, y no hay endpoint de reactivación en ninguna capa. El diálogo se cierra sin error, o sea éxito para el usuario | `AdministrarTenantService`, `establecimientos-table.tsx` | endpoint de reactivación, o rechazar el alta sobre uno dado de baja |
| ⬜ | El `AddressTypeCode` del domicilio fiscal es texto libre y nada lo cruza con los anexos: poniendo `0002` ahí, **todas** las series en `0000` emiten con ese código y la dirección fiscal. El objeto de respuesta se contradice consigo mismo (`codigo:"0000"` con `domicilio.codigo_establecimiento:"0002"`). Si el código no está declarado en el RUC, SUNAT devuelve **ERROR 3239** en cada comprobante | `datos-fiscales-form.tsx`, `Domicilio.java`, `EstablecimientoResponse` | quitar el campo del formulario del domicilio fiscal, o validarlo contra los anexos |
| ⬜ | Un código de anexo mal tipeado deja la serie inservible y no hay forma de corregirlo: el primer envío devuelve 3239 con el correlativo ya consumido, el código del anexo es inmutable y no existe endpoint para reasignar el establecimiento de una serie | varios | ligado al hueco de series |
| ⬜ | Un anexo que alguna vez tuvo una serie no se puede dar de baja nunca: el 409 pide «reasígnelas antes de darlo de baja» y no existe forma de reasignar, desactivar ni borrar una serie. Ya registrado en [series.md](series.md) | `AdministrarTenantService` | falta el endpoint |
| ⬜ | **[POSIBLE]** Lost update: el `PUT` lee `activo` fuera de la transacción y sin bloqueo, mientras la baja sí toma `SELECT … FOR UPDATE`. Si el `PUT` lee `activo=true`, la baja commitea y luego el `PUT` hace su upsert, el anexo vuelve a quedar activo en silencio y admite series nuevas. El mecanismo está a la vista y el puerto ya ofrece el `buscarConBloqueo` que este camino no usa | `AdministrarTenantService` | usar el bloqueo que ya existe |

## Menores

En [pendientes.md](pendientes.md): las citas de regla equivocadas (el formato de 4 dígitos es **4242**, no 3030; el 3239 no se menciona en ninguna parte), el `PUT` sobre un código inexistente que lo **crea** y responde 200 en vez de 404, el tope de 100 del nombre sin test, la acción principal del top bar que en esta página dice «Nuevo comprobante» en vez del alta, el foco perdido tras confirmar una baja y sin región viva que la anuncie, los espacios Unicode que `isISOControl` no filtra, y el mock con 5 ubigeos (ninguno de Tacna, así que ningún e2e puede ejercitar el primer bloqueante).

## Mutaciones

12 mueren de 19 reales. El letal de control muere y el no-op sobrevive.

Sobreviven 7, y dos de ellas son la evidencia de los bloqueantes: **imprimir siempre el domicilio fiscal** (justo donde vive el segundo) y **editar reactiva el anexo** (el primero de los importantes). Las otras: el tope de 4 dígitos sombreado por una segunda validación que tira el mismo código, el nombre hasta 1000, emitir con un anexo inexistente, el intercambio de los códigos 4096/4097 en los mensajes, y el principal marcado como inactivo.

## Contraste SUNAT

Fuente: `docs/sunat/ref/reglas_validacion_2026-08-26.xlsx`, hoja `Factura2_0`.

**Coincide exactamente**, y está pineado tag por tag en los tests de UBL: fila 80 (**3030**, `AddressTypeCode` obligatorio, sale siempre incluso sin domicilio configurado), filas 85 y 86 (4251/4252, los atributos del catálogo), fila 54 (**4093**, ubigeo del catálogo 13 con sus 1892 entradas), fila 51 (4094, dirección 3–200 en una línea), 52 (4095, urbanización ≤25), 53 (4096, provincia), 57 (4097, departamento), 58 (4098, distrito). El mapeo provincia/departamento/distrito es correcto, que es fácil de cruzar y no está cruzado.

**Divergen**: el formato de 4 dígitos se reporta como 3030 cuando la hoja lo tipifica **4242** (fila 84), y el 3030 es el tag ausente. El distrito se **rechaza** a los 31 caracteres cuando la fila 58 es OBSERV `an..30` y el propio catálogo trae nombres de 33 a 36.

**No implementado**: fila 82, **3239** ERROR «El código de local anexo consignado no se encuentra declarado en el RUC». khipu no lo valida ni lo advierte.

## Lo que sí resiste

- **El XML del anexo.** El `AddressTypeCode` nunca falta y no puede discrepar de la dirección del anexo: el dominio reescribe el domicilio con su propio código, y esa mutación muere. Hay escenario de homologación beta dedicado.
- **Auto-escapado XML**: las cuatro plantillas declaran `output_format="XML"`, así que una dirección con `&` o `<` no rompe el XSD.
- **La serialización entre dar de baja un anexo y crear una serie es real, no aspiracional**: las dos toman `SELECT … FOR UPDATE` sobre la misma fila, y un test con dos transacciones concurrentes comprueba que no se solapen.
- **No hay hueco de correlativo por un anexo inválido**: la resolución del emisor corre dentro de la transacción de emisión, después de asignar el número pero con rollback.
- El combo de «Nueva serie» excluye los anexos dados de baja, verificado por e2e.
- **Aislamiento por empresa** en todos los `SELECT`, con los estados HTTP correctos y el `0000` protegido en alta, edición y baja.
- Los comprobantes ya emitidos **conservan** el anexo dado de baja al imprimirse, que es lo correcto. El problema es la edición, no la baja.
