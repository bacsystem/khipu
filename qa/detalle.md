# Flujo 9 · Detalle del comprobante y descargas (XML, PDF, CDR)

Auditoría independiente (contexto limpio, worktree `wt-detalle`, `E2E_PORT=3300`, `API_BASE_URL` a puerto muerto).
20 mutaciones dirigidas, arnés validado con un letal conocido y un no-op. Suites: Vitest 150/150 · `:adapters:out-pdf` `:adapters:out-storage` `:application` `:adapters:in-rest` `:adapters:out-persistence` verdes · `:bootstrap` con los tests de punta a punta y de arquitectura.
**Veredicto de la auditoría #1: NO CERTIFICADO — 3 bloqueantes, 7 importantes.**

Leyenda: 🔧 corregido, a la espera de recert · ✅ verificado por una recert posterior · ⬜ abierto, en [pendientes.md](pendientes.md).

## Bloqueantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | **«Reenviar» no maneja ningún error: botón muerto para siempre y fallo silencioso.** Usa `fetch` directo, sin `try/catch` y sin mirar si la respuesta fue correcta. Con la red cortada el botón queda en «Reenviando…» deshabilitado, sin un solo mensaje. Con un 409, un 422, un 401 o un 500, el botón vuelve a su estado y **la ficha no dice nada**. En un comprobante con error de envío que SUNAT en realidad ya aceptó —caso que la propia API documenta— el usuario reintenta a ciegas y no tiene forma de enterarse. Es el único de los cuatro botones de la ficha que no usa el cliente compartido | `reenviar-button.tsx` | usar `apiRequest` y mostrar el error, como ya hacen los botones de baja y de correo |
| 🔧 | **El PDF no imprime las leyendas del catálogo 52 que declaró el emisor.** Las leyendas no se usan en ninguna plantilla. Las automáticas tienen sustituto visual, pero las que declara el emisor desaparecen del documento impreso: Amazonía (2001–2003), paquete turístico, emisor itinerante, zona comercial de Tacna (2008), primera venta en zona comercial y exportación de servicios. El texto del catálogo **es la frase que va impresa**, así que una factura de Amazonía o de Tacna sale del portal sin la leyenda que sustenta su exoneración. El XML sí las lleva y la ficha del portal las muestra | `adapters/out-pdf/.../comun.ftl` | bloque propio antes del pie, con el texto del catálogo sin el prefijo ni las comillas. Las automáticas no se repiten: ya salen en el cuerpo |
| 🔧 | **El PDF se arma con los datos fiscales de hoy, no con los que se firmaron.** Se genera con el tenant leído en el momento de la descarga y con la asignación actual de la serie a un establecimiento; el XML firmado congeló los de la emisión. Cambiar el domicilio fiscal, editar un anexo o reasignar la serie rompe la correspondencia: una factura de enero cuyo PDF nunca se pidió sale en marzo con la dirección nueva mientras el XML y el CDR llevan la vieja. La clave de caché solo lleva la huella del **diseño**, así que ni es coherente consigo misma: los PDF ya cacheados quedan con los datos viejos y los nuevos salen con los nuevos. Es el mismo defecto que vio la auditoría de establecimientos | `ConsultarComprobanteService`, `EmisorDeSerie.paraImprimir` | congelar los datos del emisor en la emisión, o incluirlos en la clave |

## Importantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| ⬜ | «Copiar» de la vista previa entrega un XML reindentado, así que la firma no valida. El botón recibe el texto ya reformateado, no los bytes descargados. El panel se titula «XML firmado» y muestra el nombre de archivo, así que invita a guardarlo. Reescribir los nodos de texto en blanco cambia la canonicalización y el resumen de la firma deja de cuadrar: SUNAT responde **2336**. El botón de descarga sí baja los bytes buenos | `lib/xml.ts`, `vista-previa.tsx` | copiar el original, no el formateado |
| ⬜ | Las descargas son anclas sin manejo de error: el usuario termina viendo un JSON crudo. «Ver PDF» de un comprobante sin firma abre una pestaña con el sobre de error; «Descargar ZIP» sin CDR **navega la pestaña actual** y se pierde la ficha. El panel de vista previa sí maneja bien su propio error; las descargas no | `[id]/page.tsx`, `vista-previa.tsx` | pedir por fetch y avisar |
| ⬜ | Paridad del mock: los tres endpoints de descarga no validan nada, ni la empresa, ni el id, ni el estado. Un id inexistente da 404 en el detalle pero **200 con contenido** en XML, PDF y CDR; un comprobante sin constancia devuelve el ZIP igual; un formato inválido también. Ningún e2e podría detectar una regresión de aislamiento, de constancia ausente ni de falta de firma | `src/mocks/handlers.ts` | |
| ⬜ | Paridad del mock: los fixtures «no felices» omiten los enlaces de PDF y de CDR, que el backend **siempre** manda (el de CDR en cuanto existe la constancia). Así que ningún e2e ve nunca la ficha de un rechazado o anulado ofreciendo «Ver PDF» ni «Ver CDR», que es lo que hará el backend real | `src/mocks/data.ts` | |
| ⬜ | El PDF de un rechazado, anulado o fuera de plazo se imprime como válido: el enlace viene siempre y la plantilla imprime el encabezado, el número, el QR y el valor resumen **sin ninguna marca** de que SUNAT lo rechazó o de que hay una baja aceptada. El usuario puede entregarle a su cliente la representación impresa de un documento que no existe para SUNAT | `[id]/page.tsx`, `comun.ftl` | marca de agua o leyenda de estado |
| ⬜ | La versión del PDF quedó en 1 y nada la obliga: la plantilla cambió **cuatro veces** después de introducirla sin subirla. Hoy no corrompe datos, pero el contrato documentado está roto y la próxima corrección de importes en el PDF no llegará a los ya cacheados | `ConsultarComprobanteService` | test que ate el bump al cambio de plantilla |
| 🔧 | El aviso antiduplicado del formulario de notas y de emisión había quedado muerto tras hacer que el cliente no lance | `browser.ts` y los cuatro formularios | corregido en el PR de los bloqueantes de sesión |

