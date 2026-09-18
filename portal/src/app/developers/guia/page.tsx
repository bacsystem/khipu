import Link from "next/link";
import { BloqueCodigo } from "@/components/developers/guia/bloque-codigo";
import { CASOS, ERROR_EJEMPLO, RESPUESTA_EJEMPLO } from "@/components/developers/guia/casos-emision";
import { Aviso, Codigo, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { Rico } from "@/components/developers/guia/rico";
import { apiPublicUrl } from "@/lib/api/client";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

export const metadata = { title: `Guía de emisión · ${messages.app.nombre}` };

const INDICE = [
  { id: "inicio-rapido", label: "Inicio rápido" },
  { id: "anatomia", label: "Anatomía de una factura" },
  { id: "casos", label: "Casos de emisión" },
  ...CASOS.map((c) => ({ id: c.id, label: `· ${c.titulo}` })),
  { id: "respuesta", label: "Cómo leer la respuesta" },
  { id: "errores", label: "Errores" },
  { id: "buenas-practicas", label: "Buenas prácticas" },
];

export default function GuiaPage() {
  const base = apiPublicUrl();
  return (
    <PaginaGuia
      titulo="Guía de emisión de facturas"
      resumen="Todo lo necesario para emitir facturas electrónicas válidas ante SUNAT con una sola llamada: qué enviar en cada caso, qué calcula khipu por usted, qué devuelve y cómo actuar ante cada estado o error."
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
            <strong className="text-foreground">Cree una serie</strong> de factura (<Codigo>F001</Codigo>) en <em>Series</em>.
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
    "cliente": { "tipo_doc": "6", "num_doc": "20601234567", "razon_social": "COMERCIAL ANDINA S.A.C." },
    "items": [
      { "descripcion": "Servicio de consultoría", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10" }
    ]
  }'`}
        />
      </Seccion>

      <Seccion id="anatomia" titulo="Anatomía de una factura">
        <P>
          Una factura electrónica es un XML UBL 2.1 firmado. Usted envía los datos comerciales; khipu resuelve el resto: numeración correlativa,
          cálculo de IGV y totales, monto en letras, catálogos, firma XML-DSig, validación contra los esquemas oficiales, envío SOAP a SUNAT,
          lectura del CDR y reintentos.
        </P>
        <Tabla
          cabeceras={["Campo", "Qué es", "Valores / catálogo"]}
          filas={[
            ["serie", "Serie de numeración registrada en la empresa.", <>Formato <Codigo>F</Codigo> + 3 alfanuméricos (<Codigo>F001</Codigo>).</>],
            ["correlativo", "Número dentro de la serie. Opcional.", "Omítalo para que khipu asigne el siguiente; envíelo si su sistema lleva la numeración."],
            ["fecha_emision", "Fecha del comprobante.", <><Codigo>YYYY-MM-DD</Codigo>, no futura. SUNAT debe recibirla en 3 días calendario.</>],
            ["moneda", "Moneda de todo el comprobante.", <><Codigo>PEN</Codigo>, <Codigo>USD</Codigo>, <Codigo>EUR</Codigo> — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-02">catálogo 02</Link>.</>],
            ["tipo_operacion", "Naturaleza de la operación.", <><Codigo>0101</Codigo> venta interna (por defecto), <Codigo>1001</Codigo> sujeta a detracción (exportación <Codigo>0200</Codigo> aún no soportada) — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-51">catálogo 51</Link>.</>],
            ["cliente.tipo_doc", "Tipo de documento del adquirente.", <>En factura siempre <Codigo>6</Codigo> (RUC) — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-06">catálogo 06</Link>.</>],
            ["cliente.num_doc", "RUC del adquirente.", "11 dígitos."],
            ["items[].unidad", "Unidad de medida.", <><Codigo>NIU</Codigo> unidad (bienes), <Codigo>ZZ</Codigo> unidad (servicios), <Codigo>KGM</Codigo>, <Codigo>HUR</Codigo>… — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-03">catálogo 03</Link>.</>],
            ["items[].precio_unitario", "Precio de venta unitario.", "Con IGV incluido para gravados; khipu obtiene el valor unitario y el IGV."],
            ["items[].tipo_afectacion_igv", "Cómo tributa el ítem.", <><Codigo>10</Codigo> gravado, <Codigo>20</Codigo> exonerado, <Codigo>30</Codigo> inafecto; gratuitas 11–17/21/31–37 — <Link className="text-primary hover:underline" href="/developers/catalogos#cat-07">catálogo 07</Link>.</>],
            ["forma_pago", "Contado o crédito con cuotas.", <>Por defecto contado. Al crédito: <Codigo>monto_pendiente</Codigo> + <Codigo>cuotas[]</Codigo>.</>],
            ["enviar_automatico", "Enviar a SUNAT en la misma llamada.", <><Codigo>true</Codigo> por defecto; <Codigo>false</Codigo> deja el comprobante FIRMADO.</>],
          ]}
        />
        <Aviso>
          Los códigos vienen de los catálogos oficiales de SUNAT. Puede consultarlos en la sección{" "}
          <Link href="/developers/catalogos" className="font-medium underline">
            Catálogos SUNAT
          </Link>{" "}
          o por API (<Codigo>GET /v1/catalogos/07</Codigo>, sin credenciales). Un <Codigo>tipo_operacion</Codigo>, <Codigo>tipo_afectacion_igv</Codigo>,{" "}
          <Codigo>tipo_doc</Codigo> o <Codigo>moneda</Codigo> fuera de catálogo responde <Codigo>422</Codigo> antes de consumir numeración; la{" "}
          <Codigo>unidad</Codigo> no se valida localmente (la lista UN/ECE completa excede el catálogo) y un código inexistente lo rechaza SUNAT.
        </Aviso>
      </Seccion>

      <Seccion id="casos" titulo="Casos de emisión">
        <P>
          Cada caso muestra el JSON exacto para <Codigo>POST /v1/facturas</Codigo> y las reglas que aplican.
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
              <BloqueCodigo titulo={c.disponible ? "request" : "request (contrato previsto)"} codigo={c.request} />
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
            ["enlaces.xml / enlaces.cdr", <>Descargas. <Codigo>cdr</Codigo> solo aparece cuando SUNAT emitió la constancia.</>],
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

      <Seccion id="buenas-practicas" titulo="Buenas prácticas">
        <ul className="list-disc space-y-2 pl-5 text-[13px] leading-relaxed text-muted-foreground">
          <li>
            <strong className="text-foreground">Idempotencia:</strong> si su sistema puede reintentar una llamada (timeouts), envíe <Codigo>correlativo</Codigo>: un
            duplicado responde <Codigo>409</Codigo> en vez de emitir dos facturas.
          </li>
          <li>
            <strong className="text-foreground">Guarde el <Codigo>id</Codigo></strong> que devuelve khipu junto a su documento interno; con él consulta el estado y
            descarga XML y CDR.
          </li>
          <li>
            <strong className="text-foreground">No trate <Codigo>ERROR_ENVIO</Codigo> como fallo definitivo:</strong> es SUNAT no disponible; consulte el estado más
            tarde o fuerce el reenvío. Sí es definitivo <Codigo>RECHAZADO</Codigo>: corrija y vuelva a emitir (puede reutilizar el número).
          </li>
          <li>
            <strong className="text-foreground">Plazo:</strong> emita el mismo día de la operación; SUNAT rechaza facturas recibidas más de 3 días calendario después
            de la fecha de emisión.
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
