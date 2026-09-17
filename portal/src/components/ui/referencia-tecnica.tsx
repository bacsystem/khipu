"use client";

import { BookOpenIcon, KeyRoundIcon } from "lucide-react";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

type Icono = typeof BookOpenIcon;

export type CampoReferencia = { campo: string; tipo: string; descripcion: string; clave?: boolean };

const ICONO = "flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary";
const TH = "px-4 py-2 text-left text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";

/**
 * Modal "Referencia técnica" de una sección del portal: disparador, cabecera, rejilla de dos columnas
 * (con `@container`, se apila en angosto) y pie con "Entendido". `pie` va a la izquierda del pie (p. ej. un enlace).
 */
export function ReferenciaTecnicaDialog({
  titulo,
  descripcion,
  className,
  pie,
  children,
}: {
  titulo: string;
  descripcion: React.ReactNode;
  className?: string;
  pie?: React.ReactNode;
  children: React.ReactNode;
}) {
  const [abierto, setAbierto] = useState(false);

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger
        className={
          className ??
          "inline-flex h-8 items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 text-[12px] font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground"
        }
      >
        <BookOpenIcon className="size-4" />
        Referencia técnica
      </DialogTrigger>

      {/* Ancho por estilo inline: no depende de breakpoints ni del orden de las utilidades. */}
      <DialogContent className="gap-0 p-0" style={{ maxWidth: "min(1200px, calc(100vw - 3rem))" }}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className={ICONO}>
              <BookOpenIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{titulo}</DialogTitle>
              <DialogDescription className="text-[13px]">{descripcion}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="@container bg-muted/40 px-5 py-4">
          <div className="grid grid-cols-1 items-start gap-4 @3xl:grid-cols-2">{children}</div>
        </div>

        <div className={cn("flex flex-wrap items-center gap-2 border-t border-border/60 px-5 py-3", pie ? "justify-between" : "justify-end")}>
          {pie}
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

/** Tarjeta de la rejilla: icono, título, subtítulo en mono y contenido libre. `accion` se alinea a la derecha de la cabecera. */
export function SeccionReferencia({
  icono: IconoSeccion,
  titulo,
  subtitulo,
  accion,
  children,
}: {
  icono: Icono;
  titulo: React.ReactNode;
  subtitulo: React.ReactNode;
  accion?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-xl border border-border/80 bg-card shadow-2xs">
      <div className="flex flex-wrap items-center gap-x-2.5 gap-y-2 border-b border-border/60 px-4 py-3">
        <div className={ICONO}>
          <IconoSeccion className="size-4" />
        </div>
        <div className="flex min-w-0 flex-1 basis-48 flex-col">
          <h4 className="text-[13px] font-semibold text-foreground">{titulo}</h4>
          <span className="font-mono text-[11px] text-muted-foreground">{subtitulo}</span>
        </div>
        {accion}
      </div>
      {children}
    </section>
  );
}

/** Tabla campo · tipo · descripción de una entidad; `nota` es la franja inferior (claves, cifrado…). */
export function TablaCampos({ campos, etiquetaClave = "Clave primaria", nota }: { campos: CampoReferencia[]; etiquetaClave?: string; nota?: React.ReactNode }) {
  return (
    <>
      <div className="overflow-x-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border/60 bg-muted">
              <th className={TH}>Campo</th>
              <th className={TH}>Tipo</th>
              <th className={TH}>Descripción</th>
            </tr>
          </thead>
          <tbody>
            {campos.map((c) => (
              <tr key={c.campo} className="border-b border-border/60 last:border-0">
                <td className="px-4 py-2 whitespace-nowrap">
                  <span className="inline-flex items-center gap-1.5 font-mono font-semibold text-foreground">
                    {c.campo}
                    {c.clave ? <KeyRoundIcon className="size-3 text-warning-solid" aria-label={etiquetaClave} /> : null}
                  </span>
                </td>
                <td className="px-4 py-2 whitespace-nowrap">
                  <span className="rounded bg-secondary px-1.5 py-0.5 font-mono text-[11px] font-medium text-primary">{c.tipo}</span>
                </td>
                <td className="px-4 py-2 text-muted-foreground">{c.descripcion}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {nota ? (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border/60 bg-muted/60 px-4 py-2 font-mono text-[11px] text-muted-foreground">{nota}</div>
      ) : null}
    </>
  );
}

/** Fila de regla: insignia a la izquierda (icono o código), título + detalle, y un valor en mono a la derecha. */
export function ReglaReferencia({ insignia, titulo, detalle, valor }: { insignia: React.ReactNode; titulo: string; detalle: string; valor: string }) {
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5">
      <span className="flex size-8 shrink-0 items-center justify-center rounded bg-accent font-mono text-[11px] font-semibold text-primary">{insignia}</span>
      <div className="flex min-w-0 flex-1 basis-40 flex-col">
        <span className="text-[13px] font-medium text-foreground">{titulo}</span>
        <span className="text-[11px] text-muted-foreground">{detalle}</span>
      </div>
      <span className="shrink-0 rounded bg-card px-2 py-0.5 font-mono text-[12px] font-semibold text-foreground shadow-2xs">{valor}</span>
    </div>
  );
}

/** Párrafo de cierre de una sección. */
export function NotaReferencia({ children }: { children: React.ReactNode }) {
  return <p className="border-t border-border/60 px-4 py-3 text-[12px] leading-relaxed text-muted-foreground">{children}</p>;
}
