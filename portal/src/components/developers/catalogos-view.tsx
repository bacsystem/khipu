"use client";

import { SearchIcon } from "lucide-react";
import { useMemo, useState } from "react";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import { cn } from "@/lib/utils";
import { Rico } from "./guia/rico";

/** Qué campo de la API usa cada catálogo, para que el lector sepa dónde aplicarlo (la disponibilidad de cada caso la indica la guía). */
const USO: Record<string, string> = {
  "01": "Tipo de comprobante (`tipo` en las respuestas).",
  "02": "`moneda` del comprobante.",
  "03": "`items[].unidad`. Lista de las unidades más usadas (UN/ECE rec 20); la API acepta cualquier código de la lista completa de la UNECE y SUNAT rechaza los inexistentes.",
  "05": "Tributos que khipu escribe en el XML según la afectación de cada ítem; no se envía en la API.",
  "06": "`cliente.tipo_doc`. En factura solo `6` (RUC).",
  "07": "`items[].tipo_afectacion_igv`. Onerosas 10/20/30; gratuitas 11–16, 21, 31–37 (precio = valor referencial, no se cobran). Columna adicional: código de tributo que genera. No soportadas: 17 (IVAP) y 40 (exportación).",
  "08": "`items[].isc.sistema`.",
  "09": "Motivo de una nota de crédito.",
  "10": "Motivo de una nota de débito.",
  "12": "Tipo de documento relacionado (facturas de anticipo, guías).",
  "16": "Tipo de precio de la línea: `01` precio de venta, `02` valor referencial en gratuitas. Lo asigna khipu.",
  "22": "`percepcion.regimen`.",
  "23": "Régimen de retención.",
  "51": "`tipo_operacion`. Columna adicional: comprobantes en los que aplica; un código fuera del catálogo responde `422`.",
  "52": "Leyendas que khipu añade al XML (monto en letras 1000, gratuitas 1002, detracción 2006…). No se envían en la API.",
  "53": "`descuento`/`descuento_global` (00–03, los asigna khipu), `items[].cargos[].codigo` (47/48) y `cargos[].codigo` (46/49/50); retención 62, percepción 51–53 y anticipos 04–06 los escribe khipu. Columna adicional: nivel (línea o global).",
  "54": "`detraccion.codigo_bien_servicio`.",
  "59": "`detraccion.medio_pago`.",
  "60": "Tipo de dirección del emisor.",
};

export function CatalogosView({ catalogos }: { catalogos: CatalogoSunat[] }) {
  const [filtro, setFiltro] = useState("");
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

  return (
    <div className="mx-auto grid w-full max-w-6xl grid-cols-1 gap-8 px-4 py-8 md:px-6 lg:grid-cols-[200px_minmax(0,1fr)]">
      <aside className="lg:sticky lg:top-16 lg:max-h-[calc(100vh-5rem)] lg:overflow-y-auto">
        <div className="relative mb-3">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <input
            value={filtro}
            onChange={(e) => setFiltro(e.target.value)}
            placeholder="Buscar código o texto…"
            aria-label="Buscar en los catálogos"
            className="h-8 w-full rounded-lg border border-border bg-muted pl-8 pr-2 text-[12px] text-foreground outline-none placeholder:text-muted-foreground/70 focus:border-ring focus:bg-card"
          />
        </div>
        <ul className="space-y-0.5">
          {catalogos.map((c) => (
            <li key={c.id}>
              <a href={`#cat-${c.id}`} className="flex items-baseline gap-2 rounded px-2 py-1 text-[12px] text-muted-foreground hover:bg-muted hover:text-foreground">
                <span className="font-mono text-foreground/80">{c.id}</span>
                <span className="truncate">{c.nombre}</span>
              </a>
            </li>
          ))}
        </ul>
      </aside>

      <div className="min-w-0 space-y-8">
        <header className="space-y-2">
          <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">Catálogos SUNAT</h1>
          <p className="text-[14px] leading-relaxed text-muted-foreground">
            Códigos oficiales que esperan los campos de la API (Anexo 8 de las reglas de validación de SUNAT, versión 2026-08-26). Los mismos datos están
            disponibles por API, sin credenciales: <code className="rounded bg-secondary px-1 font-mono text-[12px] text-foreground">GET /v1/catalogos/{"{id}"}</code>.
          </p>
        </header>
        {visibles.length === 0 ? <p className="text-[13px] text-muted-foreground">Sin coincidencias para “{filtro}”.</p> : null}
        {visibles.map((c) => (
          <section key={c.id} id={`cat-${c.id}`} className="scroll-mt-16 overflow-hidden rounded-xl border border-border bg-card shadow-2xs">
            <div className="border-b border-border/60 px-4 py-3">
              <div className="flex flex-wrap items-baseline gap-2">
                <span className="rounded bg-secondary px-1.5 py-0.5 font-mono text-[11px] font-semibold text-primary">Catálogo {c.id}</span>
                <h2 className="text-[15px] font-semibold text-foreground">{c.nombre}</h2>
                <span className="font-mono text-[11px] text-muted-foreground">{c.entradas.length} códigos</span>
              </div>
              {USO[c.id] ? (
                <p className="mt-1 text-[12px] text-muted-foreground">
                  <Rico texto={USO[c.id]} />
                </p>
              ) : null}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="border-b border-border/60 bg-muted">
                    {c.columnas.map((col) => (
                      <th key={col} className="px-4 py-2 text-left text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">
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
                        <td key={col} className="px-4 py-1.5 font-mono text-[12px] whitespace-nowrap text-muted-foreground">
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
