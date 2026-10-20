/**
 * Generado por `npm run generate:api` (o `generate:types`) desde openapi.json: no se edita a mano.
 * No lo importa ningún componente: lo usa `contrato-openapi.ts` para que `tsc` avise si la API cambia el contrato (#5, ver `contrato.ts`).
 */

export interface paths {
    "/v1/productos/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Ver un producto
         * @description `404 NO_ENCONTRADO` si no es de la empresa.
         */
        get: operations["obtener"];
        /**
         * Cambiar un producto
         * @description Reemplaza sus datos. `409 DUPLICADO` si el nuevo código interno ya es de otro producto.
         */
        put: operations["actualizar"];
        post?: never;
        /**
         * Eliminar un producto
         * @description Solo lo quita de la lista guardada: los comprobantes ya emitidos conservan sus datos.
         */
        delete: operations["eliminar_1"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/personalizacion-pdf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Ver el diseño del PDF */
        get: operations["ver_1"];
        /**
         * Guardar el diseño del PDF
         * @description Reemplaza plantilla, color, pie y observaciones por defecto (envíe vacío o `null` para borrar un texto). El logo no cambia: use `PUT/DELETE /v1/empresa/logo`. `422 PERSONALIZACION_INVALIDA` si algún valor no cumple el formato.
         */
        put: operations["guardar"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/logo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar el logo actual
         * @description `404 NO_ENCONTRADO` si la empresa no cargó ninguno.
         */
        get: operations["logo"];
        /**
         * Cargar el logo
         * @description PNG o JPEG de hasta 200 KB (`multipart/form-data`, campo `archivo`); se imprime en la cabecera del PDF escalado a 55×18 mm. Reemplaza el anterior. `422 LOGO_INVALIDO` si no es PNG/JPEG o pesa más.
         */
        put: operations["cargarLogo"];
        post?: never;
        /** Quitar el logo */
        delete: operations["borrarLogo"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/establecimientos/{codigo}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Editar un establecimiento anexo
         * @description Reemplaza nombre y domicilio; conserva el estado. El `0000` se edita en `PUT /v1/empresa/datos-fiscales`.
         */
        put: operations["editarEstablecimiento"];
        post?: never;
        /**
         * Dar de baja un establecimiento anexo
         * @description Baja lógica: deja de admitir series nuevas y emisiones; los comprobantes ya emitidos conservan su domicilio.
         *     `409 ESTABLECIMIENTO_EN_USO` si tiene series activas; `404 NO_ENCONTRADO`; el `0000` no se puede dar de baja.
         */
        delete: operations["darDeBajaEstablecimiento"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/envio-al-cliente": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Ver la preferencia de envío al cliente
         * @description Si la empresa envía automáticamente el comprobante aceptado a su cliente, y si el servidor tiene correo configurado para hacerlo.
         */
        get: operations["ver_2"];
        /**
         * Activar o desactivar el envío al cliente
         * @description Vale para los comprobantes que SUNAT acepte desde ahora; lo ya aceptado no se envía. Activarlo sin correo configurado en el servidor responde
         *     `503 CORREO_NO_CONFIGURADO` (los clientes no recibirían nada); desactivarlo siempre se puede, y corta también lo que esperaba salir: esos envíos
         *     quedan `OMITIDO` («la empresa desactivó el envío automático»). Tampoco sale un comprobante con una baja en curso.
         */
        put: operations["fijar"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/datos-fiscales": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Guardar domicilio fiscal, cuenta de detracciones y nombre comercial
         * @description Domicilio fiscal del emisor (ubigeo del catálogo 13, dirección, urbanización, establecimiento anexo) que khipu escribe en
         *     `cac:RegistrationAddress` de cada XML, la cuenta de detracciones del Banco de la Nación que se usa cuando una factura
         *     sujeta a detracción no indica la suya, y el nombre comercial (`cac:PartyName`). Reemplaza los tres valores: envíe `null`
         *     en el que quiera borrar. Errores: `422 DOMICILIO_INVALIDO` (mensaje con la regla SUNAT: 4093 ubigeo, 4094 dirección,
         *     3030 establecimiento), `422 CUENTA_DETRACCIONES_INVALIDA` o `422 NOMBRE_COMERCIAL_INVALIDO` (4092). `padron_tasa_especial_igv`
         *     activa la tasa reducida del IGV (padrón de restaurantes y hoteles) para los comprobantes que se emitan desde entonces.
         */
        put: operations["datosFiscales"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/credenciales-sol": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Guardar las credenciales SOL
         * @description Usuario secundario SOL (sin el RUC; khipu antepone `RUC+usuario` al enviar) y su clave. Obligatorias antes de enviar comprobantes a SUNAT.
         */
        put: operations["credencialesSol"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/credenciales-api-sunat": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Guardar las credenciales API SUNAT
         * @description El «Id» (`client_id`) y la «Clave» (`client_secret`) de la aplicación que la empresa registra en SOL, opción
         *     «Credenciales de API SUNAT». Con ellas khipu consulta la validez de comprobantes por la API REST de SUNAT (también en beta)
         *     en vez de `billValidService`, que pide la clave SOL y solo existe en producción. Reemplazan las anteriores; se guardan
         *     cifradas y nunca vuelven por la API. `422 VALIDACION` si falta alguna o está en blanco; `422 CREDENCIALES_API_SUNAT_INVALIDAS`
         *     si el `client_id` trae espacios o caracteres que no son de un identificador. No se comprueban contra SUNAT al guardarlas: la
         *     primera consulta lo hace.
         */
        put: operations["credencialesApiSunat"];
        post?: never;
        /**
         * Borrar las credenciales API SUNAT
         * @description La validez vuelve a consultarse solo con la clave SOL. Idempotente.
         */
        delete: operations["borrarCredencialesApiSunat"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/usuarios/{id}/rol": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Cambiar el rol de un usuario
         * @description Vale desde la siguiente petición del usuario, sin que vuelva a entrar. Quitarle el rol de administrador revoca las invitaciones pendientes que creó. `409 ULTIMO_ADMINISTRADOR` si le quitaría el rol al único `ADMIN` activo (también a uno
         *     mismo).
         */
        put: operations["cambiarRolDeUsuario"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/clientes/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Ver un cliente
         * @description `404 NO_ENCONTRADO` si no es de la empresa.
         */
        get: operations["obtener_3"];
        /**
         * Cambiar un cliente
         * @description Reemplaza sus datos (también puede cambiar el documento). `409 DUPLICADO` si el nuevo documento ya es de otro cliente.
         */
        put: operations["actualizar_1"];
        post?: never;
        /**
         * Eliminar un cliente
         * @description Solo lo quita de la lista guardada: los comprobantes ya emitidos conservan sus datos.
         */
        delete: operations["eliminar_2"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/clientes/documento/{tipoDoc}/{numDoc}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Guardar o actualizar un cliente por su documento
         * @description Crea el cliente (201) o, si ya existe, actualiza sus datos (200). Es idempotente: repetirlo no duplica nada. Al actualizar, la dirección, el país y el correo que no se envían se conservan. Es lo que usa el guardado automático al emitir.
         */
        put: operations["guardarPorDocumento"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/planes/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Editar un plan
         * @description Reemplaza nombre, precio y límites. **El nombre y el precio cambian al instante; los límites, no**: si difieren de los vigentes quedan programados para el
         *     inicio del ciclo siguiente (`limites_programados`), así que subir un tope a mitad de mes no regala documentos del mes corriente ni bajarlo le corta a
         *     nadie. Un segundo cambio antes de que llegue reemplaza al primero, y poner otra vez los límites vigentes cancela el programado. No toca a las cuentas
         *     que ya tienen el plan. Sin ningún cambio no escribe ni deja registro. `404 NO_ENCONTRADO`; `409 NOMBRE_DUPLICADO`. Queda en la bitácora, con qué
         *     cambió y de qué a qué.
         */
        put: operations["editar"];
        post?: never;
        /**
         * Borrar un plan
         * @description Solo un plan que nadie usó nunca. Con alguna cuenta que lo tiene —o que lo tuvo— responde `409 PLAN_EN_USO`: hay que **desactivarlo**. El plan con el
         *     que nacen las cuentas nuevas no se borra (`409 PLAN_POR_DEFECTO`). `404 NO_ENCONTRADO`. Queda en la bitácora.
         */
        delete: operations["eliminar_3"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/configuracion/plantillas/{tipo}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Editar el texto de un correo
         * @description Reemplaza el asunto y el cuerpo de un correo; vale desde el siguiente. `422 PLANTILLA_INVALIDA` si el asunto o el cuerpo están vacíos o pasan de 150 / 5000 caracteres, el asunto
         *     tiene más de una línea, usa una variable que ese correo no tiene, o el cuerpo no incluye una variable indispensable (el `{enlace}` de los correos de acceso: sin él nadie
         *     podría entrar). `404 NO_ENCONTRADO` si el correo no existe. Queda en la bitácora (sin el texto).
         */
        put: operations["guardarPlantilla"];
        post?: never;
        /**
         * Restaurar el texto de fábrica de un correo
         * @description Quita el texto propio y el correo vuelve al de fábrica. Sin texto propio no hace nada. `404 NO_ENCONTRADO` si el correo no existe. Queda en la bitácora.
         */
        delete: operations["restaurarPlantilla"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/configuracion/correo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Remitente de los correos
         * @description De quién salen los correos de la plataforma: el remitente vigente, si lo fijó un administrador (`personalizado`) o es el de la configuración del servidor, y ese predeterminado,
         *     al que se vuelve al restablecer. Solo lectura.
         */
        get: operations["remitente"];
        /**
         * Cambiar el remitente de los correos
         * @description Fija el correo remitente, el nombre con que se muestra y, si se quiere, adónde llegan las respuestas. Vale desde el siguiente correo, sin reiniciar. `422 REMITENTE_INVALIDO` si
         *     el correo no es una sola dirección válida en ASCII o el nombre lleva saltos de línea, comillas o < >. **El servidor SMTP tiene que aceptar mandar desde esa dirección**
         *     (remitente verificado, SPF/DKIM): eso no se puede comprobar acá. Queda en la bitácora con el antes y el después.
         */
        put: operations["cambiarRemitente"];
        post?: never;
        /**
         * Volver al remitente del servidor
         * @description Quita el remitente fijado y vuelve al de la configuración del servidor. Sin remitente propio no hace nada. Queda en la bitácora.
         */
        delete: operations["restablecerRemitente"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/configuracion/banner": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aviso de mantenimiento publicado
         * @description El aviso que ven todos los clientes en su portal: su texto, su vigencia y si se está mostrando ahora (`vigente_ahora`; puede estar programado para después o haber vencido).
         *     `datos` es nulo si no hay ninguno. Solo lectura.
         */
        get: operations["banner"];
        /**
         * Publicar el aviso de mantenimiento
         * @description Publica el aviso, o reemplaza el anterior (hay uno solo). Siempre tiene fin: `hasta` es obligatorio, posterior a `desde` y a ahora, y a lo sumo 90 días después de empezar.
         *     `422 BANNER_INVALIDO` si el texto está vacío, pasa de 300 caracteres o tiene más de una línea, o la vigencia no cumple eso. Los clientes lo ven mientras `desde ≤ ahora < hasta`.
         *     Queda en la bitácora con su texto y vigencia.
         */
        put: operations["publicarBanner"];
        post?: never;
        /**
         * Retirar el aviso de mantenimiento
         * @description Lo retira antes de que venza. `404 NO_ENCONTRADO` si no hay ninguno. Queda en la bitácora.
         */
        delete: operations["retirarBanner"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/webhooks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar webhooks
         * @description Los endpoints de la empresa, sin el secreto.
         */
        get: operations["listar"];
        put?: never;
        /**
         * Registrar un webhook
         * @description Crea un endpoint. **El secreto de firma se muestra una sola vez** en esta respuesta. La URL debe ser https y apuntar a un servidor público: una dirección interna
         *     responde `422 WEBHOOK_INVALIDO`. Con 5 endpoints ya creados responde `409 WEBHOOK_LIMITE`. Requiere sesión del portal.
         */
        post: operations["crear"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/webhooks/{id}/secreto": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Rotar el secreto de firma
         * @description Genera un secreto nuevo; el anterior deja de valer al instante. **Se muestra una sola vez.** Requiere sesión del portal.
         */
        post: operations["rotarSecreto"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/webhooks/{id}/prueba": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Enviar un evento de prueba
         * @description Encola un evento `webhook.prueba` firmado. Devuelve el id de la entrega, que se ve en el log. `409 WEBHOOK_PAUSADO` si el endpoint está apagado. Requiere sesión del portal.
         */
        post: operations["probar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/webhooks/entregas/{entregaId}/reenviar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reenviar una entrega
         * @description Vuelve a mandar una entrega ya cerrada (exitosa o fallida) con el mismo cuerpo. `409 ENTREGA_NO_REENVIABLE` si sigue pendiente y `409 WEBHOOK_PAUSADO` si el endpoint está apagado. Requiere sesión del portal.
         */
        post: operations["reenviar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sire/rvie/conciliacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Conciliar el registro de ventas con la propuesta de SUNAT (experimental)
         * @description **EXPERIMENTAL: formato reconstruido, no validado con el PVSIRE.** Es el paso obligado antes de reemplazar una propuesta con el archivo de `GET /v1/sire/rvie`, que
         *     **cubre solo lo emitido en khipu**: `solo_en_sunat` debe estar vacío, o el reemplazo sacaría esas ventas del registro.
         *
         *     Compare lo que khipu emitió con la propuesta del RVIE que descarga del SIRE: envíe el archivo tal como lo descargó, `.txt` o `.zip` con un `.txt`, como cuerpo
         *     de la petición (no multipart), hasta 20 MB (64 MB descomprimido). La respuesta dice qué comprobantes están solo en SUNAT, solo en khipu, y cuáles están en los
         *     dos con algún dato distinto (fecha, moneda, estado, base gravada, IGV, exonerado, inafecto o total), comparados por tipo, serie y número. No guarda nada.
         *
         *     La propuesta se lee con la misma estructura de 40 campos que el archivo de `GET /v1/sire/rvie` (una fila de títulos al principio se ignora; una fila con el campo 10, un rango
         *     consolidado, no se concilia y se cuenta en `filas_consolidadas_no_conciliadas`): **supuesto sin verificar contra el SIRE**. Errores: `400 PARAMETRO_INVALIDO` (periodo, o la propuesta enviada como formulario), `422 RVIE_ARCHIVO_INVALIDO` (cuerpo vacío o sin filas de datos, ZIP dañado, una línea de más de 16 KB, una fila que no se puede leer, de otro RUC o de otro
         *     periodo, o un comprobante repetido; el mensaje dice la línea y el campo), `422 RVIE_PROPUESTA_DEMASIADO_GRANDE` y `422 RVIE_EXCEDE_EL_TOPE` (más de 100 000 filas).
         */
        post: operations["conciliarRvie"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/series": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar series
         * @description Series de la empresa con su tipo, último número asignado y si acepta emisiones.
         */
        get: operations["series"];
        put?: never;
        /**
         * Crear una serie
         * @description Registra una serie de numeración. El prefijo indica el tipo: `F###` factura, `B###` boleta, `FC##`/`BC##` nota de
         *     crédito, `FD##`/`BD##` nota de débito, `T###` guía de remisión remitente (tipo `09`), `V###` guía de remisión transportista (tipo `31`) (3 alfanuméricos tras el prefijo). `correlativo_inicial` es el último
         *     número ya usado en otro sistema (0 si es nueva): khipu emitirá desde el siguiente. `409 DUPLICADO` si ya existe.
         */
        post: operations["crearSerie"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/retenciones": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Emitir un comprobante de retención
         * @description El agente de retención paga al `proveedor` (siempre con RUC) los `documentos` que este le emitió y le retiene el IGV a la tasa del `regimen` (catálogo 23: `01` 3 %, el vigente;
         *     `02` 6 %, solo para comprobantes emitidos hasta el 28/02/2014). Por documento: retenido = pago × tipo de cambio × tasa, y neto pagado = pago × tipo de cambio − retenido. Todos
         *     los pagos son del mes del comprobante (2625). SUNAT lo recibe hasta 7 días calendario desde el día siguiente a su emisión (2600).
         *
         *     **Errores**: `422 VALIDACION`, `422 AGENTE_NO_DESIGNADO` (2617/4285), `422 RECEPTOR_INVALIDO` (contraparte), `422 RETENCION_INVALIDA` o `422 PERCEPCION_INVALIDA` (régimen y tasa,
         *     documentos, pagos, fechas, tipo de cambio; con el código de la regla de SUNAT), `422 FECHA_INVALIDA` (futura o pasados los 7 días, 2600),
         *     `422 SERIE_INVALIDA`, `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`, `429 LIMITE_PLAN`, `402 PLAN_VENCIDO`. Admite `Idempotency-Key` como `POST /v1/facturas`, con claves propias de las retenciones.
         */
        post: operations["retener"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/retenciones/calcular": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Calcular un comprobante de retención sin emitirlo
         * @description Recibe el mismo cuerpo que `POST /v1/retenciones` y responde lo retenido y el neto de cada documento y los totales **sin numerar, firmar, guardar ni enviar nada**, con las
         *     mismas reglas de dominio (y la misma exigencia de la designación): lo que la emisión rechazaría responde aquí el mismo `422`. El `id` de la respuesta no existe y no trae enlaces.
         */
        post: operations["calcularRetencion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/productos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Buscar productos
         * @description Los productos de la empresa por descripción. `q` (opcional) se compara con el código interno (empieza por) y con la descripción (contiene, sin distinguir mayúsculas ni tildes).
         *     El total va en `X-Total-Count`.
         */
        get: operations["listar_1"];
        put?: never;
        /**
         * Guardar un producto
         * @description Crea el producto. `409 DUPLICADO` si la empresa ya tiene uno con ese código interno; `422 ITEM_INVALIDO` si los datos no cumplen las reglas de una línea (unidad fuera del catálogo 03, precio en cero…).
         */
        post: operations["crear_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/percepciones": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Emitir un comprobante de percepción
         * @description El agente de percepción cobra al `cliente` los `documentos` que le emitió y le percibe el IGV a la tasa del `regimen` (catálogo 22: `01` venta interna 2 %, `02` combustible 1 %,
         *     `03` tasa especial 0.5 %). Por documento: percibido = cobro × tipo de cambio × tasa, y neto cobrado = cobro × tipo de cambio + percibido. Todos los cobros son del mes del
         *     comprobante (2612). SUNAT lo recibe hasta 7 días calendario desde el día siguiente a su emisión (2600). Si la percepción ya se informó en la factura (tipo de operación 2001), no se emite
         *     además este comprobante: SUNAT lo rechazaría (3312).
         *
         *     **Errores**: `422 VALIDACION`, `422 AGENTE_NO_DESIGNADO` (2617/4285), `422 RECEPTOR_INVALIDO` (contraparte), `422 RETENCION_INVALIDA` o `422 PERCEPCION_INVALIDA` (régimen y tasa,
         *     documentos, pagos, fechas, tipo de cambio; con el código de la regla de SUNAT), `422 FECHA_INVALIDA` (futura o pasados los 7 días, 2600),
         *     `422 SERIE_INVALIDA`, `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`, `429 LIMITE_PLAN`, `402 PLAN_VENCIDO`. Admite `Idempotency-Key` como `POST /v1/facturas`, con claves propias de las percepciones.
         */
        post: operations["percibir"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/percepciones/calcular": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Calcular un comprobante de percepción sin emitirlo
         * @description Recibe el mismo cuerpo que `POST /v1/percepciones` y responde lo percibido y el neto de cada documento y los totales **sin numerar, firmar, guardar ni enviar nada**, con las
         *     mismas reglas de dominio (y la misma exigencia de la designación). El `id` de la respuesta no existe y no trae enlaces.
         */
        post: operations["calcularPercepcion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/notas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Emitir una nota de crédito o débito
         * @description Sobre una factura o una boleta **aceptada** por SUNAT de la misma empresa. La nota toma el receptor, la moneda y el tipo de
         *     operación del documento; la serie debe existir con el tipo 07/08 y empezar con la letra del documento que modifica:
         *     `F` sobre una factura (p. ej. `FC01`, `FD01`), `B` sobre una boleta (p. ej. `BC01`, `BD01`; reglas 2399, 2400).
         *
         *     **RUC del comprador**: si la nota lleva un RUC (el del documento que modifica), se valida contra el padrón de SUNAT como en la
         *     factura: si no figura, `422 RECEPTOR_INVALIDO` (1083) antes de numerar; si está de baja (4013) o no habido (4014), se emite igual
         *     y la respuesta trae `avisos`.
         *
         *     **Sobre una boleta**: el comprador es el de la boleta (puede no tener documento). No se admiten la NC `13` (la boleta no
         *     tiene cuotas, regla 3259) ni la NC `04`, `05` u `08`: el descuento o la bonificación a un consumidor final va en la misma
         *     boleta (RCP art. 10 num. 1.4). La nota se envía sola hasta el 5.º día; el 6.º y el 7.º va en el resumen diario (como la boleta),
         *     salvo la NC `10` y la ND `03`, que no van en él y vencen el 5.º. Se anula con `POST /v1/facturas/{id}/baja`, que la informa en un
         *     resumen diario.
         *
         *     **Nota total** (anulación `01`, devolución total `06`): omita `items` y khipu copia los ítems, el descuento global y
         *     los cargos de la factura (no envíe `descuento_global` ni `cargos` propios: se rechazan). Si la factura regularizó
         *     anticipos, indique los `items` por el importe neto: los anticipos no viajan en una nota (si el neto es 0.00 no hay nota posible:
         *     anule la factura con la comunicación de baja). **Nota parcial** (devolución
         *     por ítem `07`, descuento `04`/`05`, disminución `09`…): envíe los `items` que cubre, con el mismo formato que en la factura.
         *
         *     **Descuentos y cargos**: SUNAT no admite descuentos ni cargos (`AllowanceCharge`) en una nota y exige que el valor de cada
         *     línea sea valor unitario × cantidad (3271) y que la base del IGV sea la suma de las líneas (3277). El descuento (`00`/`02`)
         *     y el cargo (`47`/`49`) se aplican al valor de la línea —el global, repartido entre las líneas gravadas— y la nota sale sin
         *     ellos: la base, el IGV y el total son los mismos, y el valor unitario de la línea es el neto. Un descuento que no afecta la
         *     base (`afecta_base_igv: false`, códigos `01`/`03`) no cabe en una nota (3280: el total de la nota no resta descuentos): se
         *     rechaza con `422 DESCUENTO_INVALIDO` antes de numerar; indique los `items` por su importe neto. Los cargos que no afectan la
         *     base (`48`, `46`, `50`) sí van.
         *
         *     **Nota de crédito 13** (corrección de cuotas): solo sobre facturas al crédito; envíe `forma_pago` al crédito con el
         *     neto pendiente y las cuotas corregidas (reglas 3257, 3260, 3320, 3321); la nota sale con importe 0 (regla 3315). En cualquier
         *     otra nota `forma_pago` se rechaza.
         *
         *     **Límites** (notas de crédito): el total y las bases por tributo no pueden superar los del documento (reglas 3286, 3503;
         *     sobre una boleta el total admite S/ 1 de más). khipu descuenta además lo ya acreditado por las notas de crédito anteriores
         *     sobre el mismo documento (las rechazadas no cuentan): varias notas parciales pueden sumar el documento, pero una segunda nota
         *     total —o cualquiera que exceda el saldo— se rechaza aunque SUNAT, que compara nota por nota, la aceptaría.
         *
         *     **Errores**: `422 NOTA_INVALIDA` (factura o boleta inexistente, no aceptada o anulada —2119/2120—, fecha anterior al documento
         *     —2885—, motivo fuera del catálogo —2172— o no admitido sobre una boleta —3259/4367—, importes mayores que el documento o que su
         *     saldo tras otras notas —3286/3503—), `422 DESCUENTO_INVALIDO` (un descuento que no afecta la base, código 01 o 03: la
         *     nota no lo admite —3280—), `422 FECHA_INVALIDA` (plazo de envío vencido —2108/1079—), `422 SERIE_INVALIDA`,
         *     `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`, `429 LIMITE_PLAN` (la cuenta llegó al tope de documentos del mes de su plan: una nota
         *     consume como una factura; para anular sin consumir está la comunicación de baja), `402 PLAN_VENCIDO` (el plan de la cuenta
         *     venció y pasó su gracia: se vuelve a emitir al renovarlo; la comunicación de baja sigue).
         *
         *     **Reintentos seguros** (`Idempotency-Key`): si la red se corta después de enviar el pedido, no se sabe si la nota se
         *     emitió, y repetirlo sin más emitiría otra y consumiría otro número. Mande una clave única por nota (un UUID) en la cabecera
         *     `Idempotency-Key` y repita el mismo pedido con la misma clave: si ya se había emitido, responde `200` con **la misma nota**
         *     (mismo id y número), sin emitir otra ni consumir número, aunque entretanto el documento haya pasado a tener una baja o vencido
         *     el plazo. La misma clave con otro contenido responde `422 IDEMPOTENCIA_INVALIDA`. La clave vale para la empresa durante 24 horas
         *     y es propia de las notas: la misma cadena en `POST /v1/facturas` es otra clave. Sin la cabecera, cada pedido es una nota nueva.
         */
        post: operations["crear_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/liquidaciones-compra": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Emitir una liquidación de compra
         * @description **Quién es quién**: la empresa es la compradora y la emisora; el `vendedor` es una persona sin RUC con DNI (`1`), carné de extranjería (`4`) o
         *     pasaporte (`7`), y su domicilio completo (dirección, ubigeo y condición del catálogo 60) es obligatorio, igual que el `lugar_operacion`. Un vendedor
         *     con RUC responde `422 RECEPTOR_INVALIDO` (2800): emite su propia factura o boleta.
         *
         *     **Retenciones**: el comprador retiene al vendedor el IGV de las líneas gravadas y el Impuesto a la Renta (1.5 % del valor de venta de cada línea, tributo
         *     3000), y los declara y paga en su lugar. Por eso `totales.total` es el **importe neto** que se paga al vendedor: subtotal (valor de venta + IGV) − IGV − renta
         *     + redondeo (regla 4312); el bloque `liquidacion` de la respuesta trae la retención y el subtotal. El `precio_unitario` de una línea gravada incluye el IGV,
         *     como en la factura.
         *
         *     **Plazo**: SUNAT la recibe hasta el día calendario siguiente a su emisión (2108); no tiene resumen diario. **Baja**: SUNAT no la anula con una comunicación
         *     de baja (2308) sino con un resumen de reversiones, que khipu todavía no emite: `POST /v1/facturas/{id}/baja` responde `422 BAJA_INVALIDA`.
         *
         *     **Aún no**: compra de oro (`0503`), compras gratuitas, otros tributos (9999) y anticipos. Cada línea lleva el código de producto SUNAT (obligatorio, 3506).
         *     Que el DNI exista, no sea de un menor ni de un fallecido (2450, 2460–2462) y que el vendedor no pase de 75 UIT al año (2451) lo valida SUNAT con datos que
         *     khipu no tiene: si falla, la liquidación queda `RECHAZADO` con el número consumido.
         *
         *     **Errores**: `422 VALIDACION`, `422 RECEPTOR_INVALIDO` (vendedor), `422 LIQUIDACION_INVALIDA` (ubigeos, condiciones, lugar, tasa), `422 ITEM_INVALIDO`,
         *     `422 AFECTACION_INVALIDA`, `422 TIPO_OPERACION_INVALIDO`, `422 REDONDEO_INVALIDO` (4314: el redondeo pasa de ±1.00, o el neto queda en cero o negativo, 2062), `422 FECHA_INVALIDA`, `422 SERIE_INVALIDA`, `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`,
         *     `429 LIMITE_PLAN`, `402 PLAN_VENCIDO`. Admite `Idempotency-Key` como `POST /v1/facturas`, con claves propias de las liquidaciones.
         */
        post: operations["crear_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/liquidaciones-compra/calcular": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Calcular la liquidación de compra sin emitir
         * @description Recibe el mismo cuerpo que `POST /v1/liquidaciones-compra` y responde los importes —IGV, retención de renta e importe neto— **sin numerar, firmar, guardar
         *     ni enviar nada**, con las mismas reglas de dominio: lo que la emisión rechazaría responde aquí el mismo `422`. El `id` de la respuesta no existe y no trae enlaces.
         */
        post: operations["calcular"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar guías de remisión
         * @description Guías de la empresa, de la más reciente a la más antigua, paginadas; el total va en `X-Total-Count`. Filtros: `estado`, `tipo` (`09` o `31`),
         *     `serie` exacta y fecha de emisión `desde`/`hasta` (inclusive; `desde > hasta` es `400 RANGO_INVALIDO`).
         */
        get: operations["listar_2"];
        put?: never;
        /**
         * Emitir una guía de remisión
         * @description Valida, numera en la serie (`T###` remitente, `V###` transportista), firma y —salvo `enviar_automatico: false`— envía la guía a SUNAT en la misma
         *     llamada. La respuesta trae el `estado`: `ACEPTADO` si SUNAT ya la procesó, `ENVIADO_PENDIENTE_TICKET` si todavía la procesa (khipu consulta el ticket
         *     solo, cada vez más espaciado, durante ~24 h; también `POST /v1/guias-remision/{id}/ticket`) o `ERROR_ENVIO` (ver `ultimo_error` y
         *     `reintento_automatico`).
         *
         *     **Remitente (09)**: `motivo` (catálogo 20), `modalidad` (catálogo 18) y, en transporte público (`01`), `transportista`. En privado (`02`) van
         *     `traslado.vehiculos` y `traslado.conductores` (el primero de cada lista es el principal), salvo con el indicador `vehiculo_m1_o_l`. El traslado entre
         *     establecimientos (`04`) va del establecimiento propio de partida al de llegada, con el propio remitente como destinatario.
         *
         *     **Transportista (31)**: `remitente`, `registro_mtc` y siempre el vehículo y el conductor principales; sin motivo ni modalidad.
         *
         *     **Puntos de partida y llegada**: ubigeo (catálogo 13) y dirección; un establecimiento de la propia empresa se indica solo con su código
         *     (`establecimiento`, de `GET /v1/empresa/establecimientos`; `0000` el domicilio fiscal) y khipu completa el resto.
         *
         *     **Errores** (antes de numerar, el mensaje empieza por el código SUNAT cuando lo hay): `422 VALIDACION`, `422 TRASLADO_INVALIDO`,
         *     `422 TRANSPORTE_INVALIDO`, `422 DESTINATARIO_INVALIDO`, `422 REMITENTE_INVALIDO`, `422 ITEM_INVALIDO`, `422 DOCUMENTO_RELACIONADO_INVALIDO`,
         *     `422 SERIE_INVALIDA` / `SERIE_NO_CONFIGURADA`, `422 CREDENCIALES_API_SUNAT_NO_CARGADAS` / `CREDENCIALES_SOL_NO_CARGADAS` / `GRE_NO_DISPONIBLE` /
         *     `CREDENCIALES_GRE_RECHAZADAS` (solo si se envía en el acto; `GRE_NO_DISPONIBLE` también mientras el envío esté apagado en el servidor).
         *
         *     **Reintentos seguros** (`Idempotency-Key`): igual que en las facturas, la misma clave y el mismo cuerpo devuelven `200` con la misma guía, sin
         *     numerar otra ni volver a enviarla. Las claves de las guías son aparte de las de facturas y notas.
         */
        post: operations["crear_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision/{id}/ticket": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Consultar el ticket en SUNAT
         * @description Pregunta a SUNAT por el ticket de una guía `ENVIADO_PENDIENTE_TICKET` y aplica la respuesta: el CDR (`ACEPTADO`, `ACEPTADO_CON_OBS`, `RECHAZADO`) o
         *     sigue en proceso. Si khipu había dejado de consultarlo solo (`reintento_automatico: false`) y SUNAT sigue procesándolo, retoma las consultas. Una
         *     guía que no espera un ticket responde `409 TICKET_NO_PENDIENTE`.
         */
        post: operations["ticket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision/{id}/enviar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Enviar (o reenviar) la guía a SUNAT
         * @description Envía una guía `FIRMADO` (emitida con `enviar_automatico: false`) o reenvía una en `ERROR_ENVIO` —por ejemplo, después de corregir las credenciales—
         *     sin esperar al reintento automático. Otra en cualquier otro estado responde `409 ESTADO_NO_ENVIABLE`. Antes de llamar a SUNAT la guía pasa a `ENVIANDO`:
         *     dos envíos a la vez no mandan el mismo número (el segundo responde `409`). Una guía que quedó `ENVIANDO` sin respuesta responde `409 ENVIO_EN_CURSO`;
         *     pasados 10 min se retoma con `forzar=true`, después de revisar en SOL que no llegó. Con el envío apagado en el servidor, `422 GRE_NO_DISPONIBLE`; si SUNAT
         *     rechazó las credenciales de la empresa hace menos de una hora y no cambiaron, `422 CREDENCIALES_GRE_RECHAZADAS` (sin pedir token).
         */
        post: operations["enviar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision/validar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Validar una guía sin emitirla
         * @description Recibe el mismo cuerpo que `POST /v1/guias-remision` y responde la guía que se emitiría —con los establecimientos de la empresa ya resueltos—
         *     **sin numerar, firmar, guardar ni enviar nada**. Aplica las mismas reglas que la emisión: lo que esta rechazaría responde aquí el mismo `422`. No
         *     comprueba la serie, el certificado ni las credenciales. El `id` no existe, `numero` es `null` y no trae `enlaces`.
         */
        post: operations["validar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar facturas
         * @description Facturas de la empresa, de la más reciente a la más antigua, paginadas. El total de resultados va en la cabecera
         *     `X-Total-Count` y refleja los filtros. Filtre por `estado` para trabajar una cola (p. ej. `ERROR_ENVIO` para reintentar,
         *     `RECHAZADO` para corregir y reemitir) y por fecha de emisión con `desde`/`hasta` (inclusive, rango abierto si falta uno;
         *     `desde > hasta` responde `400 RANGO_INVALIDO`, una fecha mal formada `400 PARAMETRO_INVALIDO`) y por `serie` exacta
         *     (F001, FC01…; con formato inválido `400 PARAMETRO_INVALIDO`, inexistente → lista vacía), por `tipo` (`01` factura, `03` boleta, `04` liquidación de compra, `07` nota de crédito, `08` nota de débito, `20` retención, `40` percepción),
         *     por `numero` (sin la serie) y por `cliente`: el documento (empieza por lo escrito) o parte de la razón social, sin distinguir mayúsculas. Los filtros se combinan.
         */
        get: operations["listar_3"];
        put?: never;
        /**
         * Emitir una factura o una boleta
         * @description Crea la factura (o la boleta, con una serie `B###`), la numera, la firma y la envía a SUNAT en la misma llamada
         *     (salvo `enviar_automatico: false`, que la deja `FIRMADO` para enviarla después con `POST /v1/facturas/{id}/enviar`).
         *
         *     **Qué validar antes de llamar**: la serie debe existir (ver *Series*); el receptor de una factura siempre lleva
         *     RUC (`cliente.tipo_doc = "6"`); `tipo_afectacion_igv` de cada ítem viene del catálogo 07; `unidad` del catálogo 03;
         *     `tipo_operacion` del catálogo 51 (`0101` venta interna por defecto) y que khipu emita en ese comprobante (`0113` es solo de boletas; la lista de factura está en `tipo_operacion` del request): si no, `422 TIPO_OPERACION_INVALIDO` antes de consumir número.
         *
         *     **Boleta** (serie `B###`): el comprador se identifica con cualquier documento del catálogo 06 (DNI de 8 dígitos,
         *     carné de extranjería, pasaporte, RUC…) o, si la boleta es en soles y de hasta S/ 700.00, con `tipo_doc` y `num_doc`
         *     `-` (sin documento). Se emite al contado; el crédito y la retención todavía van en factura
         *     (`422` con el motivo). Admite anticipos de otra boleta (serie `B###`) o de una factura (serie `F###`) del mismo comprador, y la exportación y el `0401` con un comprador sin RUC. Una boleta aceptada se anula con `POST /v1/facturas/{id}/baja`, que la informa en un resumen diario.
         *
         *     **Precios**: `precio_unitario` es el precio de venta unitario **con IGV incluido** para ítems gravados; khipu
         *     calcula el valor unitario, el IGV y los totales (tolerancias SUNAT ±1).
         *
         *     **Forma de pago** (obligatoria desde 2022): al contado por defecto; al crédito indique `monto_pendiente` y las
         *     `cuotas` con fecha posterior a la emisión.
         *
         *     **RUC del comprador** (factura y boleta con RUC): se valida contra el padrón de SUNAT. Si no figura, `422 RECEPTOR_INVALIDO`
         *     (1083) antes de numerar, porque SUNAT rechazaría el comprobante. Si figura pero está de baja (4013) o no habido (4014), SUNAT
         *     lo acepta con una observación: se emite igual y la respuesta trae `avisos`. Mientras el padrón no esté cargado o tenga más
         *     de 3 días, no se rechaza nada.
         *
         *     **Errores frecuentes**: `422 VALIDACION` (campos con formato inválido, detalle en `errores`),
         *     `422 FORMA_PAGO_INVALIDA` / `SERIE_INVALIDA` / `RECEPTOR_INVALIDO` (regla de negocio, el mensaje lleva el código
         *     SUNAT cuando aplica), `409 DUPLICADO` (ya existe ese `correlativo` en la serie), `422 CREDENCIALES_SOL_FALTAN`
         *     (la empresa aún no cargó usuario/clave SOL), `429 LIMITE_PLAN` (la cuenta llegó al tope de documentos del mes de su
         *     plan, sumando todas sus empresas: se vuelve a emitir el mes siguiente o con otro plan; no gasta número), `402 PLAN_VENCIDO`
         *     (el plan de la cuenta venció y pasó su gracia: se vuelve a emitir al renovarlo; no gasta número, y la consulta y la baja siguen).
         *
         *     **Reintentos seguros** (`Idempotency-Key`): si la red se corta después de enviar el pedido, no se sabe si la factura
         *     se emitió. Mande una clave única por factura (un UUID) en la cabecera `Idempotency-Key` y repita el mismo pedido con la
         *     misma clave: si ya se había emitido, responde `200` con **el mismo comprobante** (mismo id y número), sin emitir otro ni
         *     consumir número. La misma clave con otro contenido responde `422 IDEMPOTENCIA_INVALIDA`. La clave vale para la empresa
         *     durante 24 horas. Sin la cabecera, cada pedido es una factura nueva.
         */
        post: operations["crear_5"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/enviar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Enviar (o reintentar) a SUNAT
         * @description Envía un comprobante `FIRMADO` (emitido con `enviar_automatico: false`) o reintenta uno en `ERROR_ENVIO` sin
         *     esperar al reintento automático. Un comprobante `ACEPTADO`, `RECHAZADO` o `ANULADO` responde `409 ESTADO_NO_ENVIABLE`.
         *     Una boleta (o nota sobre boleta) que pasó el envío individual se informa en un resumen diario: la respuesta lo trae en `baja`
         *     (`condicion: ALTA`). Pasado el 7.º día va igual, con `baja.fuera_de_plazo: true`: SUNAT no la rechaza, pero puede observarla con la
         *     4443 (entonces `ACEPTADO_CON_OBS`; en e-beta la aceptó sin observaciones, `ACEPTADO`). Una factura, una nota de factura o una nota de crédito 10 / de débito 03 de boleta (que no van en el resumen)
         *     fuera de plazo responde `409 FUERA_DE_PLAZO`.
         *
         *     **Efecto sobre otras boletas**: las demás boletas (y notas de boleta) de la misma fecha de emisión que siguen `FIRMADO` o en `ERROR_ENVIO` y también solo van en el resumen diario
         *     se informan en el mismo resumen (hasta 500 líneas, con un solo ticket y un solo CDR), también las emitidas con `enviar_automatico: false`. Cada una trae su `linea` y las `lineas` del resumen.
         */
        post: operations["enviar_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/correo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Enviar el comprobante por correo al adquirente
         * @description Envía al correo indicado la representación impresa (PDF), el XML firmado y la constancia de recepción (CDR) como
         *     adjuntos, con un mensaje opcional del emisor. Solo comprobantes `ACEPTADO` o `ACEPTADO_CON_OBS`: `409 NO_ACEPTADO`
         *     en cualquier otro estado. Si el servidor de correo rechaza el envío, `502 CORREO_NO_ENVIADO` (reintente más tarde). Si
         *     el servidor no tiene correo configurado, `503 CORREO_NO_CONFIGURADO`: no se envió nada y reintentar no sirve.
         */
        post: operations["correo"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/cdr/recuperar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Recuperar el CDR desde SUNAT
         * @description `getStatusCdr` de `billConsultService`: pide a SUNAT la constancia de un comprobante propio que quedó `ENVIADO` o en
         *     `ERROR_ENVIO` (por ejemplo, la conexión se cortó después de que SUNAT lo aceptara) o que ya está resuelto pero perdió
         *     su CDR en el storage. Si SUNAT lo tiene, lo guarda y aplica el resultado (`ACEPTADO`/`RECHAZADO`) sin reenviar;
         *     si no, devuelve el comprobante sin cambios. Un barrido horario hace lo mismo para todas las empresas en producción.
         *     Solo facturas y sus notas (serie F): SUNAT no expone `getStatusCdr` para boletas ni notas de boleta (serie B), cuya
         *     constancia es la del envío individual o la del resumen diario que las informa.
         *     Errores: `404 NO_ENCONTRADO`, `422 SIN_FIRMA`, `409 CDR_YA_DISPONIBLE`, `409 CDR_NO_CONSULTABLE`, `422 NO_DISPONIBLE_EN_BETA`, `422 CREDENCIALES_SOL_NO_CARGADAS`.
         */
        post: operations["recuperarCdr"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/baja": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Dar de baja un comprobante
         * @description Solo comprobantes `ACEPTADO` o `ACEPTADO_CON_OBS` (facturas, boletas y notas) emitidos hace 7 días calendario o menos,
         *     contados en hora de Lima desde la fecha de emisión y no desde la aceptación de SUNAT (la regla que SUNAT aplica a las
         *     boletas y a sus notas desde 2027-01-01, RS 000048-2026; hasta entonces las cuenta desde la CDR, que nunca es anterior a
         *     la emisión, así que khipu cumple las dos): el día 7 se admite, el 8 no. SUNAT mide el plazo con el día en que recibe la
         *     comunicación: si el envío falla (`ERROR_ENVIO`) y el reintento le llega después del día 7, la rechaza con 2957 aunque
         *     khipu la haya admitido (el reintento reenvía el mismo `RA`/`RC`, con su fecha de generación original). Una baja
         *     en curso bloquea otra sobre el mismo comprobante. El motivo es obligatorio; en una boleta queda en khipu (el resumen
         *     diario no lo lleva). Una boleta solo se anula si no se entregó al adquirente (RS 097-2012, art. 14): si ya se entregó
         *     no corresponde la baja sino una nota de crédito. khipu no puede saberlo, así que no lo valida.
         *
         *     **Errores**: `422 BAJA_INVALIDA` (no aceptado, fuera de plazo —2957—, motivo inválido, baja ya en curso),
         *     `404 NO_ENCONTRADO`.
         */
        post: operations["solicitar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/calcular": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Calcular los totales sin emitir
         * @description Recibe el mismo cuerpo que `POST /v1/facturas` y responde los importes que khipu calcularía —`items` y `totales`, con descuentos, cargos, ISC, ICBPER, otros
         *     tributos, anticipos, redondeo, percepción, retención y tipo de cambio— **sin numerar, firmar, guardar ni enviar nada** y sin consumir cupo del plan. Arma el
         *     comprobante con las mismas reglas de dominio que la emisión: lo que devuelve es lo que se emitiría, y lo que la emisión rechazaría por esas reglas responde aquí el
         *     mismo `422` con su `codigo` y `mensaje`. No comprueba lo que depende de datos guardados (que la factura de anticipo exista y esté aceptada, el padrón de RUC, el
         *     plan, que haya serie y certificado): eso lo valida la emisión. El `id` de la respuesta no existe y no trae enlaces.
         */
        post: operations["calcular_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Listar las empresas de la cuenta */
        get: operations["listar_4"];
        put?: never;
        /**
         * Registrar una empresa
         * @description Alta de una razón social (RUC único en la plataforma) con su entorno SUNAT. Después hay que cargar certificado, credenciales SOL y al menos una serie antes de emitir.
         */
        post: operations["crear_6"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/establecimientos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar establecimientos
         * @description Puntos desde los que emite la empresa: el domicilio fiscal como `0000` (`principal: true`, si está configurado en
         *     datos fiscales) más los establecimientos anexos registrados, activos y dados de baja. Cada serie se asigna a uno
         *     (`POST /v1/series`) y sus comprobantes salen con ese `cac:RegistrationAddress` y `AddressTypeCode` (regla 3030).
         */
        get: operations["establecimientos"];
        put?: never;
        /**
         * Registrar un establecimiento anexo
         * @description Alta de un anexo con el código de su ficha RUC (4 dígitos, distinto de `0000`), un nombre y su domicilio (mismas
         *     reglas que el fiscal: 4093 ubigeo, 4094 dirección). Si el código ya existe se actualiza (mismo efecto que `PUT`).
         *     `422 ESTABLECIMIENTO_INVALIDO` o `422 DOMICILIO_INVALIDO`.
         */
        post: operations["crearEstablecimiento"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/certificado": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cargar el certificado digital
         * @description Sube el certificado PKCS#12 (`.p12`/`.pfx`) con el que se firmarán los XML y su contraseña (`multipart/form-data`,
         *     campos `archivo` y `clave`). El RUC de la empresa debe figurar en el campo `OU` del certificado; si no,
         *     `422 CERTIFICADO_INVALIDO`. Reemplaza el certificado anterior.
         */
        post: operations["certificado"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/api-keys": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar API keys
         * @description Prefijo, estado y fechas de cada llave; nunca el secreto. Requiere sesión del portal.
         */
        get: operations["apiKeys"];
        put?: never;
        /**
         * Crear una API key
         * @description Genera una llave `fk_…` para integraciones. **El secreto se muestra una sola vez** en esta respuesta; después solo se ve el prefijo. Requiere sesión del portal (no se puede crear con otra API key).
         */
        post: operations["apiKey"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/usuarios/{id}/reactivar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reactivar un usuario
         * @description Vuelve a entrar con su contraseña y el rol que tenía.
         */
        post: operations["reactivarUsuario"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/usuarios/{id}/desactivar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Desactivar un usuario
         * @description Ya no entra y se cierran sus sesiones abiertas. Si era administrador, se revocan las invitaciones pendientes que creó. Las API keys que haya creado
         *     **siguen vigentes**: son de la empresa, no del usuario; revóquelas en `DELETE /v1/empresa/api-keys/{id}` si hace falta. `422 NO_PUEDES_DESACTIVARTE` con
         *     el propio usuario. Se puede reactivar.
         */
        post: operations["desactivarUsuario"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/invitaciones": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Invitaciones pendientes
         * @description Las que no se aceptaron ni revocaron, también las vencidas (`vencida: true`), que se pueden reenviar.
         */
        get: operations["invitacionesPendientes"];
        put?: never;
        /**
         * Invitar a alguien a la cuenta
         * @description Le manda un enlace de un solo uso (7 días) para crear su contraseña y entrar con el rol elegido. `409 YA_ES_MIEMBRO` si ya es usuario de la cuenta,
         *     `409 USUARIO_EXISTENTE` si el correo ya tiene un usuario en khipu (cada usuario es de una sola cuenta), `409 INVITACION_PENDIENTE` si ya hay una pendiente para
         *     ese correo (reenvíela). `429 DEMASIADAS_INVITACIONES` con 20 pendientes, o si en las últimas 24 horas la cuenta ya mandó 30 correos de invitación o esa
         *     dirección ya recibió 3 (de cualquier cuenta); invitar y reenviar cuentan, también las invitaciones que después se revocaron. La invitación vale mientras
         *     quien la creó siga siendo administrador activo. `correo_enviado: false` si el correo no salió: la invitación queda y se puede reenviar.
         */
        post: operations["invitarAlEquipo"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/invitaciones/{id}/reenviar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reenviar una invitación
         * @description Un enlace nuevo que vuelve a durar 7 días; el anterior deja de servir. Hasta 5 envíos por invitación: después, `429 DEMASIADOS_ENVIOS`. Cuenta para
         *     los topes de 24 horas de invitar (`429 DEMASIADAS_INVITACIONES`).
         *     `409 INVITACION_NO_PENDIENTE` si ya se aceptó o se revocó.
         */
        post: operations["reenviarInvitacion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/clientes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Buscar clientes
         * @description Los clientes de la empresa por nombre. `q` (opcional) se compara con el documento (empieza por) y con el nombre (contiene, sin distinguir mayúsculas ni tildes). El total va en
         *     `X-Total-Count`.
         */
        get: operations["listar_5"];
        put?: never;
        /**
         * Guardar un cliente
         * @description Crea el cliente. `409 DUPLICADO` si la empresa ya tiene uno con ese documento; `422 RECEPTOR_INVALIDO` si el documento o el nombre no cumplen las reglas del comprador.
         */
        post: operations["crear_7"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/verificar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Verificar el correo
         * @description Con el token del enlace que llegó al correo (24 h, un solo uso). `422 TOKEN_INVALIDO` si venció, ya se usó o no existe.
         */
        post: operations["verificar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/verificacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reenviar el enlace de verificación
         * @description Al correo del usuario de la sesión. `409 CORREO_YA_VERIFICADO` si no hace falta. Hasta 5 enlaces cada 24 horas contando el del
         *     registro (el correo todavía no está verificado y podría no ser suyo): pasado eso, `429 DEMASIADOS_ENLACES`.
         */
        post: operations["reenviarVerificacion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/terminos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Aceptar los términos vigentes
         * @description Registra que el usuario de la sesión acepta la versión vigente de los términos del servicio y la política de privacidad, con la
         *     fecha. Si ya la había aceptado responde igual, con la fecha de entonces. Se usa cuando la versión cambió (`terminos_pendientes`
         *     en `/me`, el login o el refresh); al registrarse, la aceptación va en el propio registro. Hasta aceptarla, cualquier escritura con
         *     la sesión del portal fuera de `/v1/auth/**` responde `403 TERMINOS_PENDIENTES` (con `X-Api-Key` no aplica). Una sesión de soporte
         *     recibe `403 SOPORTE_SOLO_LECTURA`.
         */
        post: operations["aceptarTerminos"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/restablecer": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Restablecer la contraseña
         * @description Fija una contraseña nueva con el token recibido por correo. También verifica el correo: el enlace llegó ahí.
         */
        post: operations["restablecer"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/registro": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Registrar una cuenta
         * @description Crea la cuenta del portal y devuelve los tokens de sesión. El correo queda sin verificar y se le manda un enlace (24 h): hasta
         *     verificarlo puede entrar al portal, pero cualquier escritura con su sesión responde `403 CORREO_SIN_VERIFICAR`.
         *     `403 REGISTRO_CERRADO` si el autoservicio está cerrado (`app.registro-abierto`). Exige `acepta_terminos: true` (#342): sin eso,
         *     `400 TERMINOS_NO_ACEPTADOS` y no se crea nada; con eso queda guardada la versión vigente de los términos y la fecha.
         */
        post: operations["registrar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Renovar la sesión
         * @description Entrega un nuevo par de tokens a cambio del refresh vigente; el usado queda invalidado. Durante 30 segundos todavía se acepta, para una petición concurrente que mandó el mismo refresh; después, `401 SESION_INVALIDA`. Cerrar sesión lo invalida al instante.
         */
        post: operations["refrescar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/recuperar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Solicitar restablecimiento de contraseña
         * @description Envía un enlace de un solo uso al correo si la cuenta existe; responde `202` siempre para no revelar cuentas. Como mucho
         *     3 correos por dirección por hora: pasado eso responde igual, pero no envía nada.
         */
        post: operations["recuperar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cerrar sesión
         * @description Invalida el refresh token.
         */
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Iniciar sesión
         * @description Devuelve `access_token` (corta duración) y `refresh_token` (rotativo: cada refresh invalida el anterior). Tras 5 contraseñas
         *     erróneas en 15 minutos para ese correo (o 20 fallos desde la misma IP), `429 DEMASIADOS_INTENTOS_LOGIN` durante 15 minutos,
         *     aun con la contraseña correcta.
         */
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/invitacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Ver una invitación
         * @description A qué cuenta y con qué rol invitaron al dueño del enlace. `422 TOKEN_INVALIDO` si no existe, venció, se revocó, se reenvió (vale el último enlace), ya se usó o
         *     quien la creó ya no es administrador activo de la cuenta.
         */
        post: operations["consultarInvitacion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/invitacion/aceptar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Aceptar una invitación
         * @description Crea el usuario en la cuenta con el rol de la invitación y el correo ya verificado (el enlace llegó ahí), con la contraseña elegida; después entra con
         *     `POST /v1/auth/login`. `422 TOKEN_INVALIDO` como al verla, `422 PASSWORD_DEBIL` (no consume el enlace), `409 USUARIO_EXISTENTE` si el correo ya tiene un usuario.
         */
        post: operations["aceptarInvitacion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/tenants": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Crear un tenant (empresa) con su primera API key
         * @description Alta administrativa de una empresa sin pasar por el portal; devuelve la API key inicial, que se muestra una sola vez.
         */
        post: operations["crear_8"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/planes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar los planes
         * @description Todos los planes, activos o no, del más barato al más caro, con sus límites, su precio y **cuántas cuentas los tienen hoy** como su suscripción
         *     vigente. `limites` son los que mandan en el ciclo en curso; `limites_programados` es un cambio ya decidido que entra al inicio del ciclo
         *     siguiente (el mes calendario en America/Lima), si lo hay. Un límite sin tope viene con `ilimitado: true` y sin `maximo`.
         */
        get: operations["listar_6"];
        put?: never;
        /**
         * Crear un plan
         * @description Nace activo. Validación: nombre obligatorio, hasta 40 caracteres y **único sin importar mayúsculas** (`409 NOMBRE_DUPLICADO`); precio mensual cero o más
         *     con hasta dos decimales (`422 PRECIO_INVALIDO`); límites mayores que cero, o `ilimitado: true` para documentos, usuarios y API keys (`422
         *     LIMITE_INVALIDO`); retención de al menos un año (`422 RETENCION_INVALIDA`). Un límite que se omite se rechaza: nunca se vuelve «ilimitado» en
         *     silencio. Queda en la bitácora.
         */
        post: operations["crear_9"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/planes/{id}/desactivar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Desactivar un plan
         * @description Lo saca de la oferta **sin tocar a las cuentas que ya lo tienen**: siguen con su suscripción. `409 PLAN_POR_DEFECTO` si es el plan con el que nacen las
         *     cuentas nuevas; `409 PLAN_YA_INACTIVO`; `404 NO_ENCONTRADO`. Queda en la bitácora.
         */
        post: operations["desactivar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/planes/{id}/activar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Volver a ofrecer un plan
         * @description Revierte la desactivación. `409 PLAN_YA_ACTIVO`; `404 NO_ENCONTRADO`. Queda en la bitácora.
         */
        post: operations["activar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/integridad": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Verificar la integridad del storage en un rango de fechas
         * @description Recorre los comprobantes firmados de todas las empresas emitidos entre `desde` y `hasta` (inclusive) y comprueba que su XML
         *     exista en el storage con el DigestValue con el que se firmó, y que el CDR exista cuando SUNAT lo emitió. Solo lee. El mismo
         *     barrido corre a diario sobre los últimos días (`app.integridad`). Devuelve los problemas encontrados: `XML_FALTANTE`,
         *     `XML_CORRUPTO`, `CDR_FALTANTE`, `STORAGE_INACCESIBLE`.
         */
        post: operations["verificar_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas/{id}/prueba-de-conexion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Probar la conexión de una empresa con SUNAT
         * @description Consulta a SUNAT, con las credenciales SOL y el entorno de la empresa, el estado de un ticket que no existe: es de solo lectura, no envía ni cambia
         *     nada. Responde cómo contestó SUNAT: `CONECTADO` (contestó con normalidad), `RECHAZADO` (contestó con un error definitivo; con un ticket que no
         *     existe un error de ticket es esperable) o `SIN_RESPUESTA` (red, tiempo de espera, error del servicio o de autenticación HTTP), con el código y el
         *     mensaje de SUNAT tal cual. No dice por sí sola si las credenciales son válidas: la lee quien la ejecuta. Queda en la bitácora con el resultado,
         *     nunca las credenciales. `409 SOL_NO_CARGADAS` si la empresa no tiene credenciales SOL; `404 NO_ENCONTRADO` si no existe.
         */
        post: operations["probarConexion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas/{id}/entorno": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cambiar el entorno de una empresa
         * @description Cambia entre `BETA` y `PRODUCCION`. **Decide contra qué URLs de SUNAT emite la empresa** y con qué credenciales SOL y certificado se espera que
         *     lo haga: al pasar a producción hacen falta los de producción. **No toca ningún comprobante ya emitido**. Se rechaza con `409
         *     EMPRESA_CON_ENVIOS_PENDIENTES` si la empresa tiene tareas en el outbox, porque lo emitido en un entorno no debe enviarse al otro, y con `409
         *     ENTORNO_SIN_CAMBIOS` si ya estaba en ese entorno. Un entorno que no existe responde `400`; `404 NO_ENCONTRADO` si la empresa no existe.
         *     Queda en la bitácora (de cuál a cuál), también en la de su cuenta.
         */
        post: operations["cambiarEntorno"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas/{id}/avisos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Avisarle al cliente por correo
         * @description Le manda a la cuenta de la empresa un correo de aviso: `CERTIFICADO` (el certificado está por vencer o vencido) o `CREDENCIALES_SOL` (SUNAT no acepta sus credenciales). Se
         *     avisa **solo lo que es cierto ahora**: si el certificado está vigente o las credenciales no fallan, `409 AVISO_SIN_MOTIVO`. `409 EMPRESA_SIN_CUENTA` si no hay a quién
         *     escribirle (una empresa de integración). **El mismo aviso no se repite en una semana**: `409 AVISO_RECIENTE` con la fecha desde la que se puede repetir, aunque dos
         *     administradores hagan clic a la vez; pasar de «por vencer» a «vencido» sí es un aviso nuevo. `503 CORREO_NO_CONFIGURADO` si el servidor no manda correos y
         *     `502 CORREO_NO_ENVIADO` si el correo falló: en ese caso no queda registrado y se puede reintentar. Queda en la bitácora, sin el correo del cliente.
         */
        post: operations["avisar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas/{id}/api-keys/{apiKeyId}/revocar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Revocar una API key de una empresa
         * @description Revoca esa key y solo esa: deja de autenticar de inmediato. Las demás keys de la empresa no se tocan. Queda en la bitácora con su prefijo, nunca la
         *     clave. `409 API_KEY_YA_REVOCADA` si ya lo estaba; `404 NO_ENCONTRADO` si la empresa o la key no existen, o la key es de otra empresa.
         */
        post: operations["revocarApiKey"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas/{id}/agente": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Registrar la designación de una empresa como agente de retención o de percepción
         * @description #40. Registra que SUNAT designó (o dejó de designar) a la empresa agente de retención y/o de percepción del IGV. Hágalo **después de verificar la resolución de
         *     designación** en los padrones de agentes de SUNAT: khipu no la consulta. Con `retencion` la empresa puede crear series `R###` y emitir comprobantes de retención (20); con
         *     `percepcion`, series `P###` y comprobantes de percepción (40). Quitarla no toca los comprobantes ni las series ya creados: solo impide emitir más. `409
         *     DESIGNACION_SIN_CAMBIOS` si ya tenía esa designación; `404 NO_ENCONTRADO` si la empresa no existe. Queda en la bitácora (de cuál a cuál), también en la de su cuenta.
         */
        post: operations["designarAgente"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar las cuentas de clientes
         * @description Todas las cuentas, de la más reciente a la más antigua, paginadas. El total de resultados va en la cabecera
         *     `X-Total-Count` y refleja la búsqueda. `q` busca sin distinguir mayúsculas en el correo y el nombre de la cuenta,
         *     y en la razón social de sus empresas (por fragmento) y su RUC (por prefijo). En el nombre y la razón social tampoco
         *     distingue tildes, diéresis ni acentos, en los dos sentidos: `libreria` encuentra «Librería» y `librería` encuentra
         *     «Libreria». La `ñ` **sí** se distingue de la `n`: es otra letra, y `pena` no encuentra «Peña». `ultimo_acceso` mide la actividad en el
         *     portal, no el uso por API key. **Las cuentas dadas de baja (#201) no salen** salvo que se pida con `bajas`: `INCLUIDAS` las mezcla y
         *     `SOLO` devuelve únicamente esas; el total las trata igual. El plan de la cuenta se agregará más adelante.
         */
        get: operations["listar_9"];
        put?: never;
        /**
         * Dar de alta a un cliente: cuenta, empresa y primera serie
         * @description Crea en una sola transacción la cuenta con su primer usuario, la primera empresa atada a ella, su primera serie y una
         *     API key inicial, y manda al correo del cliente una invitación para que cree su contraseña (el administrador nunca la
         *     elige). Todo o nada: si algo falla, no queda nada. La API key se devuelve solo en esta respuesta. Si el correo no
         *     salió —el SMTP lo rechazó, o no hay SMTP configurado y solo quedó en el log—, `invitacion_enviada` es `false` y el
         *     alta queda hecha: el cliente puede pedir un enlace con «olvidé mi contraseña». `409 DUPLICADO` si el correo o el
         *     RUC ya existen.
         *
         *     **Reintentos** (`Idempotency-Key`, #219): con una clave por alta (un UUID), repetir el mismo pedido devuelve `200` con
         *     **la misma respuesta, incluida la API key**, sin crear nada. La respuesta se guarda cifrada y solo una hora; después el
         *     reintento responde `409 IDEMPOTENCIA_VENCIDA` (el alta ya está hecha; el cliente crea otra API key desde su portal). La
         *     misma clave con otro contenido responde `422 IDEMPOTENCIA_INVALIDA`.
         */
        post: operations["crear_10"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/suspender": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Suspender una cuenta
         * @description Corta el acceso al portal de todos los usuarios de la cuenta y la emisión por API de todas sus empresas: cualquier petición suya
         *     responde `403 CUENTA_SUSPENDIDA`, incluso con una sesión que ya estaba abierta. **No borra nada**: reactivar la cuenta lo devuelve
         *     todo a como estaba. Los documentos que ya se emitieron siguen enviándose a SUNAT (cortarlos los dejaría fuera de plazo). Queda en
         *     la bitácora de auditoría, con el motivo si se dio, en la misma transacción. `409 CUENTA_YA_SUSPENDIDA` si ya lo estaba;
         *     `404 NO_ENCONTRADO` si no existe; `422 MOTIVO_INVALIDO` si el motivo pasa de 200 caracteres.
         */
        post: operations["suspender"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/reponer": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reponer una cuenta dada de baja
         * @description Revierte la baja: la cuenta y sus empresas vuelven a los listados operativos. No toca la suspensión, que es independiente. Queda en
         *     la bitácora de auditoría. `409 CUENTA_NO_DE_BAJA` si no estaba de baja; `404 NO_ENCONTRADO` si no existe.
         */
        post: operations["reponer"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/reactivar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reactivar una cuenta suspendida
         * @description Devuelve la cuenta, sus usuarios y todas sus empresas a como estaban antes de suspenderla: las sesiones vuelven a servir y las
         *     API keys a emitir. Queda en la bitácora de auditoría. `409 CUENTA_NO_SUSPENDIDA` si ya estaba activa; `404 NO_ENCONTRADO` si no
         *     existe.
         */
        post: operations["reactivar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/plan": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * El plan de una cuenta
         * @description Cuál es el plan de la cuenta hoy, en qué estado de pago está (`VIGENTE`, `EN_GRACIA` si venció pero sigue en sus días de gracia, `VENCIDA`), hasta cuándo
         *     se la sirve (gracia incluida) y, si hay, la bajada de plan que espera el inicio del ciclo siguiente. `404 NO_ENCONTRADO` si la cuenta no existe.
         */
        get: operations["ver_4"];
        put?: never;
        /**
         * Cambiar el plan de una cuenta
         * @description **Subir de plan tiene efecto inmediato; bajar, al ciclo siguiente** (el mes calendario en America/Lima): una bajada queda programada y la cuenta sigue con su
         *     plan de hoy hasta entonces. Lo que es subir o bajar lo decide el precio; el mismo plan con otro vencimiento es una renovación y entra ya. Un cambio inmediato
         *     cancela la bajada que esperaba. `vence_en` es obligatorio para un plan de pago (`422 VENCIMIENTO_REQUERIDO`) y debe ser posterior al inicio; `dias_de_gracia`
         *     va de 0 a 90 (`422 GRACIA_INVALIDA`). `409 PLAN_INACTIVO` si el plan está fuera de la oferta; `409 CAMBIO_CONCURRENTE` si otro administrador cambió el plan
         *     en el medio (hay que mirarlo de nuevo); `404 NO_ENCONTRADO`. Queda en la bitácora, con quién, de qué plan a cuál y cuándo entra.
         */
        post: operations["cambiar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/pagos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * El historial de pagos de una cuenta
         * @description Los pagos que se registraron a mano para la cuenta, del más reciente al más antiguo (por fecha de pago, luego por cuándo se registraron), paginados. El total va en
         *     la cabecera `X-Total-Count`. Solo lectura. `404 NO_ENCONTRADO` si la cuenta no existe.
         */
        get: operations["historial"];
        put?: never;
        /**
         * Registrar el pago de una cuenta
         * @description Anota a mano que el cliente pagó: periodo que cubre, monto en soles, medio, fecha y, si se quiere, una referencia (número de operación) y una nota. **No hay pasarela de
         *     pago, a propósito.** Con `extender_vencimiento` el vencimiento de la suscripción pasa a ser la medianoche (Lima) del día siguiente a `periodo_hasta`: solo si eso lo
         *     adelanta (`409 EXTENSION_SIN_EFECTO`) y solo en un plan que vence (`409 PLAN_SIN_VENCIMIENTO`); no cambia el plan ni los días de gracia. Una cuenta no puede repetir
         *     medio y referencia (`409 PAGO_DUPLICADO`), así un reintento no anota el pago dos veces. `409 CAMBIO_CONCURRENTE` si otro administrador movió el vencimiento en el medio.
         *     `422` por un periodo, monto, medio o fecha inválidos (`PERIODO_INVALIDO`, `MONTO_INVALIDO`, `MEDIO_INVALIDO`, `FECHA_DE_PAGO_INVALIDA`, `FECHA_DE_PAGO_FUTURA`,
         *     `REFERENCIA_INVALIDA`, `NOTA_INVALIDA`). `404 NO_ENCONTRADO` si la cuenta no existe. Queda en la bitácora, en la misma transacción: periodo, monto, medio y nuevo
         *     vencimiento; la referencia y la nota no.
         */
        post: operations["registrar_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/baja": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Dar de baja una cuenta
         * @description Para el cliente que se fue: la cuenta y sus empresas salen de los listados operativos (cuentas y empresas) y del cálculo de consumo y
         *     cobro. **No se borra nada**: los comprobantes, XML y CDR se conservan (la retención es una obligación legal del emisor) y siguen
         *     siendo consultables, y el RUC de sus empresas **sigue ocupado**, así que nadie más puede registrarlo. La cuenta de baja se puede
         *     consultar con `bajas=INCLUIDAS` o `bajas=SOLO` en los listados, y se revierte con `reponer`. **No corta por sí sola el acceso del
         *     cliente al portal ni a la API**: para cortar el servicio está la suspensión. Queda en la bitácora de auditoría, con el motivo si se
         *     dio, en la misma transacción. `409 CUENTA_YA_DE_BAJA` si ya lo estaba; `404 NO_ENCONTRADO` si no existe; `422 MOTIVO_INVALIDO` si
         *     el motivo pasa de 200 caracteres.
         */
        post: operations["darDeBaja"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{cuentaId}/usuarios/{usuarioId}/verificacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reenviar a un usuario el correo para verificar su dirección
         * @description Crea un enlace nuevo de un solo uso (vale 24 horas) y se lo manda al usuario. Queda en la bitácora de auditoría. `409 CORREO_YA_VERIFICADO` si
         *     ya verificó su correo: no hay nada que reenviar. Los demás errores, como al mandar el correo de restablecimiento.
         */
        post: operations["reenviarVerificacion_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{cuentaId}/usuarios/{usuarioId}/restablecimiento": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mandar a un usuario el correo para restablecer su contraseña
         * @description Crea un enlace de un solo uso (vale 1 hora) y se lo manda al correo del usuario; es él quien elige su contraseña, el administrador
         *     nunca la ve ni la fija, y **ni el enlace ni su token salen en la respuesta**. Vale también con el correo ya verificado. Queda en la
         *     bitácora de auditoría. `404 NO_ENCONTRADO` si el usuario no existe **en esa cuenta**; `409 USUARIO_INACTIVO` si está desactivado;
         *     `503 CORREO_NO_CONFIGURADO` si el servidor no entrega correos (no se crea nada); `502 CORREO_NO_ENVIADO` si el servidor de correo lo
         *     rechaza (el intento queda en la bitácora).
         */
        post: operations["restablecer_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{cuentaId}/usuarios/{usuarioId}/impersonar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Entrar al portal como un usuario del cliente
         * @description Abre una **sesión de soporte**: un JWT del usuario elegido, de **solo lectura** (cualquier escritura, incluida cambiar la contraseña, las credenciales SOL,
         *     las API keys o emitir, responde `403 SOPORTE_SOLO_LECTURA`), que vence a los 15 minutos y **no se puede renovar** (no hay refresh). Queda en la bitácora
         *     quién impersonó a quién, cuándo y por cuánto tiempo, en la misma transacción: si no se puede dejar constancia, el token no sale. El cliente ve el acceso en
         *     su propio historial (sin el nombre del administrador). Solo sirve la sesión de un administrador: con la clave de plataforma, `403 REQUIERE_ADMINISTRADOR`.
         *     `404 NO_ENCONTRADO` si el usuario no existe en esa cuenta; `409 USUARIO_INACTIVO` si está desactivado. La respuesta no se guarda en ninguna caché.
         */
        post: operations["impersonar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/configuracion/plantillas/{tipo}/vista-previa": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Vista previa de un correo
         * @description Cómo se vería ese asunto y ese cuerpo con valores de ejemplo para cada variable, validados como al guardar (`422 PLANTILLA_INVALIDA`). No guarda nada ni deja registro.
         */
        post: operations["vistaPrevia"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/comprobantes/{id}/reintento": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reintentar el envío de un comprobante
         * @description Reintenta ahora el envío a SUNAT de un comprobante en error de envío, con las mismas reglas que el envío manual de la empresa. Que SUNAT vuelva a fallar **no** es un error:
         *     responde `200` con el estado en que quedó (`ERROR_ENVIO` y el fault, o el resultado de SUNAT). `409 ESTADO_NO_ENVIABLE` si ya no está por enviar, `409 FUERA_DE_PLAZO`
         *     si se pasó el plazo (y queda en ese estado), `404 NO_ENCONTRADO` si no existe. Queda en la bitácora, también cuando no se pudo reintentar.
         */
        post: operations["reintentar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/comprobantes/{id}/descarte": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Descartar un comprobante en error de envío
         * @description Deja de intentar un envío que falla: el comprobante pasa a `DESCARTADO` (terminal; hay que emitir de nuevo) y se saca del outbox. Solo desde error de envío
         *     (`409 ESTADO_NO_DESCARTABLE`; si cambió de estado en el medio, `409 ESTADO_CONFLICTO`). El `motivo` es obligatorio (`422 MOTIVO_REQUERIDO`) y de hasta 200 caracteres
         *     (`422 MOTIVO_LARGO`). `404 NO_ENCONTRADO` si no existe. Queda en la bitácora, en la misma transacción, con el motivo.
         *
         *     En una empresa en producción con credenciales SOL, antes de descartar una factura o una nota de factura se le pregunta a SUNAT si ya la recibió (`getStatusCdr`):
         *     si la tiene se aplica su CDR y responde `409 SUNAT_YA_LO_TIENE`; si no se le puede preguntar, `503 SUNAT_NO_DISPONIBLE`. Una boleta o una nota de boleta (serie B)
         *     no se consulta, porque SUNAT no contesta `getStatusCdr` para ellas: se descarta sin confirmar y la bitácora lo anota. Si el envío se cortó después de que SUNAT la
         *     aceptó, descartarla deja dos boletas por la misma venta; en la duda, reintente el envío antes.
         */
        post: operations["descartar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/auth/segundo-factor/verificar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Paso 2: verificar el segundo factor y entrar
         * @description Acepta los 6 dígitos de la app o un código de recuperación (cada uno sirve una vez). Un código de la app tampoco se acepta
         *     dos veces. `401 CODIGO_INVALIDO` si no es válido; tras 5 fallos, `429 DEMASIADOS_INTENTOS` durante 15 minutos.
         */
        post: operations["verificar_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/auth/segundo-factor/confirmar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Paso 2b: confirmar con el primer código y entrar
         * @description Con el primer código de la app confirma el segundo factor y abre la sesión. Devuelve **una sola vez** diez códigos de
         *     recuperación. `401 CODIGO_INVALIDO` si el código no es válido; tras 5 fallos, `429 DEMASIADOS_INTENTOS` durante 15 minutos.
         */
        post: operations["confirmar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/auth/segundo-factor/configurar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Paso 2a: generar el secreto de la app de autenticación
         * @description Solo si el segundo factor no está configurado (`409 SEGUNDO_FACTOR_YA_CONFIGURADO` si lo está: la contraseña sola no puede
         *     cambiarlo). Devuelve el QR para escanear y el secreto en texto. Pedirlo otra vez reemplaza el anterior.
         */
        post: operations["configurar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Iniciar sesión de administrador: paso 1, contraseña
         * @description Con la contraseña correcta devuelve un `desafio` de 5 minutos y el `paso` siguiente: `CONFIGURAR_SEGUNDO_FACTOR` si el
         *     administrador todavía no tiene app de autenticación, `VERIFICAR_SEGUNDO_FACTOR` si ya la tiene. La contraseña sola nunca da
         *     una sesión. Tras 5 contraseñas erróneas en 15 minutos para ese correo (o 20 fallos desde la misma IP),
         *     `429 DEMASIADOS_INTENTOS_LOGIN` durante 15 minutos, aun con la contraseña correcta.
         */
        post: operations["login_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/administradores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Crear una cuenta de administrador
         * @description Alta de un administrador de la plataforma (email + password); no pertenece a ninguna cuenta de cliente.
         */
        post: operations["crear_11"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/webhooks/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Eliminar un webhook
         * @description Borra el endpoint y su log de entregas. `404 NO_ENCONTRADO` si no es de la empresa. Requiere sesión del portal.
         */
        delete: operations["eliminar"];
        options?: never;
        head?: never;
        /**
         * Cambiar un webhook
         * @description Cambia la URL, la descripción, los eventos o el estado. Con `activo: true` reactiva un endpoint apagado o pausado por khipu y pone en cero sus fallos; las
         *     entregas que quedaron pendientes se mandan enseguida. Requiere sesión del portal.
         */
        patch: operations["actualizar_2"];
        trace?: never;
    };
    "/v1/webhooks/{id}/entregas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Log de entregas
         * @description Las entregas del endpoint, de la más reciente a la más antigua, con su estado, intentos y el último código HTTP o error. El total va en `X-Total-Count`. Se guardan 30 días.
         */
        get: operations["entregas"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/webhooks/eventos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Eventos disponibles
         * @description Los eventos a los que puede suscribirse un endpoint.
         */
        get: operations["eventos"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sire/rvie": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar el registro de ventas (RVIE) de un periodo (experimental)
         * @description **EXPERIMENTAL: formato reconstruido; no lo use para reemplazar una propuesta real antes de validarlo con el PVSIRE.** **Cubre solo lo emitido en khipu**
         *     (cabecera `X-Rvie-Alcance: solo-khipu`): antes de reemplazar la propuesta, concilie con `POST /v1/sire/rvie/conciliacion` y exija `solo_en_sunat` vacío.
         *
         *     Un ZIP `LE<RUC><AAAAMM>00140400021112.zip` con el `.txt` del mismo nombre: una fila por comprobante emitido en el periodo que SUNAT tiene —aceptado, con o sin
         *     observaciones, o anulado después—, en el orden fecha, tipo, serie y número. 40 campos separados por `|` (también al final de la fila), fin de línea CRLF, UTF-8,
         *     fechas `dd/mm/aaaa`, importes con 2 decimales en la moneda del comprobante y tipo de cambio con 3 (`0.000` en soles). Cada boleta va en su propia fila; la
         *     nota de crédito va en negativo y lleva en los campos 29–32 el comprobante que modifica y en el 34 su motivo; un anulado va con estado `2` e importes en cero.
         *
         *     Lo que todavía va en camino a SUNAT (firmado, enviado o con error de envío) no entra: cuántos son lo dice la cabecera `X-Rvie-Pendientes-Sunat`, y
         *     `X-Rvie-Registros` cuántas filas trae el archivo (contadas al prepararlo: si un comprobante cambia de estado durante la descarga puede ir uno más o menos). Errores, siempre antes de empezar a escribir el archivo: `400 PARAMETRO_INVALIDO` (periodo que no es `AAAAMM`
         *     o que todavía no empieza), `404 NO_ENCONTRADO` (nada en el periodo), `422 RVIE_EXCEDE_EL_TOPE` (más de 100 000 comprobantes) y `422 RVIE_SIN_TIPO_CAMBIO`
         *     (comprobantes en otra moneda sin tipo de cambio, que el campo 28 pide: con `permitir_sin_tipo_cambio=true` salen con el campo vacío y la cuenta va en
         *     `X-Rvie-Sin-Tipo-Cambio`). Si la lectura falla a mitad de camino, la descarga se corta con un ZIP inválido, nunca con un archivo que parezca completo.
         */
        get: operations["exportarRvie"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/planes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Planes publicados
         * @description Los planes que se venden en la página de precios, del más barato al más caro, con los límites que mandan hoy. Un plan a medida (activo pero no visible en
         *     la publicidad) o fuera de la oferta no sale. Público, sin autenticación; se puede guardar en caché unos minutos.
         */
        get: operations["publicados"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consultar una guía de remisión
         * @description Estado, ticket, respuesta de SUNAT (`cdr`), datos del traslado y enlaces de descarga.
         */
        get: operations["obtener_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision/{id}/xml": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar el XML firmado
         * @description El `DespatchAdvice` tal como se envió a SUNAT. Nombre `RUC-09-SERIE-NUMERO.xml` (o `-31-`).
         */
        get: operations["xml"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision/{id}/pdf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar la representación impresa (PDF)
         * @description Emisor, destinatario, datos del traslado (motivo, modalidad, partida, llegada, transportista, vehículos y conductores), bienes y el valor resumen. Lleva
         *     el QR con la URL de consulta que SUNAT pone en el CDR cuando la trae; antes de eso, sale sin QR. Se genera la primera vez y se guarda.
         */
        get: operations["pdf"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/guias-remision/{id}/cdr": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar la constancia de recepción (CDR)
         * @description El ZIP que devolvió SUNAT al consultar el ticket; con `formato=xml`, el `ApplicationResponse`. `404 SIN_CDR` mientras no haya.
         */
        get: operations["cdr"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consultar un comprobante
         * @description Estado actual, respuesta de SUNAT (`cdr`), totales, forma de pago y enlaces de descarga. Úselo para hacer seguimiento
         *     de un comprobante que quedó en `ERROR_ENVIO` o `ENVIADO`. Sirve también para las notas de crédito/débito emitidas con
         *     `POST /v1/notas` (traen el bloque `nota`), las liquidaciones de compra de `POST /v1/liquidaciones-compra` (traen el bloque `liquidacion`) y las retenciones y percepciones de `POST /v1/retenciones` y `POST /v1/percepciones` (traen el bloque `agente`); una factura incluye en `notas` las notas emitidas sobre ella y en `eventos` el
         *     historial de intentos y cambios de estado (fecha, estado resultante y motivo), del más antiguo al más reciente. Si la empresa
         *     tiene activado el envío automático al cliente, `envio_al_cliente` dice si el correo con el PDF, el XML y el CDR salió, sigue pendiente o falló.
         */
        get: operations["obtener_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/xml": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar el XML firmado
         * @description El XML UBL 2.1 exactamente como se envió a SUNAT (firma XML-DSig incluida). Nombre de archivo `RUC-01-SERIE-NUMERO.xml`.
         */
        get: operations["xml_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/pdf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar la representación impresa (PDF)
         * @description PDF con los datos del emisor y del adquirente, ítems, totales, el código QR (RUC|tipo|serie|número|IGV|total|fecha|
         *     tipo y número de documento del adquirente|hash) y el valor resumen (hash de la firma). Se genera la primera vez que se
         *     pide y se guarda junto al XML. Disponible desde que el comprobante está `FIRMADO`; `422 SIN_FIRMA` antes.
         */
        get: operations["pdf_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/cdr": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar la constancia de recepción (CDR)
         * @description El CDR es la prueba de que SUNAT recibió y aceptó (o rechazó) el comprobante. Por defecto se entrega el ZIP tal
         *     como lo devolvió SUNAT; con `formato=xml` se extrae el `ApplicationResponse`. Responde `404 SIN_CDR` mientras
         *     SUNAT no haya respondido.
         *
         *     **Boleta informada en un resumen diario**: SUNAT no emite un CDR por boleta en el resumen; el que se entrega es el del
         *     resumen (`El Resumen diario RC-…, ha sido aceptado`). Para no cambiar el contrato, el nombre de archivo sigue siendo
         *     `R-<RUC>-03-<serie>-<número>`; lo que lo distingue es la cabecera `X-Cdr-Resumen` con el identificador del resumen
         *     (`RC-aaaammdd-n`), que también trae `cdr.resumen` en la consulta del comprobante. Sin la cabecera, el CDR es el del propio comprobante.
         */
        get: operations["cdr_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/{id}/bajas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Comunicaciones de baja de un comprobante
         * @description De la más reciente a la más antigua; normalmente una.
         */
        get: operations["deComprobante"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/resumen": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Resumen de comprobantes de un período
         * @description Una foto de la empresa en un rango de fecha de emisión (`desde`/`hasta`, inclusive; un lado ausente deja el rango abierto de ese lado): cuántos comprobantes
         *     se **emitieron** (firmados, en cualquier estado), cuántos **aceptó SUNAT con su CDR**, cuántos piden **atención** (rechazados, con error de envío y fuera de plazo,
         *     cada clase por separado) y cuánto se **facturó por moneda**. Lo facturado cuenta lo aceptado y lo que está en camino, no lo rechazado, lo que se pasó del plazo
         *     ni lo dado de baja; las notas de crédito restan y las de débito suman. `desde > hasta` responde `400 RANGO_INVALIDO`; una fecha mal formada,
         *     `400 PARAMETRO_INVALIDO`.
         */
        get: operations["resumen"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/resumen/por-dia": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Emisión por día de un período
         * @description La emisión de la empresa día por día en un rango de fecha de emisión (`desde`/`hasta`, inclusive, **los dos obligatorios**, hasta 62 días): por cada día con
         *     comprobantes, cuántos se **emitieron** y cuánto se **facturó por moneda**, con los mismos criterios que `GET /v1/facturas/resumen`. Los días sin comprobantes no
         *     vienen. Falta una fecha o está mal formada: `400 PARAMETRO_INVALIDO`; `desde > hasta` o más de 62 días: `400 RANGO_INVALIDO`.
         */
        get: operations["porDia"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/exportacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Exportar comprobantes a CSV o Excel
         * @description Una fila por comprobante que cumple los filtros —los mismos del listado—, lo más reciente primero, para el registro de ventas o el contador. Columnas (en
         *     `snake_case`, para programar contra ellas): `tipo` (catálogo 01), `serie`, `numero`, `fecha_emision`, `cliente_tipo_doc`, `cliente_num_doc`, `cliente`, `moneda`,
         *     `tipo_cambio` (soles por unidad de `moneda` si se informó; vacío si no), las bases `gravado`, `exonerado`, `inafecto`, `gratuito` y `exportacion`, `igv`, `icbper`,
         *     `isc`, `ivap`, `otros_tributos`, `total_descuentos`, `total_cargos`, `total_anticipos`, `redondeo`, `total`, `estado` (un comprobante dado de baja sale
         *     `ANULADO`) y `documento_modificado` (solo en notas: tipo y serie-número, `01 F001-125`).
         *
         *     **Importes**: con 2 decimales y en la moneda del comprobante. Una **nota de crédito (07) sale en negativo** —todos sus importes— para que la columna sume la
         *     venta neta; la de débito (08), en positivo. Cuadran así: `total` = `gravado` + `exonerado` + `inafecto` + `exportacion` + `igv` + `ivap` + `isc` + `icbper` +
         *     `otros_tributos` + `total_cargos` − `total_descuentos` + `redondeo`; `gratuito` no se cobra, y las bases y el IGV ya vienen netos de anticipos
         *     (`total_anticipos` es informativo).
         *
         *     **Qué entra**: sin el filtro `estado` quedan fuera los que no son ventas (`RECHAZADO`, `INVALIDO`, `DESCARTADO`); con `estado` sale exactamente ese estado. Sin el
         *     filtro `tipo` quedan fuera también los tipos que no son ventas (`04` liquidación de compra, `20` retención, `40` percepción), cuyas columnas no son las de una
         *     venta; con `tipo` sale ese tipo. Un comprobante emitido después de empezar la exportación no entra; uno que cambia de estado mientras tanto sale con el estado de ese momento.
         *
         *     `formato=csv` (por defecto) es UTF-8 con BOM, separado por comas —el de Excel con la configuración regional de Perú— o por punto y coma con
         *     `separador=punto_y_coma`, con punto decimal; los textos que empiezan por `= + - @` llevan delante una comilla simple para que una hoja de cálculo no los ejecute
         *     como fórmula. `formato=xlsx` es un libro de Excel con los importes como números (recomendado para abrirlo en Excel). El archivo se escribe mientras se lee: si
         *     la conexión se corta a la mitad (o pasa el tiempo máximo de la respuesta, 2 minutos), el archivo llega incompleto y se reconoce por el corte —el CSV termina sin
         *     su última fila y el XLSX no abre—: vuelva a pedirlo. **Tope: 5000 comprobantes por exportación**; con más responde `422 EXPORTACION_EXCEDE_EL_TOPE` con cuántos
         *     hay (acote las fechas y exporte por tramos). Sin resultados sale el archivo con la cabecera sola. Un `formato` o `separador` con otro valor es
         *     `400 PARAMETRO_INVALIDO`.
         */
        get: operations["exportarComprobantes"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/facturas/descarga": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Descargar comprobantes en un ZIP
         * @description Los archivos de los comprobantes que cumplen los filtros —los mismos del listado— en un ZIP que se va escribiendo mientras se leen: `<RUC>-<tipo>-<serie>-<número>.xml` (XML firmado),
         *     `.pdf` (representación impresa) y `R-<RUC>-….zip` (CDR, solo los que ya lo tienen). `tipos` elige cuáles (`xml,pdf,cdr`, por defecto los tres). Un comprobante todavía sin firmar no tiene archivos y se
         *     omite; si un archivo no se puede leer, el ZIP lo anota en `LEEME.txt` en vez de fallar entero. **Tope: 500 comprobantes por descarga**; con más responde `422 DESCARGA_EXCEDE_EL_TOPE`
         *     con cuántos hay (acote las fechas y descargue por tramos), y sin resultados `404 NO_ENCONTRADO`. Un `tipos` con otro valor es `400 PARAMETRO_INVALIDO`.
         */
        get: operations["descargar"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Ver la empresa
         * @description RUC, razón social, entorno SUNAT (`BETA` u homologación / `PRODUCCION`), si tiene credenciales SOL y API SUNAT, la vigencia del certificado, el domicilio fiscal y la cuenta de detracciones. Nunca devuelve secretos.
         */
        get: operations["ver"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/personalizacion-pdf/vista-previa": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Vista previa del diseño
         * @description PDF de una factura de ejemplo con el diseño indicado en los parámetros (los que se omitan toman el valor guardado)
         *     y el logo actual de la empresa. No guarda nada: sirve para probar antes de `PUT`.
         */
        get: operations["vistaPrevia_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Mi cuenta: plan y consumo del mes
         * @description El nombre de la cuenta, el plan de hoy con su estado de pago (`VIGENTE`, `EN_GRACIA`, `VENCIDA`), hasta cuándo está pagado y una bajada programada si la
         *     hay, y los documentos consumidos este mes (America/Lima) contra el tope del plan. Solo lectura: el plan lo cambia el equipo de khipu. Solo con sesión de
         *     cuenta (portal), no con API key: `401 NO_AUTORIZADO`.
         */
        get: operations["ver_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/usuarios": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Usuarios de la cuenta
         * @description Activos y desactivados, por fecha de alta.
         */
        get: operations["usuariosDeLaCuenta"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/accesos-de-soporte": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Accesos de soporte a mi cuenta
         * @description Cada vez que alguien del equipo de khipu miró el portal como uno de los usuarios de la cuenta: cuándo, a qué usuario y por cuánto tiempo como máximo.
         *     Los más recientes primero, hasta 100. **No** dice qué administrador fue. Solo con sesión de cuenta (portal), no con API key.
         */
        get: operations["accesosDeSoporte"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/consultas/validez": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consultar la validez de un comprobante
         * @description Estado de un comprobante de cualquier emisor —el propio o un proveedor— por sus datos. Con credenciales API SUNAT
         *     (`PUT /v1/empresa/credenciales-api-sunat`) se consulta la API REST de SUNAT («consulta integrada», `fuente: API_REST`):
         *     `estado` `NO_EXISTE` (0), `ACEPTADO` (1), `DE_BAJA` (2, anulado), `AUTORIZADO` (3) o `NO_AUTORIZADO` (4, comprobantes
         *     físicos), más `estado_ruc`, `condicion_domicilio` y `observaciones` (p. ej. si la fecha o el monto no coinciden); ahí
         *     `fecha` es obligatoria y `monto` lo es para comprobantes electrónicos. Sin ellas, o si la API no responde o rechaza la consulta y hay clave SOL,
         *     se usa `billValidService` (`fuente: SOAP`, solo producción): `ACEPTADO` (0001), `RECHAZADO` (0002), `DE_BAJA` (0003),
         *     `NO_EXISTE` (0011), `AJENO` (0012) o `ERROR_CONSULTA`; `aviso` dice por qué. Errores: `422 CREDENCIALES_SUNAT_NO_CARGADAS`,
         *     `422 CREDENCIALES_API_SUNAT_RECHAZADAS`, `422 CREDENCIALES_SOL_RECHAZADAS`, `422 CONSULTA_RECHAZADA` (SUNAT rechazó los
         *     datos de la consulta), `503 SUNAT_NO_DISPONIBLE`.
         */
        get: operations["validez"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/consultas/ruc/{ruc}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consultar un RUC en el padrón de SUNAT
         * @description Razón social, estado, condición de domicilio y domicilio fiscal de un contribuyente, del padrón reducido que SUNAT publica cada día (no consulta a SUNAT en cada llamada).
         *     `actualizado_al` dice de qué carga viene la respuesta: un RUC creado después no aparece, así que `404 NO_ENCONTRADO` no prueba que no exista. `400 PARAMETRO_INVALIDO` si
         *     no es un RUC (11 dígitos, prefijo y dígito verificador), `503 PADRON_NO_CARGADO` si el padrón todavía no se cargó y `429 DEMASIADAS_CONSULTAS` al pasar el cupo
         *     (60 por minuto y 5 000 por día, por empresa). No requiere credenciales SOL.
         */
        get: operations["consultarRuc"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/catalogos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar catálogos
         * @description Índice de los catálogos disponibles con su número, nombre y cantidad de entradas. Con `completo=true` devuelve además las entradas de cada uno (una sola llamada para cachear toda la referencia).
         */
        get: operations["listarCatalogos"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/catalogos/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consultar un catálogo
         * @description Devuelve todas las entradas del catálogo con su código, descripción y columnas adicionales (por ejemplo, el
         *     catálogo 07 indica el código de tributo de cada afectación y el 53 el nivel —línea o global— de cada
         *     cargo/descuento). Responde `404 NO_ENCONTRADO` si el número no existe.
         */
        get: operations["obtenerCatalogo"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/banner": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aviso de mantenimiento vigente
         * @description El aviso que se está mostrando ahora a los clientes: su texto y su vigencia. `datos` es nulo si no hay ninguno (uno programado para después o ya vencido no se muestra).
         *     Público, sin autenticación, y nunca se guarda en caché.
         */
        get: operations["vigente"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/bajas/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consultar una comunicación de baja
         * @description Si está `ENVIADA`, consulta el ticket en SUNAT en el acto y devuelve el resultado actualizado.
         */
        get: operations["obtener_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Usuario de la sesión
         * @description Correo, rol y cuenta del usuario autenticado por JWT, y si le falta aceptar los términos vigentes.
         */
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/origen": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * IP de origen resuelta
         * @description La IP que el backend usa como origen de esta petición (y que registra en la bitácora de auditoría). Sirve para comprobar la cadena de proxies de confianza (`TRUSTED_PROXIES`).
         */
        get: operations["origen"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/monitor": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Monitor global de emisión
         * @description Cómo va la emisión de **todas** las empresas: los comprobantes de las últimas 24 horas por hora (aceptados, rechazados, con error de envío, en camino), el total
         *     del día de Lima con su tasa de rechazo, la cola del outbox (pendientes, vencidos y una **alerta** cuando hay envíos vencidos hace más de 5 minutos, señal de que
         *     el trabajo que la vacía no corre), los resúmenes diarios o bajas con ticket que SUNAT no responde pasadas N horas (`resumenes_sin_respuesta`; N =
         *     `app.monitor.resumen-sin-respuesta-horas`, 6 por defecto: khipu reconsulta el ticket sin abandonarlo, así que esta alerta es la única señal de uno atascado)
         *     y si los servicios de SUNAT contestan. El estado de SUNAT es una lectura guardada hasta 30 segundos: refrescar el monitor no
         *     llama a SUNAT cada vez ni envía ningún comprobante.
         */
        get: operations["monitorear"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/errores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * La cola de errores de todas las empresas
         * @description Los comprobantes con problema de **todas** las empresas, de la emisión más antigua a la más reciente (la más urgente primero), paginados; el total va en la cabecera
         *     `X-Total-Count`. Cuatro clases: `ERROR_DE_ENVIO` (SUNAT no contestó o falló: se reintenta solo, y un administrador puede reintentar a mano o descartar),
         *     `ERROR_DE_FORMATO` (SUNAT lo rechazó con un fault 1000–1999: terminal, no se reintenta), `FUERA_DE_PLAZO` (terminal: hay que emitir de nuevo) y `BOLETA_SIN_ENVIAR`
         *     (una boleta que el cliente dejó `FIRMADA` sin enviar y ya está en el 5.º día o más: no falló, pero el resumen diario cierra el 7.º; enviarla es decisión del cliente, así que
         *     el administrador no la reintenta, la avisa). Cada fila trae el fault
         *     de SUNAT, los intentos y, si hay un reintento programado, cuándo. `q` busca el cliente por RUC (prefijo), razón social, o nombre o correo de su cuenta, sin distinguir
         *     mayúsculas ni tildes. Solo lectura.
         */
        get: operations["listar_7"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar las empresas de la plataforma
         * @description Todas las empresas, de la más reciente a la más antigua, paginadas; incluye las dadas de alta por una integración, que no tienen
         *     cuenta. El total de resultados va en la cabecera `X-Total-Count` y refleja los filtros, que se combinan con «y». Por cada
         *     empresa: su cuenta, su entorno, el estado del certificado y los días que le quedan (hoy es la fecha de Lima), si tiene
         *     credenciales SOL cargadas, sus series activas, los documentos emitidos en el mes y la fecha de la última emisión. Del certificado
         *     y de las credenciales solo se informa su estado, **nunca su contenido**. `entorno`, `certificado` o `bajas` con un valor que no existe
         *     responden `400`. **Las empresas de cuentas dadas de baja (#201) no salen** salvo que se pida con `bajas`: `INCLUIDAS` las mezcla y
         *     `SOLO` devuelve únicamente esas; el total las trata igual. Las empresas sin cuenta nunca están de baja.
         */
        get: operations["listar_8"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Abrir una empresa
         * @description Lo mismo que ve su dueño, en solo lectura: datos fiscales y domicilio, estado del certificado y de las credenciales SOL (**sin
         *     exponer su contenido**), series, establecimientos, API keys (**solo el prefijo**, nunca el secreto ni el hash), personalización del
         *     PDF (si hay logo, sin dónde está guardado), los 10 comprobantes más recientes con el detalle del CDR de SUNAT, los últimos 20
         *     cambios de estado de esos comprobantes y el outbox pendiente de la empresa. No se audita. `404 NO_ENCONTRADO` si la empresa no
         *     existe; un id que no es un UUID responde `400`.
         */
        get: operations["abrir"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/empresas/{id}/consumo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consumo mensual de una empresa
         * @description Los documentos que consumió la empresa en un mes, con la misma definición que el consumo de la cuenta: solo comprobantes aceptados por SUNAT, por fecha de
         *     emisión, en el mes calendario de Lima. `mes` es `AAAA-MM`; sin él, el mes en curso. `404 NO_ENCONTRADO` si la empresa no existe.
         */
        get: operations["deEmpresa"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Abrir una cuenta
         * @description Los usuarios de la cuenta (rol, si verificaron su correo y su último acceso), sus empresas (RUC, razón social, entorno y si
         *     tienen certificado y credenciales SOL cargados, **sin exponer su contenido**) y su actividad reciente: los 10 comprobantes
         *     de fecha de emisión más reciente entre todas sus empresas y las últimas 10 acciones del administrador sobre la cuenta.
         *     Solo lectura; no se audita. `404 NO_ENCONTRADO` si la cuenta no existe.
         */
        get: operations["abrir_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/plan/previsualizacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Previsualizar un cambio de plan
         * @description Lo que pasaría con un cambio, **sin hacerlo**: si sube, baja o renueva (lo decide el precio), cuándo entra (subir y renovar, ya; bajar, al inicio del ciclo
         *     siguiente), cuántos documentos consumió la cuenta este mes —solo los comprobantes aceptados por SUNAT— y si ese consumo ya supera el tope de documentos del plan
         *     nuevo. `404 NO_ENCONTRADO` (cuenta o plan); `409 PLAN_INACTIVO` si el plan está fuera de la oferta.
         */
        get: operations["previsualizar"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/cuentas/{id}/consumo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consumo mensual de una cuenta
         * @description Los documentos que consumió la cuenta en un mes, con el detalle por empresa (cada una, aunque no haya emitido nada). **Solo cuentan los comprobantes
         *     que SUNAT aceptó** (`ACEPTADO` o `ACEPTADO_CON_OBS`, y también los que después se dieron de baja, `ANULADO`: lo aceptado ya consumió), por su fecha de
         *     emisión, dentro del mes calendario en America/Lima. No cuentan los rechazados, los errores de envío, los fuera de plazo ni los que están en camino; la
         *     comunicación de baja no suma otro documento, y un comprobante reintentado hasta que lo aceptan cuenta una sola vez.
         *     `mes` es `AAAA-MM`; sin él, el mes en curso (`400 PARAMETRO_INVALIDO` si está mal escrito). `404 NO_ENCONTRADO` si la cuenta no existe.
         */
        get: operations["deCuenta"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/consumo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consumo de todas las cuentas contra su plan
         * @description Una fila por cuenta (las dadas de baja no salen): lo que consumió en el mes —solo comprobantes que SUNAT aceptó, por fecha de emisión, en el mes calendario de
         *     America/Lima, igual que el consumo de una cuenta— y el tope de su plan. **Se compara con el plan de hoy**, con los límites que rigen hoy, también si el mes es
         *     pasado. `porcentaje` es el del tope usado, hacia abajo, y falta si el plan no tiene tope; `en_alerta` es que ya llegó a `umbral_de_alerta` (inclusive).
         *     `estado_del_plan` es el de pago de su suscripción vigente: `VIGENTE`, `EN_GRACIA` (venció pero aún se la sirve) o `VENCIDA`.
         *     `filtro`: `TODAS` (por defecto), `CERCA_DEL_LIMITE` (en alerta) o `PLAN_VENCIDO` (venció su pago, en gracia o ya sin servicio). `orden`: `PORCENTAJE` (por defecto,
         *     de mayor a menor, los planes sin tope al final) o `DOCUMENTOS`. `mes` es `AAAA-MM`; sin él, el mes en curso. Paginado; el total, que refleja el filtro, va en
         *     `X-Total-Count`.
         */
        get: operations["listar_10"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/consumo/exportacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Exportar el consumo de todas las cuentas
         * @description Lo mismo que el listado, completo y sin paginar, como CSV (UTF-8 con marca de orden de bytes, registros separados por CRLF) que se descarga como
         *     `consumo-AAAA-MM.csv`. Respeta `mes`, `filtro` y `orden`. Un plan sin tope dice `ilimitado` y no tiene porcentaje; las fechas (`pagado_hasta`, `se_sirve_hasta`) son el último día cubierto en hora de Lima (`AAAA-MM-DD`), como en la pantalla. Los textos que empiezan
         *     por `=`, `+`, `-` o `@` llevan una comilla simple delante para que una hoja de cálculo no los ejecute como fórmula.
         */
        get: operations["exportar"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/configuracion/plantillas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Plantillas de los correos
         * @description Todos los correos que la plataforma manda, con el texto con que salen ahora, el de fábrica, si alguien lo cambió y las variables que admite cada uno (`{nombre}`). Solo lectura.
         */
        get: operations["plantillas"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/comprobantes/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * La ficha de un comprobante
         * @description Un comprobante de cualquier empresa: su empresa (y la cuenta, si tiene), su identidad ante SUNAT, fecha de emisión, estado, intentos de envío, el último error,
         *     lo que respondió SUNAT, si el XML y el CDR están guardados (sin su contenido) y sus bajas (`bajas[]`: la comunicación de baja `RA-…` o el resumen diario `RC-…`
         *     con su condición, estado, ticket, motivo, fechas y CDR; #362). Solo lectura; no se audita. `404 NO_ENCONTRADO` si no existe,
         *     `400 PARAMETRO_INVALIDO` si el id no es un UUID.
         */
        get: operations["ficha"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/avisos/credenciales-sol": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Empresas cuyas credenciales SOL SUNAT no acepta
         * @description Las empresas con comprobantes **atascados** en error de envío porque SUNAT rechazó su usuario o clave SOL (códigos 0102–0106 y 0111, o un 401 de la autenticación), de la que
         *     más tiene atascados a la que menos, paginadas; el total va en `X-Total-Count`. **Se deduce de los envíos que fallan ahora**: una empresa que no está emitiendo no aparece, y una
         *     que ya reenvió con éxito sale sola. Cada una dice cuántos comprobantes tiene así, cuándo falló el último y qué dijo SUNAT. Solo lectura.
         */
        get: operations["credencialesSol_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/avisos/certificados": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Empresas con el certificado por vencer o vencido
         * @description Las empresas cuyo certificado digital vence en **menos de 30 días** o ya venció (la que vence antes, o venció hace más, primero), paginadas; el total va en la cabecera
         *     `X-Total-Count`. Cada una dice el motivo (`CERTIFICADO_POR_VENCER` o `CERTIFICADO_VENCIDO`), los días que le quedan, a qué cuenta se le avisaría, cuándo se le avisó por
         *     última vez **por ese motivo** y si se puede avisar ahora (`puede_avisar`: hay a quién escribirle y no se avisó lo mismo en la última semana). No incluye las cuentas dadas
         *     de baja. Solo lectura.
         */
        get: operations["certificados"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Administrador de la sesión
         * @description Identidad del administrador autenticado por el JWT del backoffice.
         */
        get: operations["me_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/empresa/api-keys/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Revocar una API key
         * @description La llave deja de autenticar al instante y sin período de gracia; es idempotente. `404 NO_ENCONTRADO` si no pertenece a la empresa. Requiere sesión del portal.
         */
        delete: operations["revocarApiKey_1"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cuenta/invitaciones/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Revocar una invitación
         * @description El enlace deja de servir. `409 INVITACION_NO_PENDIENTE` si ya se aceptó o se revocó.
         */
        delete: operations["revocarInvitacion"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        ProductoGuardar: {
            /**
             * @description Código interno (opcional); único por empresa cuando se informa
             * @example SKU-001
             */
            codigo?: string;
            /**
             * @description Descripción de la línea, hasta 500 caracteres
             * @example Servicio de consultoría
             */
            descripcion: string;
            /**
             * @description Unidad de medida del catálogo 03 (`GET /v1/catalogos/03`): NIU unidad (bienes), ZZ unidad (servicios), KGM, HUR…
             * @example ZZ
             */
            unidad: string;
            /**
             * @description Afectación del IGV, catálogo 07: 10–17, 20, 21, 30–37 o 40
             * @example 10
             */
            tipo_afectacion_igv: string;
            /**
             * @description Precio unitario de la línea: con IGV incluido en la gravada (10), y sin impuesto en las demás. Mayor que cero
             * @example 118
             */
            precio_unitario: number;
            /**
             * @description Código de producto SUNAT (catálogo 25, UNSPSC de 8 dígitos); opcional
             * @example 80101500
             */
            codigo_sunat?: string;
        };
        ApiResponseProducto: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Producto"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
        Aviso: {
            /** @example 4013 */
            codigo?: string;
            /** @example El RUC 20100070970 del comprador no está activo en SUNAT (BAJA DEFINITIVA): SUNAT aceptará el comprobante con una observación */
            mensaje?: string;
        };
        Producto: {
            /** Format: uuid */
            id?: string;
            codigo?: string;
            descripcion?: string;
            unidad?: string;
            tipo_afectacion_igv?: string;
            precio_unitario?: number;
            codigo_sunat?: string;
            /** Format: date-time */
            creado_en?: string;
            /** Format: date-time */
            actualizado_en?: string;
        };
        PersonalizacionPdfRequest: {
            /**
             * @description `clasico` (bordes definidos, por defecto), `moderno` (aireado, acento de color), `sutil` (líneas finas), `corporativo` (cabecera y tabla en color) o `gris` (monocromo)
             * @example moderno
             */
            plantilla?: string;
            /**
             * @description Color de acento (títulos, número, cabecera de tabla según la plantilla). La plantilla `gris` lo ignora
             * @example #1F5F4A
             */
            color_primario?: string;
            /**
             * @description Texto final del PDF (una línea, hasta 300 caracteres); vacío = leyenda por defecto
             * @example Gracias por su preferencia. Consultas: ventas@empresa.pe
             */
            pie_de_pagina?: string;
            /**
             * @description Se imprime en el bloque «Observaciones» de cada comprobante que no traiga las suyas (hasta 1000 caracteres, admite saltos de línea)
             * @example Entrega en 48 h en Lima Metropolitana.
             */
            observaciones_por_defecto?: string;
        };
        ApiResponsePersonalizacionPdfResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PersonalizacionPdfResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        PersonalizacionPdfResponse: {
            /** @example moderno */
            plantilla?: string;
            /** @example #1F5F4A */
            color_primario?: string;
            /**
             * @description Si la empresa cargó un logo; se descarga en `GET /v1/empresa/logo`
             * @example true
             */
            tiene_logo?: boolean;
            /** @example Gracias por su preferencia */
            pie_de_pagina?: string;
            /** @example Entrega en 48 h */
            observaciones_por_defecto?: string;
        };
        /** @description Domicilio del local: va en el XML como `cac:RegistrationAddress` de los comprobantes de las series asignadas a él */
        DomicilioDto: {
            /**
             * @description Ubigeo INEI del distrito, catálogo 13 (`GET /v1/catalogos/13`); khipu completa distrito, provincia y departamento a partir de él
             * @example 150122
             */
            ubigeo: string;
            /**
             * @description Dirección completa en una sola línea, de 3 a 200 caracteres (regla 4094)
             * @example Av. Javier Prado Este 123 Of. 501
             */
            direccion: string;
            /**
             * @description Urbanización (opcional, hasta 25 caracteres)
             * @example Urb. Jardín
             */
            urbanizacion?: string;
            /**
             * @description Distrito (opcional: por defecto el del ubigeo)
             * @example MIRAFLORES
             */
            distrito?: string;
            /**
             * @description Provincia (opcional: por defecto la del ubigeo)
             * @example LIMA
             */
            provincia?: string;
            /**
             * @description Departamento (opcional: por defecto el del ubigeo)
             * @example LIMA
             */
            departamento?: string;
            /**
             * @description Código del establecimiento anexo declarado en el RUC; `0000` (por defecto) es el domicilio fiscal. Un código distinto debe existir en la ficha RUC (regla 3030)
             * @example 0000
             */
            codigo_establecimiento?: string;
        };
        EstablecimientoRequest: {
            /**
             * @description Código del anexo tal como figura en la ficha RUC (4 dígitos; `0000` es el domicilio fiscal, se configura en datos fiscales)
             * @example 0002
             */
            codigo: string;
            /**
             * @description Nombre propio del local (1–100 caracteres), solo para identificarlo en el portal y la API
             * @example Tienda Miraflores
             */
            nombre: string;
            domicilio: components["schemas"]["DomicilioDto"];
        };
        ApiResponseEstablecimientoResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EstablecimientoResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        DomicilioResponse: {
            /** @example 150122 */
            ubigeo?: string;
            /** @example Av. Javier Prado Este 123 Of. 501 */
            direccion?: string;
            /** @example Urb. Jardín */
            urbanizacion?: string;
            /** @example MIRAFLORES */
            distrito?: string;
            /** @example LIMA */
            provincia?: string;
            /** @example LIMA */
            departamento?: string;
            /** @example 0000 */
            codigo_establecimiento?: string;
        };
        EstablecimientoResponse: {
            /** @example 0002 */
            codigo?: string;
            /** @example Tienda Miraflores */
            nombre?: string;
            domicilio?: components["schemas"]["DomicilioResponse"];
            /**
             * @description `false` = dado de baja: no admite series nuevas ni emisiones
             * @example true
             */
            activo?: boolean;
            /**
             * @description `true` solo en el `0000`: es el domicilio fiscal de la empresa, se edita en datos fiscales y no se puede dar de baja
             * @example false
             */
            principal?: boolean;
        };
        PreferenciaDeEnvioAlClienteRequest: {
            /**
             * @description `true` envía el comprobante a su cliente cuando SUNAT lo acepta (si se emitió con `cliente.email`); `false` deja de hacerlo
             * @example true
             */
            enviar_automaticamente: boolean;
        };
        ApiResponsePreferenciaDeEnvioAlCliente: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PreferenciaDeEnvioAlCliente"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        PreferenciaDeEnvioAlCliente: {
            /**
             * @description Si la empresa envía automáticamente el comprobante aceptado a su cliente
             * @example true
             */
            enviar_automaticamente?: boolean;
            /**
             * @description Si el servidor de khipu tiene correo configurado. Sin él no se puede activar el envío (`503 CORREO_NO_CONFIGURADO`) y lo que quedó pendiente se omite
             * @example true
             */
            correo_disponible?: boolean;
        };
        DatosFiscalesRequest: {
            domicilio?: components["schemas"]["DomicilioDto"];
            /**
             * @description Cuenta de detracciones en el Banco de la Nación; se usa cuando una factura sujeta a detracción no indica `cuenta_banco_nacion`. `null` la borra
             * @example 00-000-123456
             */
            cuenta_detracciones?: string;
            /**
             * @description Nombre comercial (hasta 1500 caracteres, sin saltos de línea; regla 4092); va en el XML como `cac:PartyName` del emisor. `null` lo borra
             * @example Andina Store
             */
            nombre_comercial?: string;
            /**
             * @description `true` si la empresa está inscrita en el Padrón de Tasa Especial del IGV (MYPE de restaurantes y hoteles, Ley 31556): sus facturas, boletas y notas gravadas llevan la tasa reducida vigente (10.5 % desde el 2026-02-13) en vez del 18 %. SUNAT observa (4439) al que la declara sin estar en el padrón. Por defecto `false`
             * @example false
             */
            padron_tasa_especial_igv?: boolean;
        };
        ApiResponseEmpresaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EmpresaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description #107: si SUNAT rechazó las credenciales SOL (usuario o clave incorrectos…), cuándo y por qué; ausente si no. Mientras esté, los envíos de la empresa esperan a que se corrijan */
        CredencialesSolRechazadas: {
            /**
             * Format: date-time
             * @example 2026-10-09T15:00:00Z
             */
            desde?: string;
            /** @example 0102 - Usuario o contrasena incorrectos */
            motivo?: string;
        };
        /** @description #40: si SUNAT designó a la empresa agente de retención o de percepción, con lo que emite comprobantes 20 o 40; la registra el soporte de khipu */
        DesignacionDeAgente: {
            /**
             * @description Agente de retención del IGV (comprobantes 20, series R###)
             * @example true
             */
            retencion: boolean;
            /**
             * @description Agente de percepción del IGV (comprobantes 40, series P###)
             * @example false
             */
            percepcion: boolean;
        };
        EmpresaResponse: {
            /**
             * Format: uuid
             * @example 5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f
             */
            id?: string;
            /** @example 20123456786 */
            ruc?: string;
            /** @example Comercial Andina SAC */
            razon_social?: string;
            /** @example BETA */
            entorno?: string;
            /** @example true */
            tiene_credenciales_sol?: boolean;
            /**
             * @description Si hay un certificado digital cargado. Aparte de la vigencia: un certificado puede no tener fecha
             * @example true
             */
            tiene_certificado?: boolean;
            /**
             * Format: date
             * @example 2027-12-31
             */
            certificado_vigencia_hasta?: string;
            domicilio?: components["schemas"]["DomicilioResponse"];
            /**
             * @description Cuenta de detracciones por defecto, o `null`
             * @example 00-000-123456
             */
            cuenta_detracciones?: string;
            /**
             * @description Nombre comercial que va en el XML (`cac:PartyName`), o `null`
             * @example Andina Store
             */
            nombre_comercial?: string;
            /**
             * @description Inscrita en el Padrón de Tasa Especial del IGV (restaurantes y hoteles): emite con la tasa reducida
             * @example false
             */
            padron_tasa_especial_igv?: boolean;
            agente?: components["schemas"]["DesignacionDeAgente"];
            credenciales_sol_rechazadas?: components["schemas"]["CredencialesSolRechazadas"];
            /**
             * @description #318: si cargó las credenciales API SUNAT (client_id y client_secret de «Credenciales de API SUNAT»). Nunca se devuelven
             * @example false
             */
            tiene_credenciales_api_sunat?: boolean;
        };
        CredencialesSolRequest: {
            /**
             * @description Usuario SOL secundario, no la clave SOL principal
             * @example MODDATOS
             */
            usuario: string;
            /** @example moddatos */
            clave: string;
        };
        CredencialesApiSunatRequest: {
            /**
             * @description «Id» de la aplicación registrada en SOL, opción «Credenciales de API SUNAT» (client_id)
             * @example d20b421e-4ace-46d8-ac8c-4fbb44cfde17
             */
            client_id: string;
            /**
             * @description «Clave» de esa aplicación (client_secret). Se guarda cifrada y nunca vuelve por la API
             * @example HhWwZVo8ZaWm4BZ7n/xEzw==
             */
            client_secret: string;
        };
        CambiarRolRequest: {
            /**
             * @description `ADMIN` todo; `EMISOR` emite, consulta y anula comprobantes y mantiene clientes y productos, pero no cambia la configuración ni el equipo; `LECTURA` solo consulta.
             * @example EMISOR
             * @enum {string}
             */
            rol: "ADMIN" | "EMISOR" | "LECTURA";
        };
        ApiResponseMiembroDelEquipo: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["MiembroDelEquipo"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        MiembroDelEquipo: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            id?: string;
            /** @example caja@ferreteriatorres.pe */
            email?: string;
            /**
             * @example EMISOR
             * @enum {string}
             */
            rol?: "ADMIN" | "EMISOR" | "LECTURA";
            /**
             * @description `false`: desactivado, no puede entrar.
             * @example true
             */
            activo?: boolean;
            /** @example true */
            correo_verificado?: boolean;
            /**
             * @description `true` en el usuario de la sesión.
             * @example false
             */
            es_usted?: boolean;
        };
        ClienteCrear: {
            /**
             * @description Tipo de documento del catálogo 06: 1 DNI, 4 carné de extranjería, 6 RUC, 7 pasaporte…
             * @example 6
             */
            tipo_doc: string;
            /** @example 20601234565 */
            num_doc: string;
            /** @example COMERCIAL ANDINA S.A.C. */
            razon_social: string;
            /** @example Av. Javier Prado Este 123, San Isidro, Lima */
            direccion?: string;
            /**
             * @description País ISO 3166-1 (catálogo 04); opcional
             * @example PE
             */
            pais?: string;
            /** @example facturas@andina.pe */
            email?: string;
        };
        ApiResponseCliente: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Cliente"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Cliente: {
            /** Format: uuid */
            id?: string;
            tipo_doc?: string;
            num_doc?: string;
            razon_social?: string;
            direccion?: string;
            pais?: string;
            email?: string;
            /** Format: date-time */
            creado_en?: string;
            /** Format: date-time */
            actualizado_en?: string;
        };
        ClienteGuardar: {
            razon_social: string;
            direccion?: string;
            pais?: string;
            email?: string;
        };
        /** @description API keys vigentes por empresa */
        LimiteDto: {
            /**
             * Format: int32
             * @description El tope; mayor que cero. Ausente si el límite es ilimitado
             * @example 300
             */
            maximo?: number | null;
            /**
             * @description `true` si no tiene tope. En ese caso `maximo` debe faltar
             * @example false
             */
            ilimitado?: boolean;
        };
        LimitesDto: {
            documentos_al_mes?: components["schemas"]["LimiteDto"];
            /**
             * Format: int32
             * @description RUC (empresas) que puede tener la cuenta; mayor que cero
             * @example 3
             */
            rucs?: number;
            usuarios?: components["schemas"]["LimiteDto"];
            api_keys?: components["schemas"]["LimiteDto"];
            /**
             * Format: int32
             * @description Años que se conservan el XML y el CDR; mayor que cero
             * @example 5
             */
            retencion_anios?: number;
        };
        PlanRequest: {
            /**
             * @description Único sin importar mayúsculas
             * @example Estudio
             */
            nombre?: string;
            /**
             * @description En soles, cero o más, con hasta dos decimales
             * @example 49.9
             */
            precio_mensual?: number;
            limites?: components["schemas"]["LimitesDto"];
            /**
             * @description Si sale en la página de precios. Ausente: al crear, sí; al editar, como estaba. Un plan a medida para un cliente va en `false`: se asigna y se cobra igual, pero no se publica
             * @example true
             */
            visible_en_publicidad?: boolean | null;
        };
        ApiResponsePlanResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PlanResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Un cambio de límites ya decidido que entra al inicio del ciclo siguiente; ausente si no hay */
        CambioProgramadoResponse: {
            limites?: components["schemas"]["LimitesDto"];
            /**
             * Format: date-time
             * @example 2026-11-01T05:00:00Z
             */
            aplica_desde?: string;
        } | null;
        PlanResponse: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
            /** @example 29 */
            precio_mensual?: number;
            limites?: components["schemas"]["LimitesDto"];
            limites_programados?: components["schemas"]["CambioProgramadoResponse"];
            /**
             * @description `INACTIVO` está fuera de la oferta pero las cuentas que ya lo tienen lo conservan
             * @example ACTIVO
             * @enum {string}
             */
            estado?: "ACTIVO" | "INACTIVO";
            /** @description El plan con el que nacen las cuentas nuevas: no se puede desactivar ni borrar */
            por_defecto?: boolean;
            /** @description Si sale en la página de precios (`GET /v1/planes`). Un plan a medida está activo pero no se publica */
            visible_en_publicidad?: boolean;
            /**
             * Format: int64
             * @description Cuentas que lo tienen hoy como su suscripción vigente
             * @example 12
             */
            cuentas?: number;
        };
        PlantillaPorGuardar: {
            /**
             * @description Una sola línea, hasta 150 caracteres; admite las variables del correo
             * @example Restablecer contraseña
             */
            asunto?: string;
            /**
             * @description Hasta 5000 caracteres; admite las variables del correo y tiene que incluir las indispensables
             * @example Para restablecer tu contraseña abre este enlace (válido {validez}):
             *     {enlace}
             */
            cuerpo?: string;
        };
        ApiResponsePlantilla: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Plantilla"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Plantilla: {
            /** @example RECUPERACION_CLAVE */
            tipo?: string;
            /** @example Restablecer la contraseña */
            etiqueta?: string;
            /** @description Cuándo se manda este correo */
            cuando_se_manda?: string;
            vigente?: components["schemas"]["TextoDeCorreo"];
            defecto?: components["schemas"]["TextoDeCorreo"];
            /** @description `true` si un administrador reemplazó el texto de fábrica */
            personalizada?: boolean;
            /**
             * Format: date-time
             * @description Cuándo se cambió; ausente si no es personalizada
             * @example 2026-10-15T15:00:00Z
             */
            actualizada_en?: string;
            /**
             * @description Correo del administrador que la cambió; ausente si no es personalizada, si fue la clave de la plataforma o si ya no existe
             * @example ana@khipu.pe
             */
            actualizada_por?: string;
            /** @description Las variables que el correo admite, escritas `{nombre}` */
            variables?: components["schemas"]["Variable"][];
        };
        /** @description El texto de fábrica, al que se vuelve al restaurar */
        TextoDeCorreo: {
            asunto?: string;
            cuerpo?: string;
        };
        /** @description Las variables que el correo admite, escritas `{nombre}` */
        Variable: {
            /** @example enlace */
            nombre?: string;
            /** @example El enlace de un solo uso. */
            descripcion?: string;
            /** @example https://app.khipu.pe/restablecer/0a1b2c3d */
            ejemplo?: string;
            /** @description El correo no sirve sin ella: el cuerpo tiene que incluirla */
            indispensable?: boolean;
        };
        RemitentePorGuardar: {
            /**
             * @description Opcional; hasta 100 caracteres, sin saltos de línea, comillas ni < >
             * @example khipu
             */
            nombre?: string;
            /**
             * @description Obligatorio; una sola dirección, en ASCII
             * @example no-responder@khipu.pe
             */
            email?: string;
            /**
             * @description Opcional; adónde llegan las respuestas
             * @example soporte@khipu.pe
             */
            responder_a?: string;
        };
        ApiResponseRemitente: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Remitente"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description El de la configuración del servidor (`MAIL_REMITENTE`), al que se vuelve al restablecer */
        Direccion: {
            /** @example khipu */
            nombre?: string;
            /** @example no-responder@khipu.pe */
            email?: string;
            /** @example soporte@khipu.pe */
            responder_a?: string;
        };
        Remitente: {
            vigente?: components["schemas"]["Direccion"];
            /** @description `true` si lo fijó un administrador; `false` si es el de la configuración del servidor */
            personalizado?: boolean;
            /**
             * Format: date-time
             * @description Cuándo se cambió; ausente si no es personalizado
             * @example 2026-10-15T15:00:00Z
             */
            actualizado_en?: string;
            /**
             * @description Correo del administrador que lo fijó; ausente si no es personalizado, si fue la clave de la plataforma o si ya no existe
             * @example ana@khipu.pe
             */
            actualizado_por?: string;
            predeterminado?: components["schemas"]["Direccion"];
        };
        BannerPorPublicar: {
            /**
             * @description Una sola línea, hasta 300 caracteres
             * @example Mantenimiento programado esta noche de 22:00 a 23:00
             */
            texto?: string;
            /**
             * Format: date-time
             * @description Desde cuándo se muestra
             * @example 2026-10-15T20:00:00Z
             */
            desde?: string;
            /**
             * Format: date-time
             * @description Hasta cuándo: obligatorio, posterior al inicio y a ahora, y a lo sumo 90 días después de empezar
             * @example 2026-10-16T01:00:00Z
             */
            hasta?: string;
        };
        ApiResponseBanner: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Banner"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Banner: {
            /** @example Mantenimiento programado esta noche de 22:00 a 23:00 */
            texto?: string;
            /**
             * Format: date-time
             * @example 2026-10-15T20:00:00Z
             */
            desde?: string;
            /**
             * Format: date-time
             * @example 2026-10-16T01:00:00Z
             */
            hasta?: string;
            /**
             * Format: date-time
             * @description Cuándo se publicó o se cambió
             * @example 2026-10-15T15:00:00Z
             */
            actualizado_en?: string;
            /** @description `true` si se está mostrando ahora; `false` si está programado para después o ya venció */
            vigente_ahora?: boolean;
        };
        WebhookCrear: {
            /**
             * @description URL https que recibe los eventos
             * @example https://mi-sistema.pe/khipu/webhook
             */
            url: string;
            /** @example ERP producción */
            descripcion?: string;
            /**
             * @description Eventos a los que se suscribe, o `["*"]` para todos (`GET /v1/webhooks/eventos`)
             * @example [
             *       "comprobante.aceptado",
             *       "comprobante.rechazado"
             *     ]
             */
            eventos: string[];
        };
        ApiResponseCreado: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Creado"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Creado: {
            webhook?: components["schemas"]["Webhook"];
            /**
             * @description Secreto para verificar la firma `X-Khipu-Signature`. Se muestra una sola vez
             * @example whsec_Zm9vYmFy…
             */
            secreto?: string;
        };
        Webhook: {
            /** Format: uuid */
            id?: string;
            /** @example https://mi-sistema.pe/khipu/webhook */
            url?: string;
            /** @example ERP producción */
            descripcion?: string;
            eventos?: string[];
            /** @description Falso si lo apagó usted o khipu lo pausó por entregas fallidas seguidas */
            activo?: boolean;
            /** @description Por qué khipu lo pausó; nulo si está activo o lo apagó usted */
            pausado_motivo?: string;
            /**
             * Format: int32
             * @description Intentos fallidos seguidos; a los 10 khipu lo pausa
             */
            fallos_consecutivos?: number;
            /** Format: date-time */
            creado_en?: string;
        };
        ApiResponseSecreto: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Secreto"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Secreto: {
            /** @description El nuevo secreto; el anterior dejó de valer. Se muestra una sola vez */
            secreto?: string;
        };
        ApiResponsePrueba: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Prueba"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Prueba: {
            /**
             * Format: uuid
             * @description Id de la entrega de prueba; aparece en el log de entregas
             */
            entrega_id?: string;
        };
        ApiResponseConciliacionRvie: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ConciliacionRvie"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description EXPERIMENTAL. Resultado de conciliar el registro de ventas de khipu con la propuesta del RVIE de SUNAT, comprobante por comprobante (tipo-serie-número) */
        ConciliacionRvie: {
            /**
             * @description Periodo conciliado, `AAAAMM`
             * @example 202610
             */
            periodo?: string;
            /**
             * @description `true` si nada falta, nada sobra y nada difiere
             * @example true
             */
            cuadra?: boolean;
            /**
             * Format: int64
             * @description Comprobantes de khipu en el registro (aceptados y anulados por SUNAT)
             * @example 120
             */
            registros_khipu?: number;
            /**
             * Format: int64
             * @description Filas leídas de la propuesta de SUNAT
             * @example 121
             */
            registros_sunat?: number;
            /**
             * Format: int64
             * @description Comprobantes que están en los dos con los mismos datos
             * @example 118
             */
            coinciden?: number;
            /**
             * Format: int64
             * @description Comprobantes que khipu todavía no tiene resueltos por SUNAT (firmados, enviados o con error de envío): no se concilian y pueden explicar parte de `solo_en_sunat`
             * @example 1
             */
            pendientes_de_sunat?: number;
            /**
             * Format: int64
             * @description Filas de la propuesta con el campo 10 (un rango de boletas o tickets del día consolidados): no se concilian una por una, y sus boletas pueden aparecer en `solo_en_khipu`
             * @example 0
             */
            filas_consolidadas_no_conciliadas?: number;
            /**
             * Format: int64
             * @description Cuántos están en la propuesta de SUNAT y no en el registro de khipu
             * @example 1
             */
            total_solo_en_sunat?: number;
            /**
             * Format: int64
             * @description Cuántos están en khipu y no en la propuesta de SUNAT
             * @example 0
             */
            total_solo_en_khipu?: number;
            /**
             * Format: int64
             * @description Cuántos comprobantes están en los dos con algún dato distinto
             * @example 1
             */
            total_difieren?: number;
            /**
             * Format: int64
             * @description Cuántos datos distintos hay en total (un comprobante puede diferir en varios)
             * @example 1
             */
            total_diferencias?: number;
            /**
             * @description Los que están solo en SUNAT, `tipo-serie-número` (hasta 500)
             * @example [
             *       "01-F001-130"
             *     ]
             */
            solo_en_sunat?: string[];
            /** @description Los que están solo en khipu, `tipo-serie-número` (hasta 500) */
            solo_en_khipu?: string[];
            /** @description Cada dato distinto (hasta 500) */
            difieren?: components["schemas"]["DiferenciaRvie"][];
        };
        /** @description Un dato que khipu y SUNAT registran distinto para el mismo comprobante */
        DiferenciaRvie: {
            /**
             * @description `tipo-serie-número`
             * @example 01-F001-125
             */
            comprobante?: string;
            /**
             * @description `fecha_emision`, `moneda`, `estado`, `base_gravada`, `igv`, `exonerado`, `inafecto` o `total`
             * @example total
             */
            campo?: string;
            /**
             * @description Lo que dice khipu, como en el archivo
             * @example 118.0
             */
            khipu?: string;
            /**
             * @description Lo que dice la propuesta de SUNAT
             * @example 120.0
             */
            sunat?: string;
        };
        SerieRequest: {
            /**
             * @description 01=factura, 03=boleta, 04=liquidación de compra, 07=nota de crédito, 08=nota de débito, 09=guía de remisión remitente (serie T###), 20=retención, 31=guía de remisión transportista (serie V###), 40=percepción (20 y 40, solo si la empresa es agente de ese tipo: `422 AGENTE_NO_DESIGNADO`)
             * @example 01
             */
            tipo: string;
            /**
             * @description Serie del tipo: `F###` factura, `B###` boleta, `L###` liquidación de compra, `F###` o `B###` las notas, `T###` guía remitente, `V###` guía transportista, `R###` retención, `P###` percepción
             * @example F001
             */
            serie: string;
            /**
             * Format: int64
             * @description Último número ya emitido; el siguiente comprobante lleva este valor + 1. Máximo 99999999 (regla 1001)
             * @example 0
             */
            correlativo_inicial?: number;
            /**
             * @description Establecimiento desde el que emite la serie (`GET /v1/empresa/establecimientos`); por defecto `0000`, el domicilio fiscal. Un anexo debe existir y estar activo: `422 ESTABLECIMIENTO_INVALIDO`
             * @example 0000
             */
            establecimiento?: string;
        };
        DocumentoDeAgente: {
            /**
             * @description Tipo de documento, catálogo 01: `01` factura, `08` nota de débito, `12` ticket y, solo en la percepción, `03` boleta. La nota de crédito (`07`) y otro comprobante de retención o percepción, que la hoja admite sin pago, todavía no: `422` (2692)
             * @example 01
             */
            tipo: string;
            /**
             * @description Serie y número: `F###`/`E001` electrónica, 4 dígitos física (y `B###`/`EB01` en la percepción) seguido del número de 1 a 8 dígitos; un ticket, la serie de la máquina y su número (2694)
             * @example F001-123
             */
            numero: string;
            /**
             * Format: date
             * @description Fecha de emisión del documento, no posterior a la del comprobante
             * @example 2026-10-05
             */
            fecha_emision: string;
            /**
             * @description Importe total del documento, en su moneda (2696)
             * @example 1180
             */
            importe_total: number;
            /**
             * @description Moneda del documento (catálogo 02); por defecto `PEN`
             * @example PEN
             */
            moneda?: string;
            pago: components["schemas"]["PagoDeDocumento"];
            tipo_cambio?: components["schemas"]["TipoCambioDeDocumento"];
        };
        /** @description El pago (retención) o el cobro (percepción) de este documento */
        PagoDeDocumento: {
            /**
             * Format: int64
             * @description Número del pago o cobro de ese documento (1 el primero, 2 el segundo de un pago en cuotas…); por defecto 1. No se repite para el mismo documento (2626)
             * @example 1
             */
            numero?: number;
            /**
             * Format: date
             * @description Fecha del pago o cobro: desde la emisión del documento (o desde el primer día del mes del comprobante si el documento es de un mes anterior) hasta la emisión del comprobante (2625/2612)
             * @example 2026-10-20
             */
            fecha: string;
            /**
             * @description Importe pagado o cobrado **sin** la retención o la percepción, en la moneda del documento; no mayor que su importe total
             * @example 1180
             */
            importe: number;
        };
        /** @description El proveedor al que se retiene: siempre con RUC */
        ProveedorDeRetencion: {
            /**
             * @description RUC del proveedor (2724), distinto del de la empresa (2620)
             * @example 20131312955
             */
            ruc: string;
            /**
             * @description Razón social (2133, 2134)
             * @example PROVEEDOR DEL NORTE S.A.C.
             */
            razon_social: string;
            /**
             * @description Dirección, en una línea de hasta 100 caracteres. Opcional
             * @example Av. Grau 123, Piura
             */
            direccion?: string;
        };
        RetencionRequest: {
            /**
             * @description Serie registrada con el tipo `20`: `R` + 3 alfanuméricos (regla 1001)
             * @example R001
             */
            serie: string;
            /**
             * Format: int64
             * @description Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`
             * @example 15
             */
            correlativo?: number;
            /**
             * Format: date
             * @description Fecha de emisión (`YYYY-MM-DD`), no futura; SUNAT la recibe hasta 7 días calendario desde el día siguiente (2600)
             * @example 2026-10-20
             */
            fecha_emision: string;
            proveedor: components["schemas"]["ProveedorDeRetencion"];
            /**
             * @description Régimen de retención, catálogo 23: `01` tasa 3 % (el vigente) o `02` tasa 6 % (solo para comprobantes emitidos hasta el 28/02/2014, 2985)
             * @example 01
             */
            regimen: string;
            /**
             * @description Tasa en porcentaje; si se omite, la del régimen. Otra que no sea la del régimen responde `422` (2619)
             * @example 3
             */
            tasa?: number;
            /** @description Los comprobantes del proveedor que se pagan, cada uno con su pago */
            documentos: components["schemas"]["DocumentoDeAgente"][];
            /**
             * @description `true` (por defecto) envía a SUNAT en la misma llamada; `false` lo deja `FIRMADO` para enviarlo con `POST /v1/facturas/{id}/enviar`
             * @example true
             */
            enviar_automatico?: boolean;
            /**
             * @description Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML
             * @example Pago de facturas de septiembre.
             */
            observaciones?: string;
        };
        /** @description Solo fuera de soles, obligatorio (2721): soles por unidad de `moneda` con que se convierte el pago */
        TipoCambioDeDocumento: {
            /**
             * @description Soles por unidad de la moneda del documento, hasta 4 enteros y 6 decimales (2716)
             * @example 3.756
             */
            factor: number;
            /**
             * Format: date
             * @description Fecha del tipo de cambio; por defecto la del pago (2722)
             * @example 2026-10-20
             */
            fecha?: string;
        };
        /** @description Facturas de anticipo regularizadas en esta factura; sus importes ya pagados se restan del total */
        AnticipoRespuesta: {
            /**
             * @description Factura de anticipo (serie-número)
             * @example F001-120
             */
            comprobante?: string;
            /** @example F001 */
            serie?: string;
            /**
             * Format: int64
             * @example 120
             */
            numero?: number;
            /**
             * @description Valor sin IGV que se descuenta de la base
             * @example 1000
             */
            monto?: number;
            /**
             * @description Importe que el cliente pagó con el anticipo (IGV incluido)
             * @example 1180
             */
            importe_pagado?: number;
            /**
             * @description `gravado`, `exonerado` o `inafecto`
             * @example gravado
             */
            afectacion?: string;
            /**
             * @description Código SUNAT del descuento global por anticipo (catálogo 53: 04/05/06)
             * @example 04
             */
            codigo_sunat?: string;
            /**
             * Format: date
             * @example 2026-09-01
             */
            fecha_pago?: string;
        };
        ApiResponseComprobanteResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ComprobanteResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Solo al consultar y al enviar: la comunicación de baja o el resumen diario más reciente del comprobante (en curso, aceptado o rechazado), o `null`. En una boleta informada en un resumen de alta, `fuera_de_plazo` dice si salió pasado el 7.º día */
        BajaResponse: {
            /**
             * Format: uuid
             * @example 5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f
             */
            id?: string;
            /**
             * @description Identificador ante SUNAT: `RA-yyyymmdd-N` (comunicación de baja) o `RC-yyyymmdd-N` (resumen diario, para una boleta). Correlativo por empresa y día
             * @example RA-20260918-1
             */
            identificador?: string;
            /**
             * @description Comprobante dado de baja
             * @example F001-125
             */
            comprobante?: string;
            /**
             * @description Tipo del comprobante (01 factura, 03 boleta, 07/08 notas)
             * @example 01
             */
            tipo_comprobante?: string;
            /**
             * Format: date
             * @example 2026-09-18
             */
            fecha_generacion?: string;
            /**
             * Format: date
             * @description Fecha de emisión del comprobante tal como se comunicó a SUNAT (cbc:ReferenceDate, reglas 2375/2671)
             * @example 2026-09-15
             */
            fecha_referencia?: string;
            /** @example Error en el RUC del cliente */
            motivo?: string;
            /**
             * @description `BAJA`: anula el comprobante. `ALTA`: informa en el resumen diario una boleta que pasó el envío individual (SUNAT ya no la recibe sola, 1079); al aceptarse, la boleta queda `ACEPTADO`, o `ACEPTADO_CON_OBS` si SUNAT la observó (por ejemplo con la 4443, si se informó fuera de plazo: `fuera_de_plazo`)
             * @example BAJA
             */
            condicion?: string;
            /**
             * @description Solo en un `ALTA`: `true` si el resumen se generó pasado el 7.º día desde la emisión de la boleta. SUNAT no la rechaza, pero la norma prevé que la observe con la 4443 «El comprobante fue informado fuera del plazo establecido» (entonces la boleta queda `ACEPTADO_CON_OBS`); en e-beta, con 8 y 30 días de atraso, la aceptó sin observaciones (`ACEPTADO`). SUNAT cuenta los 7 días hasta que recibe el resumen: si su envío falló y se reintentó otro día, puede observarlo con 4443 aunque aquí diga `false`; lo que manda es `cdr.observaciones` del comprobante. Una `BAJA` nunca sale fuera de plazo (2957)
             * @example false
             */
            fuera_de_plazo?: boolean;
            /**
             * @description `GENERADA` (firmada, sin enviar) → `ENVIADA` (SUNAT devolvió ticket, en proceso) → `ACEPTADA` / `RECHAZADA`; `ERROR_ENVIO` cuando SUNAT no respondió y khipu reintentará
             * @example ACEPTADA
             */
            estado?: string;
            /**
             * @description Ticket del proceso en SUNAT (sendSummary), o `null`
             * @example 1789768174685
             */
            ticket?: string;
            cdr?: components["schemas"]["CdrDto"];
            /**
             * Format: int32
             * @example 1
             */
            intentos?: number;
            /** @example null */
            ultimo_error?: string;
            /**
             * Format: int32
             * @description Posición (`LineID`) de este comprobante en su resumen diario. Siempre 1 en una comunicación de baja (RA) y en un resumen de una sola línea
             * @example 1
             */
            linea?: number;
            /**
             * Format: int32
             * @description Cuántas líneas tiene el resumen diario: las boletas de una misma fecha de emisión que se informan juntas viajan en un solo `RC` (hasta 500), con un mismo `identificador`, `ticket` y `cdr`. Si SUNAT lo rechaza entero (2987), cada boleta vuelve a informarse sola para aislar la defectuosa
             * @example 1
             */
            lineas?: number;
        };
        /** @description Cargos globales aplicados, si los hubo */
        CargoRespuesta: {
            /**
             * @description PORCENTAJE | MONTO
             * @example MONTO
             */
            tipo?: string;
            /** @example 25 */
            valor?: number;
            /**
             * @description Monto del cargo sin IGV
             * @example 25
             */
            monto?: number;
            /**
             * @description `true` si se suma a la base del IGV (47/49)
             * @example false
             */
            afecta_base_igv?: boolean;
            /**
             * @description `recargo_consumo` cuando es el 46; `null` en los demás
             * @example recargo_consumo
             */
            motivo?: string;
            /**
             * @description Código SUNAT derivado (catálogo 53): 47/48 por línea, 46/49/50 global
             * @example 50
             */
            codigo?: string;
        };
        /** @description Constancia de SUNAT: `0` aceptada, 2000–3999 rechazada */
        CdrDto: {
            /**
             * @description Código de respuesta SUNAT: `0` aceptado; 2000–3999 rechazado (corregir y reemitir); 4000+ aceptado con observaciones; 1000–1999 error del emisor (fault, sin CDR)
             * @example 0
             */
            codigo?: string;
            /**
             * @description Descripción oficial de SUNAT
             * @example La Factura numero F001-125, ha sido aceptada
             */
            descripcion?: string;
            observaciones?: string[];
            /**
             * @description Solo en una boleta (o nota de boleta) informada en un resumen diario: el identificador de ese resumen. SUNAT no emite un CDR por boleta en el resumen: el CDR del comprobante es el del resumen, y `descripcion` habla del resumen («El Resumen diario RC-…, ha sido aceptado»). Ausente cuando el CDR es el del propio comprobante (factura, nota de factura o boleta enviada sola)
             * @example RC-20260921-1
             */
            resumen?: string;
        };
        /** @description Solo en una retención (20) o una percepción (40): régimen, tasa, cada documento pagado o cobrado con lo retenido o percibido y el neto, y los totales en soles (`totales.total` es lo retenido o percibido); `null` en los demás */
        ComprobanteDeAgenteRespuesta: {
            /**
             * @description Régimen: catálogo 23 en la retención (01 tasa 3 %, 02 tasa 6 %), catálogo 22 en la percepción (01 venta interna 2 %, 02 combustible 1 %, 03 tasa especial 0.5 %)
             * @example 01
             */
            regimen?: string;
            /**
             * @description Descripción del régimen en su catálogo
             * @example Tasa 3%
             */
            descripcion_regimen?: string;
            /**
             * @description Tasa en porcentaje
             * @example 3
             */
            tasa?: number;
            /**
             * @description Importe total retenido o percibido, en soles (`cbc:TotalInvoiceAmount`); el mismo `totales.total`
             * @example 35.4
             */
            importe_total?: number;
            /**
             * @description Redondeo del importe total pagado o cobrado (`PayableRoundingAmount`); hoy siempre 0.00: khipu todavía no lo emite (e-beta no lee el XML que lo lleva)
             * @example 0
             */
            redondeo?: number;
            /**
             * @description Importe total pagado al proveedor (retención) o cobrado al cliente (percepción), en soles: la suma de los netos (`sac:SUNATTotalPaid` / `SUNATTotalCashed`)
             * @example 1144.6
             */
            importe_neto?: number;
            documentos?: components["schemas"]["DocumentoDeAgenteRespuesta"][];
        };
        ComprobanteResponse: {
            /**
             * Format: uuid
             * @example 5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f
             */
            id?: string;
            /**
             * @description Catálogo 01 SUNAT: 01=factura, 03=boleta, 04=liquidación de compra, 07=nota de crédito, 08=nota de débito, 20=retención, 40=percepción
             * @example 01
             */
            tipo?: string;
            /** @example F001 */
            serie?: string;
            /**
             * Format: int64
             * @example 125
             */
            numero?: number;
            /**
             * Format: date
             * @example 2026-09-14
             */
            fecha_emision?: string;
            /**
             * Format: date
             * @description Fecha de vencimiento informada, o `null`
             * @example 2026-10-14
             */
            fecha_vencimiento?: string;
            /**
             * Format: date
             * @description Último día en que SUNAT acepta recibirlo (RS 193-2020: 3 días calendario desde la emisión; una boleta, 7: va sola hasta el 5.º día y después khipu la informa en el resumen diario). Pasado ese día, una factura o nota de factura FIRMADO o en ERROR_ENVIO pasa a FUERA_DE_PLAZO; una boleta (o nota sobre boleta que va en el resumen) se sigue informando en un resumen diario, fuera de plazo: SUNAT no la rechaza y puede observarla con la 4443
             * @example 2026-09-17
             */
            fecha_limite_envio?: string;
            /** @example PEN */
            moneda?: string;
            /**
             * @description Catálogo 51 SUNAT; `null` en una retención (20) o percepción (40), que no tienen tipo de operación
             * @example 0101
             */
            tipo_operacion?: string;
            /**
             * @description Tipo de cambio informado (soles por unidad de `moneda`), o `null`: siempre `null` en soles
             * @example 3.756
             */
            tipo_cambio?: number;
            /**
             * @description Importe total en soles al `tipo_cambio`, o `null` si no se informó o el comprobante es en soles
             * @example 443.21
             */
            total_en_soles?: number;
            receptor?: components["schemas"]["ReceptorDto"];
            items?: components["schemas"]["ItemRespuesta"][];
            /**
             * @description Estado del comprobante: `RECIBIDO` → `FIRMADO` → `ENVIADO` → `ACEPTADO` / `ACEPTADO_CON_OBS` / `RECHAZADO`; `ERROR_ENVIO` (SUNAT no disponible, se reintenta), `INVALIDO` (XML no válido), `ANULADO` (comunicación de baja aceptada), `FUERA_DE_PLAZO` (factura o nota que no llegó dentro del plazo de envío; terminal. Una boleta no: se informa en el resumen diario fuera de plazo y queda `ACEPTADO_CON_OBS` si SUNAT la observa con la 4443, `ACEPTADO` si no), `DESCARTADO` (un administrador dejó de intentar enviarlo; terminal)
             * @example ACEPTADO
             */
            estado_documento?: string;
            /**
             * @description Resumen (digest) de la firma XML-DSig; se imprime en la representación impresa y en el código QR
             * @example a1b2c3d4e5f6...
             */
            hash?: string;
            /**
             * @description Nombre oficial del archivo: `RUC-TIPO-SERIE-NUMERO`
             * @example 20614798093-01-F001-00000125
             */
            nombre_archivo?: string;
            /**
             * Format: int32
             * @description Intentos de envío a SUNAT realizados
             * @example 1
             */
            intentos?: number;
            /**
             * @description Último error de envío (`código SUNAT - mensaje`), o `null`
             * @example null
             */
            ultimo_error?: string;
            cdr?: components["schemas"]["CdrDto"];
            totales?: components["schemas"]["TotalesDto"];
            forma_pago?: components["schemas"]["FormaPagoRespuesta"];
            detraccion?: components["schemas"]["DetraccionRespuesta"];
            retencion_igv?: components["schemas"]["RetencionRespuesta"];
            percepcion?: components["schemas"]["PercepcionRespuesta"];
            /** @description Facturas de anticipo regularizadas en esta factura; sus importes ya pagados se restan del total */
            anticipos?: components["schemas"]["AnticipoRespuesta"][];
            referencias?: components["schemas"]["ReferenciasDto"];
            nota?: components["schemas"]["NotaDto"];
            /** @description Solo al consultar una factura: notas de crédito/débito emitidas sobre ella (todas, con su estado); `null` en listados y en la emisión */
            notas?: components["schemas"]["NotaResumenDto"][];
            baja?: components["schemas"]["BajaResponse"];
            /** @description Solo al consultar: historial de cambios de estado e intentos de envío, del más antiguo al más reciente (vacío si no hay); `null` en listados y en la emisión */
            eventos?: components["schemas"]["EventoDto"][];
            /**
             * @description Observaciones impresas en el PDF (solo las propias del comprobante), o `null`
             * @example Entrega en almacén central.
             */
            observaciones?: string;
            /** @description Leyendas del catálogo 52 declaradas por el emisor (las automáticas no se listan): código y texto tal como van en el XML */
            leyendas?: components["schemas"]["LeyendaDto"][];
            exportacion?: components["schemas"]["ExportacionRespuesta"];
            liquidacion?: components["schemas"]["LiquidacionCompraRespuesta"];
            envio_al_cliente?: components["schemas"]["EnvioAlClienteDto"];
            agente?: components["schemas"]["ComprobanteDeAgenteRespuesta"];
            /**
             * @description cdr solo está presente cuando SUNAT emitió la constancia
             * @example {
             *       "xml": "/v1/facturas/{id}/xml",
             *       "pdf": "/v1/facturas/{id}/pdf",
             *       "cdr": "/v1/facturas/{id}/cdr"
             *     }
             */
            enlaces?: {
                [key: string]: string;
            };
        };
        /** @description Solo al crédito; el identificador SUNAT es Cuota001, Cuota002… */
        CuotaRespuesta: {
            /** @example Cuota001 */
            id?: string;
            /** @example 590 */
            monto?: number;
            /**
             * Format: date
             * @example 2026-10-15
             */
            vencimiento?: string;
        };
        /** @description Descuento global aplicado, si lo hubo */
        DescuentoRespuesta: {
            /**
             * @description PORCENTAJE | MONTO
             * @example PORCENTAJE
             */
            tipo?: string;
            /** @example 10 */
            valor?: number;
            /**
             * @description Monto del descuento sin IGV
             * @example 100
             */
            monto?: number;
            /** @example true */
            afecta_base_igv?: boolean;
            /**
             * @description Catálogo 53: 00/01 por línea, 02/03 global
             * @example 00
             */
            codigo?: string;
        };
        /** @description Detracción (SPOT), solo en operaciones 1001–1004 */
        DetraccionRespuesta: {
            /** @example 022 */
            codigo_bien_servicio?: string;
            /**
             * @description Descripción del catálogo 54
             * @example Otros servicios empresariales
             */
            descripcion?: string;
            /** @example 12 */
            porcentaje?: number;
            /**
             * @description Monto a depositar, siempre en PEN
             * @example 1416
             */
            monto?: number;
            /** @example 00-000-123456 */
            cuenta_banco_nacion?: string;
            /**
             * @description Catálogo 59
             * @example 001
             */
            medio_pago?: string;
        };
        DocumentoDeAgenteRespuesta: {
            /**
             * @description Catálogo 01: 01 factura, 03 boleta (solo en la percepción), 08 nota de débito, 12 ticket
             * @example 01
             */
            tipo?: string;
            /** @example F001-123 */
            numero?: string;
            /**
             * Format: date
             * @example 2026-10-05
             */
            fecha_emision?: string;
            /**
             * @description Importe total del documento, en su moneda
             * @example 1180
             */
            importe_total?: number;
            /** @example PEN */
            moneda?: string;
            pago?: components["schemas"]["PagoDeDocumentoRespuesta"];
            tipo_cambio?: components["schemas"]["TipoCambioDeDocumentoRespuesta"];
            /**
             * @description Lo retenido o percibido de este pago, en soles
             * @example 35.4
             */
            importe_agente?: number;
            /**
             * @description Neto pagado (pago − retenido) o cobrado (pago + percibido), en soles
             * @example 1144.6
             */
            neto?: number;
        };
        /** @description Otros documentos (catálogo 12: 04–09, 99) */
        DocumentoDto: {
            /** @example 09 */
            tipo?: string;
            /** @example T001-123 */
            numero?: string;
        };
        /** @description Solo al consultar: el envío automático del comprobante a su cliente (`PUT /v1/empresa/envio-al-cliente`), o `null` si no se anotó (la empresa no lo tenía activado, el comprobante no traía correo o SUNAT aún no lo aceptó) */
        EnvioAlClienteDto: {
            /** @example compras@andina.pe */
            destinatario?: string;
            /**
             * @description `PENDIENTE` (en cola o esperando un reintento), `ENVIADO` (el servidor de correo lo recibió), `FALLIDO` (agotó los reintentos o los adjuntos superan el tope; `ultimo_error` dice por qué) u `OMITIDO` (no correspondía mandarlo al llegar su turno: el servidor no tiene correo configurado o el comprobante ya no estaba aceptado)
             * @example ENVIADO
             */
            estado?: string;
            /**
             * Format: int32
             * @description Intentos de envío realizados (hasta 5)
             * @example 1
             */
            intentos?: number;
            /**
             * @description Motivo del último fallo o de la omisión, o `null`
             * @example null
             */
            ultimo_error?: string;
            /**
             * Format: date-time
             * @description Solo si sigue `PENDIENTE`: cuándo se intenta (UTC)
             * @example 2026-09-13T15:01:00Z
             */
            siguiente_intento?: string;
            /**
             * Format: date-time
             * @description Cuándo salió (UTC), o `null`
             * @example 2026-09-13T15:00:12Z
             */
            enviado_en?: string;
        };
        /** @description Solo al consultar: historial de cambios de estado e intentos de envío, del más antiguo al más reciente (vacío si no hay); `null` en listados y en la emisión */
        EventoDto: {
            /**
             * Format: date-time
             * @description Instante del cambio (UTC)
             * @example 2026-09-13T15:00:05Z
             */
            fecha?: string;
            /**
             * @description Estado previo, o `null` en el primer evento
             * @example FIRMADO
             */
            estado_anterior?: string;
            /** @example ERROR_ENVIO */
            estado_resultante?: string;
            /**
             * @description Motivo o resultado: error de envío, código y descripción del CDR, baja aceptada…
             * @example SUNAT no disponible (timeout)
             */
            mensaje?: string;
        };
        /** @description Solo en exportaciones (0200–0208): Incoterm y país de uso del servicio; `null` en las demás */
        ExportacionRespuesta: {
            /**
             * @description Incoterm 2020 de la venta, o `null`
             * @example FOB
             */
            incoterm?: string;
            /**
             * @description País de uso del servicio (solo 0201/0208), o `null`
             * @example US
             */
            pais_uso?: string;
        };
        FormaPagoRespuesta: {
            /**
             * @description contado | credito
             * @example credito
             */
            tipo?: string;
            /**
             * @description Solo al crédito
             * @example 1180
             */
            monto_pendiente?: number;
            /** @description Solo al crédito; el identificador SUNAT es Cuota001, Cuota002… */
            cuotas?: components["schemas"]["CuotaRespuesta"][];
        };
        /** @description GTIN del producto, o `null` */
        GtinRespuesta: {
            /** @example GTIN-13 */
            tipo?: string;
            /** @example 7750182000123 */
            codigo?: string;
        };
        /** @description Datos de recursos hidrobiológicos (solo con tipo de operación 1002), o `null` */
        Hidrobiologico: {
            matricula?: string;
            nombre_embarcacion?: string;
            especie?: string;
            lugar_descarga?: string;
            /** Format: date */
            fecha_descarga?: string;
            cantidad?: number;
        };
        /** @description ISC de la línea, si lo tiene */
        IscRespuesta: {
            /** @example 01 */
            sistema?: string;
            /**
             * @description Tasa aplicada en %: la enviada en los sistemas 01 y 03, derivada (monto ÷ base) en el 02
             * @example 35
             */
            tasa?: number;
            /** @example 350 */
            monto?: number;
            /**
             * @description Base del ISC de la línea (valor de venta, o PVP sugerido × cantidad en el sistema 03)
             * @example 1000
             */
            base?: number;
            /**
             * @description Solo sistema 03: PVP sugerido unitario
             * @example 3.5
             */
            base_pvp?: number;
            /**
             * @description Solo sistema 02: importe fijo por unidad, tal como se envió
             * @example 2.25
             */
            monto_unitario?: number;
        };
        ItemRespuesta: {
            /** @example SRV-001 */
            codigo?: string;
            /** @example Servicio de consultoría */
            descripcion?: string;
            /** @example ZZ */
            unidad?: string;
            /** @example 1 */
            cantidad?: number;
            /** @example 2000 */
            precio_unitario?: number;
            /**
             * @description Afectación del IGV, catálogo 07: `10` gravado, `17` IVAP, `20` exonerado, `30` inafecto, `40` exportación; gratuitas `11`–`16` (gravadas), `21` (exonerada), `31`–`37` (inafectas)
             * @example 10
             */
            tipo_afectacion_igv?: string;
            /**
             * @description Valor de venta de la línea sin IGV, neto de descuento que afecta la base y con los cargos 47 (en gratuitas, el valor referencial)
             * @example 1000
             */
            valor_venta?: number;
            /**
             * @description IGV de la línea; en gratuitas gravadas se informa pero no se cobra. En una línea IVAP (afectación 17) este campo trae el IVAP (4 %), no el IGV
             * @example 180
             */
            igv?: number;
            /**
             * @description Lo que paga el cliente por la línea, con cargos 48 (0.00 en gratuitas)
             * @example 1180
             */
            precio_venta?: number;
            /**
             * @description true si la afectación es gratuita (11–16, 21, 31–37)
             * @example false
             */
            gratuita?: boolean;
            descuento?: components["schemas"]["DescuentoRespuesta"];
            /** @description Cargos de la línea aplicados, si los hubo */
            cargos?: components["schemas"]["CargoRespuesta"][];
            isc?: components["schemas"]["IscRespuesta"];
            /**
             * @description ICBPER de la línea (bolsas × monto vigente)
             * @example 0
             */
            icbper?: number;
            /**
             * @description Código de producto SUNAT (catálogo 25), o `null`
             * @example 15101505
             */
            codigo_sunat?: string;
            gtin?: components["schemas"]["GtinRespuesta"];
            hidrobiologico?: components["schemas"]["Hidrobiologico"];
            transporte?: components["schemas"]["TransporteCarga"];
            otros_tributos?: components["schemas"]["OtrosTributosRespuestaDto"];
            /**
             * @description Placa del vehículo (propiedad 7000), o `null`
             * @example ABC-123
             */
            placa?: string;
        };
        /** @description Leyendas del catálogo 52 declaradas por el emisor (las automáticas no se listan): código y texto tal como van en el XML */
        LeyendaDto: {
            /** @example 2001 */
            codigo?: string;
            /** @example BIENES TRANSFERIDOS EN LA AMAZONÍA REGIÓN SELVA PARA SER CONSUMIDOS EN LA MISMA */
            texto?: string;
        };
        /** @description Solo en una liquidación de compra (04): el resto del domicilio del vendedor (que va en `receptor`), el lugar de la operación, lo que el comprador le retiene y el neto que le paga (igual a `totales.total`); `null` en los demás */
        LiquidacionCompraRespuesta: {
            /**
             * @description Ubigeo del domicilio del vendedor (catálogo 13)
             * @example 060101
             */
            ubigeo_vendedor?: string;
            /**
             * @description Condición del domicilio del vendedor (catálogo 60: 01 punto de venta, 02 producción, 03 extracción, 04 explotación, 05 otros)
             * @example 02
             */
            condicion_vendedor?: string;
            lugar_operacion?: components["schemas"]["LugarDeOperacionRespuesta"];
            /**
             * @description Subtotal de la liquidación: valor de venta + IGV (TaxInclusiveAmount)
             * @example 1180
             */
            subtotal?: number;
            /**
             * @description IGV que el comprador retiene y paga en lugar del vendedor
             * @example 180
             */
            igv_retenido?: number;
            retencion_renta?: components["schemas"]["RetencionRentaRespuesta"];
            /**
             * @description Importe neto que se paga al vendedor: subtotal − IGV − renta + redondeo (PayableAmount, regla 4312); el mismo `totales.total`
             * @example 985
             */
            neto_pagar?: number;
        };
        LugarDeOperacionRespuesta: {
            /** @example Fundo Santa Rosa, km 12 */
            direccion?: string;
            /** @example 060101 */
            ubigeo?: string;
            /**
             * @description Catálogo 60
             * @example 02
             */
            condicion?: string;
        };
        /** @description Solo en notas de crédito/débito: factura que modifica y motivo */
        NotaDto: {
            /**
             * @description Tipo del documento modificado (catálogo 01); hoy siempre `01` factura
             * @example 01
             */
            tipo_afectado?: string;
            /**
             * @description Serie-número de la factura modificada
             * @example F001-123
             */
            documento_afectado?: string;
            /**
             * @description Motivo: catálogo 09 en notas de crédito, 10 en notas de débito
             * @example 01
             */
            motivo?: string;
            /**
             * @description Descripción oficial del motivo
             * @example Anulación de la operación
             */
            motivo_descripcion?: string;
            /**
             * @description Sustento indicado al emitir
             * @example El cliente devolvió la mercadería
             */
            descripcion?: string;
        };
        /** @description Solo al consultar una factura: notas de crédito/débito emitidas sobre ella (todas, con su estado); `null` en listados y en la emisión */
        NotaResumenDto: {
            /**
             * Format: uuid
             * @example 5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f
             */
            id?: string;
            /**
             * @description `07` nota de crédito, `08` nota de débito
             * @example 07
             */
            tipo?: string;
            /** @example FC01-4 */
            comprobante?: string;
            /**
             * Format: date
             * @example 2026-09-18
             */
            fecha_emision?: string;
            /** @example 01 */
            motivo?: string;
            /** @example Anulación de la operación */
            motivo_descripcion?: string;
            /** @example ACEPTADO */
            estado_documento?: string;
            /**
             * @description Importe total de la nota
             * @example 1180
             */
            total?: number;
        };
        /** @description Otros tributos (9999) de la línea, o `null` */
        OtrosTributosRespuestaDto: {
            /** @example 3.5 */
            tasa?: number;
            /** @example 3.5 */
            monto?: number;
        };
        PagoDeDocumentoRespuesta: {
            /**
             * Format: int64
             * @example 1
             */
            numero?: number;
            /**
             * Format: date
             * @example 2026-10-20
             */
            fecha?: string;
            /**
             * @description Importe pagado o cobrado sin la retención o la percepción, en la moneda del documento
             * @example 1180
             */
            importe?: number;
        };
        /** @description Percepción cobrada (51/52/53); el cliente paga total + monto */
        PercepcionRespuesta: {
            /** @example 51 */
            regimen?: string;
            /** @example Percepción venta interna */
            descripcion?: string;
            /** @example 2 */
            porcentaje?: number;
            /** @example 1180 */
            base?: number;
            /** @example 23.6 */
            monto?: number;
            /**
             * @description Importe total más la percepción: lo que paga el cliente
             * @example 1203.6
             */
            total_con_percepcion?: number;
        };
        Punto: {
            ubigeo?: string;
            direccion?: string;
        };
        ReceptorDto: {
            /**
             * @description Catálogo 06 SUNAT: 6=RUC, 1=DNI
             * @example 6
             */
            tipo_doc?: string;
            /** @example 20554198211 */
            num_doc?: string;
            /** @example CORPORACION GRAFICA ANDINA S.A.C. */
            razon_social?: string;
            /** @example Av. Argentina 2450, Lima */
            direccion?: string;
            /**
             * @description País del adquirente (ISO 3166-1, catálogo 04), o `null`
             * @example PE
             */
            pais?: string;
            /**
             * @description Correo del adquirente indicado al emitir (una nota hereda el de su comprobante), o `null`. No va al XML
             * @example compras@andina.pe
             */
            email?: string;
        };
        /** @description Orden de compra, guías de remisión y otros documentos relacionados; `null` si no hay ninguno */
        ReferenciasDto: {
            /** @example OC-2026-0457 */
            orden_compra?: string;
            /** @description Guías de remisión (catálogo 01: 09 remitente, 31 transportista) */
            guias?: components["schemas"]["DocumentoDto"][];
            /** @description Otros documentos (catálogo 12: 04–09, 99) */
            documentos_relacionados?: components["schemas"]["DocumentoDto"][];
        };
        RetencionRentaRespuesta: {
            /**
             * @description Tasa en porcentaje
             * @example 1.5
             */
            tasa?: number;
            /**
             * @description Base: el valor de venta de las líneas
             * @example 1000
             */
            base?: number;
            /**
             * @description Retención del Impuesto a la Renta (tributo 3000): la suma de la de cada línea
             * @example 15
             */
            monto?: number;
        };
        /** @description Retención del IGV informada (código 62); el cliente paga total − monto */
        RetencionRespuesta: {
            /** @example 3 */
            porcentaje?: number;
            /** @example 35.4 */
            monto?: number;
            /**
             * @description Importe total menos la retención
             * @example 1144.6
             */
            neto_cobrar?: number;
        };
        /** @description Solo fuera de soles: soles por unidad de `moneda` con que se convirtió el pago, y su fecha; `null` en soles */
        TipoCambioDeDocumentoRespuesta: {
            /** @example 3.756 */
            factor?: number;
            /**
             * Format: date
             * @example 2026-10-20
             */
            fecha?: string;
        };
        TotalesDto: {
            /** @example 1000 */
            gravado?: number;
            /** @example 0 */
            exonerado?: number;
            /** @example 0 */
            inafecto?: number;
            /** @example 180 */
            igv?: number;
            /**
             * @description Tasa del IGV aplicada a las líneas gravadas, en porcentaje: 18.00, o la reducida del Padrón de Tasa Especial (restaurantes y hoteles) si la empresa la tiene activa; una nota usa la de su factura. En un comprobante IVAP (afectación 17) no se aplica: no hay líneas al IGV y la tasa del IVAP (4 %, fija) va en `ivap`
             * @example 18
             */
            tasa_igv?: number;
            /**
             * @description Base de las operaciones gratuitas (tributo 9996): no se cobra
             * @example 0
             */
            gratuito?: number;
            /**
             * @description IGV de las operaciones gratuitas gravadas: solo informativo, no se cobra
             * @example 0
             */
            igv_gratuitas?: number;
            /**
             * @description Total IVAP (tributo 1016, 4 % sobre la venta de arroz pilado, afectación 17): sustituye al IGV en el comprobante
             * @example 0
             */
            ivap?: number;
            /**
             * @description Total valor de venta de exportación (tributo 9995, afectación 40, sin IGV)
             * @example 0
             */
            exportacion?: number;
            /**
             * @description Total ISC (se suma al precio de venta y a la base del IGV)
             * @example 0
             */
            isc?: number;
            /**
             * @description Total ICBPER (bolsas de plástico)
             * @example 0
             */
            icbper?: number;
            /**
             * @description Total de otros tributos (9999): suma al precio de venta y no entra en la base del IGV
             * @example 0
             */
            otros_tributos?: number;
            /**
             * @description Total valor de venta onerosa (suma de bases, LineExtensionAmount)
             * @example 1000
             */
            total_valor_venta?: number;
            /**
             * @description Total precio de venta = valor de venta + tributos (TaxInclusiveAmount)
             * @example 1180
             */
            total_precio_venta?: number;
            /**
             * @description Descuentos que no afectan la base (línea 01 + global 03), AllowanceTotalAmount
             * @example 0
             */
            total_descuentos?: number;
            /**
             * @description Cargos que no afectan la base (línea 48 + globales 46/50), ChargeTotalAmount
             * @example 0
             */
            total_cargos?: number;
            /**
             * @description Suma de los importes ya pagados con facturas de anticipo, IGV incluido (PrepaidAmount)
             * @example 0
             */
            total_anticipos?: number;
            /**
             * @description Redondeo aplicado al importe total (PayableRoundingAmount), entre −1.00 y 1.00
             * @example 0
             */
            redondeo?: number;
            /**
             * @description Importe a pagar (PayableAmount) = precio de venta + cargos − descuentos que no afectan la base − anticipos + redondeo
             * @example 1180
             */
            total?: number;
            descuento_global?: components["schemas"]["DescuentoRespuesta"];
            /** @description Cargos globales aplicados, si los hubo */
            cargos?: components["schemas"]["CargoRespuesta"][];
        };
        Tramo: {
            origen_ubigeo?: string;
            destino_ubigeo?: string;
            descripcion?: string;
            valor_carga_efectiva?: number;
            valor_carga_util_nominal?: number;
            vehiculos?: components["schemas"]["Vehiculo"][];
        };
        /** @description Datos del servicio de transporte de carga (solo con tipo de operación 1004), o `null` */
        TransporteCarga: {
            origen?: components["schemas"]["Punto"];
            destino?: components["schemas"]["Punto"];
            detalle_viaje?: string;
            valor_referencial?: components["schemas"]["ValorReferencial"];
            tramos?: components["schemas"]["Tramo"][];
        };
        ValorReferencial: {
            servicio?: number;
            carga_efectiva?: number;
            carga_util_nominal?: number;
        };
        Vehiculo: {
            configuracion?: string;
            carga_util_tm?: number;
            carga_efectiva_tm?: number;
        };
        /** @description El cliente al que se percibe */
        ClienteDePercepcion: {
            /**
             * @description Tipo de documento, catálogo 06 (`6` RUC, `1` DNI, `4` carné de extranjería, `7` pasaporte…; 2511)
             * @example 6
             */
            tipo_doc: string;
            /**
             * @description Número de documento, alfanumérico de hasta 15 caracteres (2680), distinto del RUC de la empresa (2604)
             * @example 20601234565
             */
            num_doc: string;
            /**
             * @description Razón social o nombre (2133, 2134)
             * @example CLIENTE MAYORISTA S.A.C.
             */
            nombre: string;
            /**
             * @description Dirección, en una línea de hasta 100 caracteres. Opcional
             * @example Jr. Huallaga 450, Lima
             */
            direccion?: string;
        };
        PercepcionRequest: {
            /**
             * @description Serie registrada con el tipo `40`: `P` + 3 alfanuméricos (regla 1001)
             * @example P001
             */
            serie: string;
            /**
             * Format: int64
             * @description Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`
             * @example 15
             */
            correlativo?: number;
            /**
             * Format: date
             * @description Fecha de emisión (`YYYY-MM-DD`), no futura; SUNAT la recibe hasta 7 días calendario desde el día siguiente (2600)
             * @example 2026-10-20
             */
            fecha_emision: string;
            cliente: components["schemas"]["ClienteDePercepcion"];
            /**
             * @description Régimen de percepción, catálogo 22: `01` venta interna 2 %, `02` adquisición de combustible 1 %, `03` agente de percepción con tasa especial 0.5 %
             * @example 01
             */
            regimen: string;
            /**
             * @description Tasa en porcentaje; si se omite, la del régimen. Otra que no sea la del régimen responde `422` (2603)
             * @example 2
             */
            tasa?: number;
            /** @description Los comprobantes de la empresa que se cobran, cada uno con su cobro */
            documentos: components["schemas"]["DocumentoDeAgente"][];
            /**
             * @description `true` (por defecto) envía a SUNAT en la misma llamada; `false` lo deja `FIRMADO` para enviarlo con `POST /v1/facturas/{id}/enviar`
             * @example true
             */
            enviar_automatico?: boolean;
            /**
             * @description Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML
             * @example Cobro de la factura F001-9.
             */
            observaciones?: string;
        };
        /** @description Cargos globales de la nota. Solo con `items`: sin ellos se rechaza (`NOTA_INVALIDA`) porque la nota total copia los de la factura */
        CargoDto: {
            /**
             * @description Porcentaje sobre el valor de venta sin IGV (hasta 5 decimales, menor que 1000)
             * @example 10
             */
            porcentaje?: number;
            /**
             * @description Monto fijo sin IGV (hasta 2 decimales)
             * @example 25
             */
            monto?: number;
            /**
             * @description `true` (por defecto): se suma a la base imponible y paga IGV (flete, embalaje) → código 47 por línea, 49 global. `false`: se cobra sin IGV (reembolso de gastos) → 48 por línea, 50 global
             * @example true
             */
            afecta_base_igv?: boolean;
            /**
             * @description Solo global: `recargo_consumo` para el recargo al consumo y/o propinas (código 46, sin IGV por la Ley 25988); no admite `afecta_base_igv: true`. Un motivo desconocido responde `422 CARGO_INVALIDO`
             * @example recargo_consumo
             */
            motivo?: string;
        };
        /** @description Cuotas; obligatorias al crédito y deben sumar el monto pendiente */
        CuotaDto: {
            /**
             * @description Positivo, hasta 2 decimales (SUNAT 3253)
             * @example 590
             */
            monto?: number;
            /**
             * Format: date
             * @description Posterior a la fecha de emisión (SUNAT 3256, 3267)
             * @example 2026-10-15
             */
            vencimiento?: string;
        };
        /** @description Descuento global de la nota. Solo con `items`: sin ellos se rechaza (`NOTA_INVALIDA`) porque la nota total copia el de la factura. Debe afectar la base del IGV (`afecta_base_igv` `true`, el valor por defecto): con `false` (código 03) responde `422 DESCUENTO_INVALIDO`, porque el importe total de una nota no resta descuentos (3280). La nota no lo lleva como descuento: se reparte en el valor de las líneas gravadas */
        DescuentoDto: {
            /**
             * @description Porcentaje sobre el valor de venta sin IGV (hasta 5 decimales, menor que 100)
             * @example 10
             */
            porcentaje?: number;
            /**
             * @description Monto fijo sin IGV (hasta 2 decimales, menor que la base)
             * @example 50
             */
            monto?: number;
            /**
             * @description `true` (por defecto): reduce la base imponible y por tanto el IGV. `false`: reduce solo lo que se paga (descuento financiero)
             * @example true
             */
            afecta_base_igv?: boolean;
        };
        /** @description Factura o boleta que se modifica; debe estar ACEPTADA por SUNAT y no anulada (reglas 2119, 2120) */
        DocumentoAfectadoDto: {
            /**
             * @description Serie de la factura (`F…`) o boleta (`B…`) que se modifica
             * @example F001
             */
            serie: string;
            /**
             * Format: int64
             * @example 123
             */
            numero: number;
        };
        /** @description Solo en una nota de crédito con motivo `13` (en cualquier otra nota se rechaza con `NOTA_INVALIDA`): la forma de pago al crédito con el neto pendiente y las cuotas corregidas de la factura (reglas 3257, 3320, 3321). Esta nota no mueve importes: khipu genera una única línea de valor 0 (regla 3315) e ignora `items` */
        FormaPagoDto: {
            /**
             * @description `contado`: pago único al emitir. `credito`: pago diferido; exige `monto_pendiente` y al menos una cuota (RS 193-2020)
             * @example credito
             */
            tipo: string;
            /**
             * @description Monto neto pendiente de pago; obligatorio al crédito. Formato e importes los valida el dominio con el código SUNAT (3250, 3265, 3319)
             * @example 1180
             */
            monto_pendiente?: number;
            /** @description Cuotas; obligatorias al crédito y deben sumar el monto pendiente */
            cuotas?: components["schemas"]["CuotaDto"][];
        };
        /** @description Código GTIN (GS1) del producto. Opcional */
        GtinDto: {
            /**
             * @description Estructura GS1: `GTIN-8`, `GTIN-12`, `GTIN-13` o `GTIN-14` (regla 4335)
             * @example GTIN-13
             */
            tipo: string;
            /**
             * @description Dígitos del código; la longitud debe coincidir con el tipo (regla 4334)
             * @example 7750182000123
             */
            codigo: string;
        };
        /** @description Recursos hidrobiológicos con detracción: obligatorio en cada ítem cuando `tipo_operacion` es `1002` (campos 107–112, catálogo 55) y no admitido en otras operaciones */
        HidrobiologicoDto: {
            /**
             * @description Matrícula de la embarcación pesquera (1–15 caracteres, concepto 3001)
             * @example CO-12345-PM
             */
            matricula: string;
            /**
             * @description Nombre de la embarcación pesquera (concepto 3002)
             * @example DON JOSÉ II
             */
            nombre_embarcacion: string;
            /**
             * @description Descripción del tipo de la especie vendida (concepto 3003)
             * @example Anchoveta (Engraulis ringens)
             */
            especie: string;
            /**
             * @description Lugar de descarga (concepto 3004)
             * @example Muelle de Chimbote
             */
            lugar_descarga: string;
            /**
             * Format: date
             * @description Fecha de descarga, `YYYY-MM-DD` (concepto 3005)
             * @example 2026-09-15
             */
            fecha_descarga: string;
            /**
             * @description Cantidad de la especie vendida en toneladas métricas (TNE), hasta 2 decimales (concepto 3006)
             * @example 12.5
             */
            cantidad: number;
        };
        /** @description Impuesto Selectivo al Consumo del ítem (bebidas alcohólicas, combustibles, vehículos…). Opcional; el `precio_unitario` lo incluye. */
        IscDto: {
            /**
             * @description `01` al valor (tasa sobre el valor de venta), `02` monto fijo por unidad, `03` al valor según precio de venta al público (tasa sobre `base_pvp`, el PVP sugerido unitario; cervezas, cigarrillos, gaseosas) — catálogo 08
             * @example 01
             */
            sistema: string;
            /**
             * @description Tasa en %, hasta 5 decimales (sistemas 01 y 03)
             * @example 35
             */
            tasa?: number;
            /**
             * @description Importe por unidad (sistema 02), hasta 5 decimales
             * @example 2.25
             */
            monto_unitario?: number;
            /**
             * @description Solo sistema 03: precio de venta al público sugerido por unidad, sin IGV (base del ISC en el XML, regla 3108); no puede ser menor que el valor unitario. Hasta 5 decimales
             * @example 3.5
             */
            base_pvp?: number;
        };
        ItemDto: {
            /**
             * @description Código interno del producto o servicio (opcional)
             * @example SKU-001
             */
            codigo?: string;
            /**
             * @description Descripción detallada del bien o servicio
             * @example Servicio de consultoría
             */
            descripcion: string;
            /**
             * @description Unidad de medida UN/ECE rec 20 (catálogo 03, `GET /v1/catalogos/03`, las más usadas primero): `NIU` unidad (bienes), `ZZ` unidad (servicios), `KGM` kilogramo, `HUR` hora… Un código que no está en el catálogo responde `422 ITEM_INVALIDO` (2936) sin consumir numeración
             * @example NIU
             */
            unidad: string;
            /**
             * @description Cantidad, hasta 10 decimales. En un ítem con `icbper` es el número de bolsas: entero de 1 a 99999 (`422 ITEM_INVALIDO`, regla `2892`)
             * @example 2
             */
            cantidad: number;
            /**
             * @description Precio de venta unitario **con IGV incluido** (gravados); khipu calcula el valor unitario sin IGV. Mayor que cero: SUNAT rechaza el precio negativo o en cero (`2369`, `2367`) y una línea cuyo valor de venta queda en `0.00` (`2370`) — también el de una gratuita, cuyo `precio_unitario` es su valor referencial; responde `422 ITEM_INVALIDO` sin consumir numeración
             * @example 1000
             */
            precio_unitario: number;
            /**
             * @description Afectación del IGV, catálogo 07 (`GET /v1/catalogos/07`). Onerosas: `10` gravado (IGV 18 %, o la tasa reducida del padrón de restaurantes y hoteles si la empresa la activó; precio con IGV),
             *     `20` exonerado (Apéndice I de la Ley del IGV), `30` inafecto (fuera del ámbito). Gratuitas (bonificaciones, muestras,
             *     retiros): `11`–`16` gravadas, `21` exonerada, `31`–`37` inafectas — el `precio_unitario` es el **valor referencial sin
             *     IGV**, la línea no suma al importe a pagar y su IGV solo se informa (tributo 9996). `17` IVAP (arroz pilado, Ley 28211): 4 % en
             *     lugar del IGV (tributo 1016), precio con IVAP incluido; el comprobante entero debe ser IVAP (no se mezcla con 10/20/30 ni
             *     gratuitas) y la línea no admite ISC ni ICBPER. `40` exportación (tributo 9995, sin IGV: el `precio_unitario` es el valor
             *     de venta): todos los ítems de una factura `0200`–`0208` la llevan y ninguno fuera de ella (2642, 3107); sin ISC ni ICBPER (3223).
             * @example 10
             */
            tipo_afectacion_igv: string;
            descuento?: components["schemas"]["DescuentoDto"];
            /** @description Cargos de la línea (catálogo 53): `afecta_base_igv: true` → `47` (se suma al valor de venta y paga IGV), `false` → `48` (se cobra sin IGV). No admitidos en gratuitas. Opcional. */
            cargos?: components["schemas"]["CargoDto"][];
            isc?: components["schemas"]["IscDto"];
            /**
             * @description `true` si el ítem son bolsas de plástico afectas al ICBPER: una bolsa por unidad (`unidad` NIU: el ICBPER es el monto por bolsa × `cantidad`, y la regla `4320` de SUNAT solo la observa; khipu la rechaza con `422 ITEM_INVALIDO` en un comprobante nuevo; `cantidad` entera de hasta 5 dígitos, `2892`), monto fijo vigente por año incluido en `precio_unitario`, que debe superarlo (`2370`). No se admite en una boleta con `tipo_operacion` `0113` (NRUS, regla `3240`)
             * @example false
             */
            icbper?: boolean;
            /**
             * @description Código de producto SUNAT (catálogo 25, UNSPSC de 8 dígitos; `GET /v1/catalogos/25` lista los que SUNAT exige a los padrones obligados, detracciones y percepciones). Opcional; obligatorio para los emisores del padrón (regla 4331). SUNAT observa los que no llegan al tercer nivel (terminados en 0000, regla 4337)
             * @example 15101505
             */
            codigo_sunat?: string;
            gtin?: components["schemas"]["GtinDto"];
            hidrobiologico?: components["schemas"]["HidrobiologicoDto"];
            transporte?: components["schemas"]["TransporteDto"];
            otros_tributos?: components["schemas"]["OtrosTributosDto"];
            /**
             * @description Placa del vehículo automotor (propiedad 7000 del catálogo 55, hasta 8 caracteres), p. ej. en la venta de combustible. Opcional
             * @example ABC-123
             */
            placa?: string;
        };
        NotaRequest: {
            /**
             * @description `07` nota de crédito, `08` nota de débito (catálogo 01)
             * @example 07
             */
            tipo: string;
            /**
             * @description Serie de la nota, registrada en `POST /v1/series` con el tipo 07/08. Empieza con la letra del documento que modifica (reglas 1001, 2399, 2400): `F` sobre una factura (`FC01`), `B` sobre una boleta (`BC01`)
             * @example FC01
             */
            serie: string;
            /**
             * Format: int64
             * @description Número correlativo. Omítalo para que khipu asigne el siguiente (recomendado)
             * @example 4
             */
            correlativo?: number;
            /**
             * Format: date
             * @description Fecha de emisión, no futura y no anterior a la del documento que modifica (regla 2885). Sobre una factura, se envía hasta el 3.er día; sobre una boleta, sola hasta el 5.º y en el resumen diario hasta el 7.º (la NC `10` y la ND `03` no van en el resumen: hasta el 5.º)
             * @example 2026-09-18
             */
            fecha_emision: string;
            documento_afectado: components["schemas"]["DocumentoAfectadoDto"];
            /**
             * @description Motivo: catálogo 09 para notas de crédito (`01` anulación, `06` devolución total, `07` devolución por ítem, `13` corrección de cuotas…), catálogo 10 para débito (`01` intereses por mora, `02` aumento en el valor, `03` penalidades…). `GET /v1/catalogos/09` y `/10`. Sobre una boleta no se admiten la NC `13` (regla 3259) ni la NC `04`, `05` u `08`: el descuento o la bonificación a un consumidor final va en la misma boleta (RCP art. 10 num. 1.4)
             * @example 01
             */
            motivo: string;
            /**
             * @description Sustento de la nota (1–500 caracteres, sin saltos de línea; regla 2135)
             * @example El cliente devolvió la mercadería completa
             */
            descripcion: string;
            /** @description Ítems que cubre la nota, con el mismo formato (y las mismas reglas de línea: ICBPER, precio mayor que cero) que en la factura, hasta 999 (`422 ITEM_INVALIDO`, regla `2137`). **Omítalos** para una nota total: khipu copia los ítems, el descuento global y los cargos de la factura (anulación, devolución total). Obligatorios si la factura regularizó anticipos (la nota va por el importe neto; si el neto es 0.00 no hay nota posible y se anula con la comunicación de baja). El `descuento` de un ítem debe afectar la base del IGV (`afecta_base_igv` `true`): con `false` (código 01) responde `422 DESCUENTO_INVALIDO` (3280); el descuento y el cargo de línea que afectan la base pasan al valor de la línea, la nota no los lleva como `AllowanceCharge` */
            items?: components["schemas"]["ItemDto"][];
            descuento_global?: components["schemas"]["DescuentoDto"];
            /** @description Cargos globales de la nota. Solo con `items`: sin ellos se rechaza (`NOTA_INVALIDA`) porque la nota total copia los de la factura */
            cargos?: components["schemas"]["CargoDto"][];
            forma_pago?: components["schemas"]["FormaPagoDto"];
            /**
             * @description `true` (por defecto) envía a SUNAT en la misma llamada; `false` deja la nota `FIRMADO` para enviarla con `POST /v1/facturas/{id}/enviar`
             * @example true
             */
            enviar_automatico?: boolean;
            /**
             * @description Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML
             * @example Devolución recibida en tienda.
             */
            observaciones?: string;
        };
        /** @description Otros tributos de la línea (tributo 9999 «OTROS», #317): una tasa sobre el valor de venta de la línea. Se suman al total y no entran en la base del IGV; el `precio_unitario` **no** los incluye. No admitidos en gratuitas ni en exportación; todavía no se combinan con un `descuento_global` ni un cargo global que afectan la base. Opcional */
        OtrosTributosDto: {
            /**
             * @description Tasa en porcentaje sobre el valor de venta de la línea (hasta 3 enteros y 5 decimales, regla 3102)
             * @example 3.5
             */
            tasa: number;
        };
        /** @description Punto de destino del viaje */
        PuntoDto: {
            /**
             * @description Ubigeo INEI de 6 dígitos (catálogo 13)
             * @example 021801
             */
            ubigeo: string;
            /**
             * @description Dirección detallada (3–200 caracteres)
             * @example Av. Los Pescadores 450, Chimbote
             */
            direccion: string;
        };
        /** @description Tramos del viaje con sus vehículos. Opcional */
        TramoDto: {
            /**
             * @description Ubigeo de origen del tramo (catálogo 13). Opcional
             * @example 021801
             */
            origen_ubigeo?: string;
            /**
             * @description Ubigeo de destino del tramo (catálogo 13). Opcional
             * @example 150101
             */
            destino_ubigeo?: string;
            /**
             * @description Descripción del tramo (3–100 caracteres). Opcional
             * @example Chimbote – Lima por Panamericana Norte
             */
            descripcion?: string;
            /**
             * @description Valor preliminar referencial sobre la carga efectiva del tramo, en soles. Opcional
             * @example 2400
             */
            valor_carga_efectiva?: number;
            /**
             * @description Valor preliminar referencial por carga útil nominal del tramo (con más de un vehículo), en soles. Opcional
             * @example 2600
             */
            valor_carga_util_nominal?: number;
            /** @description Vehículos del tramo. Opcional */
            vehiculos?: components["schemas"]["VehiculoDto"][];
        };
        /** @description Servicio de transporte de carga con detracción: obligatorio en cada ítem cuando `tipo_operacion` es `1004` (campos 113–127) y no admitido en otras operaciones */
        TransporteDto: {
            origen: components["schemas"]["PuntoDto"];
            destino: components["schemas"]["PuntoDto"];
            /**
             * @description Detalle del viaje (3–500 caracteres)
             * @example Traslado de 20 t de harina de pescado en camión furgón, ruta Chimbote–Lima
             */
            detalle_viaje: string;
            valor_referencial: components["schemas"]["ValorReferencialDto"];
            /** @description Tramos del viaje con sus vehículos. Opcional */
            tramos?: components["schemas"]["TramoDto"][];
        };
        /** @description Valores referenciales del D.S. 010-2006-MTC, en soles: los tres son obligatorios */
        ValorReferencialDto: {
            /**
             * @description Valor referencial del servicio de transporte (tipo 01), en soles
             * @example 2500
             */
            servicio: number;
            /**
             * @description Valor referencial sobre la carga efectiva (tipo 02), en soles
             * @example 2400
             */
            carga_efectiva: number;
            /**
             * @description Valor referencial sobre la carga útil nominal (tipo 03), en soles
             * @example 2600
             */
            carga_util_nominal: number;
        };
        /** @description Vehículos del tramo. Opcional */
        VehiculoDto: {
            /**
             * @description Configuración vehicular (D.S. 058-2003-MTC), 1–15 caracteres sin espacios. Opcional
             * @example T3S3
             */
            configuracion?: string;
            /**
             * @description Carga útil en toneladas métricas. Opcional
             * @example 30
             */
            carga_util_tm?: number;
            /**
             * @description Carga efectiva en toneladas métricas. Opcional
             * @example 20
             */
            carga_efectiva_tm?: number;
        };
        DocumentoRelacionadoDto: {
            /**
             * @description Catálogo 12: `04` ticket de salida ENAPU, `05` código SCOP, `06` factura electrónica remitente y `07` guía de remisión remitente (solo en factura: en una boleta SUNAT los observa con la 4009 y khipu los rechaza antes de numerar), `08` declaración de salida del depósito franco, `09` declaración simplificada de importación, `99` otros
             * @example 05
             */
            tipo: string;
            /**
             * @description Número del documento, hasta 30 caracteres sin espacios (regla 4010)
             * @example SCOP-8841203
             */
            numero: string;
        };
        GuiaDto: {
            /**
             * @description `09` guía de remisión remitente, `31` guía de remisión transportista (catálogo 01)
             * @example 09
             */
            tipo: string;
            /**
             * @description Serie-número de la guía: electrónica `T001-123`/`V001-45`, física `0001-123` o `EG01-45` (regla 4006)
             * @example T001-123
             */
            numero: string;
        };
        ItemLiquidacionCompra: {
            /**
             * @description Código interno del producto (opcional, hasta 30 caracteres)
             * @example CAF-01
             */
            codigo?: string;
            /**
             * @description Descripción del bien comprado (2026, 2027)
             * @example Café pergamino seco
             */
            descripcion: string;
            /**
             * @description Unidad de medida UN/ECE rec 20 (catálogo 03): `KGM` kilogramo, `NIU` unidad, `TNE` tonelada… (2936)
             * @example KGM
             */
            unidad: string;
            /**
             * @description Cantidad, hasta 10 decimales
             * @example 100
             */
            cantidad: number;
            /**
             * @description Precio unitario: **con IGV incluido** en una línea gravada (`10`), como en la factura; khipu calcula el valor unitario, el IGV y la retención. Mayor que cero (2369)
             * @example 11.8
             */
            precio_unitario: number;
            /**
             * @description Afectación del IGV (catálogo 07): `10` gravado, `20` exonerado (p. ej. productos agrícolas del Apéndice I), `30` inafecto. Las gratuitas todavía no se emiten en una liquidación de compra
             * @example 20
             */
            tipo_afectacion_igv: string;
            /**
             * @description Código de producto SUNAT (catálogo 25, UNSPSC de 8 dígitos): **obligatorio** en la liquidación de compra (3506) y debe estar en el catálogo de SUNAT (3496)
             * @example 50201706
             */
            codigo_sunat: string;
        };
        LiquidacionCompraRequest: {
            /**
             * @description Serie registrada en `POST /v1/series` con el tipo `04`: `L` + 3 alfanuméricos (regla 1001)
             * @example L001
             */
            serie: string;
            /**
             * Format: int64
             * @description Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`
             * @example 15
             */
            correlativo?: number;
            /**
             * Format: date
             * @description Fecha de emisión (`YYYY-MM-DD`), no futura. SUNAT recibe la liquidación de compra hasta el día calendario siguiente (2108): con una fecha anterior responde `422 FECHA_INVALIDA` sin consumir número
             * @example 2026-10-20
             */
            fecha_emision: string;
            /**
             * @description Tipo de operación, catálogo 51: `0501` compra interna (por defecto) o `0502` anticipos. El `0503` (compra de oro) todavía no: exige los datos del derecho minero (catálogo 55, reglas 2466–2469) y responde `422 TIPO_OPERACION_INVALIDO`
             * @example 0501
             */
            tipo_operacion?: string;
            /**
             * @description Moneda ISO 4217 (catálogo 02), las mismas que la factura. Otro código responde `422 MONEDA_INVALIDA` (3088)
             * @example PEN
             */
            moneda: string;
            vendedor: components["schemas"]["VendedorLiquidacionCompra"];
            lugar_operacion: components["schemas"]["LugarDeOperacion"];
            /** @description Lo que se compra: de 1 a 999 líneas (2023) */
            items: components["schemas"]["ItemLiquidacionCompra"][];
            retencion_renta?: components["schemas"]["RetencionRentaDto"];
            /** @description Guías de remisión del traslado (`09` remitente, `31` transportista). Opcional */
            guias?: components["schemas"]["GuiaDto"][];
            /** @description Otros documentos relacionados: en una liquidación de compra solo el tipo `99` (otros); otro tipo responde `422 DOCUMENTO_RELACIONADO_INVALIDO` (4009). Opcional */
            documentos_relacionados?: components["schemas"]["DocumentoRelacionadoDto"][];
            /**
             * @description Redondeo del importe neto (`PayableRoundingAmount`): entre −1.00 y 1.00 con 2 decimales (4314). Opcional
             * @example 0.25
             */
            redondeo?: number;
            /**
             * @description Tipo de cambio: soles por unidad de `moneda` (#316). Opcional y solo fuera de `PEN`; no va al XML
             * @example 3.756
             */
            tipo_cambio?: number;
            /**
             * @description `true` (por defecto) envía a SUNAT en la misma llamada; `false` la deja `FIRMADO` para enviarla con `POST /v1/facturas/{id}/enviar`
             * @example true
             */
            enviar_automatico?: boolean;
            /**
             * @description Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML
             * @example Compra en chacra; pago en efectivo.
             */
            observaciones?: string;
        };
        /** @description Lugar donde se realiza la operación (`cac:DeliveryTerms/cac:DeliveryLocation`): obligatorio (2454, 2455, 2458) */
        LugarDeOperacion: {
            /**
             * @description Dirección del lugar y lo que permita ubicarlo, de 3 a 250 caracteres (2454, 2778)
             * @example Fundo Santa Rosa, km 12 carretera a Bambamarca
             */
            direccion: string;
            /**
             * @description Ubigeo INEI del lugar (catálogo 13; 2455, 2917)
             * @example 060101
             */
            ubigeo: string;
            /**
             * @description Condición del lugar, catálogo 60: `01` punto de venta, `02` producción, `03` extracción, `04` explotación, `05` otros (2458, 2459)
             * @example 02
             */
            condicion: string;
        };
        /** @description Retención del Impuesto a la Renta. Si se omite, la tasa vigente (1.5 %) */
        RetencionRentaDto: {
            /**
             * @description Tasa en porcentaje sobre el valor de venta de cada línea; hoy solo la vigente, 1.5 (otra responde `422 LIQUIDACION_INVALIDA`, 4237)
             * @example 1.5
             */
            tasa?: number;
        };
        /** @description Quien vende: una persona sin RUC con su domicilio */
        VendedorLiquidacionCompra: {
            /**
             * @description Catálogo 06: `1` DNI, `4` carné de extranjería o `7` pasaporte (2800). Quien tiene RUC (`6`) emite su propia factura o boleta
             * @example 1
             */
            tipo_doc: string;
            /**
             * @description DNI de 8 dígitos (2450); carné o pasaporte, hasta 15 caracteres sin espacios (2802)
             * @example 45678912
             */
            num_doc: string;
            /**
             * @description Apellidos y nombres, hasta 1500 caracteres sin saltos de línea (2021, 2022)
             * @example JUAN QUISPE MAMANI
             */
            nombre: string;
            /**
             * @description Dirección del domicilio del vendedor y lo que permita ubicarlo, de 3 a 250 caracteres (2452, 2593)
             * @example Caserío Santa Rosa s/n
             */
            direccion: string;
            /**
             * @description Ubigeo INEI del domicilio (catálogo 13, `GET /v1/catalogos/13`; 2453, 4339)
             * @example 060101
             */
            ubigeo: string;
            /**
             * @description Condición del domicilio, catálogo 60: `01` punto de venta, `02` producción, `03` extracción, `04` explotación, `05` otros (2456, 2457)
             * @example 02
             */
            condicion: string;
        };
        GuiaRemisionConductorRequest: {
            /**
             * @description Tipo de documento, catálogo 06 (1 DNI, 4 carné de extranjería, 7 pasaporte…)
             * @example 1
             */
            tipo_doc: string;
            /** @example 45678912 */
            num_doc: string;
            /** @example JUAN */
            nombres: string;
            /** @example QUISPE MAMANI */
            apellidos: string;
            /**
             * @description Licencia de conducir, de 9 a 10 letras o dígitos
             * @example Q45678912
             */
            licencia: string;
        };
        GuiaRemisionDocumentoRequest: {
            /**
             * @description Tipo, catálogo 61 (01 factura, 03 boleta, 09 guía remitente, 50 DAM…)
             * @example 01
             */
            tipo: string;
            /**
             * @description Número; serie-número en los comprobantes y guías
             * @example F001-123
             */
            numero: string;
            /**
             * @description RUC de quien emitió el documento; obligatorio en los comprobantes y guías
             * @example 20100066603
             */
            ruc_emisor?: string;
        };
        /** @description Datos del traslado */
        GuiaRemisionEnvioRequest: {
            /**
             * Format: date
             * @description Inicio del traslado (o entrega al transportista), no anterior a la emisión (3343). Obligatoria
             * @example 2026-10-21
             */
            fecha_inicio?: string;
            /**
             * @description Peso bruto total, mayor que cero, hasta 3 decimales (2880). Obligatorio
             * @example 25.5
             */
            peso_bruto?: number;
            /**
             * @description `KGM` (por defecto) o `TNE`
             * @example KGM
             */
            unidad_peso?: string;
            /**
             * Format: int32
             * @description Bultos o pallets: solo en una importación (4195), que khipu aún no emite; debe omitirse
             */
            bultos?: number;
            partida: components["schemas"]["GuiaRemisionPuntoRequest"];
            llegada: components["schemas"]["GuiaRemisionPuntoRequest"];
            /** @description El primero es el principal; hasta dos secundarios (4389) */
            vehiculos?: components["schemas"]["GuiaRemisionVehiculoRequest"][];
            /** @description El primero es el principal; hasta dos secundarios (4376) */
            conductores?: components["schemas"]["GuiaRemisionConductorRequest"][];
            /** @description Indicadores especiales: `vehiculo_m1_o_l` (autos o motos: sin vehículo ni conductor), `retorno_vehiculo_envase_vacio`, `retorno_vehiculo_vacio`, `transbordo_programado`, `vehiculos_conductores_transportista` (público: el remitente registra los del transportista) */
            indicadores?: string[];
        };
        GuiaRemisionItemRequest: {
            /** @example LAP-15 */
            codigo?: string;
            /** @example Laptop 15 pulgadas */
            descripcion: string;
            /**
             * @description Unidad, catálogo 03
             * @example NIU
             */
            unidad: string;
            /** @example 2 */
            cantidad: number;
            /**
             * @description Código de producto SUNAT (UNSPSC, 8 dígitos), opcional
             * @example 43211503
             */
            codigo_sunat?: string;
        };
        /** @description Solo transportista: quien envía los bienes */
        GuiaRemisionParteRequest: {
            /**
             * @description Tipo de documento, catálogo 06
             * @example 6
             */
            tipo_doc: string;
            /** @example 20601234565 */
            num_doc: string;
            /**
             * @description Nombre o razón social, de 3 a 250 caracteres
             * @example COMERCIAL ANDINA S.A.C.
             */
            nombre: string;
        };
        GuiaRemisionPuntoRequest: {
            /**
             * @description Ubigeo del catálogo 13. Con solo `establecimiento` (de la propia empresa en una guía remitente) khipu lo completa
             * @example 150101
             */
            ubigeo?: string;
            /**
             * @description Dirección detallada, de 3 a 500 caracteres
             * @example Av. Argentina 1234, Cercado de Lima
             */
            direccion?: string;
            /**
             * @description Código de 4 dígitos del establecimiento anexo (`0000` el domicilio fiscal). Los de la empresa se toman de `GET /v1/empresa/establecimientos`
             * @example 0001
             */
            establecimiento?: string;
            /**
             * @description RUC al que pertenece el establecimiento (3410); por defecto el del remitente
             * @example 20100066603
             */
            ruc?: string;
        };
        GuiaRemisionRequest: {
            /**
             * @description Serie registrada en `POST /v1/series` (tipo `09` o `31`). Su letra decide la guía: `T` + 3 alfanuméricos la remitente (09), `V` + 3 alfanuméricos la transportista (31)
             * @example T001
             */
            serie: string;
            /**
             * Format: date
             * @description Fecha de emisión (`YYYY-MM-DD`), no futura. Por defecto, hoy
             * @example 2026-10-20
             */
            fecha_emision?: string;
            destinatario: components["schemas"]["GuiaRemisionParteRequest"];
            /**
             * @description Solo remitente. Motivo de traslado, catálogo 20 (`GET /v1/catalogos/20`): `01` venta, `02` compra, `03` venta con entrega a terceros, `04` traslado entre establecimientos de la misma empresa, `05` consignación, `06` devolución, `07` recojo de bienes transformados, `13` otros, `14` venta sujeta a confirmación, `17` traslado para transformación. Aún no: `08` importación, `09` exportación y `18` emisor itinerante
             * @example 01
             */
            motivo?: string;
            /**
             * @description Solo remitente. Descripción del motivo (hasta 100 caracteres); obligatoria con `13` (otros). Por defecto, la del catálogo
             * @example Venta
             */
            descripcion_motivo?: string;
            /**
             * @description Solo remitente. Modalidad, catálogo 18: `01` transporte público (va `transportista`, sin vehículos ni conductores) o `02` privado (van `vehiculos` y `conductores`)
             * @example 02
             */
            modalidad?: string;
            transportista?: components["schemas"]["GuiaRemisionTransportistaRequest"];
            remitente?: components["schemas"]["GuiaRemisionParteRequest"];
            /**
             * @description Solo transportista: su número de registro del MTC (obligatorio, regla 4391)
             * @example 1512345CNG
             */
            registro_mtc?: string;
            traslado: components["schemas"]["GuiaRemisionEnvioRequest"];
            /** @description Bienes que se trasladan, de 1 a 9999 */
            items: components["schemas"]["GuiaRemisionItemRequest"][];
            /** @description Documentos que sustentan el traslado (catálogo 61): la factura de la venta, la guía remitente que lleva el transportista, la DAM… */
            documentos_relacionados?: components["schemas"]["GuiaRemisionDocumentoRequest"][];
            /**
             * @description Observaciones (`cbc:Note`), hasta 250 caracteres en una línea
             * @example Mercadería frágil
             */
            observaciones?: string;
            /**
             * @description Enviar a SUNAT en la misma llamada (por defecto `true`). Con `false` queda `FIRMADO` y se envía con `POST /v1/guias-remision/{id}/enviar`
             * @example true
             */
            enviar_automatico?: boolean;
        };
        /** @description Solo remitente en transporte público: la empresa de transporte */
        GuiaRemisionTransportistaRequest: {
            /** @example 20480072872 */
            ruc: string;
            /** @example TRANSPORTES DEL SUR S.A.C. */
            razon_social: string;
            /**
             * @description Número de registro del MTC (opcional)
             * @example 1512345CNG
             */
            registro_mtc?: string;
        };
        GuiaRemisionVehiculoRequest: {
            /**
             * @description Placa, de 6 a 8 letras o dígitos (los guiones se quitan)
             * @example ABC123
             */
            placa: string;
        };
        ApiResponseGuiaRemisionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["GuiaRemisionResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        GuiaRemisionCdrResponse: {
            codigo?: string;
            descripcion?: string;
            observaciones?: string[];
        };
        GuiaRemisionConductorResponse: {
            tipo_doc?: string;
            num_doc?: string;
            nombres?: string;
            apellidos?: string;
            licencia?: string;
            principal?: boolean;
        };
        GuiaRemisionDocumentoResponse: {
            tipo?: string;
            descripcion?: string;
            numero?: string;
            ruc_emisor?: string;
        };
        /** @description Descargas; ausente en `/validar` */
        GuiaRemisionEnlacesResponse: {
            xml?: string;
            pdf?: string;
            cdr?: string;
        };
        GuiaRemisionEnvioResponse: {
            /** Format: date */
            fecha_inicio?: string;
            peso_bruto?: number;
            unidad_peso?: string;
            partida?: components["schemas"]["GuiaRemisionPuntoResponse"];
            llegada?: components["schemas"]["GuiaRemisionPuntoResponse"];
            vehiculos?: components["schemas"]["GuiaRemisionVehiculoResponse"][];
            conductores?: components["schemas"]["GuiaRemisionConductorResponse"][];
            indicadores?: string[];
        };
        GuiaRemisionItemResponse: {
            codigo?: string;
            descripcion?: string;
            unidad?: string;
            cantidad?: number;
            codigo_sunat?: string;
        };
        GuiaRemisionParteResponse: {
            tipo_doc?: string;
            num_doc?: string;
            nombre?: string;
        };
        GuiaRemisionPuntoResponse: {
            ubigeo?: string;
            direccion?: string;
            establecimiento?: string;
            ruc?: string;
        };
        GuiaRemisionResponse: {
            /**
             * Format: uuid
             * @description Id de la guía; en `/validar` es provisional y no existe
             */
            id?: string;
            /**
             * @description `09` remitente o `31` transportista
             * @example 09
             */
            tipo?: string;
            /** @example T001 */
            serie?: string;
            /**
             * Format: int64
             * @description Correlativo; `null` en `/validar`
             * @example 15
             */
            numero?: number;
            /** @example 20100066603-09-T001-15 */
            nombre_archivo?: string;
            /**
             * Format: date
             * @example 2026-10-20
             */
            fecha_emision?: string;
            /** @example 10:04:05 */
            hora_emision?: string;
            /**
             * @description `FIRMADO`, `ERROR_ENVIO`, `ENVIANDO` (envío en curso), `ENVIADO_PENDIENTE_TICKET`, `ACEPTADO`, `ACEPTADO_CON_OBS` o `RECHAZADO` (`RECIBIDO` solo en `/validar`)
             * @example ACEPTADO
             */
            estado?: string;
            destinatario?: components["schemas"]["GuiaRemisionParteResponse"];
            /** @example 01 */
            motivo?: string;
            /** @example Venta */
            descripcion_motivo?: string;
            /** @example 02 */
            modalidad?: string;
            transportista?: components["schemas"]["GuiaRemisionTransportistaResponse"];
            remitente?: components["schemas"]["GuiaRemisionParteResponse"];
            /** @example 1512345CNG */
            registro_mtc?: string;
            traslado?: components["schemas"]["GuiaRemisionEnvioResponse"];
            items?: components["schemas"]["GuiaRemisionItemResponse"][];
            documentos_relacionados?: components["schemas"]["GuiaRemisionDocumentoResponse"][];
            observaciones?: string;
            /** @description Valor resumen de la firma (DigestValue) */
            hash?: string;
            /**
             * @description Ticket que dio SUNAT al recibirla
             * @example 7f7b00ab-4fee-4ae2-9e04-fbd16ca18677
             */
            ticket?: string;
            /**
             * Format: date-time
             * @description Fecha de recepción que informó SUNAT (hora de Lima)
             */
            fecha_recepcion?: string;
            /**
             * Format: int32
             * @description Envíos intentados
             * @example 1
             */
            intentos?: number;
            /**
             * Format: int32
             * @description Consultas del ticket hechas
             * @example 2
             */
            consultas_ticket?: number;
            /** @description Si khipu sigue solo: reintenta el envío (ERROR_ENVIO) o consulta el ticket (ENVIADO_PENDIENTE_TICKET). `false` en ERROR_ENVIO: corrija lo que dice `ultimo_error` y reenvíe; en ENVIADO_PENDIENTE_TICKET: reconsulte el ticket */
            reintento_automatico?: boolean;
            /** @description El último fallo del envío o de la consulta del ticket */
            ultimo_error?: string;
            cdr?: components["schemas"]["GuiaRemisionCdrResponse"];
            /** @description URL de consulta de la guía que trae el CDR (la del QR de la representación impresa), si la trae */
            enlace_consulta?: string;
            enlaces?: components["schemas"]["GuiaRemisionEnlacesResponse"];
        };
        GuiaRemisionTransportistaResponse: {
            ruc?: string;
            razon_social?: string;
            registro_mtc?: string;
        };
        GuiaRemisionVehiculoResponse: {
            placa?: string;
            /** @description El primero es el principal */
            principal?: boolean;
        };
        /** @description Facturas de anticipo que se regularizan en esta factura. Los ítems describen la operación completa y khipu descuenta cada anticipo de la base (código 04/05/06) y del importe a pagar. */
        AnticipoDto: {
            /**
             * @description Serie del comprobante de anticipo: `F###` si fue una factura (tipo 02) o `B###` si fue una boleta (tipo 03); la serie decide el tipo
             * @example F001
             */
            serie: string;
            /**
             * Format: int64
             * @description Número de la factura o boleta de anticipo, emitida por esta empresa y ACEPTADA por SUNAT
             * @example 120
             */
            numero: number;
            /**
             * @description Valor sin IGV que se regulariza; no puede superar lo facturado en el anticipo ni lo facturado en esta factura para la misma afectación
             * @example 1000
             */
            monto: number;
            /**
             * @description Afectación del anticipo (catálogo 53): `gravado` (04, por defecto), `exonerado` (05) o `inafecto` (06). Decide de qué base se descuenta
             * @example gravado
             */
            afectacion?: string;
            /**
             * Format: date
             * @description Fecha en que se pagó el anticipo (opcional, informativa)
             * @example 2026-09-01
             */
            fecha_pago?: string;
        };
        ClienteDto: {
            /**
             * @description Tipo de documento de identidad, catálogo 06. En una factura de venta interna debe ser `6` (RUC); en una exportación (0200–0208), el del cliente del exterior: `0` documento tributario no domiciliado sin RUC, `1` DNI, `4` carné de extranjería, `7` pasaporte, `A`–`G` (regla 2800; `6` no se admite en 0200/0201/0204). En una boleta, cualquiera del catálogo 06, o `-` si el comprador no se identifica: solo en soles y hasta S/ 700.00 de total; en una boleta de exportación (0200–0208) o a no domiciliados (0401), `6` no se admite (regla 2800)
             * @example 6
             */
            tipo_doc: string;
            /**
             * @description RUC de 11 dígitos del adquirente; en exportación y en boleta, el número del documento (DNI de 8 dígitos, los demás hasta 15 caracteres sin espacios: 2801/2802, 4207/4208). Con `tipo_doc` `-`, también `-`
             * @example 20123456786
             */
            num_doc: string;
            /**
             * @description Razón social tal como figura en la ficha RUC del adquirente; en una boleta, el nombre del comprador. De 3 a 1500 caracteres sin contar los espacios de los extremos y sin saltos de línea (regla 2022). Con `tipo_doc` `-`, `CLIENTES VARIOS`
             * @example Comercial Andina SAC
             */
            razon_social: string;
            /**
             * @description Dirección del adquirente (opcional; se imprime en el XML)
             * @example Av. Javier Prado Este 123, San Isidro
             */
            direccion?: string;
            /**
             * @description País del adquirente, código ISO 3166-1 de dos letras (catálogo 04): `cac:Country/cbc:IdentificationCode`. Obligatorio en una exportación; opcional en las demás
             * @example PE
             */
            pais?: string;
            /**
             * @description Correo del adquirente (opcional, hasta 150 caracteres; se guarda en minúsculas). No va al XML ni a SUNAT. Si la empresa activó el envío automático (`PUT /v1/empresa/envio-al-cliente`), cuando SUNAT acepta el comprobante khipu le envía a este correo el PDF, el XML y el CDR; la nota de crédito o débito hereda el correo del comprobante que modifica. Mal formado: `422 RECEPTOR_INVALIDO` antes de consumir número
             * @example compras@andina.pe
             */
            email?: string;
        };
        /** @description Detracción (SPOT). Obligatoria cuando `tipo_operacion` es 1001–1004 y prohibida en los demás casos. Con `1002` cada ítem lleva además `hidrobiologico`; con `1004`, `transporte`. */
        DetraccionDto: {
            /**
             * @description Bien o servicio sujeto a detracción, catálogo 54 (`GET /v1/catalogos/54`). Con tipo de operación 1002/1003/1004 debe ser 004/028/027
             * @example 022
             */
            codigo_bien_servicio: string;
            /**
             * @description Porcentaje de detracción que corresponde al bien/servicio (hasta 5 decimales)
             * @example 12
             */
            porcentaje: number;
            /**
             * @description Monto a depositar **en soles**. Opcional en facturas en PEN (khipu lo calcula); obligatorio en otras monedas
             * @example 1416
             */
            monto?: number;
            /**
             * @description Número de cuenta de detracciones del emisor en el Banco de la Nación. Opcional si la empresa la tiene configurada (`PUT /v1/empresa/datos-fiscales`)
             * @example 00-000-123456
             */
            cuenta_banco_nacion?: string;
            /**
             * @description Medio de pago, catálogo 59; por defecto `001` depósito en cuenta
             * @example 001
             */
            medio_pago?: string;
        };
        /** @description Datos de una factura de exportación (`tipo_operacion` 0200–0208): Incoterm y, en servicios 0201/0208, país de uso. `422 EXPORTACION_INVALIDA` fuera de esos tipos. Opcional */
        ExportacionDto: {
            /**
             * @description Incoterm 2020 de la venta (EXW, FCA, FAS, FOB, CFR, CIF, CPT, CIP, DAP, DPU, DDP): `cac:DeliveryTerms/cbc:ID`. Sin validación SUNAT; lo exige Aduanas. Opcional
             * @example FOB
             */
            incoterm?: string;
            /**
             * @description País donde se usa o aprovecha el servicio exportado (ISO 3166-1, distinto de PE): `cac:Delivery/cac:DeliveryLocation/cac:Address/cac:Country`. Obligatorio en `0201`/`0208` (regla 3098), no admitido en los demás tipos
             * @example US
             */
            pais_uso?: string;
        };
        FacturaRequest: {
            /**
             * @description Serie registrada previamente en `POST /v1/series`. Su letra decide qué se emite: `F` + 3 alfanuméricos una factura (01), `B` + 3 alfanuméricos una boleta de venta (03). La boleta se envía igual que la factura, una por una
             * @example F001
             */
            serie: string;
            /**
             * Format: int64
             * @description Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`
             * @example 125
             */
            correlativo?: number;
            /**
             * Format: date
             * @description Fecha de emisión (`YYYY-MM-DD`), no futura ni con el plazo de envío vencido (`422 FECHA_INVALIDA`): SUNAT exige recibir la factura dentro de los 3 días calendario siguientes (2108); la boleta va sola hasta el 5.º día y en el resumen diario hasta el 7.º (1079)
             * @example 2026-09-14
             */
            fecha_emision: string;
            /**
             * Format: date
             * @description Fecha de vencimiento del pago (`cbc:DueDate`), no anterior a la de emisión. Informativa: al crédito las cuotas de `forma_pago` siguen siendo obligatorias. Opcional
             * @example 2026-10-14
             */
            fecha_vencimiento?: string;
            /**
             * @description Tipo de operación, catálogo 51 (`GET /v1/catalogos/51`); un código fuera del catálogo, o que khipu todavía no emite, responde `422 TIPO_OPERACION_INVALIDO` (regla 3206) antes de consumir número. En factura: `0101` venta interna (por defecto), `0200`/`0201`/`0203`/`0204`/`0206`/`0207`/`0208` exportación (ítems con afectación `40`, cliente del exterior con `pais`, y `exportacion` con el Incoterm), `0401` ventas a no domiciliados que no califican como exportación, `1001`–`1004` operación sujeta a detracción y `2001` percepción. Aún no: `0112`, `0202`/`0205` (hospedaje y paquete turístico: piden los datos del huésped), `0301`, `0302`, `2002`, `2100`–`2104` (entidades financieras y de seguros del SEE-Empresas supervisadas) y `2106`. En boleta (serie `B###`): `0101`, `0113` NRUS, las mismas exportaciones y `0401`, `1001`–`1004` y `2001`; en exportación y en `0401` el comprador no lleva RUC (`422 RECEPTOR_INVALIDO`, regla 2800): su documento del exterior, su DNI o, en soles y hasta S/ 700, sin documento. Aún no en boleta: `0202`/`0205`, `0301`, `0302` y `2100`–`2105`
             * @example 0101
             */
            tipo_operacion?: string;
            /**
             * @description Moneda ISO 4217 de todo el comprobante (catálogo 02): `PEN`, `USD`, `EUR`, `GBP`, `CAD`, `AUD`, `CHF`, `JPY`, `CNY`, `MXN`, `BRL`, `CLP`, `COP`, `ARS`, `BOB`, `UYU` o `PYG`. Otro código responde `422 MONEDA_INVALIDA` (3088) sin consumir número
             * @example PEN
             */
            moneda: string;
            cliente: components["schemas"]["ClienteDto"];
            /** @description Líneas del comprobante: de 1 a 999 (el número de orden del ítem es de hasta 3 dígitos). Con más, `422 ITEM_INVALIDO` con la regla `2023` (`2137` en las notas) sin consumir numeración */
            items: components["schemas"]["ItemDto"][];
            forma_pago?: components["schemas"]["FormaPagoDto"];
            descuento_global?: components["schemas"]["DescuentoDto"];
            /** @description Cargos sobre el total (catálogo 53): `afecta_base_igv: true` → `49` (se suma a la base gravada; requiere ítems gravados), `false` → `50`, `motivo: recargo_consumo` → `46`. Los que no afectan la base suman al importe a pagar (`ChargeTotalAmount`). Opcional. */
            cargos?: components["schemas"]["CargoDto"][];
            detraccion?: components["schemas"]["DetraccionDto"];
            retencion_igv?: components["schemas"]["RetencionDto"];
            percepcion?: components["schemas"]["PercepcionDto"];
            /** @description Facturas de anticipo que se regularizan en esta factura. Los ítems describen la operación completa y khipu descuenta cada anticipo de la base (código 04/05/06) y del importe a pagar. */
            anticipos?: components["schemas"]["AnticipoDto"][];
            /**
             * @description Número de la orden de compra o de servicio del cliente (1–20 caracteres, sin saltos de línea): `cac:OrderReference`. Opcional, informativo (regla 4233)
             * @example OC-2026-0457
             */
            orden_compra?: string;
            /** @description Guías de remisión que sustentan el traslado (`cac:DespatchDocumentReference`). Opcional. */
            guias?: components["schemas"]["GuiaDto"][];
            /** @description Otros documentos relacionados con la operación (`cac:AdditionalDocumentReference`, catálogo 12). Opcional; las facturas de anticipo van en `anticipos`. En una boleta no se admiten los tipos 06 y 07 (regla 4009 de Boleta2_0). */
            documentos_relacionados?: components["schemas"]["DocumentoRelacionadoDto"][];
            /**
             * @description Redondeo del importe total (`PayableRoundingAmount`): se suma al total a pagar; entre −1.00 y 1.00 con 2 decimales (regla 3303). Útil para cobrar en efectivo sin céntimos. Opcional
             * @example -0.4
             */
            redondeo?: number;
            /**
             * @description `true` (por defecto) envía a SUNAT en la misma llamada; `false` deja el comprobante `FIRMADO` para enviarlo luego con `POST /v1/facturas/{id}/enviar` (p. ej. para emitir en lote y enviar después)
             * @example true
             */
            enviar_automatico?: boolean;
            /**
             * @description Texto libre que se imprime en el bloque «Observaciones» del PDF (hasta 1000 caracteres, admite saltos de línea). No va al XML ni a SUNAT. Si se omite, se imprimen las observaciones por defecto de la empresa
             * @example Entrega en almacén central. Horario: 9 a 18 h.
             */
            observaciones?: string;
            /**
             * @description Leyendas del catálogo 52 que declara el emisor (`GET /v1/catalogos/52`): `2001` bienes en Amazonía, `2002` servicios en Amazonía, `2003` contratos de construcción en Amazonía, `2004` paquete turístico, `2005` venta itinerante, `2008`/`2009` zona comercial de Tacna… Van al XML como `cbc:Note`. 2001/2002/2003/2008 exigen total exonerado mayor a 0 (3283–3285, 3289). Las automáticas (monto en letras, gratuitas, detracción, percepción, IVAP) las pone khipu: `422 LEYENDA_INVALIDA` si se envían. Opcional
             * @example [
             *       "2001"
             *     ]
             */
            leyendas?: string[];
            exportacion?: components["schemas"]["ExportacionDto"];
            /**
             * @description Tipo de cambio: soles por unidad de `moneda`, de hasta 3 enteros y 3 decimales (#316). Opcional y solo fuera de `PEN` (en soles responde `422 TIPO_CAMBIO_INVALIDO`). SUNAT no lo recibe en el XML: khipu lo guarda con el comprobante para el registro de ventas, el equivalente en soles del PDF y el tope de S/ 700 de una boleta sin documento en otra moneda
             * @example 3.756
             */
            tipo_cambio?: number;
        };
        /** @description Percepción del IGV que cobra la empresa por ser agente de percepción (catálogo 53: 51/52/53). Solo con `tipo_operacion` 2001, al contado y en PEN. */
        PercepcionDto: {
            /**
             * @description Régimen (catálogo 53): `51` venta interna 2 %, `52` combustible 1 %, `53` tasa especial 0,5 %
             * @example 51
             */
            regimen: string;
            /**
             * @description Tasa; opcional, la fija el régimen (catálogo 22)
             * @example 2
             */
            porcentaje?: number;
            /**
             * @description Base de la percepción; por defecto el importe total
             * @example 1180
             */
            base?: number;
            /**
             * @description Monto; opcional: khipu lo calcula (base × tasa, tolerancia ±1)
             * @example 23.6
             */
            monto?: number;
        };
        /** @description Retención del IGV que aplicará el cliente por ser agente de retención (catálogo 53: 62). Informativa; no cambia los totales. */
        RetencionDto: {
            /**
             * @description Porcentaje de retención; por defecto la tasa legal 3 %
             * @example 3
             */
            porcentaje?: number;
            /**
             * @description Importe retenido; opcional: khipu lo calcula sobre el importe total (tolerancia SUNAT ±1)
             * @example 354
             */
            monto?: number;
        };
        CorreoRequest: {
            /**
             * @description Correo del adquirente al que se envía el comprobante
             * @example compras@cliente.pe
             */
            email: string;
            /**
             * @description Texto opcional que encabeza el correo
             * @example Gracias por su compra.
             */
            mensaje?: string;
        };
        ApiResponseVoid: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: Record<string, never>;
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        BajaRequest: {
            /**
             * @description Motivo de la baja (3–100 caracteres, sin saltos de línea; reglas 2315, 4203). En una factura o nota de factura va en el XML de la comunicación de baja y lo ve SUNAT; en una boleta o nota de boleta queda solo en khipu, porque el resumen diario no lo lleva
             * @example Error en el RUC del cliente
             */
            motivo: string;
        };
        ApiResponseBajaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["BajaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        CrearEmpresaRequest: {
            /** @example 20123456786 */
            ruc: string;
            /**
             * @description Hasta 1500 caracteres, sin saltos de línea ni tabuladores (regla 4338)
             * @example Comercial Andina SAC
             */
            razon_social: string;
            /**
             * @example BETA
             * @enum {string}
             */
            entorno: "BETA" | "PRODUCCION";
        };
        ApiResponseEmpresaVistaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EmpresaVistaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        EmpresaVistaResponse: {
            /**
             * Format: uuid
             * @example 5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f
             */
            id?: string;
            /** @example 20123456786 */
            ruc?: string;
            /** @example Comercial Andina SAC */
            razon_social?: string;
            /** @example BETA */
            entorno?: string;
            /** @example true */
            tiene_certificado?: boolean;
            /** @example true */
            tiene_credenciales_sol?: boolean;
            /**
             * @description Si el domicilio fiscal ya está configurado (va en el XML del emisor)
             * @example true
             */
            tiene_domicilio?: boolean;
        };
        ApiKeyResponse: {
            /**
             * @description Solo se muestra una vez, en texto plano
             * @example fac_live_9k2m4p1q7r3s5t8u
             */
            api_key?: string;
        };
        ApiResponseApiKeyResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ApiKeyResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        InvitarAlEquipoRequest: {
            /** @example contador@estudio.pe */
            email: string;
            /**
             * @description `ADMIN` todo; `EMISOR` emite, consulta y anula comprobantes y mantiene clientes y productos, pero no cambia la configuración ni el equipo; `LECTURA` solo consulta.
             * @example LECTURA
             * @enum {string}
             */
            rol: "ADMIN" | "EMISOR" | "LECTURA";
        };
        ApiResponseInvitacionEnviada: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["InvitacionEnviada"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        InvitacionAlEquipo: {
            /**
             * Format: uuid
             * @example 5e0b7c1a-2d3f-4a5b-8c6d-7e8f9a0b1c2d
             */
            id?: string;
            /** @example contador@estudio.pe */
            email?: string;
            /**
             * @example LECTURA
             * @enum {string}
             */
            rol?: "ADMIN" | "EMISOR" | "LECTURA";
            /**
             * Format: date-time
             * @example 2026-10-27T12:00:00Z
             */
            expira_en?: string;
            /**
             * @description `true` si el enlace ya venció: se puede reenviar.
             * @example false
             */
            vencida?: boolean;
            /**
             * Format: int32
             * @description Cuántas veces se mandó el enlace, contando el primero (hasta 5).
             * @example 1
             */
            envios?: number;
            /**
             * Format: date-time
             * @example 2026-10-20T12:00:00Z
             */
            creada_en?: string;
        };
        InvitacionEnviada: {
            invitacion?: components["schemas"]["InvitacionAlEquipo"];
            /**
             * @description `false` si el correo no salió (servidor de correo caído o sin configurar): la invitación queda pendiente y se puede reenviar.
             * @example true
             */
            correo_enviado?: boolean;
        };
        VerificarRequest: {
            /** @example q8Zf3kV1x... */
            token: string;
        };
        AceptacionDeTerminosResponse: {
            /**
             * @description La versión vigente de los términos del servicio y la política de privacidad, la que se aceptó
             * @example 2026-10-20
             */
            version?: string;
            /**
             * Format: date-time
             * @description Cuándo la aceptó por primera vez
             * @example 2026-10-20T15:00:00Z
             */
            aceptada_en?: string;
        };
        ApiResponseAceptacionDeTerminosResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AceptacionDeTerminosResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        RestablecerRequest: {
            /** @example 3f7a2c9e-1b4d-4e8a-9c2f-6d5e4a3b2c1d */
            token: string;
            /** @example Cambiar123 */
            password: string;
        };
        RegistroRequest: {
            /** @example Comercial Andina SAC */
            nombre: string;
            /** @example facturacion@comercialandina.pe */
            email: string;
            /**
             * @description Mínimo 8 caracteres, con una letra y un número
             * @example Cambiar123
             */
            password: string;
            /**
             * @description Celular de contacto en Perú (9 dígitos, empieza con 9); admite +51/51, espacios y guiones
             * @example 987654321
             */
            telefono: string;
            /**
             * @description Que acepta los términos del servicio y la política de privacidad vigentes (#342). Sin `true`, `400 TERMINOS_NO_ACEPTADOS`.
             * @example true
             */
            acepta_terminos: boolean;
        };
        ApiResponseTokensResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["TokensResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        TokensResponse: {
            /** @example eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ... */
            access?: string;
            /** @example eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ... */
            refresh?: string;
            usuario?: components["schemas"]["UsuarioResponse"];
        };
        UsuarioResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            id?: string;
            /**
             * Format: uuid
             * @example 1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d
             */
            cuenta_id?: string;
            /** @example facturacion@comercialandina.pe */
            email?: string;
            /** @example ADMIN */
            rol?: string;
            /**
             * @description `false` hasta abrir el enlace de verificación (#22): sin verificar no puede crear empresas ni emitir desde el portal.
             * @example true
             */
            correo_verificado?: boolean;
            /**
             * @description `true` si no aceptó la versión vigente de los términos del servicio y la política de privacidad (#342): un usuario anterior a ellos, o uno que aceptó una versión que ya cambió. El portal no deja usar el panel hasta que acepte (`POST /v1/auth/terminos`). Ausente donde no aplica (la respuesta de abrir una sesión de soporte).
             * @example false
             */
            terminos_pendientes?: boolean;
            /**
             * Format: date-time
             * @description Solo en una sesión de soporte (#184: un administrador mirando el portal como este usuario): hasta cuándo vale. Ausente en una sesión normal. El portal lo usa para avisar que se está actuando como el cliente
             * @example 2026-10-04T12:15:00Z
             */
            soporte_hasta?: string;
        };
        RefreshRequest: {
            /** @example eyJhbGciOiJIUzI1NiJ9... */
            refresh: string;
        };
        RecuperarRequest: {
            /** @example facturacion@comercialandina.pe */
            email: string;
        };
        LoginRequest: {
            /** @example facturacion@comercialandina.pe */
            email: string;
            /** @example Cambiar123 */
            password: string;
        };
        InvitacionTokenRequest: {
            /** @example q8Zf3kV1x... */
            token: string;
        };
        ApiResponseInvitacionRecibida: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["InvitacionRecibida"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        InvitacionRecibida: {
            /** @example contador@estudio.pe */
            email?: string;
            /**
             * @description El nombre de la cuenta que invita.
             * @example Ferretería Torres
             */
            cuenta?: string;
            /**
             * @example LECTURA
             * @enum {string}
             */
            rol?: "ADMIN" | "EMISOR" | "LECTURA";
            /**
             * Format: date-time
             * @example 2026-10-27T12:00:00Z
             */
            expira_en?: string;
        };
        AceptarInvitacionRequest: {
            /** @example q8Zf3kV1x... */
            token: string;
            /**
             * @description Al menos 8 caracteres, una letra y un dígito.
             * @example Segura123
             */
            password: string;
        };
        CrearTenantRequest: {
            /** @example 20123456786 */
            ruc: string;
            /** @example Comercial Andina SAC */
            razon_social: string;
            /**
             * @example BETA
             * @enum {string}
             */
            entorno: "BETA" | "PRODUCCION";
        };
        ApiResponseCrearTenantResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["CrearTenantResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        CrearTenantResponse: {
            /**
             * Format: uuid
             * @example 5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f
             */
            tenant_id?: string;
            /** @example 20123456786 */
            ruc?: string;
            /**
             * @description Solo se muestra una vez, en texto plano
             * @example fac_live_9k2m4p1q7r3s5t8u
             */
            api_key?: string;
        };
        ApiResponseInforme: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Informe"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Informe: {
            /** Format: date */
            desde?: string;
            /** Format: date */
            hasta?: string;
            /** Format: int32 */
            verificados?: number;
            problemas?: components["schemas"]["Problema"][];
        };
        Problema: {
            /** Format: uuid */
            comprobante_id?: string;
            /** Format: uuid */
            tenant_id?: string;
            nombre_archivo?: string;
            tipo?: string;
            detalle?: string;
        };
        ApiResponseResultadoDeConexionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ResultadoDeConexionResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ResultadoDeConexionResponse: {
            /**
             * @description `CONECTADO`: contestó con normalidad. `RECHAZADO`: contestó con un error definitivo. `SIN_RESPUESTA`: red, tiempo de espera, error del servicio o de autenticación HTTP
             * @enum {string}
             */
            resultado?: "CONECTADO" | "RECHAZADO" | "SIN_RESPUESTA";
            /**
             * @description Contra qué entorno de SUNAT se probó: el que la empresa tiene ahora
             * @enum {string}
             */
            entorno?: "BETA" | "PRODUCCION";
            /**
             * @description El código de SUNAT; ausente si contestó con normalidad
             * @example 1033
             */
            codigo?: string;
            /**
             * @description El mensaje de SUNAT, tal cual; ausente si contestó con normalidad
             * @example El ticket no existe
             */
            mensaje?: string;
        };
        CambiarEntornoRequest: {
            /**
             * @description El entorno al que pasa la empresa: `BETA` o `PRODUCCION`. Cualquier otro valor responde 400
             * @example PRODUCCION
             * @enum {string}
             */
            entorno?: "BETA" | "PRODUCCION";
        };
        ApiResponseCambioDeEntornoResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["CambioDeEntornoResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        CambioDeEntornoResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            empresa_id?: string;
            /**
             * @description El entorno que tenía
             * @enum {string}
             */
            desde?: "BETA" | "PRODUCCION";
            /**
             * @description El entorno que tiene ahora
             * @enum {string}
             */
            hacia?: "BETA" | "PRODUCCION";
        };
        AvisarRequest: {
            /**
             * @example CERTIFICADO
             * @enum {string}
             */
            tipo?: "CERTIFICADO" | "CREDENCIALES_SOL" | "CERTIFICADO" | "CREDENCIALES_SOL";
        };
        ApiResponseEnviado: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Enviado"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Enviado: {
            /** Format: uuid */
            empresa_id?: string;
            /**
             * @example CERTIFICADO_POR_VENCER
             * @enum {string}
             */
            motivo?: "CERTIFICADO_POR_VENCER" | "CERTIFICADO_VENCIDO" | "CREDENCIALES_SOL_INVALIDAS";
            /**
             * @description A quién se le escribió
             * @example ana@negocio.pe
             */
            destinatario?: string;
            /** Format: date-time */
            enviado_en?: string;
            /**
             * Format: date-time
             * @description Desde cuándo se puede repetir este aviso
             */
            avisar_desde?: string;
        };
        ApiKeyRevocadaResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            api_key_id?: string;
            /**
             * Format: date-time
             * @example 2026-10-03T09:00:00Z
             */
            revocada_en?: string;
        };
        ApiResponseApiKeyRevocadaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ApiKeyRevocadaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseCambioDeDesignacionDeAgente: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["CambioDeDesignacionDeAgente"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        CambioDeDesignacionDeAgente: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            empresa_id?: string;
            desde?: components["schemas"]["DesignacionDeAgente"];
            hacia?: components["schemas"]["DesignacionDeAgente"];
        };
        AltaAsistidaRequest: {
            /** @example Comercial Andina */
            nombre: string;
            /**
             * @description Correo del primer usuario de la cuenta; recibe la invitación
             * @example ana@andina.pe
             */
            email: string;
            /**
             * @description Celular de contacto en Perú (9 dígitos, empieza con 9); admite +51/51, espacios y guiones
             * @example 987654321
             */
            telefono: string;
            empresa: components["schemas"]["Empresa"];
            serie: components["schemas"]["Serie"];
        };
        Empresa: {
            /** @example 20123456786 */
            ruc: string;
            /** @example Comercial Andina SAC */
            razon_social: string;
            /**
             * @description Por defecto BETA
             * @example BETA
             * @enum {string}
             */
            entorno?: "BETA" | "PRODUCCION";
        };
        Serie: {
            /**
             * @description 01=factura, 03=boleta, 07=nota de crédito, 08=nota de débito
             * @example 01
             */
            tipo: string;
            /** @example F001 */
            serie: string;
        };
        AltaAsistidaResponse: {
            /**
             * Format: uuid
             * @example 0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11
             */
            cuenta_id?: string;
            /**
             * Format: uuid
             * @example 5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f
             */
            tenant_id?: string;
            /** @example 20123456786 */
            ruc?: string;
            /**
             * @description Solo se muestra una vez, en texto plano
             * @example fk_9k2m4p1q7r3s5t8u
             */
            api_key?: string;
            serie?: components["schemas"]["SerieCreada"];
            /** @description false si el correo de invitación no salió; el alta queda hecha y el cliente puede pedir un enlace con «olvidé mi contraseña» */
            invitacion_enviada?: boolean;
        };
        ApiResponseAltaAsistidaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AltaAsistidaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        SerieCreada: {
            /** @example 01 */
            tipo?: string;
            /** @example F001 */
            serie?: string;
        };
        SuspenderCuentaRequest: {
            /**
             * @description Por qué se suspende; es opcional y queda en la bitácora de auditoría. Nunca debe llevar secretos
             * @example Factura de septiembre sin pagar
             */
            motivo?: string;
        };
        ApiResponseEstadoCuentaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EstadoCuentaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        EstadoCuentaResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            cuenta_id?: string;
            /** @enum {string} */
            estado?: "ACTIVA" | "SUSPENDIDA" | "BAJA";
            /**
             * Format: date-time
             * @description Desde cuándo está suspendida; ausente si la cuenta no lo está
             * @example 2026-10-02T15:00:00Z
             */
            suspendida_en?: string;
            /**
             * Format: date-time
             * @description Desde cuándo está dada de baja (#201); ausente si está en servicio
             * @example 2026-10-03T09:00:00Z
             */
            baja_en?: string;
        };
        ApiResponseBajaCuentaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["BajaCuentaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        BajaCuentaResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            cuenta_id?: string;
            /**
             * Format: date-time
             * @description Desde cuándo está dada de baja; ausente si la cuenta quedó repuesta
             * @example 2026-10-03T09:00:00Z
             */
            baja_en?: string;
        };
        CambiarPlanRequest: {
            /**
             * Format: uuid
             * @description El plan al que pasa; tiene que estar en la oferta
             */
            plan_id?: string;
            /**
             * Format: date-time
             * @description Hasta cuándo queda pagado (exclusivo: en ese instante empieza la gracia). Obligatorio para un plan de pago; opcional para uno gratis
             * @example 2026-11-01T05:00:00Z
             */
            vence_en?: string;
            /**
             * Format: int32
             * @description Días que se sigue sirviendo después de vencido; 0 si no se indica
             * @example 5
             */
            dias_de_gracia?: number;
        };
        ApiResponsePlanDeCuentaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PlanDeCuentaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        PlanDeCuentaResponse: {
            /** Format: uuid */
            cuenta_id?: string;
            plan?: components["schemas"]["PlanResumenResponse"];
            /**
             * @description `EN_GRACIA`: venció pero sigue sirviéndose los días de gracia; `VENCIDA`: se acabó la gracia
             * @example VIGENTE
             * @enum {string}
             */
            estado?: "VIGENTE" | "EN_GRACIA" | "VENCIDA";
            /**
             * Format: date-time
             * @example 2026-09-01T10:00:00Z
             */
            inicia_en?: string;
            /**
             * Format: date-time
             * @description Hasta cuándo está pagado (exclusivo); ausente en un plan sin vencimiento (el gratis)
             * @example 2026-11-01T05:00:00Z
             */
            vence_en?: string;
            /**
             * Format: int32
             * @example 5
             */
            dias_de_gracia?: number;
            /**
             * Format: date-time
             * @description Hasta cuándo se sirve con este plan, gracia incluida; ausente si no vence
             * @example 2026-11-06T05:00:00Z
             */
            hasta_cuando_cubre?: string;
            programado?: components["schemas"]["ProgramadoResponse"];
        };
        PlanResumenResponse: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
            /** @example 29 */
            precio_mensual?: number;
            limites?: components["schemas"]["LimitesDto"];
        };
        /** @description Una bajada de plan decidida que entra al inicio del ciclo siguiente; ausente si no hay */
        ProgramadoResponse: {
            plan?: components["schemas"]["PlanResumenResponse"];
            /**
             * Format: date-time
             * @example 2026-10-01T05:00:00Z
             */
            aplica_desde?: string;
            /** Format: date-time */
            vence_en?: string;
            /** Format: int32 */
            dias_de_gracia?: number;
        };
        RegistrarPagoRequest: {
            /**
             * Format: date
             * @description Primer día que cubre el pago
             * @example 2026-10-01
             */
            periodo_desde?: string;
            /**
             * Format: date
             * @description Último día que cubre el pago (inclusive); como mucho un año después del primero
             * @example 2026-10-31
             */
            periodo_hasta?: string;
            /**
             * @description En soles, mayor que cero, con hasta dos decimales
             * @example 29
             */
            monto?: number;
            /**
             * @description `TRANSFERENCIA`, `DEPOSITO`, `YAPE`, `PLIN`, `TARJETA`, `EFECTIVO` u `OTRO`
             * @example YAPE
             * @enum {string}
             */
            medio?: "TRANSFERENCIA" | "DEPOSITO" | "YAPE" | "PLIN" | "TARJETA" | "EFECTIVO" | "OTRO";
            /**
             * Format: date
             * @description El día que el cliente pagó; no puede ser futuro
             * @example 2026-10-14
             */
            fecha_de_pago?: string;
            /**
             * @description Número de operación o similar. Opcional; si se da, esa cuenta no puede repetir medio y referencia
             * @example OP-123456
             */
            referencia?: string;
            /** @description Nota libre, opcional */
            nota?: string;
            /** @description Si es `true`, el vencimiento de la suscripción pasa a ser la medianoche (Lima) del día siguiente a `periodo_hasta`. Por defecto `false` */
            extender_vencimiento?: boolean;
        };
        ApiResponsePagoResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PagoResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        PagoResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            cuenta_id?: string;
            /**
             * Format: date
             * @example 2026-10-01
             */
            periodo_desde?: string;
            /**
             * Format: date
             * @description Último día cubierto, inclusive
             * @example 2026-10-31
             */
            periodo_hasta?: string;
            /**
             * @description En soles
             * @example 29
             */
            monto?: number;
            /** @example YAPE */
            medio?: string;
            /**
             * Format: date
             * @example 2026-10-14
             */
            fecha_de_pago?: string;
            /** @description Ausente si no se dio */
            referencia?: string;
            /** @description Ausente si no se dio */
            nota?: string;
            /**
             * Format: date-time
             * @description Cuándo se registró
             */
            registrado_en?: string;
            /**
             * Format: date-time
             * @description El nuevo vencimiento de la suscripción si este pago lo extendió; ausente si solo quedó anotado
             * @example 2026-11-01T05:00:00Z
             */
            extendio_hasta?: string;
        };
        DarDeBajaCuentaRequest: {
            /**
             * @description Por qué se da de baja; es opcional y queda en la bitácora de auditoría. Nunca debe llevar secretos
             * @example Cerró su negocio
             */
            motivo?: string;
        };
        ApiResponseDestinatarioResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["DestinatarioResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        DestinatarioResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            usuario_id?: string;
            /**
             * @description El correo al que se mandó
             * @example ana@negocio.pe
             */
            correo?: string;
        };
        ApiResponseImpersonacionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ImpersonacionResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ImpersonacionResponse: {
            /** @description JWT de una sesión de soporte: del usuario al que se mira, **solo lectura** y sin refresh */
            access_token?: string;
            /**
             * Format: date-time
             * @description Hasta cuándo vale; no se puede renovar: para seguir mirando se impersona de nuevo, y cada vez queda su registro
             * @example 2026-10-04T12:15:00Z
             */
            expira_en?: string;
            usuario?: components["schemas"]["UsuarioResponse"];
        };
        ApiResponseTextoDeCorreo: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["TextoDeCorreo"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseReintentoResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ReintentoResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description El fault de SUNAT si volvió a fallar o lo rechazó; ausente si no */
        Fault: {
            /**
             * @description El código de SUNAT; ausente en un fallo propio (`INFRA - …`) o un texto sin código
             * @example 0109
             */
            codigo?: string;
            /** @example El sistema no puede responder su solicitud */
            mensaje?: string;
        };
        ReintentoResponse: {
            /** Format: uuid */
            comprobante_id?: string;
            /**
             * @description El estado en que quedó: aceptado, rechazado, o `ERROR_ENVIO` si SUNAT volvió a fallar
             * @example ACEPTADO
             */
            estado?: string;
            /**
             * Format: int32
             * @description Cuántas veces falló el envío en total
             * @example 4
             */
            intentos?: number;
            fault?: components["schemas"]["Fault"];
        };
        DescartarComprobanteRequest: {
            /** @example El cliente lo reemitió con otra serie */
            motivo?: string;
        };
        ApiResponseDescarteResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["DescarteResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        DescarteResponse: {
            /** Format: uuid */
            comprobante_id?: string;
            /** @example DESCARTADO */
            estado?: string;
        };
        AdminCodigoRequest: {
            /** @example eyJhbGciOiJIUzI1NiJ9... */
            desafio: string;
            /**
             * @description Los 6 dígitos de la app, o un código de recuperación (`XXXXX-XXXXX`).
             * @example 123456
             */
            codigo: string;
        };
        AdminSesionResponse: {
            /** @example eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ... */
            access_token?: string;
            /**
             * Format: int64
             * @description Segundos de vida del token (`ADMIN_SESION_MINUTOS`). Sin refresh: al vencer se vuelve a iniciar sesión.
             * @example 1800
             */
            expira_en?: number;
            administrador?: components["schemas"]["AdministradorResponse"];
        };
        AdministradorResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            id?: string;
            /** @example ana@khipu.pe */
            email?: string;
        };
        ApiResponseAdminSesionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AdminSesionResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        AdminSesionNuevaResponse: {
            /** @example eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ... */
            access_token?: string;
            /**
             * Format: int64
             * @example 1800
             */
            expira_en?: number;
            administrador?: components["schemas"]["AdministradorResponse"];
            /**
             * @description Diez códigos de un solo uso para entrar sin la app. No se pueden volver a consultar.
             * @example [
             *       "K7M2P-QX9RT"
             *     ]
             */
            codigos_recuperacion?: string[];
        };
        ApiResponseAdminSesionNuevaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AdminSesionNuevaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        AdminDesafioRequest: {
            /** @example eyJhbGciOiJIUzI1NiJ9... */
            desafio: string;
        };
        AdminConfiguracionResponse: {
            /**
             * @description El secreto en Base32, para tipearlo si la app no puede leer el QR.
             * @example JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP
             */
            secreto?: string;
            /** @example otpauth://totp/khipu:ana%40khipu.pe?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=khipu&algorithm=SHA1&digits=6&period=30 */
            uri?: string;
            /** @description PNG del QR de `uri`, en Base64. */
            qr_png?: string;
        };
        ApiResponseAdminConfiguracionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AdminConfiguracionResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        AdminLoginRequest: {
            /** @example ana@khipu.pe */
            email: string;
            /** @example Cambiar123 */
            password: string;
        };
        AdminDesafioResponse: {
            /**
             * @description Token de 5 minutos para completar el login. No autentica ninguna otra ruta.
             * @example eyJhbGciOiJIUzI1NiJ9...
             */
            desafio?: string;
            /**
             * @description `CONFIGURAR_SEGUNDO_FACTOR` la primera vez; después, `VERIFICAR_SEGUNDO_FACTOR`.
             * @enum {string}
             */
            paso?: "CONFIGURAR_SEGUNDO_FACTOR" | "VERIFICAR_SEGUNDO_FACTOR";
        };
        ApiResponseAdminDesafioResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AdminDesafioResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        CrearAdministradorRequest: {
            /** @example ana@khipu.pe */
            email: string;
            /** @example Cambiar123 */
            password: string;
        };
        ApiResponseAdministradorResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AdministradorResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Actualizar: {
            /** @description Nueva URL https; omitida, se deja la actual */
            url?: string;
            /** @description Nueva descripción; vacía la borra */
            descripcion?: string;
            /** @description Nuevos eventos; omitidos, se dejan los actuales */
            eventos?: string[];
            /** @description `false` lo apaga; `true` lo reactiva (también uno que khipu pausó) con los fallos en cero */
            activo?: boolean;
        };
        ApiResponseWebhook: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Webhook"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListWebhook: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Webhook"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListEntrega: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Entrega"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Entrega: {
            /** Format: uuid */
            id?: string;
            /** @example comprobante.aceptado */
            evento?: string;
            /**
             * Format: uuid
             * @description El comprobante del evento; nulo en la entrega de prueba
             */
            comprobante_id?: string;
            /** @description PENDIENTE, EXITOSA o FALLIDA */
            estado?: string;
            /** Format: int32 */
            intentos?: number;
            /**
             * Format: date-time
             * @description Cuándo se reintenta; solo si está pendiente
             */
            siguiente_intento?: string;
            /**
             * Format: int32
             * @description Código HTTP del último intento; nulo si no hubo respuesta
             */
            ultimo_http?: number;
            ultimo_error?: string;
            /** Format: date-time */
            ultimo_intento_en?: string;
            /** Format: date-time */
            entregada_en?: string;
            /** Format: date-time */
            creada_en?: string;
        };
        ApiResponseListString: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: string[];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        StreamingResponseBody: Record<string, never>;
        ApiResponseListSerieResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["SerieResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        SerieResponse: {
            /**
             * @description 01=factura, 03=boleta, 07=nota de crédito, 08=nota de débito
             * @example 01
             */
            tipo?: string;
            /** @example F001 */
            serie?: string;
            /**
             * Format: int64
             * @example 125
             */
            ultimo_numero?: number;
            /** @example true */
            activa?: boolean;
            /**
             * @description Establecimiento desde el que emite (`0000` = domicilio fiscal)
             * @example 0000
             */
            establecimiento?: string;
        };
        ApiResponseListProducto: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Producto"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListPlanPublicado: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PlanPublicado"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        PlanPublicado: {
            /** @example Emprende */
            nombre?: string;
            /** @example 29 */
            precio_mensual?: number;
            limites?: components["schemas"]["LimitesDto"];
        };
        ApiResponseListGuiaRemisionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["GuiaRemisionResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListComprobanteResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ComprobanteResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListBajaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["BajaResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseResumenDeComprobantesResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ResumenDeComprobantesResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Lo que pide que el emisor haga algo: rechazados, con error de envío, fuera de plazo y boletas sin enviar */
        AtencionRequerida: {
            /**
             * Format: int64
             * @description La suma de los cuatro
             * @example 9
             */
            total?: number;
            /**
             * Format: int64
             * @description Rechazados por SUNAT: hay que corregir y reemitir
             * @example 3
             */
            rechazados?: number;
            /**
             * Format: int64
             * @description Con error de envío: se reintentan solos o con `POST /v1/facturas/{id}/enviar`
             * @example 2
             */
            errores_de_envio?: number;
            /**
             * Format: int64
             * @description No llegaron a SUNAT dentro del plazo: hay que emitir de nuevo
             * @example 2
             */
            fuera_de_plazo?: number;
            /**
             * Format: int64
             * @description Boletas (o notas que las modifican) que quedaron `FIRMADAS` porque se emitieron con `enviar_automatico: false` y ya están en el 5.º día o más: nadie las ha enviado y el resumen diario cierra el 7.º. Se envían con `POST /v1/facturas/{id}/enviar`
             * @example 2
             */
            boletas_sin_enviar?: number;
        };
        /** @description Lo facturado por moneda, ordenado por moneda; vacío si no hay nada. Cuentan los aceptados y los que están en camino; no los rechazados, fuera de plazo ni dados de baja */
        Facturado: {
            /** @example PEN */
            moneda?: string;
            /**
             * @description Facturas, boletas y notas de débito suman; las notas de crédito restan. Negativo solo si las notas superan lo emitido
             * @example 12345.67
             */
            total?: number;
        };
        ResumenDeComprobantesResponse: {
            /**
             * Format: date
             * @description El `desde` pedido; ausente si el rango estaba abierto de ese lado
             * @example 2026-09-01
             */
            desde?: string;
            /**
             * Format: date
             * @description El `hasta` pedido; ausente si el rango estaba abierto de ese lado
             * @example 2026-09-30
             */
            hasta?: string;
            /**
             * Format: int64
             * @description Comprobantes firmados del período, en cualquier estado (rechazados y dados de baja incluidos)
             * @example 120
             */
            emitidos?: number;
            /**
             * Format: int64
             * @description Aceptados por SUNAT, con o sin observaciones: los que ya tienen su CDR
             * @example 100
             */
            aceptados_con_cdr?: number;
            /**
             * Format: int64
             * @description En camino a SUNAT, sin nada que corregir: firmados que aún no se envían, enviados que esperan su CDR y boletas que esperan su resumen diario. Las boletas sin enviar desde el 5.º día cuentan acá y también en `atencion_requerida.boletas_sin_enviar`
             * @example 8
             */
            en_camino?: number;
            atencion_requerida?: components["schemas"]["AtencionRequerida"];
            /** @description Lo facturado por moneda, ordenado por moneda; vacío si no hay nada. Cuentan los aceptados y los que están en camino; no los rechazados, fuera de plazo ni dados de baja */
            facturado?: components["schemas"]["Facturado"][];
        };
        ApiResponseEmisionPorDiaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EmisionPorDiaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Solo los días con algo emitido o facturado, ordenados por fecha; un día sin comprobantes no viene. Vacío si no hay nada en el rango */
        DiaDeEmision: {
            /**
             * Format: date
             * @description Fecha de emisión
             * @example 2026-10-05
             */
            fecha?: string;
            /**
             * Format: int64
             * @description Comprobantes firmados ese día, en cualquier estado, como `emitidos` del resumen
             * @example 12
             */
            emitidos?: number;
            /** @description Lo facturado ese día por moneda, ordenado por moneda, como `facturado` del resumen; vacío si no hay nada */
            facturado?: components["schemas"]["Facturado"][];
        };
        EmisionPorDiaResponse: {
            /**
             * Format: date
             * @description El `desde` pedido
             * @example 2026-10-01
             */
            desde?: string;
            /**
             * Format: date
             * @description El `hasta` pedido
             * @example 2026-10-31
             */
            hasta?: string;
            /** @description Solo los días con algo emitido o facturado, ordenados por fecha; un día sin comprobantes no viene. Vacío si no hay nada en el rango */
            dias?: components["schemas"]["DiaDeEmision"][];
        };
        ApiResponseListEmpresaVistaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EmpresaVistaResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListEstablecimientoResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EstablecimientoResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiKeyResumenResponse: {
            /**
             * Format: uuid
             * @example 3b9d5c1e-7f2a-4d8b-9c6e-1a2b3c4d5e6f
             */
            id?: string;
            /**
             * @description Primeros 10 caracteres de la key, para identificarla
             * @example fk_9k2m4p1q
             */
            prefijo?: string;
            /** @example true */
            activa?: boolean;
            /**
             * Format: date-time
             * @example 2026-09-15T10:00:00Z
             */
            creada_en?: string;
            /**
             * Format: date-time
             * @description Presente solo si fue revocada
             */
            revocada_en?: string;
        };
        ApiResponseListApiKeyResumenResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ApiKeyResumenResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseMiCuentaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["MiCuentaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ConsumoDelMesResponse: {
            /**
             * @description El mes calendario en curso (America/Lima)
             * @example 2026-10
             */
            mes?: string;
            /**
             * Format: int64
             * @description Lo que consumieron todas las empresas de la cuenta: lo que SUNAT aceptó, aunque después se anule
             * @example 12
             */
            documentos?: number;
            /**
             * Format: int32
             * @description El tope de documentos al mes del plan de hoy; ausente si es ilimitado
             * @example 300
             */
            maximo?: number;
        };
        MiCuentaResponse: {
            /** @example Ferretería Torres */
            nombre?: string;
            plan?: components["schemas"]["PlanDeCuentaResponse"];
            consumo?: components["schemas"]["ConsumoDelMesResponse"];
        };
        ApiResponseListMiembroDelEquipo: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["MiembroDelEquipo"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListInvitacionAlEquipo: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["InvitacionAlEquipo"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        AccesoDeSoporteResponse: {
            /**
             * Format: date-time
             * @example 2026-10-04T10:00:00Z
             */
            ocurrido_en?: string;
            /**
             * @description A qué usuario de la cuenta se miró; ausente si el registro no tiene un formato que se entienda
             * @example ana@negocio.pe
             */
            usuario?: string;
            /**
             * Format: int64
             * @description Cuánto duró como máximo la sesión, en segundos; ausente si el registro no tiene un formato que se entienda
             * @example 900
             */
            duracion_segundos?: number;
        };
        ApiResponseListAccesoDeSoporteResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["AccesoDeSoporteResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseValidezResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ValidezResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ValidezResponse: {
            /**
             * @description `ACEPTADO`, `RECHAZADO`, `DE_BAJA`, `NO_EXISTE`, `AJENO`, `AUTORIZADO`, `NO_AUTORIZADO` o `ERROR_CONSULTA`
             * @example ACEPTADO
             */
            estado?: string;
            /**
             * @description Código de retorno de SUNAT en su fuente: `statusCode` de billValidService (`0001`…) o `estadoCp` de la API REST (`0`…`4`)
             * @example 0001
             */
            codigo?: string;
            /** @example El comprobante existe y está aceptado. */
            mensaje?: string;
            /**
             * @description Por dónde respondió SUNAT: `API_REST` (credenciales API SUNAT) o `SOAP` (billValidService, clave SOL)
             * @example API_REST
             */
            fuente?: string;
            /**
             * @description Solo por la API REST: estado del RUC del emisor (`00` activo, `01` baja provisional, `02` baja prov. por oficio, `03` suspensión temporal, `10` baja definitiva, `11` baja de oficio, `22` inhabilitado-vent. única)
             * @example 00
             */
            estado_ruc?: string;
            /**
             * @description Solo por la API REST: condición de domicilio del emisor (`00` habido, `09` pendiente, `11` por verificar, `12` no habido, `20` no hallado)
             * @example 00
             */
            condicion_domicilio?: string;
            /** @description Solo por la API REST: observaciones de SUNAT sobre los datos consultados (p. ej. la fecha no coincide); ausente si no hay */
            observaciones?: string[];
            /**
             * @description Por qué se respondió por SOAP teniendo credenciales API SUNAT; ausente si no
             * @example La API de SUNAT no respondió (HTTP 503): se usó billValidService con la clave SOL
             */
            aviso?: string;
        };
        ApiResponsePadronRucResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PadronRucResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        PadronRucResponse: {
            /** @example 20100066603 */
            ruc?: string;
            /** @example TIENDAS DEL SUR S.A.C. */
            razon_social?: string;
            /**
             * @description Estado del contribuyente, tal como lo publica SUNAT (`ACTIVO`, `BAJA DE OFICIO`, `BAJA DEFINITIVA`, `SUSPENSION TEMPORAL`…; el archivo los trunca a 20 caracteres)
             * @example ACTIVO
             */
            estado?: string;
            /**
             * @description Condición del domicilio fiscal, tal como la publica SUNAT (`HABIDO`, `NO HABIDO`, `PENDIENTE`, `NO HALLADO …`, `-`)
             * @example HABIDO
             */
            condicion?: string;
            /** @description `estado` es `ACTIVO` */
            activo?: boolean;
            /** @description `condicion` es `HABIDO` */
            habido?: boolean;
            /**
             * @description Ubigeo del domicilio fiscal; casi nunca viene para un RUC 10
             * @example 150101
             */
            ubigeo?: string | null;
            /**
             * @description Domicilio fiscal armado con las partes que publica SUNAT; sin departamento, provincia ni distrito (solo el `ubigeo`)
             * @example AV. REPUBLICA DE PANAMA NRO. 2457
             */
            direccion?: string | null;
            /**
             * Format: date-time
             * @description Cuándo terminó la carga del padrón que respondió. Un RUC creado después de esa fecha todavía no aparece
             */
            actualizado_al?: string;
        };
        ApiResponseListCliente: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Cliente"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListCatalogoIndice: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["CatalogoIndice"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Resumen del catálogo (por defecto) o el catálogo completo con sus entradas (`completo=true`) */
        CatalogoIndice: components["schemas"]["Resumen"] | components["schemas"]["CatalogoResponse"];
        /** @description Catálogo oficial de SUNAT (Anexo 8 de las reglas de validación) con sus códigos y descripciones. */
        CatalogoResponse: {
            /**
             * @description Número del catálogo SUNAT
             * @example 07
             */
            id?: string;
            /** @example Código de tipo de afectación del IGV */
            nombre?: string;
            /** @description Cabeceras de la tabla oficial; las adicionales a Código/Descripción aparecen en `extra` de cada entrada */
            columnas?: string[];
            entradas?: components["schemas"]["EntradaDto"][];
        };
        EntradaDto: {
            /** @example 10 */
            codigo?: string;
            /** @example Gravado - Operación Onerosa */
            descripcion?: string;
            /** @description Columnas adicionales del catálogo (p. ej. Codigo de tributo = 1000) */
            extra?: {
                [key: string]: string;
            };
        };
        Resumen: {
            id?: string;
            nombre?: string;
            /**
             * Format: int32
             * @example 19
             */
            entradas?: number;
        };
        ApiResponseCatalogoResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["CatalogoResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseBannerPublico: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["BannerPublico"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        BannerPublico: {
            /** @example Mantenimiento programado esta noche de 22:00 a 23:00 */
            texto?: string;
            /** Format: date-time */
            desde?: string;
            /** Format: date-time */
            hasta?: string;
        };
        ApiResponseUsuarioResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["UsuarioResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseListPlanResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PlanResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseOrigenResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["OrigenResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        OrigenResponse: {
            /**
             * @description IP que el backend resolvió para esta petición: la misma que la bitácora de auditoría registraría
             * @example 203.0.113.7
             */
            ip?: string;
        };
        ApiResponseMonitorDeEmisionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["MonitorDeEmisionResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Boletas que siguen sin informarse a SUNAT y ya están en el 5.º día o más, de todas las empresas */
        BoletasSinInformarResponse: {
            /**
             * Format: int64
             * @description Cuántas boletas (o notas que van en el resumen diario) siguen sin informarse desde el 5.º día
             * @example 3
             */
            cantidad?: number;
            /**
             * Format: int64
             * @description De ellas, las que están en el 7.º día (el último del resumen diario) o ya lo pasaron
             * @example 1
             */
            por_vencer_el_plazo?: number;
            /**
             * Format: date
             * @description La fecha de emisión de la más antigua; ausente si no hay
             * @example 2026-10-08
             */
            mas_antigua_emitida_el?: string;
            /** @description Hay al menos una boleta sin informar desde el 5.º día */
            alerta?: boolean;
        };
        /** @description El día de Lima (desde su medianoche) hasta ahora */
        FranjaResponse: {
            /**
             * Format: date-time
             * @description El inicio de la hora (o de la medianoche de Lima en `hoy`)
             * @example 2026-10-15T15:00:00Z
             */
            desde?: string;
            /**
             * Format: int64
             * @description Comprobantes creados en la franja, de cualquier estado
             * @example 120
             */
            total?: number;
            /**
             * Format: int64
             * @description Aceptados por SUNAT, con o sin observaciones
             * @example 100
             */
            aceptados?: number;
            /**
             * Format: int64
             * @description Rechazados por SUNAT
             * @example 3
             */
            rechazados?: number;
            /**
             * Format: int64
             * @description No llegaron: error de envío o fuera de plazo
             * @example 2
             */
            con_error?: number;
            /**
             * Format: int64
             * @description Firmados, enviados o a la espera del resumen diario
             * @example 10
             */
            en_camino?: number;
            /**
             * Format: int64
             * @description Recibidos, inválidos o dados de baja
             * @example 5
             */
            otros?: number;
            /**
             * Format: double
             * @description Rechazados sobre los que SUNAT ya resolvió (aceptados + rechazados), de 0 a 1; ausente si no resolvió ninguno
             * @example 0.0291
             */
            tasa_de_rechazo?: number;
        };
        MonitorDeEmisionResponse: {
            /**
             * Format: date-time
             * @description Cuándo se tomó esta lectura
             * @example 2026-10-15T15:20:00Z
             */
            generado_en?: string;
            /** @description Las últimas 24 horas, de la más vieja a la actual; las horas sin comprobantes van en cero */
            horas?: components["schemas"]["FranjaResponse"][];
            hoy?: components["schemas"]["FranjaResponse"];
            outbox?: components["schemas"]["OutboxResponse"];
            resumenes_sin_respuesta?: components["schemas"]["ResumenesSinRespuestaResponse"];
            boletas_sin_informar?: components["schemas"]["BoletasSinInformarResponse"];
            /** @description Si cada servicio de SUNAT contesta; un servicio sin URL configurada no aparece. Es una lectura guardada hasta 30 segundos */
            sunat?: components["schemas"]["SunatResponse"][];
        };
        OutboxResponse: {
            /**
             * Format: int64
             * @description Envíos a SUNAT que esperan en la cola
             * @example 12
             */
            pendientes?: number;
            /**
             * Format: int64
             * @description Los que ya tocaba enviar y nadie tomó; no cuenta lo que está en proceso ni lo que espera su reintento
             * @example 1
             */
            vencidos?: number;
            /**
             * Format: date-time
             * @description Desde cuándo está el más antiguo; ausente si la cola está vacía
             * @example 2026-10-15T12:00:00Z
             */
            mas_viejo_desde?: string;
            /**
             * Format: int64
             * @description Segundos que lleva vencido el que más espera; ausente si no hay vencidos
             * @example 420
             */
            vencido_hace_segundos?: number;
            /** @description Hay envíos vencidos hace más de 5 minutos: el trabajo que vacía la cola probablemente no corre */
            alerta?: boolean;
        };
        /** @description Resúmenes diarios y comunicaciones de baja que SUNAT recibió (tienen ticket) y no ha respondido pasado el umbral configurado */
        ResumenesSinRespuestaResponse: {
            /**
             * Format: int64
             * @description Cuántos resúmenes diarios (RC) o comunicaciones de baja (RA) enviados llevan más del umbral sin respuesta
             * @example 2
             */
            cantidad?: number;
            /**
             * Format: date-time
             * @description Cuándo SUNAT dio el ticket del más antiguo (no cuándo se creó el resumen); ausente si no hay
             * @example 2026-10-15T05:00:00Z
             */
            mas_viejo_desde?: string;
            /**
             * Format: int64
             * @description Segundos que lleva el más antiguo sin respuesta; ausente si no hay
             * @example 37200
             */
            mas_viejo_hace_segundos?: number;
            /**
             * Format: int64
             * @description Horas sin respuesta a partir de las cuales se alerta (`app.monitor.resumen-sin-respuesta-horas`)
             * @example 6
             */
            umbral_horas?: number;
            /** @description Hay al menos un resumen o baja con ticket sin respuesta pasado el umbral */
            alerta?: boolean;
        };
        /** @description Si cada servicio de SUNAT contesta; un servicio sin URL configurada no aparece. Es una lectura guardada hasta 30 segundos */
        SunatResponse: {
            /**
             * @example ENVIO_PRODUCCION
             * @enum {string}
             */
            servicio?: "ENVIO_PRODUCCION" | "ENVIO_BETA" | "CONSULTA_DE_CDR" | "CONSULTA_DE_VALIDEZ";
            /** @description Contestó con un éxito dentro del plazo */
            disponible?: boolean;
            /**
             * Format: int64
             * @description Milisegundos que tardó en contestar; ausente si no contestó
             * @example 140
             */
            milisegundos?: number;
            /**
             * @description Por qué no está disponible (código HTTP, «Sin conexión», «No contestó en N s»); ausente si lo está
             * @example HTTP 503
             */
            detalle?: string;
        };
        ApiResponseListErrorDeEmisionResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ErrorDeEmisionResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ErrorDeEmisionResponse: {
            /** Format: uuid */
            comprobante_id?: string;
            /**
             * Format: uuid
             * @description La empresa dueña del comprobante
             */
            empresa_id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            /**
             * Format: uuid
             * @description La cuenta dueña de la empresa; ausente en una empresa dada de alta por una integración
             */
            cuenta_id?: string;
            /**
             * @description El nombre de esa cuenta; ausente si no hay cuenta
             * @example Ana Pérez
             */
            cuenta_nombre?: string;
            /**
             * @description RUC-tipo-serie-número: la identidad del comprobante ante SUNAT
             * @example 20100066603-01-F001-7
             */
            nombre_archivo?: string;
            /** @example 01 */
            tipo?: string;
            /** @example F001 */
            serie?: string;
            /**
             * Format: int64
             * @example 7
             */
            numero?: number;
            /**
             * Format: date
             * @example 2026-10-12
             */
            fecha_emision?: string;
            /**
             * @description El estado del comprobante: `ERROR_ENVIO`, `FUERA_DE_PLAZO`, `RECHAZADO` (con un fault de formato) o `FIRMADO` (una boleta sin enviar)
             * @example ERROR_ENVIO
             */
            estado?: string;
            /**
             * @description Qué tipo de problema es. `ERROR_DE_FORMATO` es un rechazo por fault de SUNAT 1000–1999: terminal, no se reintenta. `BOLETA_SIN_ENVIAR` es una boleta (o nota que la modifica) que el cliente dejó `FIRMADO` con `enviar_automatico: false` y ya está en el 5.º día o más: no falló nada, pero el resumen diario cierra el 7.º día
             * @example ERROR_DE_ENVIO
             * @enum {string}
             */
            clase?: "ERROR_DE_ENVIO" | "ERROR_DE_FORMATO" | "FUERA_DE_PLAZO" | "BOLETA_SIN_ENVIAR";
            /**
             * Format: int32
             * @description Cuántas veces falló el envío
             * @example 3
             */
            intentos?: number;
            fault?: components["schemas"]["Fault"];
            /**
             * Format: date-time
             * @description Cuándo lo reintentará el trabajo del outbox; ausente si no hay un reintento programado
             * @example 2026-10-15T18:30:00Z
             */
            proximo_intento?: string;
            /**
             * Format: date-time
             * @description Cuándo cambió por última vez
             * @example 2026-10-15T15:00:00Z
             */
            actualizado_en?: string;
            /** @description Si un administrador puede reintentarlo o descartarlo: solo un error de envío; las otras clases ya son terminales, o enviar es decisión del cliente */
            accionable?: boolean;
        };
        ApiResponseListEmpresaAdminResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EmpresaAdminResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        EmpresaAdminResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            /**
             * Format: uuid
             * @description Cuenta dueña de la empresa; ausente en las empresas dadas de alta por una integración, que no tienen cuenta
             */
            cuenta_id?: string;
            /**
             * @description Nombre de esa cuenta
             * @example Mi negocio
             */
            cuenta_nombre?: string;
            /** @enum {string} */
            entorno?: "BETA" | "PRODUCCION";
            /**
             * @description Estado del certificado hoy (fecha de Lima). `POR_VENCER`: menos de 30 días; el último día de vigencia todavía vale y `VENCIDO` empieza el día siguiente. `SIN_FECHA`: cargado, sin vigencia conocida
             * @enum {string}
             */
            certificado?: "SIN_CERTIFICADO" | "SIN_FECHA" | "VIGENTE" | "POR_VENCER" | "VENCIDO";
            /**
             * Format: date
             * @description Hasta cuándo vale el certificado; ausente si no hay certificado o no se conoce
             * @example 2027-03-01
             */
            certificado_vigente_hasta?: string;
            /**
             * Format: int32
             * @description Días hasta esa fecha; negativo si ya venció; ausente si no hay fecha
             * @example 17
             */
            certificado_dias_restantes?: number;
            /** @description Credenciales SOL cargadas (usuario y clave); no se sabe si son válidas */
            tiene_credenciales_sol?: boolean;
            /**
             * Format: int32
             * @description Series activas
             * @example 2
             */
            series?: number;
            /**
             * Format: int32
             * @description Documentos con fecha de emisión en el mes de hoy (Lima)
             * @example 31
             */
            comprobantes_del_mes?: number;
            /**
             * Format: date
             * @description Fecha de emisión del documento más reciente; ausente si nunca emitió
             * @example 2026-10-02
             */
            ultima_emision?: string;
            /**
             * Format: date-time
             * @description Desde cuándo su cuenta está dada de baja (#201); ausente si está en servicio o si la empresa no tiene cuenta
             * @example 2026-10-03T09:00:00Z
             */
            cuenta_de_baja_en?: string;
        };
        /** @description Sin el secreto: solo el prefijo con el que el dueño reconoce cada key */
        ApiKeyDeDetalle: {
            /** Format: uuid */
            id?: string;
            /** @example fk_demo001 */
            prefijo?: string;
            activa?: boolean;
            /** Format: date-time */
            creada_en?: string;
            /**
             * Format: date-time
             * @description Ausente si sigue vigente
             */
            revocada_en?: string;
        };
        ApiResponseEmpresaDetalleResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["EmpresaDetalleResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description CDR de SUNAT; ausente si todavía no respondió */
        CdrResponse: {
            /**
             * @description 0 = aceptada
             * @example 0
             */
            codigo?: string;
            /** @example La Factura numero F001-12, ha sido aceptada */
            descripcion?: string;
            /** @description Observaciones de SUNAT; vacía si no hay ninguna */
            observaciones?: string[];
        };
        /** @description Los 10 comprobantes de fecha de emisión más reciente, con el detalle del CDR de SUNAT */
        ComprobanteDeEmpresa: {
            /** Format: uuid */
            id?: string;
            /**
             * @description Tipo de comprobante (catálogo 01)
             * @example 01
             */
            tipo?: string;
            /** @example F001 */
            serie?: string;
            /**
             * Format: int64
             * @example 12
             */
            numero?: number;
            /**
             * Format: date
             * @example 2026-09-30
             */
            fecha_emision?: string;
            /** @example ACEPTADO */
            estado?: string;
            /** @example PEN */
            moneda?: string;
            /** @example 118 */
            total?: number;
            /**
             * Format: int32
             * @description Intentos de envío a SUNAT
             */
            intentos?: number;
            /** @description Error del último intento; ausente si no falló */
            ultimo_error?: string;
            cdr?: components["schemas"]["CdrResponse"];
        };
        DomicilioDeDetalle: {
            /** @example 150122 */
            ubigeo?: string;
            /** @example AV. LARCO 345 */
            direccion?: string;
            urbanizacion?: string;
            /** @example MIRAFLORES */
            distrito?: string;
            /** @example LIMA */
            provincia?: string;
            /** @example LIMA */
            departamento?: string;
            /**
             * @description Código de establecimiento; 0000 es el domicilio fiscal
             * @example 0000
             */
            codigo_establecimiento?: string;
        };
        EmpresaDetalleResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            /**
             * @description Ausente si no tiene nombre comercial
             * @example ANDINA
             */
            nombre_comercial?: string;
            /** @enum {string} */
            entorno?: "BETA" | "PRODUCCION";
            /**
             * Format: date-time
             * @description Fecha de alta
             * @example 2026-09-01T10:00:00Z
             */
            creada_en?: string;
            /**
             * Format: uuid
             * @description Cuenta dueña; ausente en las empresas dadas de alta por una integración, que no tienen cuenta
             */
            cuenta_id?: string;
            /** @example Mi negocio */
            cuenta_nombre?: string;
            /**
             * @description Estado del certificado hoy (fecha de Lima), con la misma regla del listado de empresas
             * @enum {string}
             */
            certificado?: "SIN_CERTIFICADO" | "SIN_FECHA" | "VIGENTE" | "POR_VENCER" | "VENCIDO";
            /**
             * Format: date
             * @description Hasta cuándo vale el certificado; ausente si no hay certificado o no se conoce
             * @example 2027-03-01
             */
            certificado_vigente_hasta?: string;
            /**
             * Format: int32
             * @description Días hasta esa fecha; negativo si ya venció; ausente si no hay fecha
             * @example 17
             */
            certificado_dias_restantes?: number;
            /** @description Si están cargados el usuario y la clave SOL; nunca se exponen */
            tiene_credenciales_sol?: boolean;
            domicilio?: components["schemas"]["DomicilioDeDetalle"];
            /** @example 00-123-456789 */
            cuenta_detracciones?: string;
            /** @description Si figura en el padrón de contribuyentes con tasa especial de IGV */
            padron_tasa_especial_igv?: boolean;
            pdf?: components["schemas"]["PdfResponse"];
            series?: components["schemas"]["SerieDeDetalle"][];
            establecimientos?: components["schemas"]["EstablecimientoDeDetalle"][];
            /** @description Sin el secreto: solo el prefijo con el que el dueño reconoce cada key */
            api_keys?: components["schemas"]["ApiKeyDeDetalle"][];
            /** @description Los 10 comprobantes de fecha de emisión más reciente, con el detalle del CDR de SUNAT */
            comprobantes?: components["schemas"]["ComprobanteDeEmpresa"][];
            /** @description Los últimos 20 cambios de estado de esos comprobantes, del más reciente al más antiguo */
            eventos?: components["schemas"]["EventoDeEmpresa"][];
            outbox?: components["schemas"]["OutboxDeEmpresa"];
        };
        EstablecimientoDeDetalle: {
            /** @example 0002 */
            codigo?: string;
            /** @example Tienda Surco */
            nombre?: string;
            domicilio?: components["schemas"]["DomicilioDeDetalle"];
            activo?: boolean;
        };
        /** @description Los últimos 20 cambios de estado de esos comprobantes, del más reciente al más antiguo */
        EventoDeEmpresa: {
            /**
             * @description Comprobante al que pertenece el cambio
             * @example F001-00000012
             */
            comprobante?: string;
            /**
             * @description Ausente en el primer evento de un comprobante
             * @example FIRMADO
             */
            estado_anterior?: string;
            /** @example ACEPTADO */
            estado_nuevo?: string;
            detalle?: string;
            /**
             * Format: date-time
             * @example 2026-09-30T15:00:00Z
             */
            ocurrido_en?: string;
        };
        OutboxDeEmpresa: {
            /**
             * Format: int64
             * @description Tareas pendientes de esta empresa; el outbox se vacía al completar cada una
             * @example 12
             */
            total?: number;
            /** @description Las próximas 10 en intentarse */
            proximas?: components["schemas"]["TareaResponse"][];
        };
        PdfResponse: {
            /**
             * @description CLASICO, MODERNO, SUTIL, CORPORATIVO o GRIS
             * @example CLASICO
             */
            plantilla?: string;
            /** @example #1E1E24 */
            color_primario?: string;
            /** @description Si hay un logo cargado; no se expone dónde está guardado */
            tiene_logo?: boolean;
            pie_de_pagina?: string;
            observaciones_por_defecto?: string;
        };
        SerieDeDetalle: {
            /**
             * @description Tipo de comprobante (catálogo 01)
             * @example 01
             */
            tipo?: string;
            /** @example F001 */
            codigo?: string;
            /**
             * Format: int64
             * @description Último número emitido
             * @example 12
             */
            ultimo_numero?: number;
            activa?: boolean;
            /** @example 0000 */
            establecimiento?: string;
        };
        /** @description Las próximas 10 en intentarse */
        TareaResponse: {
            /** @example DOCUMENTO */
            agregado?: string;
            /** Format: uuid */
            agregado_id?: string;
            /** @example ENVIAR */
            accion?: string;
            /** Format: int32 */
            intentos?: number;
            /** Format: date-time */
            siguiente_intento?: string;
            /** @description Error del último intento; ausente si todavía no falló */
            ultimo_error?: string;
        };
        ApiResponseConsumoEmpresaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ConsumoEmpresaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ConsumoEmpresaResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            empresa_id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            /**
             * @description El mes calendario (America/Lima) al que corresponde
             * @example 2026-10
             */
            mes?: string;
            /**
             * Format: int64
             * @description Comprobantes aceptados por SUNAT (con o sin observaciones) con fecha de emisión en el mes
             * @example 312
             */
            documentos?: number;
        };
        ApiResponseListCuentaAdminResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["CuentaAdminResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        CuentaAdminResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            id?: string;
            /** @example Mi negocio */
            nombre?: string;
            /** @example ana@negocio.pe */
            email?: string;
            /**
             * @description Celular de contacto; ausente en cuentas anteriores a ese campo
             * @example 987654321
             */
            telefono?: string;
            /**
             * Format: date-time
             * @description Fecha de alta
             * @example 2026-09-01T10:00:00Z
             */
            creada_en?: string;
            /**
             * Format: int32
             * @description Empresas de la cuenta
             * @example 2
             */
            empresas?: number;
            /**
             * Format: date-time
             * @description Última sesión de sus usuarios en el portal (inicio de sesión o refresco de token); el uso por API key no cuenta. Ausente si nunca inició sesión
             * @example 2026-10-01T09:00:00Z
             */
            ultimo_acceso?: string;
            /**
             * @description ACTIVA, SUSPENDIDA o BAJA. Una cuenta suspendida no entra al portal ni emite por API, y sigue en el listado para poder reactivarla. Una de BAJA (#201) es la de un cliente que se fue: solo sale si se pide con `bajas`, y manda sobre la suspensión
             * @enum {string}
             */
            estado?: "ACTIVA" | "SUSPENDIDA" | "BAJA";
            /**
             * Format: date-time
             * @description Desde cuándo está suspendida; ausente si la cuenta no lo está
             * @example 2026-10-02T15:00:00Z
             */
            suspendida_en?: string;
            /**
             * Format: date-time
             * @description Desde cuándo está dada de baja; ausente si está en servicio
             * @example 2026-10-03T09:00:00Z
             */
            baja_en?: string;
        };
        ApiResponseCuentaDetalleResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["CuentaDetalleResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Los 10 comprobantes de fecha de emisión más reciente entre todas las empresas de la cuenta */
        ComprobanteDeCuenta: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            empresa_id?: string;
            /**
             * @description RUC de la empresa de la cuenta a la que pertenece
             * @example 20100066603
             */
            ruc?: string;
            /**
             * @description Tipo de comprobante (catálogo 01)
             * @example 01
             */
            tipo?: string;
            /** @example F001 */
            serie?: string;
            /**
             * Format: int64
             * @example 15
             */
            numero?: number;
            /**
             * Format: date
             * @example 2026-09-30
             */
            fecha_emision?: string;
            /** @example ACEPTADO */
            estado?: string;
            /** @example PEN */
            moneda?: string;
            /** @example 118 */
            total?: number;
        };
        CuentaDetalleResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            id?: string;
            /** @example Mi negocio */
            nombre?: string;
            /** @example ana@negocio.pe */
            email?: string;
            /**
             * @description Celular de contacto; ausente en cuentas anteriores a ese campo
             * @example 987654321
             */
            telefono?: string;
            /**
             * Format: date-time
             * @description Fecha de alta
             * @example 2026-09-01T10:00:00Z
             */
            creada_en?: string;
            /**
             * @description ACTIVA, SUSPENDIDA (#182) o BAJA (#201); la baja manda sobre la suspensión
             * @enum {string}
             */
            estado?: "ACTIVA" | "SUSPENDIDA" | "BAJA";
            /**
             * Format: date-time
             * @description Desde cuándo está suspendida; ausente si la cuenta no lo está
             * @example 2026-10-02T15:00:00Z
             */
            suspendida_en?: string;
            /**
             * Format: date-time
             * @description Desde cuándo está dada de baja; ausente si está en servicio. Una cuenta de baja se abre igual, con todo lo suyo
             * @example 2026-10-03T09:00:00Z
             */
            baja_en?: string;
            usuarios?: components["schemas"]["UsuarioDeCuenta"][];
            empresas?: components["schemas"]["EmpresaDeCuenta"][];
            /** @description Los 10 comprobantes de fecha de emisión más reciente entre todas las empresas de la cuenta */
            comprobantes?: components["schemas"]["ComprobanteDeCuenta"][];
            /** @description Las últimas 10 acciones del administrador sobre esta cuenta (bitácora) */
            eventos?: components["schemas"]["EventoDeCuenta"][];
            /** @description Todas las suspensiones, reactivaciones, bajas y reposiciones de la cuenta, de la más reciente a la más antigua, con su motivo en `detalle` */
            historial_estado?: components["schemas"]["EventoDeCuenta"][];
        };
        EmpresaDeCuenta: {
            /** Format: uuid */
            id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            /**
             * @description BETA o PRODUCCION
             * @example PRODUCCION
             */
            entorno?: string;
            /** @description Si hay un certificado digital cargado; nunca se expone su contenido ni su clave */
            tiene_certificado?: boolean;
            /**
             * Format: date
             * @description Hasta cuándo vale el certificado; ausente si no hay certificado
             * @example 2027-03-01
             */
            certificado_vigente_hasta?: string;
            /** @description Si están cargados el usuario y la clave SOL; nunca se exponen */
            tiene_credenciales_sol?: boolean;
        };
        /** @description Todas las suspensiones, reactivaciones, bajas y reposiciones de la cuenta, de la más reciente a la más antigua, con su motivo en `detalle` */
        EventoDeCuenta: {
            /** @example CREAR_CUENTA */
            accion?: string;
            /**
             * @description ADMINISTRADOR o CLAVE_PLATAFORMA
             * @example ADMINISTRADOR
             */
            actor?: string;
            /**
             * @description Correo del administrador que la hizo; ausente si fue la clave de la plataforma o si ese administrador ya no existe
             * @example ana@khipu.pe
             */
            administrador?: string;
            /**
             * Format: date-time
             * @example 2026-09-01T10:00:05Z
             */
            ocurrido_en?: string;
            /**
             * @description Contexto de la acción; nunca lleva secretos
             * @example ruc=20100066603 serie=F001
             */
            detalle?: string;
        };
        UsuarioDeCuenta: {
            /** Format: uuid */
            id?: string;
            /** @example ana@negocio.pe */
            email?: string;
            /**
             * @description ADMIN, EMISOR o LECTURA
             * @example ADMIN
             */
            rol?: string;
            activo?: boolean;
            /**
             * Format: date-time
             * @description Cuándo verificó su correo (#22); ausente si todavía no
             */
            correo_verificado_en?: string;
            /**
             * Format: date-time
             * @description Su sesión más reciente en el portal; el uso por API key no cuenta. Ausente si nunca inició sesión
             */
            ultimo_acceso?: string;
        };
        ApiResponsePrevisualizacionDePlanResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PrevisualizacionDePlanResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description La bajada que estaba esperando y que este cambio deja sin efecto (un cambio inmediato la cancela; otra bajada la reemplaza); ausente si no hay */
        DescartadoResponse: {
            plan?: components["schemas"]["PlanResumenResponse"];
            /**
             * Format: date-time
             * @example 2026-10-01T05:00:00Z
             */
            aplica_desde?: string;
        } | null;
        PrevisualizacionDePlanResponse: {
            /** Format: uuid */
            cuenta_id?: string;
            plan_actual?: components["schemas"]["PlanResumenResponse"];
            plan_nuevo?: components["schemas"]["PlanResumenResponse"];
            /**
             * @description Lo decide el precio: más caro es subida, más barato bajada, el mismo plan una renovación
             * @example BAJADA
             * @enum {string}
             */
            direccion?: "SUBIDA" | "BAJADA" | "RENOVACION";
            /**
             * @description Subir y renovar entran ya; bajar, al inicio del ciclo siguiente
             * @example CICLO_SIGUIENTE
             * @enum {string}
             */
            efecto?: "INMEDIATO" | "CICLO_SIGUIENTE";
            /**
             * Format: date-time
             * @description Desde cuándo manda el plan nuevo
             * @example 2026-10-01T05:00:00Z
             */
            aplica_desde?: string;
            /**
             * @description El mes en curso (calendario de America/Lima) del que se habla
             * @example 2026-09
             */
            mes?: string;
            /**
             * Format: int64
             * @description Documentos que la cuenta ha consumido este mes: solo los comprobantes aceptados por SUNAT
             * @example 312
             */
            consumo_del_mes?: number;
            limite_de_documentos?: components["schemas"]["LimiteDto"];
            /** @description El consumo de este mes ya pasa el tope del plan nuevo: una advertencia (un cambio inmediato dejaría a la cuenta por encima de su límite; una bajada no toca este mes) */
            supera_el_limite?: boolean;
            programado_que_se_descarta?: components["schemas"]["DescartadoResponse"];
        };
        ApiResponseListPagoResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["PagoResponse"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseConsumoCuentaResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ConsumoCuentaResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ConsumoCuentaResponse: {
            /**
             * Format: uuid
             * @example 9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d
             */
            cuenta_id?: string;
            /**
             * @description El mes calendario (America/Lima) al que corresponde
             * @example 2026-10
             */
            mes?: string;
            /**
             * Format: int64
             * @description La suma de lo que consumió cada una de sus empresas
             * @example 450
             */
            documentos?: number;
            /** @description Cada empresa de la cuenta, por RUC, aunque no haya emitido nada ese mes */
            empresas?: components["schemas"]["ConsumoEmpresaResponse"][];
        };
        ApiResponseConsumoDeCuentasResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["ConsumoDeCuentasResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ConsumoDeCuentasResponse: {
            /**
             * @description El mes calendario (America/Lima) que se midió
             * @example 2026-10
             */
            mes?: string;
            /**
             * Format: int32
             * @description Desde qué porcentaje del tope una cuenta está en alerta (inclusive)
             * @example 80
             */
            umbral_de_alerta?: number;
            /** @description Una página de cuentas, en el orden pedido */
            cuentas?: components["schemas"]["CuentaConsumo"][];
        };
        /** @description Una página de cuentas, en el orden pedido */
        CuentaConsumo: {
            /** Format: uuid */
            cuenta_id?: string;
            /** @example Librería Andina */
            nombre?: string;
            /** @example ana@libreria.pe */
            email?: string;
            /** Format: uuid */
            plan_id?: string;
            /**
             * @description El plan de hoy de la cuenta; se compara con él aunque el mes sea pasado
             * @example Emprende
             */
            plan?: string;
            /**
             * Format: int64
             * @description Comprobantes aceptados por SUNAT en el mes
             * @example 240
             */
            documentos?: number;
            /**
             * Format: int32
             * @description El tope de documentos al mes que rige hoy; ausente si el plan no tiene tope
             * @example 300
             */
            limite?: number;
            /**
             * Format: int32
             * @description El porcentaje del tope usado, hacia abajo; ausente si el plan no tiene tope
             * @example 80
             */
            porcentaje?: number;
            /** @description Ya llegó al umbral de alerta */
            en_alerta?: boolean;
            /**
             * @description `VIGENTE`, `EN_GRACIA` o `VENCIDA`
             * @example EN_GRACIA
             */
            estado_del_plan?: string;
            /**
             * Format: date-time
             * @description Hasta cuándo está pagado el plan; ausente si no vence
             */
            pagado_hasta?: string;
            /**
             * Format: date-time
             * @description Hasta cuándo se la sirve, con la gracia incluida; ausente si no vence
             */
            se_sirve_hasta?: string;
        };
        ApiResponseListPlantilla: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Plantilla"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        ApiResponseFichaDeComprobanteResponse: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["FichaDeComprobanteResponse"];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        /** @description Sus comunicaciones de baja (`RA-…`) y resúmenes diarios (`RC-…`: la baja o el alta de una boleta o de una nota de boleta), de la más reciente a la más antigua; vacía si no tiene. Solo lectura */
        BajaDto: {
            /** Format: uuid */
            id?: string;
            /**
             * @description `RA-yyyymmdd-N` (comunicación de baja) o `RC-yyyymmdd-N` (resumen diario)
             * @example RC-20261013-2
             */
            identificador?: string;
            /**
             * @description `BAJA` anula el comprobante; `ALTA` informa en el resumen diario una boleta que pasó el envío individual
             * @example BAJA
             */
            condicion?: string;
            /**
             * @description `GENERADA` → `ENVIADA` → `ACEPTADA` / `RECHAZADA`; `ERROR_ENVIO` (se reintenta)
             * @example ACEPTADA
             */
            estado?: string;
            /**
             * @description Ticket del proceso en SUNAT; ausente si todavía no lo dio
             * @example 1789768174685
             */
            ticket?: string;
            /** @example Se cobró dos veces */
            motivo?: string;
            /**
             * Format: date
             * @description Fecha de generación del RA o RC (la del identificador)
             * @example 2026-10-13
             */
            fecha_generacion?: string;
            /**
             * Format: date
             * @description Fecha de emisión del comprobante tal como se comunicó a SUNAT
             * @example 2026-10-12
             */
            fecha_referencia?: string;
            /** @description Solo en un `ALTA`: se generó pasado el 7.º día desde la emisión (previsión de khipu; la que cuenta es `cdr` del comprobante) */
            fuera_de_plazo?: boolean;
            cdr?: components["schemas"]["RespuestaSunat"];
            /**
             * Format: int32
             * @description Cuántas veces falló el envío o la consulta del ticket
             * @example 0
             */
            intentos?: number;
            /** @description El último fallo; ausente si no hubo */
            ultimo_error?: string;
        };
        FichaDeComprobanteResponse: {
            /** Format: uuid */
            id?: string;
            /**
             * Format: uuid
             * @description La empresa dueña del comprobante
             */
            empresa_id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            /**
             * Format: uuid
             * @description La cuenta dueña de la empresa; ausente en una empresa dada de alta por una integración
             */
            cuenta_id?: string;
            /**
             * @description RUC-tipo-serie-número: la identidad del comprobante ante SUNAT
             * @example 20100066603-01-F001-7
             */
            nombre_archivo?: string;
            /** @example 01 */
            tipo?: string;
            /** @example F001 */
            serie?: string;
            /**
             * Format: int64
             * @example 7
             */
            numero?: number;
            /**
             * Format: date
             * @example 2026-10-12
             */
            fecha_emision?: string;
            /** @example ERROR_ENVIO */
            estado?: string;
            /**
             * Format: int32
             * @description Cuántas veces falló el envío
             * @example 3
             */
            intentos?: number;
            /**
             * @description El último fallo de envío; ausente si no hubo
             * @example 0109 - El sistema no puede responder
             */
            ultimo_error?: string;
            respuesta_sunat?: components["schemas"]["RespuestaSunat"];
            /** @description Si el XML firmado está guardado */
            tiene_xml?: boolean;
            /** @description Si el CDR de SUNAT está guardado */
            tiene_cdr?: boolean;
            /** @description Sus comunicaciones de baja (`RA-…`) y resúmenes diarios (`RC-…`: la baja o el alta de una boleta o de una nota de boleta), de la más reciente a la más antigua; vacía si no tiene. Solo lectura */
            bajas?: components["schemas"]["BajaDto"][];
        };
        /** @description Lo que respondió SUNAT; ausente si todavía no respondió */
        RespuestaSunat: {
            /** @example 0 */
            codigo?: string;
            /** @example La Factura numero F001-7, ha sido aceptada */
            descripcion?: string;
        };
        ApiResponseListSol: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Sol"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Cuenta: {
            /** Format: uuid */
            id?: string;
            /** @example Ana Pérez */
            nombre?: string;
            /** @example ana@negocio.pe */
            email?: string;
        };
        Sol: {
            /** Format: uuid */
            empresa_id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            cuenta?: components["schemas"]["Cuenta"];
            /**
             * Format: int64
             * @description Cuántos comprobantes están en error de envío por las credenciales ahora mismo
             * @example 4
             */
            comprobantes_afectados?: number;
            /**
             * Format: date-time
             * @description Cuándo falló el último
             * @example 2026-10-15T14:50:00Z
             */
            ultimo_fallo?: string;
            /**
             * @description Lo que dijo SUNAT en el último fallo
             * @example 0102 - Usuario o contraseña incorrectos
             */
            ultimo_error?: string;
            ultimo_aviso?: components["schemas"]["Ultimo"];
            /** Format: date-time */
            avisar_desde?: string;
            puede_avisar?: boolean;
        };
        Ultimo: {
            /**
             * Format: date-time
             * @example 2026-10-10T15:00:00Z
             */
            enviado_en?: string;
            /** @example ana@negocio.pe */
            destinatario?: string;
        };
        ApiResponseListCertificado: {
            /**
             * @description "exito" o "error"
             * @example exito
             */
            estado?: string;
            datos?: components["schemas"]["Certificado"][];
            /**
             * @description Mensaje legible, presente solo en error
             * @example null
             */
            mensaje?: string;
            /**
             * @description Código de error, presente solo en error
             * @example null
             */
            codigo?: string;
            /**
             * @description Errores de validación por campo, presente solo en VALIDACION
             * @example null
             */
            errores?: {
                [key: string]: string[];
            };
            /** @description Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar */
            avisos?: components["schemas"]["Aviso"][];
        };
        Certificado: {
            /** Format: uuid */
            empresa_id?: string;
            /** @example 20100066603 */
            ruc?: string;
            /** @example COMERCIAL ANDINA SAC */
            razon_social?: string;
            cuenta?: components["schemas"]["Cuenta"];
            /**
             * @example CERTIFICADO_POR_VENCER
             * @enum {string}
             */
            motivo?: "CERTIFICADO_POR_VENCER" | "CERTIFICADO_VENCIDO";
            /**
             * Format: date
             * @description El último día que vale el certificado
             * @example 2026-10-25
             */
            vigente_hasta?: string;
            /**
             * Format: int32
             * @description Días que le quedan; negativo si ya venció
             * @example 10
             */
            dias_restantes?: number;
            ultimo_aviso?: components["schemas"]["Ultimo"];
            /**
             * Format: date-time
             * @description Desde cuándo se puede repetir el aviso; ausente si nunca se avisó o la espera ya pasó
             * @example 2026-10-17T15:00:00Z
             */
            avisar_desde?: string;
            /** @description Hay a quién avisarle y no se avisó lo mismo en la última semana */
            puede_avisar?: boolean;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    obtener: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseProducto"];
                };
            };
        };
    };
    actualizar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProductoGuardar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseProducto"];
                };
            };
        };
    };
    eliminar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    ver_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePersonalizacionPdfResponse"];
                };
            };
        };
    };
    guardar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonalizacionPdfRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePersonalizacionPdfResponse"];
                };
            };
        };
    };
    logo: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    cargarLogo: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    /** Format: binary */
                    archivo: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePersonalizacionPdfResponse"];
                };
            };
        };
    };
    borrarLogo: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePersonalizacionPdfResponse"];
                };
            };
        };
    };
    editarEstablecimiento: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                codigo: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EstablecimientoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEstablecimientoResponse"];
                };
            };
        };
    };
    darDeBajaEstablecimiento: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                codigo: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    ver_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePreferenciaDeEnvioAlCliente"];
                };
            };
        };
    };
    fijar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PreferenciaDeEnvioAlClienteRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePreferenciaDeEnvioAlCliente"];
                };
            };
        };
    };
    datosFiscales: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DatosFiscalesRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEmpresaResponse"];
                };
            };
        };
    };
    credencialesSol: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CredencialesSolRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    credencialesApiSunat: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CredencialesApiSunatRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    borrarCredencialesApiSunat: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    cambiarRolDeUsuario: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CambiarRolRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMiembroDelEquipo"];
                };
            };
        };
    };
    obtener_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCliente"];
                };
            };
        };
    };
    actualizar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ClienteCrear"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCliente"];
                };
            };
        };
    };
    eliminar_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    guardarPorDocumento: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                tipoDoc: string;
                numDoc: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ClienteGuardar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCliente"];
                };
            };
        };
    };
    editar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del plan */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PlanRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlanResponse"];
                };
            };
        };
    };
    eliminar_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del plan */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
        };
    };
    guardarPlantilla: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description El correo
                 * @example RECUPERACION_CLAVE
                 */
                tipo: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PlantillaPorGuardar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlantilla"];
                };
            };
        };
    };
    restaurarPlantilla: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description El correo
                 * @example RECUPERACION_CLAVE
                 */
                tipo: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlantilla"];
                };
            };
        };
    };
    remitente: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseRemitente"];
                };
            };
        };
    };
    cambiarRemitente: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["RemitentePorGuardar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseRemitente"];
                };
            };
        };
    };
    restablecerRemitente: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseRemitente"];
                };
            };
        };
    };
    banner: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBanner"];
                };
            };
        };
    };
    publicarBanner: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["BannerPorPublicar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBanner"];
                };
            };
        };
    };
    retirarBanner: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
        };
    };
    listar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListWebhook"];
                };
            };
        };
    };
    crear: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WebhookCrear"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCreado"];
                };
            };
        };
    };
    rotarSecreto: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseSecreto"];
                };
            };
        };
    };
    probar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePrueba"];
                };
            };
        };
    };
    reenviar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entregaId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    conciliarRvie: {
        parameters: {
            query: {
                /**
                 * @description Periodo tributario, `AAAAMM`
                 * @example 202610
                 */
                periodo: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** @description La propuesta del RVIE descargada del SIRE (.txt o .zip) */
        requestBody: {
            content: {
                "application/zip": string;
                "text/plain": string;
                "application/octet-stream": string;
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseConciliacionRvie"];
                };
            };
        };
    };
    series: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListSerieResponse"];
                };
            };
        };
    };
    crearSerie: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SerieRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    retener: {
        parameters: {
            query?: never;
            header?: {
                /**
                 * @description Clave única por retención (un UUID), para repetir el pedido sin emitir dos veces
                 * @example 5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f
                 */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RetencionRequest"];
            };
        };
        responses: {
            /** @description Pedido repetido con la misma `Idempotency-Key`: la retención ya emitida, en su estado actual */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Retención creada; `estado_documento` indica si SUNAT ya la aceptó */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Correlativo repetido en la serie (DUPLICADO) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Datos inválidos, empresa no designada o regla de negocio incumplida; `codigo` y `mensaje` explican cuál */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    calcularRetencion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RetencionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    listar_1: {
        parameters: {
            query?: {
                /** @description Texto a buscar en el código o la descripción */
                q?: string;
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListProducto"];
                };
            };
        };
    };
    crear_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProductoGuardar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseProducto"];
                };
            };
        };
    };
    percibir: {
        parameters: {
            query?: never;
            header?: {
                /**
                 * @description Clave única por percepción (un UUID), para repetir el pedido sin emitir dos veces
                 * @example 5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f
                 */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PercepcionRequest"];
            };
        };
        responses: {
            /** @description Pedido repetido con la misma `Idempotency-Key`: la percepción ya emitida, en su estado actual */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Percepción creada; `estado_documento` indica si SUNAT ya la aceptó */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Correlativo repetido en la serie (DUPLICADO) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Datos inválidos, empresa no designada o regla de negocio incumplida; `codigo` y `mensaje` explican cuál */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    calcularPercepcion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PercepcionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    crear_2: {
        parameters: {
            query?: never;
            header?: {
                /**
                 * @description Clave única por nota (un UUID), para repetir el pedido sin emitir dos veces
                 * @example 5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f
                 */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NotaRequest"];
            };
        };
        responses: {
            /** @description Pedido repetido con la misma `Idempotency-Key`: la nota ya emitida, en su estado actual */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Nota creada; `estado_documento` indica si SUNAT ya la aceptó y `nota` trae la factura modificada y el motivo */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    crear_3: {
        parameters: {
            query?: never;
            header?: {
                /**
                 * @description Clave única por liquidación (un UUID), para repetir el pedido sin emitir dos veces
                 * @example 5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f
                 */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LiquidacionCompraRequest"];
            };
        };
        responses: {
            /** @description Pedido repetido con la misma `Idempotency-Key`: la liquidación ya emitida, en su estado actual */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Liquidación creada; `estado_documento` indica si SUNAT ya la aceptó */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Correlativo repetido en la serie (DUPLICADO) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    calcular: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LiquidacionCompraRequest"];
            };
        };
        responses: {
            /** @description Importes calculados; no se guardó nada */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Datos inválidos o regla de dominio incumplida, igual que en la emisión */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    listar_2: {
        parameters: {
            query?: {
                /** @description Estado de la guía */
                estado?: "RECIBIDO" | "FIRMADO" | "ERROR_ENVIO" | "ENVIANDO" | "ENVIADO_PENDIENTE_TICKET" | "ACEPTADO" | "ACEPTADO_CON_OBS" | "RECHAZADO";
                /**
                 * @description `09` remitente o `31` transportista
                 * @example 09
                 */
                tipo?: string;
                /**
                 * @description Serie exacta (T001, V001…)
                 * @example T001
                 */
                serie?: string;
                /** @description Fecha de emisión mínima, `YYYY-MM-DD` */
                desde?: string;
                /** @description Fecha de emisión máxima, `YYYY-MM-DD` */
                hasta?: string;
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListGuiaRemisionResponse"];
                };
            };
        };
    };
    crear_4: {
        parameters: {
            query?: never;
            header?: {
                /**
                 * @description Clave única por guía (un UUID), para repetir el pedido sin emitir dos veces
                 * @example 5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f
                 */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["GuiaRemisionRequest"];
            };
        };
        responses: {
            /** @description Pedido repetido con la misma `Idempotency-Key`: la guía ya emitida, en su estado actual */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseGuiaRemisionResponse"];
                };
            };
            /** @description Guía emitida; `estado` dice si SUNAT ya la aceptó */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseGuiaRemisionResponse"];
                };
            };
            /** @description Datos inválidos o regla de SUNAT incumplida; `codigo` y `mensaje` dicen cuál */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseGuiaRemisionResponse"];
                };
            };
        };
    };
    ticket: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseGuiaRemisionResponse"];
                };
            };
        };
    };
    enviar: {
        parameters: {
            query?: {
                /** @description Retoma una guía que quedó `ENVIANDO` sin respuesta hace más de 10 min. No se sabe si SUNAT la recibió: úselo solo después de revisar en SOL que no llegó */
                forzar?: boolean;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseGuiaRemisionResponse"];
                };
            };
        };
    };
    validar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["GuiaRemisionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseGuiaRemisionResponse"];
                };
            };
        };
    };
    listar_3: {
        parameters: {
            query?: {
                /** @description Estado del comprobante (ver *Estados del comprobante*) */
                estado?: "RECIBIDO" | "INVALIDO" | "FIRMADO" | "ERROR_ENVIO" | "PENDIENTE_AGRUPACION" | "ENVIADO" | "ACEPTADO" | "ACEPTADO_CON_OBS" | "RECHAZADO" | "ANULADO" | "FUERA_DE_PLAZO" | "DESCARTADO";
                /**
                 * @description Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-01
                 */
                desde?: string;
                /**
                 * @description Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-30
                 */
                hasta?: string;
                /**
                 * @description Serie exacta del comprobante (4 caracteres: F001, FC01, FD01…)
                 * @example F001
                 */
                serie?: string;
                /**
                 * @description Tipo de comprobante: `01` factura, `03` boleta, `04` liquidación de compra, `07` nota de crédito, `08` nota de débito, `20` retención, `40` percepción
                 * @example 01
                 */
                tipo?: string;
                /**
                 * @description Cliente: el documento (empieza por lo escrito, si son solo dígitos) o parte de la razón social, sin distinguir mayúsculas; hasta 100 caracteres
                 * @example andina
                 */
                cliente?: string;
                /**
                 * @description Número del comprobante (sin la serie)
                 * @example 125
                 */
                numero?: number;
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListComprobanteResponse"];
                };
            };
        };
    };
    crear_5: {
        parameters: {
            query?: never;
            header?: {
                /**
                 * @description Clave única por factura (un UUID), para repetir el pedido sin emitir dos veces
                 * @example 5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f
                 */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FacturaRequest"];
            };
        };
        responses: {
            /** @description Pedido repetido con la misma `Idempotency-Key`: el comprobante ya emitido, en su estado actual */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Factura creada; `estado_documento` indica si SUNAT ya la aceptó */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Correlativo repetido en la serie (DUPLICADO) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    enviar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    correo: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CorreoRequest"];
            };
        };
        responses: {
            /** @description Accepted */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
        };
    };
    recuperarCdr: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    solicitar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BajaRequest"];
            };
        };
        responses: {
            /** @description Comunicación creada; `estado` indica si SUNAT ya la aceptó (`ACEPTADA`) o sigue en proceso (`ENVIADA`) */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBajaResponse"];
                };
            };
            /** @description El comprobante no puede darse de baja; `mensaje` lleva la regla SUNAT */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBajaResponse"];
                };
            };
        };
    };
    calcular_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FacturaRequest"];
            };
        };
        responses: {
            /** @description Importes calculados; no se guardó nada */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
            /** @description Datos inválidos o regla de dominio incumplida, igual que en la emisión */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    listar_4: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListEmpresaVistaResponse"];
                };
            };
        };
    };
    crear_6: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CrearEmpresaRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEmpresaVistaResponse"];
                };
            };
        };
    };
    establecimientos: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListEstablecimientoResponse"];
                };
            };
        };
    };
    crearEstablecimiento: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EstablecimientoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEstablecimientoResponse"];
                };
            };
        };
    };
    certificado: {
        parameters: {
            query: {
                clave: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    /** Format: binary */
                    archivo: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    apiKeys: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListApiKeyResumenResponse"];
                };
            };
        };
    };
    apiKey: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseApiKeyResponse"];
                };
            };
        };
    };
    reactivarUsuario: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMiembroDelEquipo"];
                };
            };
        };
    };
    desactivarUsuario: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMiembroDelEquipo"];
                };
            };
        };
    };
    invitacionesPendientes: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListInvitacionAlEquipo"];
                };
            };
        };
    };
    invitarAlEquipo: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["InvitarAlEquipoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseInvitacionEnviada"];
                };
            };
        };
    };
    reenviarInvitacion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseInvitacionEnviada"];
                };
            };
        };
    };
    listar_5: {
        parameters: {
            query?: {
                /** @description Texto a buscar en el documento o el nombre */
                q?: string;
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListCliente"];
                };
            };
        };
    };
    crear_7: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ClienteCrear"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCliente"];
                };
            };
        };
    };
    verificar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["VerificarRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    reenviarVerificacion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    aceptarTerminos: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAceptacionDeTerminosResponse"];
                };
            };
        };
    };
    restablecer: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RestablecerRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    registrar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RegistroRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseTokensResponse"];
                };
            };
        };
    };
    refrescar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RefreshRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseTokensResponse"];
                };
            };
        };
    };
    recuperar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RecuperarRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RefreshRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseTokensResponse"];
                };
            };
        };
    };
    consultarInvitacion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["InvitacionTokenRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseInvitacionRecibida"];
                };
            };
        };
    };
    aceptarInvitacion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AceptarInvitacionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    crear_8: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CrearTenantRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCrearTenantResponse"];
                };
            };
        };
    };
    listar_6: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListPlanResponse"];
                };
            };
        };
    };
    crear_9: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PlanRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlanResponse"];
                };
            };
        };
    };
    desactivar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del plan */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlanResponse"];
                };
            };
        };
    };
    activar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del plan */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlanResponse"];
                };
            };
        };
    };
    verificar_1: {
        parameters: {
            query: {
                /** @example 2026-09-01 */
                desde: string;
                /** @example 2026-09-30 */
                hasta: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseInforme"];
                };
            };
        };
    };
    probarConexion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la empresa */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseResultadoDeConexionResponse"];
                };
            };
        };
    };
    cambiarEntorno: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la empresa */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["CambiarEntornoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCambioDeEntornoResponse"];
                };
            };
        };
    };
    avisar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la empresa */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["AvisarRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEnviado"];
                };
            };
        };
    };
    revocarApiKey: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la empresa */
                id: string;
                /** @description Id de la API key, dentro de esa empresa */
                apiKeyId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseApiKeyRevocadaResponse"];
                };
            };
        };
    };
    designarAgente: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la empresa */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DesignacionDeAgente"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCambioDeDesignacionDeAgente"];
                };
            };
        };
    };
    listar_9: {
        parameters: {
            query?: {
                /**
                 * @description Búsqueda libre: correo, nombre, razón social o RUC
                 * @example ana@
                 */
                q?: string;
                /** @description Qué hacer con las cuentas dadas de baja: `OCULTAS` (por defecto), `INCLUIDAS` o `SOLO` */
                bajas?: "OCULTAS" | "INCLUIDAS" | "SOLO";
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListCuentaAdminResponse"];
                };
            };
        };
    };
    crear_10: {
        parameters: {
            query?: never;
            header?: {
                /**
                 * @description Clave única por alta (un UUID), para repetir el pedido sin perder la API key
                 * @example a1b2c3d4-0000-4000-8000-000000000001
                 */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AltaAsistidaRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAltaAsistidaResponse"];
                };
            };
        };
    };
    suspender: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SuspenderCuentaRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEstadoCuentaResponse"];
                };
            };
        };
    };
    reponer: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBajaCuentaResponse"];
                };
            };
        };
    };
    reactivar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEstadoCuentaResponse"];
                };
            };
        };
    };
    ver_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlanDeCuentaResponse"];
                };
            };
        };
    };
    cambiar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CambiarPlanRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePlanDeCuentaResponse"];
                };
            };
        };
    };
    historial: {
        parameters: {
            query?: {
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListPagoResponse"];
                };
            };
        };
    };
    registrar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RegistrarPagoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePagoResponse"];
                };
            };
        };
    };
    darDeBaja: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DarDeBajaCuentaRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBajaCuentaResponse"];
                };
            };
        };
    };
    reenviarVerificacion_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                cuentaId: string;
                /** @description Id del usuario, dentro de esa cuenta */
                usuarioId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseDestinatarioResponse"];
                };
            };
        };
    };
    restablecer_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                cuentaId: string;
                /** @description Id del usuario, dentro de esa cuenta */
                usuarioId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseDestinatarioResponse"];
                };
            };
        };
    };
    impersonar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                cuentaId: string;
                /** @description Id del usuario, dentro de esa cuenta */
                usuarioId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseImpersonacionResponse"];
                };
            };
        };
    };
    vistaPrevia: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description El correo
                 * @example RECUPERACION_CLAVE
                 */
                tipo: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PlantillaPorGuardar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseTextoDeCorreo"];
                };
            };
        };
    };
    reintentar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del comprobante */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseReintentoResponse"];
                };
            };
        };
    };
    descartar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del comprobante */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DescartarComprobanteRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseDescarteResponse"];
                };
            };
        };
    };
    verificar_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdminCodigoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAdminSesionResponse"];
                };
            };
        };
    };
    confirmar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdminCodigoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAdminSesionNuevaResponse"];
                };
            };
        };
    };
    configurar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdminDesafioRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAdminConfiguracionResponse"];
                };
            };
        };
    };
    login_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdminLoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAdminDesafioResponse"];
                };
            };
        };
    };
    crear_11: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CrearAdministradorRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAdministradorResponse"];
                };
            };
        };
    };
    eliminar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    actualizar_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Actualizar"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseWebhook"];
                };
            };
        };
    };
    entregas: {
        parameters: {
            query?: {
                /** @description PENDIENTE, EXITOSA o FALLIDA */
                estado?: "PENDIENTE" | "EXITOSA" | "FALLIDA";
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListEntrega"];
                };
            };
        };
    };
    eventos: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListString"];
                };
            };
        };
    };
    exportarRvie: {
        parameters: {
            query: {
                /**
                 * @description Periodo tributario, `AAAAMM`
                 * @example 202610
                 */
                periodo: string;
                /** @description Escribir aun si hay comprobantes en otra moneda sin tipo de cambio (campo 28 vacío) */
                permitir_sin_tipo_cambio?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description El ZIP del registro de ventas */
            200: {
                headers: {
                    /** @description `solo-khipu`: el archivo trae solo lo emitido en khipu */
                    "X-Rvie-Alcance"?: string;
                    /** @description Comprobantes del periodo que todavía no resolvió SUNAT y no entran */
                    "X-Rvie-Pendientes-Sunat"?: number;
                    /** @description Filas del archivo, contadas al prepararlo (aproximado si un comprobante cambia de estado durante la descarga) */
                    "X-Rvie-Registros"?: number;
                    /** @description Filas fuera de soles con el tipo de cambio vacío (solo con permitir_sin_tipo_cambio) */
                    "X-Rvie-Sin-Tipo-Cambio"?: number;
                    [name: string]: unknown;
                };
                content: {
                    "application/zip": string;
                };
            };
            /** @description No hay comprobantes en el periodo (NO_ENCONTRADO) */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StreamingResponseBody"];
                };
            };
            /** @description RVIE_EXCEDE_EL_TOPE o RVIE_SIN_TIPO_CAMBIO */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    publicados: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListPlanPublicado"];
                };
            };
        };
    };
    obtener_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseGuiaRemisionResponse"];
                };
            };
        };
    };
    xml: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    pdf: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    cdr: {
        parameters: {
            query?: {
                /** @description `zip` (por defecto) o `xml` */
                formato?: string;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    obtener_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseComprobanteResponse"];
                };
            };
        };
    };
    xml_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    pdf_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    cdr_1: {
        parameters: {
            query?: {
                /** @description `zip` (por defecto) o `xml` */
                formato?: string;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    deComprobante: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListBajaResponse"];
                };
            };
        };
    };
    resumen: {
        parameters: {
            query?: {
                /**
                 * @description Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-01
                 */
                desde?: string;
                /**
                 * @description Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-30
                 */
                hasta?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseResumenDeComprobantesResponse"];
                };
            };
        };
    };
    porDia: {
        parameters: {
            query: {
                /**
                 * @description Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-10-01
                 */
                desde: string;
                /**
                 * @description Fecha de emisión máxima, `YYYY-MM-DD` (inclusive); a lo más 61 días después de `desde`
                 * @example 2026-10-31
                 */
                hasta: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEmisionPorDiaResponse"];
                };
            };
        };
    };
    exportarComprobantes: {
        parameters: {
            query?: {
                /** @description Estado del comprobante */
                estado?: "RECIBIDO" | "INVALIDO" | "FIRMADO" | "ERROR_ENVIO" | "PENDIENTE_AGRUPACION" | "ENVIADO" | "ACEPTADO" | "ACEPTADO_CON_OBS" | "RECHAZADO" | "ANULADO" | "FUERA_DE_PLAZO" | "DESCARTADO";
                /**
                 * @description Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-01
                 */
                desde?: string;
                /**
                 * @description Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-30
                 */
                hasta?: string;
                /**
                 * @description Serie exacta
                 * @example F001
                 */
                serie?: string;
                /**
                 * @description Tipo: `01`, `03`, `07` u `08` (las ventas, lo que entra sin filtro); `04`, `20` o `40` solo si se piden
                 * @example 01
                 */
                tipo?: string;
                /**
                 * @description Cliente: documento (prefijo) o parte de la razón social
                 * @example andina
                 */
                cliente?: string;
                /**
                 * @description Número del comprobante (sin la serie)
                 * @example 125
                 */
                numero?: number;
                /**
                 * @description `csv` (por defecto) o `xlsx`
                 * @example xlsx
                 */
                formato?: string;
                /**
                 * @description Solo en CSV: `coma` (por defecto) o `punto_y_coma`
                 * @example coma
                 */
                separador?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description El archivo, como adjunto (`Content-Disposition`) */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/csv": string;
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": string;
                };
            };
            /** @description `formato`, `separador` o un filtro con formato inválido (`PARAMETRO_INVALIDO`, `RANGO_INVALIDO`) */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StreamingResponseBody"];
                };
            };
            /** @description Más de 5000 comprobantes con esos filtros (`EXPORTACION_EXCEDE_EL_TOPE`) */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    descargar: {
        parameters: {
            query?: {
                /** @description Estado del comprobante */
                estado?: "RECIBIDO" | "INVALIDO" | "FIRMADO" | "ERROR_ENVIO" | "PENDIENTE_AGRUPACION" | "ENVIADO" | "ACEPTADO" | "ACEPTADO_CON_OBS" | "RECHAZADO" | "ANULADO" | "FUERA_DE_PLAZO" | "DESCARTADO";
                /**
                 * @description Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-01
                 */
                desde?: string;
                /**
                 * @description Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)
                 * @example 2026-09-30
                 */
                hasta?: string;
                /**
                 * @description Serie exacta
                 * @example F001
                 */
                serie?: string;
                /**
                 * @description Tipo: `01`, `03`, `04`, `07`, `08`, `20` o `40`
                 * @example 01
                 */
                tipo?: string;
                /**
                 * @description Cliente: documento (prefijo) o parte de la razón social
                 * @example andina
                 */
                cliente?: string;
                /**
                 * @description Número del comprobante (sin la serie)
                 * @example 125
                 */
                numero?: number;
                /**
                 * @description Qué archivos incluir: `xml`, `pdf`, `cdr` separados por coma
                 * @example xml,pdf
                 */
                tipos?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    ver: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEmpresaResponse"];
                };
            };
        };
    };
    vistaPrevia_1: {
        parameters: {
            query?: {
                /** @description clasico | moderno | sutil | corporativo | gris */
                plantilla?: string;
                /** @description Hexadecimal, p. ej. %231F5F4A */
                color_primario?: string;
                pie_de_pagina?: string;
                observaciones_por_defecto?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    ver_3: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMiCuentaResponse"];
                };
            };
        };
    };
    usuariosDeLaCuenta: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListMiembroDelEquipo"];
                };
            };
        };
    };
    accesosDeSoporte: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListAccesoDeSoporteResponse"];
                };
            };
        };
    };
    validez: {
        parameters: {
            query: {
                /**
                 * @description RUC del emisor del comprobante
                 * @example 20100066603
                 */
                ruc: string;
                /**
                 * @description 01 factura, 03 boleta, 07 nota de crédito, 08 nota de débito
                 * @example 01
                 */
                tipo: string;
                /** @example F001 */
                serie: string;
                /** @example 125 */
                numero: number;
                /**
                 * @description Tipo de documento del receptor (catálogo 06); opcional
                 * @example 6
                 */
                tipo_doc_receptor?: string;
                /**
                 * @description Número de documento del receptor; opcional
                 * @example 20601234565
                 */
                num_doc_receptor?: string;
                /**
                 * @description Fecha de emisión (`YYYY-MM-DD`); obligatoria por la API REST, opcional por SOAP
                 * @example 2026-09-14
                 */
                fecha?: string;
                /**
                 * @description Importe total; por la API REST, obligatorio para comprobantes electrónicos
                 * @example 1180
                 */
                monto?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseValidezResponse"];
                };
            };
        };
    };
    consultarRuc: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @example 20100066603 */
                ruc: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePadronRucResponse"];
                };
            };
        };
    };
    listarCatalogos: {
        parameters: {
            query?: {
                /** @description `true` para incluir las entradas de cada catálogo */
                completo?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListCatalogoIndice"];
                };
            };
        };
    };
    obtenerCatalogo: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description Número del catálogo SUNAT, p. ej. `07`
                 * @example 07
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCatalogoResponse"];
                };
            };
        };
    };
    vigente: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBannerPublico"];
                };
            };
        };
    };
    obtener_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBajaResponse"];
                };
            };
        };
    };
    me: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUsuarioResponse"];
                };
            };
        };
    };
    origen: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseOrigenResponse"];
                };
            };
        };
    };
    monitorear: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMonitorDeEmisionResponse"];
                };
            };
        };
    };
    listar_7: {
        parameters: {
            query?: {
                /** @description Solo esta clase de error */
                clase?: "ERROR_DE_ENVIO" | "ERROR_DE_FORMATO" | "FUERA_DE_PLAZO" | "BOLETA_SIN_ENVIAR";
                /** @description Solo esta empresa */
                empresa_id?: string;
                /**
                 * @description Cliente: RUC, razón social, o nombre o correo de la cuenta
                 * @example andina
                 */
                q?: string;
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListErrorDeEmisionResponse"];
                };
            };
        };
    };
    listar_8: {
        parameters: {
            query?: {
                /** @description Solo las empresas de ese entorno */
                entorno?: "BETA" | "PRODUCCION";
                /** @description Solo las empresas con el certificado en ese estado */
                certificado?: "SIN_CERTIFICADO" | "SIN_FECHA" | "VIGENTE" | "POR_VENCER" | "VENCIDO";
                /** @description Qué hacer con las empresas de cuentas dadas de baja: `OCULTAS` (por defecto), `INCLUIDAS` o `SOLO` */
                bajas?: "OCULTAS" | "INCLUIDAS" | "SOLO";
                /** @description Buscar por RUC (desde el principio) o por razón social (cualquier parte), sin distinguir mayúsculas ni tildes */
                q?: string;
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListEmpresaAdminResponse"];
                };
            };
        };
    };
    abrir: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la empresa */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseEmpresaDetalleResponse"];
                };
            };
        };
    };
    deEmpresa: {
        parameters: {
            query?: {
                /**
                 * @description Mes, `AAAA-MM`; por defecto el mes en curso
                 * @example 2026-10
                 */
                mes?: string;
            };
            header?: never;
            path: {
                /** @description Id de la empresa */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseConsumoEmpresaResponse"];
                };
            };
        };
    };
    abrir_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseCuentaDetalleResponse"];
                };
            };
        };
    };
    previsualizar: {
        parameters: {
            query: {
                /** @description Id del plan al que se pasaría */
                plan_id: string;
            };
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePrevisualizacionDePlanResponse"];
                };
            };
        };
    };
    deCuenta: {
        parameters: {
            query?: {
                /**
                 * @description Mes, `AAAA-MM`; por defecto el mes en curso
                 * @example 2026-10
                 */
                mes?: string;
            };
            header?: never;
            path: {
                /** @description Id de la cuenta */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseConsumoCuentaResponse"];
                };
            };
        };
    };
    listar_10: {
        parameters: {
            query?: {
                /**
                 * @description Mes, `AAAA-MM`; por defecto el mes en curso
                 * @example 2026-10
                 */
                mes?: string;
                /** @description `TODAS` (por defecto), `CERCA_DEL_LIMITE` o `PLAN_VENCIDO` */
                filtro?: "TODAS" | "CERCA_DEL_LIMITE" | "PLAN_VENCIDO";
                /** @description `PORCENTAJE` (por defecto) o `DOCUMENTOS` */
                orden?: "PORCENTAJE" | "DOCUMENTOS";
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseConsumoDeCuentasResponse"];
                };
            };
        };
    };
    exportar: {
        parameters: {
            query?: {
                /**
                 * @description Mes, `AAAA-MM`; por defecto el mes en curso
                 * @example 2026-10
                 */
                mes?: string;
                /** @description `TODAS` (por defecto), `CERCA_DEL_LIMITE` o `PLAN_VENCIDO` */
                filtro?: "TODAS" | "CERCA_DEL_LIMITE" | "PLAN_VENCIDO";
                /** @description `PORCENTAJE` (por defecto) o `DOCUMENTOS` */
                orden?: "PORCENTAJE" | "DOCUMENTOS";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    plantillas: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListPlantilla"];
                };
            };
        };
    };
    ficha: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del comprobante */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseFichaDeComprobanteResponse"];
                };
            };
        };
    };
    credencialesSol_1: {
        parameters: {
            query?: {
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListSol"];
                };
            };
        };
    };
    certificados: {
        parameters: {
            query?: {
                /** @description Página, desde 1 */
                pagina?: number;
                /** @description Resultados por página, 1–100 */
                por_pagina?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListCertificado"];
                };
            };
        };
    };
    me_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAdministradorResponse"];
                };
            };
        };
    };
    revocarApiKey_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    revocarInvitacion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
