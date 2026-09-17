"use client";

import { BookOpenIcon, TerminalSquareIcon } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { BotonCopiar } from "@/components/ui/boton-copiar";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";

type Lenguaje = "curl" | "node" | "python";

const TABS: Array<{ id: Lenguaje; etiqueta: string }> = [
  { id: "curl", etiqueta: "cURL" },
  { id: "node", etiqueta: "Node.js / TS" },
  { id: "python", etiqueta: "Python" },
];

const CUERPO = {
  serie: "F001",
  fecha_emision: "2026-09-15",
  tipo_operacion: "0101",
  moneda: "PEN",
  cliente: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
  items: [{ codigo: "SRV-001", descripcion: "Desarrollo e integración web", unidad: "ZZ", cantidad: 1, precio_unitario: 2000.0, tipo_afectacion_igv: "10" }],
  enviar_automatico: true,
};

const RESPUESTA = `{
  "estado": "exito",
  "datos": {
    "id": "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f",
    "tipo": "01",
    "serie": "F001",
    "numero": 126,
    "estado_documento": "ACEPTADO",
    "hash": "y4M8+jW8Xp278K1aM02q19KjvO3k=",
    "nombre_archivo": "20614798093-01-F001-00000126",
    "cdr": {
      "codigo": "0",
      "descripcion": "La Factura numero F001-126, ha sido aceptada",
      "observaciones": []
    },
    "totales": { "gravado": 2000.00, "igv": 360.00, "total": 2360.00 },
    "enlaces": {
      "xml": "/v1/facturas/5f2c1e6a-…/xml",
      "cdr": "/v1/facturas/5f2c1e6a-…/cdr"
    }
  }
}`;

function json(indent: number): string {
  const sangria = " ".repeat(indent);
  return JSON.stringify(CUERPO, null, 2)
    .split("\n")
    .map((l, i) => (i === 0 ? l : sangria + l))
    .join("\n");
}

function snippet(lenguaje: Lenguaje, baseUrl: string, apiKey: string): string {
  const url = `${baseUrl}/v1/facturas`;
  switch (lenguaje) {
    case "curl":
      return `curl -X POST ${url} \\
  -H "X-Api-Key: ${apiKey}" \\
  -H "Content-Type: application/json" \\
  -d '${JSON.stringify(CUERPO, null, 2)}'`;
    case "node":
      return `const res = await fetch("${url}", {
  method: "POST",
  headers: { "X-Api-Key": process.env.FACTURA_API_KEY!, "Content-Type": "application/json" },
  body: JSON.stringify(${json(2)}),
});

const { estado, datos } = await res.json();
if (estado !== "exito") throw new Error(datos?.mensaje ?? "Emisión rechazada");
console.log(datos.serie, datos.numero, datos.estado_documento); // F001 126 ACEPTADO`;
    case "python":
      return `import os, requests

res = requests.post(
    "${url}",
    headers={"X-Api-Key": os.environ["FACTURA_API_KEY"]},
    json=${json(4).replace(/true/g, "True").replace(/false/g, "False").replace(/null/g, "None")},
)
res.raise_for_status()
datos = res.json()["datos"]
print(datos["serie"], datos["numero"], datos["estado_documento"])  # F001 126 ACEPTADO`;
  }
}

/** Ejemplo de emisión por API en varios lenguajes con la respuesta real del endpoint, como referencia en un modal. */
export function EjemploIntegracionDialog({ baseUrl, className }: { baseUrl: string; className?: string }) {
  const [abierto, setAbierto] = useState(false);
  const [lenguaje, setLenguaje] = useState<Lenguaje>("curl");
  const codigo = snippet(lenguaje, baseUrl, "fk_TU_API_KEY");

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger
        className={
          className ??
          "inline-flex h-8 items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 text-[12px] font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground"
        }
      >
        <TerminalSquareIcon className="size-4" />
        Prueba de emisión
      </DialogTrigger>

      <DialogContent className="gap-0 p-0" style={{ maxWidth: "min(1200px, calc(100vw - 3rem))" }}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <TerminalSquareIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Prueba de emisión por API</DialogTitle>
              <DialogDescription className="text-[13px]">Firma XML-DSig, validación XSD y envío a SUNAT en una sola llamada</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="@container bg-muted/40 px-5 py-4">
          <div className="grid grid-cols-1 items-start gap-4 @3xl:grid-cols-12">
            <div className={cn(TARJETA, "min-w-0 overflow-hidden @3xl:col-span-7")}>
              <div className="flex flex-wrap items-center gap-2 border-b border-border/60 bg-muted px-4 py-2">
                <span className="rounded bg-primary px-1.5 py-0.5 font-mono text-[10px] font-bold text-primary-foreground uppercase">POST</span>
                <code className="min-w-0 flex-1 truncate font-mono text-[12px] text-foreground">{baseUrl}/v1/facturas</code>
                <div className="inline-flex h-7 shrink-0 items-center gap-0.5 rounded-lg border border-border/60 bg-secondary/80 p-0.5">
                  {TABS.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() => setLenguaje(t.id)}
                      className={cn(
                        "h-6 rounded-md px-2 text-[11px] font-medium transition-colors",
                        lenguaje === t.id ? "bg-card text-foreground shadow-2xs" : "text-muted-foreground hover:text-foreground",
                      )}
                    >
                      {t.etiqueta}
                    </button>
                  ))}
                </div>
                <BotonCopiar texto={codigo} etiqueta className="h-7 px-2 text-[11px] font-medium" />
              </div>

              <pre className="max-h-[52vh] overflow-auto bg-code p-4 font-mono text-[12px] leading-relaxed text-code-foreground">
                <code>{codigo}</code>
              </pre>

              <div className="border-t border-border/60 px-4 py-2 text-[11px] text-muted-foreground">
                Reemplaza <code className="font-mono text-foreground">fk_TU_API_KEY</code> por una llave activa. Si omites{" "}
                <code className="font-mono text-foreground">correlativo</code>, el número lo asigna la serie de forma secuencial.
              </div>
            </div>

            <div className={cn(TARJETA, "min-w-0 overflow-hidden @3xl:col-span-5")}>
              <div className="flex items-center justify-between gap-2 border-b border-border/60 px-4 py-2">
                <h4 className="text-[13px] font-semibold text-foreground">Respuesta</h4>
                <span className="rounded border border-success-border bg-success px-2 py-0.5 font-mono text-[11px] font-semibold text-success-foreground">
                  201 Created
                </span>
              </div>
              <pre className="max-h-[52vh] overflow-auto bg-muted/40 p-4 font-mono text-[11.5px] leading-relaxed text-foreground">
                <code>{RESPUESTA}</code>
              </pre>
              <p className="border-t border-border/60 px-4 py-2 text-[11px] text-muted-foreground">
                <code className="font-mono text-foreground">estado_documento</code> queda en <code className="font-mono">ENVIADO</code> si SUNAT demora; el
                CDR llega por reintentos automáticos y lo ves en Comprobantes.
              </p>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border/60 px-5 py-3">
          <Link href="/developers" target="_blank" className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline">
            <BookOpenIcon className="size-3.5" />
            Referencia completa de la API
          </Link>
          <button
            type="button"
            onClick={() => setAbierto(false)}
            className="inline-flex h-9 items-center rounded-lg bg-foreground px-3.5 text-[13px] font-medium text-background shadow-xs transition-colors hover:bg-foreground/90"
          >
            Entendido
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
