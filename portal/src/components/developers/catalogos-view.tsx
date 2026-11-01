"use client";

import { MagnifyingGlassIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useMemo, useState } from "react";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import { useSeccionActiva } from "@/lib/seccion-activa";
import { cn } from "@/lib/utils";
import { Rico } from "./guia/rico";

/**
 * Qué campo de la API usa cada catálogo, para que el lector sepa dónde aplicarlo (la disponibilidad de cada caso la indica la guía): el texto
 * está en `messages.developers.catalogos.uso`, por número de catálogo.
 */
function uso(messages: Mensajes, id: string): string | undefined {
  const usos: Record<string, string> = messages.developers.catalogos.uso;
  return Object.hasOwn(usos, id) ? usos[id] : undefined;
}

export function CatalogosView({ catalogos }: { catalogos: CatalogoSunat[] }) {
  const [filtro, setFiltro] = useState("");
  const messages = useMessages();
  const t = messages.developers.catalogos;
  const q = filtro.trim().toLowerCase();
  const visibles = useMemo(
    () =>
      catalogos
        .map((c) => ({
          ...c,
          entradas: q ? c.entradas.filter((e) => e.codigo.toLowerCase().includes(q) || e.descripcion.toLowerCase().includes(q)) : c.entradas,
        }))
        .filter((c) => !q || c.entradas.length > 0 || c.nombre.toLowerCase().includes(q)),
    [catalogos, q],
  );
  // Solo se siguen los catálogos que están en el documento: con el filtro puesto, el resto ni existe.
  const activo = useSeccionActiva(visibles.map((c) => `cat-${c.id}`));

  return (
    <div className="mx-auto grid w-full max-w-6xl grid-cols-1 gap-8 px-4 py-8 md:px-6 lg:grid-cols-[200px_minmax(0,1fr)]">
      <aside className="lg:sticky lg:top-16 lg:max-h-[calc(100vh-5rem)] lg:overflow-y-auto">
        <div className="relative mb-3">
          <MagnifyingGlassIcon weight="bold" className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <input
            value={filtro}
            onChange={(e) => setFiltro(e.target.value)}
            placeholder={t.buscarPlaceholder}
            aria-label={t.buscarAria}
            className="h-control w-full rounded-lg border border-border bg-muted pl-8 pr-2 text-control text-foreground outline-none placeholder:text-muted-foreground/70 focus:border-ring focus:bg-card"
          />
        </div>
        {/*
          La lista sigue al filtro: antes mostraba los 22 catálogos siempre, así que con una búsqueda puesta la mitad
          de los enlaces no llevaba a ninguna parte, porque esas secciones no están en el documento.
        */}
        <ul className="space-y-0.5">
          {visibles.map((c) => {
            const esActivo = `cat-${c.id}` === activo;
            return (
              <li key={c.id}>
                <a
                  href={`#cat-${c.id}`}
                  aria-current={esActivo ? "location" : undefined}
                  className={cn(
                    "flex items-baseline gap-2 rounded px-2 py-1 text-control transition-colors",
                    esActivo ? "bg-muted font-medium text-foreground" : "text-muted-foreground hover:bg-muted/60 hover:text-foreground",
                  )}
                >
                  <span className={cn("font-mono", esActivo ? "text-primary" : "text-foreground/80")}>{c.id}</span>
                  <span className="truncate">{c.nombre}</span>
                </a>
              </li>
            );
          })}
        </ul>
      </aside>

      <div className="min-w-0 space-y-8">
        <header className="space-y-2">
          <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">{t.titulo}</h1>
          <p className="text-cuerpo leading-relaxed text-muted-foreground">
            <Rico texto={t.intro} />
          </p>
        </header>
        {visibles.length === 0 ? <p className="text-control text-muted-foreground">{interpolar(t.sinCoincidencias, { filtro })}</p> : null}
        {visibles.map((c) => (
          <section key={c.id} id={`cat-${c.id}`} className="scroll-mt-16 overflow-hidden rounded-xl border border-border bg-card shadow-xs">
            <div className="border-b border-border/60 px-4 py-3">
              <div className="flex flex-wrap items-baseline gap-2">
                <span className="rounded bg-secondary px-1.5 py-0.5 font-mono text-etiqueta font-semibold text-primary">{interpolar(t.catalogo, { id: c.id })}</span>
                <h2 className="text-base font-semibold text-foreground">{c.nombre}</h2>
                <span className="font-mono text-etiqueta text-muted-foreground">{interpolar(t.codigos, { n: c.entradas.length })}</span>
              </div>
              {uso(messages, c.id) ? (
                <p className="mt-1 text-control text-muted-foreground">
                  <Rico texto={uso(messages, c.id) ?? ""} />
                </p>
              ) : null}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-control">
                <thead>
                  <tr className="border-b border-border/60 bg-muted">
                    {c.columnas.map((col) => (
                      <th key={col} className="px-4 py-2 text-left text-etiqueta font-semibold text-muted-foreground/80">
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {c.entradas.map((e) => (
                    <tr key={e.codigo} className="border-b border-border/60 last:border-0">
                      <td className="px-4 py-1.5 font-mono font-semibold whitespace-nowrap text-foreground">{e.codigo}</td>
                      <td className={cn("px-4 py-1.5 text-muted-foreground", c.columnas.length === 2 && "w-full")}>{e.descripcion}</td>
                      {c.columnas.slice(2).map((col) => (
                        <td key={col} className="px-4 py-1.5 font-mono text-control whitespace-nowrap text-muted-foreground">
                          {e.extra[col] ?? "—"}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
