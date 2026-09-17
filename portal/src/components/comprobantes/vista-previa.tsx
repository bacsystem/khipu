"use client";

import { BadgeCheckIcon, CheckIcon, CopyIcon, DownloadIcon, FileCodeIcon, Loader2Icon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import { formatearXml } from "@/lib/xml";

type Documento = "xml" | "cdr";

type Estado = { estado: "cargando" } | { estado: "ok"; texto: string } | { estado: "error"; mensaje: string };

const BOTON = "inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-card px-3 text-xs font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground";

function urlDe(id: string, doc: Documento, descarga: boolean): string {
  if (doc === "xml") return `/api/proxy/facturas/${id}/xml`;
  return descarga ? `/api/proxy/facturas/${id}/cdr` : `/api/proxy/facturas/${id}/cdr?formato=xml`;
}

function BotonCopiarTexto({ texto }: { texto: string }) {
  const [copiado, setCopiado] = useState(false);
  return (
    <button
      type="button"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(texto);
          setCopiado(true);
          setTimeout(() => setCopiado(false), 1500);
        } catch {
          // el navegador puede denegar el acceso al portapapeles; no hay nada más que hacer
        }
      }}
      className={BOTON}
    >
      {copiado ? <CheckIcon className="size-3.5 text-success-foreground" /> : <CopyIcon className="size-3.5" />}
      {copiado ? "Copiado" : "Copiar"}
    </button>
  );
}

export function VistaPrevia({
  id,
  numero,
  nombreArchivo,
  tieneCdr,
}: {
  id: string;
  numero: string;
  nombreArchivo: string | null;
  tieneCdr: boolean;
}) {
  const [abierto, setAbierto] = useState<Documento | null>(null);
  const [cache, setCache] = useState<Partial<Record<Documento, Estado>>>({});
  const pedidos = useRef<Set<Documento>>(new Set());

  const activo = abierto;

  useEffect(() => {
    if (!activo || pedidos.current.has(activo)) return;
    pedidos.current.add(activo);
    setCache((c) => ({ ...c, [activo]: { estado: "cargando" } }));
    fetch(urlDe(id, activo, false))
      .then(async (res) => {
        if (!res.ok) throw new Error(res.status === 404 ? "El documento todavía no existe." : `Error ${res.status} al obtener el documento.`);
        return res.text();
      })
      .then((texto) => setCache((c) => ({ ...c, [activo]: { estado: "ok", texto: formatearXml(texto) } })))
      .catch((e: Error) => {
        pedidos.current.delete(activo);
        setCache((c) => ({ ...c, [activo]: { estado: "error", mensaje: e.message } }));
      });
  }, [activo, id]);

  const contenido = activo ? cache[activo] : undefined;
  const titulo = activo === "cdr" ? "Constancia de recepción (CDR)" : "XML firmado";
  const archivo = activo === "cdr" ? `R-${nombreArchivo ?? numero}.zip` : `${nombreArchivo ?? numero}.xml`;

  return (
    <>
      <button type="button" onClick={() => setAbierto("xml")} className={BOTON}>
        <FileCodeIcon className="size-4 text-muted-foreground" />
        Ver XML
      </button>
      {tieneCdr ? (
        <button type="button" onClick={() => setAbierto("cdr")} className={BOTON}>
          <BadgeCheckIcon className="size-4 text-success-foreground" />
          Ver CDR
        </button>
      ) : null}

      <Sheet open={abierto !== null} onOpenChange={(open) => !open && setAbierto(null)}>
        <SheetContent side="right" className="w-full gap-0 p-0 data-[side=right]:sm:max-w-4xl">
          <SheetHeader className="border-b border-border/60 px-5 py-4 pr-14">
            <SheetTitle className="text-sm font-semibold">{titulo}</SheetTitle>
            <SheetDescription className="font-mono text-xs">{archivo}</SheetDescription>
          </SheetHeader>

          <div className="flex items-center justify-between gap-2 border-b border-border/60 bg-muted/60 px-5 py-2">
            <div className="inline-flex h-8 items-center gap-1 rounded-lg border border-border/60 bg-secondary/80 p-1">
              {(["xml", "cdr"] as Documento[])
                .filter((d) => d === "xml" || tieneCdr)
                .map((d) => (
                  <button
                    key={d}
                    type="button"
                    onClick={() => setAbierto(d)}
                    className={cn(
                      "inline-flex h-6 items-center rounded-md px-3 text-[12px] font-medium transition-colors",
                      activo === d ? "bg-card text-foreground shadow-2xs" : "text-muted-foreground hover:text-foreground",
                    )}
                  >
                    {d === "xml" ? "XML" : "CDR"}
                  </button>
                ))}
            </div>
            <div className="flex items-center gap-2">
              {contenido?.estado === "ok" ? <BotonCopiarTexto texto={contenido.texto} /> : null}
              {activo ? (
                <a href={urlDe(id, activo, true)} className={cn(BOTON, "bg-foreground text-background hover:bg-foreground/90 hover:text-background")}>
                  <DownloadIcon className="size-3.5" />
                  Descargar {activo === "cdr" ? "ZIP" : "XML"}
                </a>
              ) : null}
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-auto bg-card">
            {!contenido || contenido.estado === "cargando" ? (
              <div className="flex h-40 items-center justify-center gap-2 text-xs text-muted-foreground">
                <Loader2Icon className="size-4 animate-spin" />
                Cargando documento…
              </div>
            ) : contenido.estado === "error" ? (
              <div className="m-5 rounded-lg border border-destructive-border bg-destructive/10 p-3 text-xs text-destructive">{contenido.mensaje}</div>
            ) : (
              <pre className="px-5 py-4 font-mono text-[11.5px] leading-relaxed break-all whitespace-pre-wrap text-foreground/90 tabular-nums">
                {contenido.texto}
              </pre>
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
