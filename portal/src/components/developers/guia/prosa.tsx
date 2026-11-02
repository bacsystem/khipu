import type { ReactNode } from "react";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { CONTENEDOR_TABLA } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { CABECERA_TABLA } from "@/lib/estilos";
import { IndiceActivo } from "@/components/developers/guia/indice-activo";
import { cn } from "@/lib/utils";

export function Seccion({ id, titulo, children }: { id: string; titulo: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-16 space-y-4">
      <h2 className="font-heading text-lg font-semibold tracking-tight text-foreground">{titulo}</h2>
      {children}
    </section>
  );
}

export function P({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cn("text-control leading-relaxed text-muted-foreground", className)}>{children}</p>;
}

export function Codigo({ children }: { children: ReactNode }) {
  return <code className="rounded bg-secondary px-1 py-0.5 font-mono text-control text-foreground">{children}</code>;
}

/**
 * Aviso destacado dentro de la prosa de la guía (advertencia SUNAT, nota importante).
 *
 * Se llama `NotaProsa` y no `Aviso` para no chocar con el `Aviso` del design system (`patrones/cabecera-dialogo`),
 * que es el de diálogos y formularios y tiene otra API (exige `icon`, `tono: "aviso" | "ok"`). Renombrar este —que
 * es propio del portal— en vez de aquel evita bifurcar un archivo vendorizado que hoy es idéntico al de upstream.
 */
export function NotaProsa({ tono = "info", children }: { tono?: "info" | "aviso"; children: ReactNode }) {
  // La `Alerta` del DS con su tinte; solo se elige el tono.
  return <Alerta tono={tono}>{children}</Alerta>;
}

export function Tabla({ cabeceras, filas }: { cabeceras: string[]; filas: ReactNode[][] }) {
  return (
    <div className={CONTENEDOR_TABLA}>
      {/*
        La primera columna NO lleva `whitespace-nowrap`. Con él, una celda que enumera varios códigos
        —«DESCUENTO_INVALIDO / CARGO_INVALIDO / DETRACCION_INVALIDA / …»— no podía cortarse y estiraba la columna a
        804 px, dejando 179 px para el significado: las filas quedaban de 95 px de alto, con el texto aplastado en una
        tira y el resto en blanco. Sin él, el corte ocurre en los espacios y ningún código se parte, porque el guion
        bajo no es un punto de corte. Medido en la página de errores: la tabla pasó de 6511 a 4191 px de alto.
      */}
      <Table className="text-control">
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            {cabeceras.map((c) => (
              <TableHead key={c} className={CABECERA_TABLA}>
                {c}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {filas.map((f, i) => (
            <TableRow key={i}>
              {f.map((c, k) => (
                <TableCell key={k} className={cn("py-3 align-top leading-relaxed whitespace-normal", k === 0 && "font-mono text-foreground")}>
                  {c}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}


export function PaginaGuia({ titulo, resumen, indice, children }: { titulo: string; resumen: string; indice: Array<{ id: string; label: string }>; children: ReactNode }) {
  return (
    <div className="mx-auto grid w-full max-w-6xl grid-cols-1 gap-12 px-4 py-10 md:px-6 lg:grid-cols-[minmax(0,1fr)_228px]">
      <div className="min-w-0 space-y-12">
        <header className="space-y-2">
          <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">{titulo}</h1>
          <P className="text-cuerpo">{resumen}</P>
        </header>
        {children}
      </div>
      <IndiceActivo items={indice} />
    </div>
  );
}
