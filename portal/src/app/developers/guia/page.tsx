import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";
import { BloqueCodigo } from "@/components/developers/guia/bloque-codigo";
import { indiceGuia } from "@/components/developers/indices";
import { casos, ERROR_EJEMPLO, RESPUESTA_EJEMPLO } from "@/components/developers/guia/casos-emision";
import { Codigo, NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { Rico } from "@/components/developers/guia/rico";
import { EJEMPLO_ENTREGA, RESPUESTA_CREAR_WEBHOOK, VERIFICAR_NODE, VERIFICAR_PHP, VERIFICAR_PYTHON } from "@/components/developers/guia/webhooks";
import { interpolar } from "@/i18n/mensajes";
import { apiPublicUrl } from "@/lib/api/client";
import { tecnico } from "@/lib/developers/tecnico";
import { cn } from "@/lib/utils";

/** El sobre de toda respuesta de la API: técnico, igual en cualquier idioma. */
const SOBRE = "{ estado, datos, codigo, mensaje, errores }";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: interpolar(messages.developers.meta.guia, { app: messages.app.nombre }) };
}

export default function GuiaPage() {
  const base = apiPublicUrl();
  const messages = useMessages();
  const t = messages.developers.guia;
  const casosDeEmision = casos(messages);
  const lista = "list-disc space-y-1.5 pl-5 text-control leading-relaxed text-muted-foreground";
  const subtitulo = "mt-2 text-cuerpo font-semibold text-foreground";
  return (
    <PaginaGuia titulo={t.titulo} resumen={t.resumen} indice={indiceGuia(messages)}>
      <Seccion id="inicio-rapido" titulo={t.indice.inicioRapido}>
        <ol className="list-decimal space-y-2 pl-5 text-control leading-relaxed text-muted-foreground">
          {t.inicioRapido.pasos.map((paso) => (
            <li key={paso}>
              <Rico texto={interpolar(paso, { base })} negrita="text-foreground" />
            </li>
          ))}
        </ol>
        <BloqueCodigo
          titulo={t.inicioRapido.curl}
          codigo={tecnico`curl -X POST ${base}/v1/facturas \
  -H "X-Api-Key: fk_TU_API_KEY" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "serie": "F001",
  "fecha_emision": "$(TZ=America/Lima date +%F)",
  "moneda": "PEN",
  "cliente": { "tipo_doc": "6", "num_doc": "20601234565", "razon_social": "COMERCIAL ANDINA S.A.C." },
  "items": [
    { "descripcion": "Servicio de consultoría", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10" }
  ]
}
EOF`}
        />
        <BloqueCodigo
          titulo={t.inicioRapido.node}
          lenguaje="javascript"
          codigo={tecnico`// Node 18 o superior. Guárdelo como .mjs: usa await en el nivel superior.
import { randomUUID } from "node:crypto";

const apiKey = process.env.KHIPU_API_KEY;
if (!apiKey) throw new Error("Defina KHIPU_API_KEY con su API key");
const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }); // aaaa-mm-dd, en hora de Lima

const respuesta = await fetch("${base}/v1/facturas", {
  method: "POST",
  headers: {
    "X-Api-Key": apiKey,
    "Content-Type": "application/json",
    "Idempotency-Key": randomUUID(), // un UUID por factura: reintentar con la misma clave no emite otra
  },
  body: JSON.stringify({
    serie: "F001",
    fecha_emision: hoy,
    moneda: "PEN",
    cliente: { tipo_doc: "6", num_doc: "20601234565", razon_social: "COMERCIAL ANDINA S.A.C." },
    items: [{ descripcion: "Servicio de consultoría", unidad: "ZZ", cantidad: 1, precio_unitario: 1180.0, tipo_afectacion_igv: "10" }],
  }),
});
const texto = await respuesta.text();
let cuerpo;
try {
  cuerpo = JSON.parse(texto);
} catch {
  throw new Error("HTTP " + respuesta.status + " sin JSON: " + texto.slice(0, 200)); // por ejemplo, un 502 de un proxy
}
if (cuerpo.estado !== "exito") throw new Error(cuerpo.codigo + ": " + cuerpo.mensaje);
console.log(cuerpo.datos.estado_documento, cuerpo.datos.enlaces.pdf);`}
        />
        <BloqueCodigo
          titulo={t.inicioRapido.python}
          lenguaje="python"
          codigo={tecnico`import os
import uuid
from datetime import datetime
from zoneinfo import ZoneInfo

import requests

api_key = os.environ.get("KHIPU_API_KEY")
if not api_key:
    raise SystemExit("Defina KHIPU_API_KEY con su API key")
hoy = datetime.now(ZoneInfo("America/Lima")).date().isoformat()  # aaaa-mm-dd, en hora de Lima

respuesta = requests.post(
    "${base}/v1/facturas",
    headers={
        "X-Api-Key": api_key,
        "Idempotency-Key": str(uuid.uuid4()),  # un UUID por factura: reintentar con la misma clave no emite otra
    },
    json={
        "serie": "F001",
        "fecha_emision": hoy,
        "moneda": "PEN",
        "cliente": {"tipo_doc": "6", "num_doc": "20601234565", "razon_social": "COMERCIAL ANDINA S.A.C."},
        "items": [{"descripcion": "Servicio de consultoría", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10"}],
    },
    timeout=30,
)
try:
    cuerpo = respuesta.json()
except ValueError:
    raise RuntimeError(f"HTTP {respuesta.status_code} sin JSON: {respuesta.text[:200]}")  # por ejemplo, un 502 de un proxy
if cuerpo["estado"] != "exito":
    raise RuntimeError(f"{cuerpo['codigo']}: {cuerpo['mensaje']}")
print(cuerpo["datos"]["estado_documento"], cuerpo["datos"]["enlaces"]["pdf"])`}
        />
      </Seccion>

      <Seccion id="anatomia" titulo={t.indice.anatomia}>
        <P>
          <Rico texto={t.anatomia.intro} />
        </P>
        <Tabla
          cabeceras={t.anatomia.cabeceras}
          filas={t.anatomia.filas.map(([campo, queEs, valores]) => [campo, queEs, <Rico key={campo} texto={valores} />])}
        />
        <NotaProsa>
          <Rico texto={t.anatomia.nota} enlace="font-medium underline" />
        </NotaProsa>
      </Seccion>

      <Seccion id="casos" titulo={t.indice.casos}>
        <P>
          <Rico texto={t.casos.intro} />
          {casosDeEmision.some((c) => !c.disponible) ? (
            <>
              {" "}
              {t.casos.marcadosAntes}{" "}
              <span className="rounded border border-warning-border bg-warning px-1.5 py-0.5 text-etiqueta font-medium text-warning-foreground">{t.casos.enDesarrollo}</span>{" "}
              {t.casos.marcadosDespues}
            </>
          ) : null}
        </P>
        <div className="space-y-8">
          {casosDeEmision.map((c) => (
            <article key={c.id} id={c.id} className={cn("scroll-mt-16 space-y-3 rounded-xl border border-border bg-card p-5 shadow-xs", !c.disponible && "opacity-90")}>
              <header className="flex flex-wrap items-center gap-2">
                <h3 className="text-base font-semibold text-foreground">{c.titulo}</h3>
                {c.disponible ? null : (
                  <span className="rounded border border-warning-border bg-warning px-1.5 py-0.5 text-etiqueta font-medium text-warning-foreground">{t.casos.enDesarrollo}</span>
                )}
              </header>
              <P>
                <strong className="font-medium text-foreground">{t.casos.cuando}</strong> {c.cuando}
              </P>
              <BloqueCodigo
                titulo={interpolar(c.disponible ? t.casos.request : t.casos.requestPrevisto, { endpoint: c.endpoint ?? "/v1/facturas" })}
                codigo={c.request}
              />
              <ul className="list-disc space-y-1 pl-5 text-control leading-relaxed text-muted-foreground">
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

      <Seccion id="respuesta" titulo={t.indice.respuesta}>
        <P>
          <Rico texto={interpolar(t.respuesta.intro, { sobre: SOBRE })} />
        </P>
        <BloqueCodigo titulo={tecnico`201 Created`} codigo={RESPUESTA_EJEMPLO} />
        <Tabla cabeceras={t.respuesta.cabeceras} filas={t.respuesta.filas.map(([campo, texto]) => [campo, <Rico key={campo} texto={texto} />])} />
      </Seccion>

      <Seccion id="errores" titulo={t.indice.errores}>
        <P>
          <Rico texto={t.errores.intro} />
        </P>
        <BloqueCodigo titulo={tecnico`422 Unprocessable Entity`} codigo={ERROR_EJEMPLO} />
        <P>
          <Rico texto={t.errores.lista} />
        </P>
      </Seccion>

      <Seccion id="webhooks" titulo={t.indice.webhooks}>
        <P>
          <Rico texto={t.webhooks.intro} />
        </P>
        <BloqueCodigo titulo={tecnico`POST /v1/webhooks · 201 Created`} codigo={RESPUESTA_CREAR_WEBHOOK} />
        <ul className={lista}>
          {t.webhooks.registro.map((texto) => (
            <li key={texto}>
              <Rico texto={texto} negrita="text-foreground" />
            </li>
          ))}
        </ul>

        <h3 className={subtitulo}>{t.webhooks.eventosTitulo}</h3>
        <Tabla
          cabeceras={t.webhooks.eventosCabeceras}
          filas={t.webhooks.eventos.map(([evento, cuando]) => [<Codigo key={evento}>{evento}</Codigo>, cuando])}
        />
        <NotaProsa>
          <Rico texto={t.webhooks.notaEstados} />
        </NotaProsa>

        <h3 className={subtitulo}>{t.webhooks.recibeTitulo}</h3>
        <BloqueCodigo titulo={t.webhooks.entrega} codigo={EJEMPLO_ENTREGA} lenguaje="http" />
        <Tabla
          cabeceras={t.webhooks.cabecerasCabeceras}
          filas={t.webhooks.cabeceras.map(([cabecera, para]) => [<Codigo key={cabecera}>{cabecera}</Codigo>, <Rico key={`${cabecera}-texto`} texto={para} negrita="" />])}
        />

        <h3 className={subtitulo}>{t.webhooks.verificarTitulo}</h3>
        <P>
          <Rico texto={t.webhooks.verificar} negrita="" />
        </P>
        <BloqueCodigo titulo={tecnico`Node.js`} codigo={VERIFICAR_NODE} lenguaje="javascript" />
        <BloqueCodigo titulo={tecnico`Python`} codigo={VERIFICAR_PYTHON} lenguaje="python" />
        <BloqueCodigo titulo={tecnico`PHP`} codigo={VERIFICAR_PHP} lenguaje="php" />

        <h3 className={subtitulo}>{t.webhooks.reintentosTitulo}</h3>
        <ul className={lista}>
          {t.webhooks.reintentos.map((texto) => (
            <li key={texto}>
              <Rico texto={texto} negrita="text-foreground" />
            </li>
          ))}
        </ul>
      </Seccion>

      <Seccion id="sire" titulo={t.indice.sire}>
        <NotaProsa>
          <Rico texto={t.sire.experimental} negrita="" />
        </NotaProsa>
        <P>{t.sire.intro}</P>
        <BloqueCodigo
          titulo={t.sire.curlRegistro}
          codigo={tecnico`curl -o rvie.zip -D - "${base}/v1/sire/rvie?periodo=202610" \\
  -H "X-Api-Key: fk_TU_API_KEY"`}
        />
        <ul className={lista}>
          {t.sire.archivo.map((texto) => (
            <li key={texto}>
              <Rico texto={texto} negrita="text-foreground" />
            </li>
          ))}
        </ul>
        <BloqueCodigo
          titulo={t.sire.curlConciliar}
          codigo={tecnico`curl -X POST "${base}/v1/sire/rvie/conciliacion?periodo=202610" \\
  -H "X-Api-Key: fk_TU_API_KEY" \\
  -H "Content-Type: application/zip" \\
  --data-binary @propuesta.zip`}
        />
        <P>
          <Rico texto={t.sire.conciliacion} />
        </P>
        <NotaProsa>
          <Rico texto={t.sire.consolidadas} />
        </NotaProsa>
        <P>
          <Rico texto={t.sire.propuestaSunat} />
        </P>
        <BloqueCodigo
          titulo={t.sire.curlPropuesta}
          codigo={tecnico`curl -X POST "${base}/v1/sire/rvie/propuesta?periodo=202610" \\
  -H "X-Api-Key: fk_TU_API_KEY"
curl "${base}/v1/sire/rvie/propuesta/TICKET/conciliacion?periodo=202610" \\
  -H "X-Api-Key: fk_TU_API_KEY"`}
        />
      </Seccion>

      <Seccion id="buenas-practicas" titulo={t.indice.buenasPracticas}>
        <ul className="list-disc space-y-2 pl-5 text-control leading-relaxed text-muted-foreground">
          {t.buenasPracticas.map((texto) => (
            <li key={texto}>
              <Rico texto={texto} negrita="text-foreground" />
            </li>
          ))}
        </ul>
      </Seccion>
    </PaginaGuia>
  );
}
