import Link from "next/link";
import { BloqueCodigo } from "@/components/developers/guia/bloque-codigo";
import { CASOS, ERROR_EJEMPLO, RESPUESTA_EJEMPLO } from "@/components/developers/guia/casos-emision";
import { Codigo, NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { Rico } from "@/components/developers/guia/rico";
import { EJEMPLO_ENTREGA, RESPUESTA_CREAR_WEBHOOK, VERIFICAR_NODE, VERIFICAR_PHP, VERIFICAR_PYTHON } from "@/components/developers/guia/webhooks";
import { apiPublicUrl } from "@/lib/api/client";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

export const metadata = { title: `Guía de emisión · ${messages.app.nombre}` };

const INDICE = [
  { id: "inicio-rapido", label: "Inicio rápido" },
  { id: "anatomia", label: "Anatomía de un comprobante" },
  { id: "casos", label: "Casos de emisión" },
  ...CASOS.map((c) => ({ id: c.id, label: `· ${c.titulo}` })),
  { id: "respuesta", label: "Cómo leer la respuesta" },
  { id: "errores", label: "Errores" },
  { id: "webhooks", label: "Webhooks" },
  { id: "buenas-practicas", label: "Buenas prácticas" },
];

export default function GuiaPage() {
  const base = apiPublicUrl();
  return (
    <PaginaGuia
      titulo="Guía de emisión de facturas y boletas"
      resumen="Todo lo necesario para emitir facturas y boletas electrónicas válidas ante SUNAT con una sola llamada: qué enviar en cada caso, qué calcula khipu por usted, qué devuelve y cómo actuar ante cada estado o error."
      indice={INDICE}
    >
      <Seccion id="inicio-rapido" titulo="Inicio rápido">
        <ol className="list-decimal space-y-2 pl-5 text-[13px] leading-relaxed text-muted-foreground">
          <li>
            <strong className="text-foreground">Cree su cuenta y su empresa</strong> en el portal (RUC y razón social). Empiece en entorno{" "}
            <Codigo>BETA</Codigo>: es la homologación de SUNAT, los comprobantes no tienen validez tributaria y puede probar sin riesgo.
          </li>
          <li>
            <strong className="text-foreground">Cargue el certificado digital</strong> (<Codigo>.p12</Codigo>/<Codigo>.pfx</Codigo>) y las{" "}
            <strong className="text-foreground">credenciales SOL</strong> (usuario secundario) en <em>Empresa</em>. En BETA sirven las de prueba de SUNAT
            (usuario <Codigo>MODDATOS</Codigo>).
          </li>
          <li>
            <strong className="text-foreground">Complete los datos fiscales</strong> en <em>Empresa</em> (o con <Codigo>PUT /v1/empresa/datos-fiscales</Codigo>):
            domicilio fiscal con ubigeo del catálogo 13, que va en cada XML como dirección del emisor, y la cuenta de detracciones si emitirá facturas
            sujetas a SPOT.
          </li>
          <li>
            <strong className="text-foreground">Cree una serie</strong> en <em>Series</em>: de factura (tipo 01, <Codigo>F001</Codigo>) o de boleta (tipo 03,{" "}
            <Codigo>B001</Codigo>).
          </li>
          <li>
            <strong className="text-foreground">Genere una API key</strong> en <em>API keys</em>. Se muestra una sola vez: guárdela como secreto.
          </li>
          <li>
            <strong className="text-foreground">Emita su primera factura</strong> con el caso base de abajo contra <Codigo>{base}</Codigo>. La respuesta trae el estado
            SUNAT y los enlaces al XML y al CDR.
          </li>
          <li>
            <strong className="text-foreground">Pase a producción</strong>: cuando SUNAT haya aprobado su homologación, cambie el entorno de la empresa a{" "}
            <Codigo>PRODUCCION</Codigo>. La URL y la API key no cambian.
          </li>
        </ol>
        <BloqueCodigo
          titulo="cURL — primera factura"
          codigo={`curl -X POST ${base}/v1/facturas \\
  -H "X-Api-Key: fk_TU_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "serie": "F001",
    "fecha_emision": "2026-09-17",
    "moneda": "PEN",
    "cliente": { "tipo_doc": "6", "num_doc": "20601234565", "razon_social": "COMERCIAL ANDINA S.A.C." },
    "items": [
      { "descripcion": "Servicio de consultoría", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10" }
    ]
  }'`}
        />
      </Seccion>

      <Seccion id="anatomia" titulo="Anatomía de un comprobante">
        <P>
          Una factura o una boleta electrónica es un XML UBL 2.1 firmado, y las dos se emiten con <Codigo>POST /v1/facturas</Codigo>: la letra de
          la serie decide cuál. Usted envía los datos comerciales; khipu resuelve el resto: numeración correlativa,
          cálculo de IGV y totales, monto en letras, catálogos, firma XML-DSig, validación contra los esquemas oficiales, envío SOAP a SUNAT,
          lectura del CDR y reintentos.
        </P>
        <Tabla
          cabeceras={["Campo", "Qué es", "Valores / catálogo"]}
          filas={[
            ["serie", "Serie de numeración registrada en la empresa.", <><Codigo>F</Codigo> + 3 alfanuméricos emite una factura (<Codigo>F001</Codigo>); <Codigo>B</Codigo> + 3, una boleta (<Codigo>B001</Codigo>).</>],
            ["correlativo", "Número dentro de la serie. Opcional.", "Omítalo para que khipu asigne el siguiente; envíelo si su sistema lleva la numeración."],
            ["fecha_emision", "Fecha del comprobante.", <><Codigo>YYYY-MM-DD</Codigo>, no futura. SUNAT debe recibir la factura en 3 días calendario; la boleta, sola hasta el 5.º día y en el resumen diario hasta el 7.º.</>],
            ["moneda", "Moneda de todo el comprobante.", <><Codigo>PEN</Codigo>, <Codigo>USD</Codigo>, <Codigo>EUR</Codigo> — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-02">catálogo 02</Link>.</>],
            ["tipo_operacion", "Naturaleza de la operación.", <><Codigo>0101</Codigo> venta interna (por defecto), <Codigo>1001</Codigo> sujeta a detracción, <Codigo>0200</Codigo>–<Codigo>0208</Codigo> exportación (ítems <Codigo>40</Codigo>, cliente del exterior con <Codigo>pais</Codigo>; sin <Codigo>0202</Codigo> ni <Codigo>0205</Codigo>), <Codigo>0401</Codigo> no domiciliados y <Codigo>2001</Codigo> percepción; un tipo que khipu aún no emite responde <Codigo>422</Codigo> antes de numerar — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-51">catálogo 51</Link>.</>],
            ["cliente.tipo_doc", "Tipo de documento del adquirente.", <>En factura de venta interna, <Codigo>6</Codigo> (RUC); en exportación, el documento del cliente del exterior; en boleta, cualquiera del catálogo, o <Codigo>-</Codigo> sin documento (solo en soles y hasta S/ 700) —<Link className="text-primary hover:underline" href="/developers/catalogos#cat-06">catálogo 06</Link>.</>],
            ["cliente.num_doc", "Número del documento.", <>RUC de 11 dígitos; en boleta, DNI de 8 dígitos, los demás hasta 15 caracteres, o <Codigo>-</Codigo>.</>],
            ["items[].unidad", "Unidad de medida.", <><Codigo>NIU</Codigo> unidad (bienes), <Codigo>ZZ</Codigo> unidad (servicios), <Codigo>KGM</Codigo>, <Codigo>HUR</Codigo>… — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-03">catálogo 03</Link>.</>],
            ["items[].precio_unitario", "Precio de venta unitario.", "Con IGV incluido para gravados; khipu obtiene el valor unitario y el IGV."],
            ["items[].tipo_afectacion_igv", "Cómo tributa el ítem.", <><Codigo>10</Codigo> gravado, <Codigo>20</Codigo> exonerado, <Codigo>30</Codigo> inafecto; gratuitas 11–17/21/31–37 — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-07">catálogo 07</Link>.</>],
            ["forma_pago", "Contado o crédito con cuotas.", <>Por defecto contado. Al crédito: <Codigo>monto_pendiente</Codigo> + <Codigo>cuotas[]</Codigo>.</>],
            ["enviar_automatico", "Enviar a SUNAT en la misma llamada.", <><Codigo>true</Codigo> por defecto; <Codigo>false</Codigo> deja el comprobante FIRMADO.</>],
          ]}
        />
        <NotaProsa>
          Los códigos vienen de los catálogos oficiales de SUNAT. Puede consultarlos en la sección{" "}
          <Link href="/developers/catalogos" className="font-medium underline">
            Catálogos SUNAT
          </Link>{" "}
          o por API (<Codigo>GET /v1/catalogos/07</Codigo>, sin credenciales). Un <Codigo>tipo_operacion</Codigo>, <Codigo>tipo_afectacion_igv</Codigo>,{" "}
          <Codigo>tipo_doc</Codigo>, <Codigo>moneda</Codigo> o <Codigo>unidad</Codigo> fuera de catálogo responde <Codigo>422</Codigo> antes de consumir
          numeración. El catálogo 03 trae la lista UN/ECE completa, con las unidades más usadas primero.
        </NotaProsa>
      </Seccion>

      <Seccion id="casos" titulo="Casos de emisión">
        <P>
          Cada caso muestra el JSON exacto para <Codigo>POST /v1/facturas</Codigo> (o el endpoint que indica) y las reglas que aplican. Los de factura
          valen también para la boleta salvo lo que dicen sus casos propios: comprador, operaciones admitidas, plazos y anulación.
          {CASOS.some((c) => !c.disponible) ? (
            <>
              {" "}
              Los casos marcados{" "}
              <span className="rounded border border-warning-border bg-warning px-1.5 py-0.5 text-[11px] font-medium text-warning-foreground">en desarrollo</span>{" "}
              describen el contrato previsto para que planifique su integración; la API los rechazará hasta que estén disponibles.
            </>
          ) : null}
        </P>
        <div className="space-y-8">
          {CASOS.map((c) => (
            <article key={c.id} id={c.id} className={cn("scroll-mt-16 space-y-3 rounded-xl border border-border bg-card p-5 shadow-2xs", !c.disponible && "opacity-90")}>
              <header className="flex flex-wrap items-center gap-2">
                <h3 className="text-[15px] font-semibold text-foreground">{c.titulo}</h3>
                {c.disponible ? null : (
                  <span className="rounded border border-warning-border bg-warning px-1.5 py-0.5 text-[11px] font-medium text-warning-foreground">en desarrollo</span>
                )}
              </header>
              <P>
                <strong className="font-medium text-foreground">Cuándo:</strong> {c.cuando}
              </P>
              <BloqueCodigo titulo={`${c.disponible ? "request" : "request (contrato previsto)"} · POST ${c.endpoint ?? "/v1/facturas"}`} codigo={c.request} />
              <ul className="list-disc space-y-1 pl-5 text-[13px] leading-relaxed text-muted-foreground">
                {c.notas.map((n, i) => (
                  <li key={i}>
                    <Rico texto={n} />
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      </Seccion>

      <Seccion id="respuesta" titulo="Cómo leer la respuesta">
        <P>
          Toda respuesta viene en el sobre <Codigo>{`{ estado, datos, codigo, mensaje, errores }`}</Codigo>. En una emisión correcta{" "}
          <Codigo>datos</Codigo> es el comprobante completo:
        </P>
        <BloqueCodigo titulo="201 Created" codigo={RESPUESTA_EJEMPLO} />
        <Tabla
          cabeceras={["Campo", "Qué mirar"]}
          filas={[
            ["estado_documento", <>Dónde está el comprobante. <Codigo>ACEPTADO</Codigo> es el final feliz; vea <Link className="text-primary hover:underline" href="/developers/errores#estados">todos los estados</Link>.</>],
            ["cdr.codigo", <><Codigo>0</Codigo> aceptado · 2000–3999 rechazado (corrija y reemita) · 4000+ aceptado con observaciones · 1000–1999 error del emisor sin constancia.</>],
            ["cdr.descripcion / observaciones", "Texto oficial de SUNAT. Las observaciones no invalidan la factura, pero conviene corregir la causa en la siguiente."],
            ["ultimo_error", <>Solo en <Codigo>ERROR_ENVIO</Codigo>: código y mensaje del fallo de comunicación con SUNAT; khipu reintenta solo.</>],
            ["totales", "Lo que khipu calculó: úselo para conciliar con su sistema (tolerancia SUNAT ±1)."],
            ["hash", "Resumen de la firma: se imprime en la representación impresa y en el QR."],
            ["enlaces.xml / enlaces.pdf / enlaces.cdr", <>Descargas: XML firmado, representación impresa (PDF con QR y hash, disponible desde <Codigo>FIRMADO</Codigo>) y CDR. <Codigo>cdr</Codigo> solo aparece cuando SUNAT emitió la constancia.</>],
          ]}
        />
      </Seccion>

      <Seccion id="errores" titulo="Errores">
        <P>
          Los errores llevan un <Codigo>codigo</Codigo> estable para programar y un <Codigo>mensaje</Codigo> para personas; cuando la regla es de SUNAT el mensaje
          empieza por el código oficial, el mismo que devolvería SUNAT si el comprobante hubiera llegado hasta allí:
        </P>
        <BloqueCodigo titulo="422 Unprocessable Entity" codigo={ERROR_EJEMPLO} />
        <P>
          La lista completa de códigos de khipu, los rangos de SUNAT y qué hacer en cada caso está en{" "}
          <Link href="/developers/errores" className="text-primary hover:underline">
            Errores y estados
          </Link>
          .
        </P>
      </Seccion>

      <Seccion id="webhooks" titulo="Webhooks">
        <P>
          En vez de consultar <Codigo>GET /v1/facturas/{"{id}"}</Codigo> hasta que SUNAT conteste, registre una URL y khipu le avisa con un <Codigo>POST</Codigo> firmado cada
          vez que un comprobante cambia de estado. Se registran desde el portal (<em>API keys y webhooks</em>) o por API; hasta 5 por empresa.
        </P>
        <BloqueCodigo titulo="POST /v1/webhooks · 201 Created" codigo={RESPUESTA_CREAR_WEBHOOK} />
        <ul className="list-disc space-y-1.5 pl-5 text-[13px] leading-relaxed text-muted-foreground">
          <li>
            La URL debe ser <Codigo>https</Codigo> y apuntar a un servidor público: una dirección interna (localhost, redes privadas, <Codigo>169.254.x.x</Codigo>) responde{" "}
            <Codigo>422 WEBHOOK_INVALIDO</Codigo>. khipu no sigue redirecciones.
          </li>
          <li>
            <strong className="text-foreground">El secreto de firma</strong> (<Codigo>whsec_…</Codigo>) se muestra <strong className="text-foreground">una sola vez</strong>, al
            crear el endpoint o al rotarlo (<Codigo>POST /v1/webhooks/{"{id}"}/secreto</Codigo>, el anterior deja de valer al instante). Crear, cambiar, rotar y borrar exige la
            sesión del portal, no una API key: una llave filtrada no debe poder redirigir los eventos. Listar y ver el log funciona con API key.
          </li>
        </ul>

        <h3 className="mt-2 text-[14px] font-semibold text-foreground">Eventos</h3>
        <Tabla
          cabeceras={["Evento", "Cuándo"]}
          filas={[
            [<Codigo key="a">comprobante.aceptado</Codigo>, "SUNAT aceptó el comprobante (con CDR 0)."],
            [<Codigo key="b">comprobante.aceptado_con_obs</Codigo>, "SUNAT lo aceptó y dejó observaciones (CDR 4000+); la factura es válida."],
            [<Codigo key="c">comprobante.rechazado</Codigo>, "SUNAT lo rechazó (2000–3999): corrija y emita uno nuevo."],
            [<Codigo key="d">comprobante.error_envio</Codigo>, <>No se pudo enviar a SUNAT; khipu reintenta. Llega al empezar a fallar, no en cada reintento.</>],
            [<Codigo key="e">comprobante.fuera_de_plazo</Codigo>, "Pasó el plazo de envío: hay que emitir de nuevo."],
            [<Codigo key="f">comprobante.anulado</Codigo>, "Se dio de baja ante SUNAT."],
            [<Codigo key="g">comprobante.descartado</Codigo>, "Un administrador dejó de intentar el envío."],
          ]}
        />
        <NotaProsa>
          Los estados de paso (<Codigo>RECIBIDO</Codigo>, <Codigo>FIRMADO</Codigo>, <Codigo>ENVIADO</Codigo>) no se publican. Un endpoint que usted apaga no recibe los eventos que ocurran mientras está apagado; uno que khipu pausa por fallos sigue guardándolos
          (hasta 30 días) y los manda al reactivarlo. <Codigo>GET /v1/webhooks/eventos</Codigo> lista los nombres; <Codigo>{`["*"]`}</Codigo> suscribe a todos.
        </NotaProsa>

        <h3 className="mt-2 text-[14px] font-semibold text-foreground">Qué recibe su servidor</h3>
        <BloqueCodigo titulo="Una entrega (el cuerpo va en una línea, tal como se firma)" codigo={EJEMPLO_ENTREGA} lenguaje="http" />
        <Tabla
          cabeceras={["Cabecera", "Para qué"]}
          filas={[
            [<Codigo key="1">X-Khipu-Event</Codigo>, "El evento, igual que el campo evento del cuerpo."],
            [<Codigo key="2">X-Khipu-Delivery</Codigo>, <>El id de la entrega. <strong>No cambia entre reintentos</strong>: úselo para no procesar dos veces el mismo evento.</>],
            [<Codigo key="3">X-Khipu-Signature</Codigo>, <><Codigo>t=&lt;segundos Unix&gt;,v1=&lt;hex&gt;</Codigo>: HMAC-SHA256 del secreto sobre <Codigo>t.cuerpo</Codigo>.</>],
          ]}
        />

        <h3 className="mt-2 text-[14px] font-semibold text-foreground">Verifique la firma</h3>
        <P>
          Calcule <Codigo>HMAC-SHA256(secreto, t + &quot;.&quot; + cuerpo)</Codigo> con el <strong>cuerpo crudo</strong> —los bytes tal como llegaron, sin volver a escribir el
          JSON— y compárelo en tiempo constante con <Codigo>v1</Codigo>. Descarte la entrega si <Codigo>t</Codigo> es de hace más de unos minutos: sin ese límite, quien capture
          una entrega podría repetirla. Responda <Codigo>2xx</Codigo> en menos de 10 segundos y procese el evento después.
        </P>
        <BloqueCodigo titulo="Node.js" codigo={VERIFICAR_NODE} lenguaje="javascript" />
        <BloqueCodigo titulo="Python" codigo={VERIFICAR_PYTHON} lenguaje="python" />
        <BloqueCodigo titulo="PHP" codigo={VERIFICAR_PHP} lenguaje="php" />

        <h3 className="mt-2 text-[14px] font-semibold text-foreground">Reintentos, pausa y log</h3>
        <ul className="list-disc space-y-1.5 pl-5 text-[13px] leading-relaxed text-muted-foreground">
          <li>
            Es <strong className="text-foreground">al menos una vez</strong>: un reintento (o un reenvío manual) llega con el mismo cuerpo y el mismo{" "}
            <Codigo>X-Khipu-Delivery</Codigo>. El orden entre eventos distintos no está garantizado; si importa, compare con el <Codigo>estado</Codigo> actual con{" "}
            <Codigo>GET /v1/facturas/{"{id}"}</Codigo>.
          </li>
          <li>
            Cualquier respuesta que no sea <Codigo>2xx</Codigo> (o un tiempo agotado, o un fallo de red) cuenta como fallo y se reintenta con espera creciente: 1 min, 5 min,
            15 min, 1 h, 3 h, 6 h y 12 h; tras el octavo intento la entrega queda <Codigo>FALLIDA</Codigo> y se puede reenviar a mano.
          </li>
          <li>
            Con <strong className="text-foreground">10 intentos fallidos seguidos</strong> khipu apaga el endpoint (<Codigo>pausado_motivo</Codigo> dice por qué) y avisa por correo
            a la cuenta. Active el endpoint de nuevo con <Codigo>PATCH /v1/webhooks/{"{id}"}</Codigo> <Codigo>{"{ \"activo\": true }"}</Codigo> o desde el portal; las entregas
            pendientes salen enseguida.
          </li>
          <li>
            <Codigo>GET /v1/webhooks/{"{id}"}/entregas</Codigo> es el log (30 días): estado, intentos, último código HTTP o error. <Codigo>POST /v1/webhooks/{"{id}"}/prueba</Codigo>{" "}
            envía un evento <Codigo>webhook.prueba</Codigo> firmado, y <Codigo>POST /v1/webhooks/entregas/{"{id}"}/reenviar</Codigo> repite una entrega cerrada; ambos, como todo lo que
            escribe, piden la sesión del portal.
          </li>
        </ul>
      </Seccion>

      <Seccion id="buenas-practicas" titulo="Buenas prácticas">
        <ul className="list-disc space-y-2 pl-5 text-[13px] leading-relaxed text-muted-foreground">
          <li>
            <strong className="text-foreground">Idempotencia:</strong> si su sistema puede reintentar una llamada (timeouts), envíe una{" "}
            <Codigo>Idempotency-Key</Codigo> (un UUID por comprobante) en <Codigo>POST /v1/facturas</Codigo> y en <Codigo>POST /v1/notas</Codigo>: repetir el mismo
            pedido con la misma clave devuelve <Codigo>200</Codigo> con el mismo comprobante, sin emitir otro. Si numera usted mismo, <Codigo>correlativo</Codigo> también
            lo evita: un duplicado responde <Codigo>409</Codigo>. Vea{" "}
            <a className="text-primary hover:underline" href="#idempotency-key">
              Reintento seguro con Idempotency-Key
            </a>
            .
          </li>
          <li>
            <strong className="text-foreground">Guarde el <Codigo>id</Codigo></strong> que devuelve khipu junto a su documento interno; con él consulta el estado y
            descarga XML, PDF y CDR.
          </li>
          <li>
            <strong className="text-foreground">Entregue el comprobante al cliente</strong> con <Codigo>POST /v1/facturas/{"{id}"}/correo</Codigo>{" "}
            <Codigo>{"{ email, mensaje? }"}</Codigo>: khipu adjunta PDF, XML y CDR. Solo comprobantes aceptados (<Codigo>409 NO_ACEPTADO</Codigo>); el
            QR del PDF codifica <Codigo>RUC|tipo|serie|número|IGV|total|fecha|tipo y nº de documento del cliente|hash</Codigo>.
          </li>
          <li>
            <strong className="text-foreground">No consulte en bucle:</strong> registre un{" "}
            <a className="text-primary hover:underline" href="#webhooks">
              webhook
            </a>{" "}
            y khipu le avisa cuando SUNAT contesta.
          </li>
          <li>
            <strong className="text-foreground">No trate <Codigo>ERROR_ENVIO</Codigo> como fallo definitivo:</strong> es SUNAT no disponible; consulte el estado más
            tarde o fuerce el reenvío. Sí es definitivo <Codigo>RECHAZADO</Codigo>: corrija y emita un comprobante nuevo, con otro número. El rechazado
            conserva el suyo: en un rechazo 2000–3999 SUNAT da el número por utilizado, y khipu nunca reasigna un número (un{" "}
            <Codigo>correlativo</Codigo> repetido responde <Codigo>409 DUPLICADO</Codigo>).
          </li>
          <li>
            <strong className="text-foreground">Plazo:</strong> emita el mismo día de la operación. SUNAT rechaza facturas recibidas más de 3 días calendario después
            de la fecha de emisión (2108); una boleta va sola hasta el 5.º día, en el resumen diario hasta el 7.º y después se informa fuera de plazo: SUNAT
            no la rechaza y puede observarla con la 4443.
          </li>
          <li>
            <strong className="text-foreground">Entornos:</strong> integre y pruebe todo en <Codigo>BETA</Codigo>; lo que SUNAT acepta en BETA lo acepta en producción
            con las mismas reglas.
          </li>
          <li>
            <strong className="text-foreground">Secretos:</strong> la API key identifica a su empresa; guárdela en un gestor de secretos y revóquela desde el portal si se
            filtra.
          </li>
        </ul>
      </Seccion>
    </PaginaGuia>
  );
}