## Menores

En [pendientes.md](pendientes.md): una fila de tabla con `colSpan` de 9 en una tabla de 10 columnas; el monto en letras revienta con importes de diez cifras o más, que SUNAT admite, y el golpe cae en la **emisión** con un 500 en vez de un 422; falta la página de no encontrado, así que un id inválido cae en el 404 pelado de Next; el número grande rotulado «Total a pagar» no incluye la percepción y más abajo aparece otro número que sí (el PDF lo hace mejor); un estado terminal de fallo sale en una caja gris neutra; y dos posibles sin confirmar, el campo de IGV del QR en un comprobante IVAP y una lectura sin guarda de nulo.

## Mutaciones

15 mueren de 20. Los dos controles se comportan como deben.

Sobreviven 5, y tres son huecos reales: **ignorar el establecimiento de la serie al imprimir**, que es la evidencia del tercer bloqueante y no tiene ningún test; **servir el CDR en lugar del XML firmado**, que solo lo ataja un test de punta a punta con Docker, no los módulos rápidos; y **subir la versión del PDF**, que nada ata. Las otras dos son mutantes equivalentes: un modo de redondeo sobre valores que ya vienen a dos decimales, y un filtro de ruta que otra barrera vuelve a atajar.

## Contraste SUNAT

Fuente: `docs/sunat/ref/reglas_validacion_2026-08-26.xlsx`.

| Hallazgo | Hoja · fila | Contenido |
|---|---|---|
| Leyendas | `Factura2_0` 426 | campo «Leyenda», `an4` del catálogo 52, con la regla 3027 si el código no existe |
| Leyendas de régimen | `Factura2_0` 308–311 | 2001→3283, 2002→3284, 2003→3285, 2008→3289; cada una exige total exonerado mayor a cero. El texto del catálogo es literalmente la frase a consignar |
| Domicilio del emisor | `Factura2_0` 51–59 | la dirección de registro completa, con el ubigeo del catálogo 13 |
| Establecimiento anexo | `Factura2_0` 80 | distinto de `0000` se verifica contra los sistemas de SUNAT |
| Firma alterada | `CódigosRetorno` 475 | **2336**, error en la validación de la firma digital |
| Importe total | `Factura2_0` 41 y 46 | `n(12,2)`: hasta doce enteros, que es lo que el monto en letras no soporta |

**Lo que sí cuadra**: el orden de los campos del código QR coincide con la resolución que lo define, y el valor resumen impreso es el resumen de la firma.

## Lo que sí resiste

- **Aislamiento por tenant: sólido.** El filtro exige que la empresa pertenezca a la cuenta del JWT, y todas las lecturas del flujo filtran por tenant en SQL, con tests que lo verifican y la mutación que abre la fuga muriendo. Cambiar el id en la URL da 404, no fuga. Las claves de almacenamiento empiezan por el identificador del tenant, así que tampoco colisionan entre empresas.
- **El XML que se baja es el firmado.** Se leen los bytes del almacenamiento; **no hay regeneración al vuelo** en ningún camino, y un test de punta a punta afirma sobre la firma y el identificador de lo descargado.
- **El CDR como prueba.** Constancia ausente da 404, una constancia corrupta da 500 y no 422, el formato se valida antes de tocar el almacenamiento, y la extracción del XML del ZIP está anclada. El enlace aparece solo si la constancia existe, y el portal distingue en el tooltip una constancia de un código de error del envío.
- **Ningún estado miente en la ficha.** Los once estados tienen su color, la caja de respuesta distingue la constancia de un intento fallido, el historial llega del backend con estado anterior y resultante en hora de Lima, y lo que la ficha habilita coincide con lo que el backend permite.
- **El QR y el hash del PDF** están anclados: orden de campos, separador final y RUC del emisor mueren como mutaciones, y un test **decodifica el QR desde el PDF renderizado**.
- **Los importes del PDF**: el importe total está anclado, el monto en letras lleva su moneda, y la exportación y el IVAP tienen filas propias.
- **La caché del PDF** incluye la huella del diseño, y un test verifica que cambiar el diseño no sobrescriba los PDF ya emitidos.
- **El proxy** rechaza los segmentos de escape antes de armar la URL, y el refresco por token evita que dos peticiones concurrentes roten la misma sesión.
