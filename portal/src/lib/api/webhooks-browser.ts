import { ESPERA_MAXIMA_LECTURA_MS } from "./browser";
import type { EntregaWebhook, EstadoEntrega } from "./webhooks";
import type { ApiEnvelope } from "./types";

export type PaginaDeEntregas = { entregas: EntregaWebhook[]; total: number };

/**
 * El log de entregas de un endpoint (#19). Aparte de `apiRequest` porque el total de resultados viaja en la cabecera `X-Total-Count`, que el sobre no expone. Como el
 * resto del cliente del navegador, no lanza: un corte de red o una respuesta que no es JSON vuelven como error.
 */
export async function listarEntregas(
  webhookId: string,
  { estado, pagina, porPagina }: { estado?: EstadoEntrega; pagina: number; porPagina: number },
): Promise<{ estado: "exito"; datos: PaginaDeEntregas } | { estado: "error"; mensaje: string; codigo: string | null }> {
  const params = new URLSearchParams({ pagina: String(pagina), por_pagina: String(porPagina) });
  if (estado) params.set("estado", estado);
  try {
    const res = await fetch(`/api/proxy/webhooks/${webhookId}/entregas?${params}`, { signal: AbortSignal.timeout(ESPERA_MAXIMA_LECTURA_MS) });
    const cuerpo = (await res.json()) as ApiEnvelope<EntregaWebhook[]>;
    if (cuerpo.estado !== "exito" || !cuerpo.datos) return { estado: "error", mensaje: cuerpo.mensaje ?? "No se pudo leer el log de entregas", codigo: cuerpo.codigo };
    const total = Number(res.headers.get("x-total-count") ?? cuerpo.datos.length);
    return { estado: "exito", datos: { entregas: cuerpo.datos, total: Number.isFinite(total) ? total : cuerpo.datos.length } };
  } catch {
    return { estado: "error", mensaje: "No se pudo leer el log de entregas. Revisa tu conexión e intenta de nuevo.", codigo: "RED" };
  }
}
