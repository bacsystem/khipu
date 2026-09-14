"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { esEstadoFinal, type Comprobante, type EstadoDocumento } from "@/lib/api/facturas";
import { EstadoBadge } from "./estado-badge";

const ESTADOS: EstadoDocumento[] = [
  "RECIBIDO",
  "FIRMADO",
  "ERROR_ENVIO",
  "PENDIENTE_AGRUPACION",
  "ENVIADO",
  "ACEPTADO",
  "ACEPTADO_CON_OBS",
  "RECHAZADO",
  "ANULADO",
  "INVALIDO",
];

async function fetchComprobantes(estado: string | undefined, pagina: number): Promise<Comprobante[]> {
  const qs = new URLSearchParams({ pagina: String(pagina), por_pagina: "20" });
  if (estado) qs.set("estado", estado);
  const res = await fetch(`/api/proxy/facturas?${qs}`);
  const json = await res.json();
  if (json.estado !== "exito") throw new Error(json.mensaje ?? "Error al listar comprobantes");
  return json.datos as Comprobante[];
}

export function ComprobantesTable({
  inicial,
  estado,
  pagina,
}: {
  inicial: Comprobante[];
  estado?: EstadoDocumento;
  pagina: number;
}) {
  const router = useRouter();
  const params = useSearchParams();

  const { data } = useQuery({
    queryKey: ["facturas", estado ?? null, pagina],
    queryFn: () => fetchComprobantes(estado, pagina),
    initialData: inicial,
    refetchInterval: (query) => {
      const rows = query.state.data ?? [];
      return rows.some((c) => !esEstadoFinal(c.estado_documento)) ? 10_000 : false;
    },
  });

  function irA(nuevaPagina: number, nuevoEstado?: string) {
    const next = new URLSearchParams(params.toString());
    next.set("pagina", String(nuevaPagina));
    if (nuevoEstado !== undefined) {
      if (nuevoEstado) next.set("estado", nuevoEstado);
      else next.delete("estado");
    }
    router.push(`/comprobantes?${next.toString()}`);
  }

  return (
    <div>
      <div className="mb-4 flex items-center gap-3">
        <select
          value={estado ?? ""}
          onChange={(e) => irA(1, e.target.value)}
          className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <option value="">Todos los estados</option>
          {ESTADOS.map((e) => (
            <option key={e} value={e}>
              {e}
            </option>
          ))}
        </select>
      </div>

      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <thead className="bg-muted text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-2 font-medium">Serie - número</th>
              <th className="px-4 py-2 font-medium">Fecha</th>
              <th className="px-4 py-2 font-medium">Total</th>
              <th className="px-4 py-2 font-medium">Estado</th>
              <th className="px-4 py-2 font-medium" />
            </tr>
          </thead>
          <tbody>
            {data.map((c) => (
              <tr key={c.id} className="border-t border-border">
                <td className="px-4 py-2 font-mono">
                  {c.serie}-{String(c.numero).padStart(8, "0")}
                </td>
                <td className="px-4 py-2">{c.fecha_emision}</td>
                <td className="px-4 py-2">
                  {c.moneda} {Number(c.totales.total).toFixed(2)}
                </td>
                <td className="px-4 py-2">
                  <EstadoBadge estado={c.estado_documento} />
                </td>
                <td className="px-4 py-2 text-right">
                  <Link href={`/comprobantes/${c.id}`} className="text-sm text-primary hover:underline">
                    Ver
                  </Link>
                </td>
              </tr>
            ))}
            {data.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                  No hay comprobantes.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="mt-4 flex justify-end gap-2">
        <Button variant="outline" size="sm" disabled={pagina <= 1} onClick={() => irA(pagina - 1)}>
          Anterior
        </Button>
        <Button variant="outline" size="sm" disabled={data.length < 20} onClick={() => irA(pagina + 1)}>
          Siguiente
        </Button>
      </div>
    </div>
  );
}
