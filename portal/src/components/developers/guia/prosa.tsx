import type { ReactNode } from "react";
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
  return <p className={cn("text-[13px] leading-relaxed text-muted-foreground", className)}>{children}</p>;
}

export function Codigo({ children }: { children: ReactNode }) {
  return <code className="rounded bg-secondary px-1 py-0.5 font-mono text-[12px] text-foreground">{children}</code>;
}

/**
 * Aviso destacado dentro de la prosa de la guía (advertencia SUNAT, nota importante).
 *
 * Se llama `NotaProsa` y no `Aviso` para no chocar con el `Aviso` del design system (`patrones/cabecera-dialogo`),
 * que es el de diálogos y formularios y tiene otra API (exige `icon`, `tono: "aviso" | "ok"`). Renombrar este —que
 * es propio del portal— en vez de aquel evita bifurcar un archivo vendorizado que hoy es idéntico al de upstream.
 */
export function NotaProsa({ tono = "info", children }: { tono?: "info" | "aviso"; children: ReactNode }) {
  return (
    <div
      className={cn(
        "rounded-lg border px-3 py-2.5 text-[13px] leading-relaxed",
        tono === "aviso" ? "border-warning-border bg-warning text-warning-foreground" : "border-accent-border bg-accent/60 text-accent-foreground",
      )}
    >
      {children}
    </div>
  );
}

export function Tabla({ cabeceras, filas }: { cabeceras: string[]; filas: ReactNode[][] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-[13px]">
        <thead>
          <tr className="border-b border-border/60 bg-muted">
            {cabeceras.map((c) => (
              <th key={c} className="px-3 py-2 text-left text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((f, i) => (
            <tr key={i} className="border-b border-border/60 align-top last:border-0">
              {f.map((c, k) => (
                <td key={k} className={cn("px-3 py-2", k === 0 && "font-mono whitespace-nowrap text-foreground")}>
                  {c}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Índice lateral de una página de guía. */
export function Indice({ items }: { items: Array<{ id: string; label: string }> }) {
  return (
    <nav aria-label="Contenido" className="sticky top-16 hidden max-h-[calc(100vh-5rem)] overflow-y-auto lg:block">
      <span className="mb-2 block text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">En esta página</span>
      <ul className="space-y-1 border-l border-border/60">
        {items.map((i) => (
          <li key={i.id}>
            <a href={`#${i.id}`} className="-ml-px block border-l border-transparent py-0.5 pl-3 text-[12px] text-muted-foreground hover:border-primary hover:text-foreground">
              {i.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export function PaginaGuia({ titulo, resumen, indice, children }: { titulo: string; resumen: string; indice: Array<{ id: string; label: string }>; children: ReactNode }) {
  return (
    <div className="mx-auto grid w-full max-w-6xl grid-cols-1 gap-10 px-4 py-8 md:px-6 lg:grid-cols-[minmax(0,1fr)_200px]">
      <div className="min-w-0 space-y-10">
        <header className="space-y-2">
          <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">{titulo}</h1>
          <P className="text-[14px]">{resumen}</P>
        </header>
        {children}
      </div>
      <Indice items={indice} />
    </div>
  );
}
