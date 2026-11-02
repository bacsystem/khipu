"use client";

import { BookOpenIcon, KeyIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useState } from "react";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { cn } from "@/lib/utils";
import { ACCION_SECUNDARIA, BOTON_PRIMARIO, CABECERA_TABLA, TARJETA } from "@/lib/estilos";
import { CabeceraDialogo } from "khipu-design-system/patrones/cabecera-dialogo";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { ChipCodigo } from "khipu-design-system/patrones/pill-estado";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";

type Icono = typeof BookOpenIcon;

export type CampoReferencia = { campo: string; tipo: string; descripcion: string; clave?: boolean };


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
  const t = useMessages().referenciaTecnica;
  const [abierto, setAbierto] = useState(false);

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger
        className={className ?? ACCION_SECUNDARIA}
      >
        <BookOpenIcon className="size-4" />
        {t.abrir}
      </DialogTrigger>

      {/* Ancho por estilo inline: no depende de breakpoints ni del orden de las utilidades. */}
      <DialogContent className="gap-0 p-0" style={{ maxWidth: "min(1200px, calc(100vw - 3rem))" }}>
        <CabeceraDialogo icon={BookOpenIcon} titulo={titulo} descripcion={descripcion} />

        <div className="@container bg-muted/40 px-5 py-4">
          <div className="grid grid-cols-1 items-start gap-4 @3xl:grid-cols-2">{children}</div>
        </div>

        <div className={cn("flex flex-wrap items-center gap-2 border-t border-border/60 px-5 py-3", pie ? "justify-between" : "justify-end")}>
          {pie}
          <button type="button" onClick={() => setAbierto(false)} className={BOTON_PRIMARIO}>
            {t.entendido}
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
    <section className={TARJETA}>
      <CabeceraSeccion icon={IconoSeccion} titulo={titulo} subtitulo={subtitulo} derecha={accion} />
      {children}
    </section>
  );
}

/** Tabla campo · tipo · descripción de una entidad; `nota` es la franja inferior (claves, cifrado…). */
export function TablaCampos({ campos, etiquetaClave, nota }: { campos: CampoReferencia[]; etiquetaClave?: string; nota?: React.ReactNode }) {
  const t = useMessages().referenciaTecnica;
  return (
    <>
      <div className="px-2 pb-1">
        <Table className="text-control">
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={CABECERA_TABLA}>{t.campo}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.tipo}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.descripcion}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {campos.map((c) => (
              <TableRow key={c.campo}>
                <TableCell>
                  <span className="inline-flex items-center gap-1.5 font-mono font-semibold text-foreground">
                    {c.campo}
                    {c.clave ? <KeyIcon className="size-3 text-warning-solid" aria-label={etiquetaClave ?? t.clavePrimaria} /> : null}
                  </span>
                </TableCell>
                <TableCell>
                  <ChipCodigo>{c.tipo}</ChipCodigo>
                </TableCell>
                <TableCell className="whitespace-normal text-muted-foreground">{c.descripcion}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {nota ? (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border/60 bg-muted/60 px-4 py-2 font-mono text-etiqueta text-muted-foreground">{nota}</div>
      ) : null}
    </>
  );
}

/** Fila de regla: insignia a la izquierda (icono o código), título + detalle, y un valor en mono a la derecha. */
export function ReglaReferencia({ insignia, titulo, detalle, valor }: { insignia: React.ReactNode; titulo: string; detalle: string; valor: string }) {
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5">
      <span className="flex size-8 shrink-0 items-center justify-center rounded bg-accent font-mono text-etiqueta font-semibold text-primary">{insignia}</span>
      <div className="flex min-w-0 flex-1 basis-40 flex-col">
        <span className="text-control font-medium text-foreground">{titulo}</span>
        <span className="text-etiqueta text-muted-foreground">{detalle}</span>
      </div>
      <span className="shrink-0 rounded bg-card px-2 py-0.5 font-mono text-control font-semibold text-foreground shadow-xs">{valor}</span>
    </div>
  );
}

/** Párrafo de cierre de una sección. */
export function NotaReferencia({ children }: { children: React.ReactNode }) {
  return <p className="border-t border-border/60 px-4 py-3 text-control leading-relaxed text-muted-foreground">{children}</p>;
}
