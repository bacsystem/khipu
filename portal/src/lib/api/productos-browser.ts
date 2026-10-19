import { ESPERA_MAXIMA_LECTURA_MS } from "./browser";
import type { ApiEnvelope } from "./types";
import type { PaginaDeProductos, ProductoGuardado } from "./productos";

export type ResultadoDeBusquedaDeProductos = { estado: "exito"; datos: PaginaDeProductos } | { estado: "error"; mensaje: string; codigo: string | null };

/**
 * Busca productos guardados (#320). Aparte de `apiRequest` porque el total viaja en la cabecera `X-Total-Count`, que el sobre no expone. No lanza: un corte de red o una respuesta que no es JSON
 * vuelven como error. `senal` cancela la búsqueda (el autocompletado descarta la anterior al teclear otra letra).
 */
export async function buscarProductos({ q, pagina = 1, porPagina = 20, senal }: { q?: string; pagina?: number; porPagina?: number; senal?: AbortSignal }): Promise<ResultadoDeBusquedaDeProductos> {
  const params = new URLSearchParams({ pagina: String(pagina), por_pagina: String(porPagina) });
  if (q && q.trim() !== "") params.set("q", q.trim());
  try {
    const res = await fetch(`/api/proxy/productos?${params}`, { signal: senal ? AbortSignal.any([senal, AbortSignal.timeout(ESPERA_MAXIMA_LECTURA_MS)]) : AbortSignal.timeout(ESPERA_MAXIMA_LECTURA_MS) });
    const cuerpo = (await res.json()) as ApiEnvelope<ProductoGuardado[]>;
    if (cuerpo.estado !== "exito" || !cuerpo.datos) return { estado: "error", mensaje: cuerpo.mensaje ?? "No se pudo leer la lista de productos", codigo: cuerpo.codigo };
    const total = Number(res.headers.get("x-total-count") ?? cuerpo.datos.length);
    return { estado: "exito", datos: { productos: cuerpo.datos, total: Number.isFinite(total) ? total : cuerpo.datos.length } };
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") return { estado: "error", mensaje: "cancelada", codigo: "CANCELADA" };
    return { estado: "error", mensaje: "No se pudo leer la lista de productos. Revisa tu conexión e intenta de nuevo.", codigo: "RED" };
  }
}
