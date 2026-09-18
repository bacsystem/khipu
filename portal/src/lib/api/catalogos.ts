import { apiBaseUrl } from "./client";
import type { ApiEnvelope } from "./types";

/** Catálogo oficial SUNAT tal como lo entrega `GET /v1/catalogos/{id}` (público, sin credenciales). */
export type CatalogoSunat = {
  id: string;
  nombre: string;
  columnas: string[];
  entradas: Array<{ codigo: string; descripcion: string; extra: Record<string, string> }>;
};

async function publico<T>(path: string): Promise<T> {
  const res = await fetch(`${apiBaseUrl()}${path}`, { next: { revalidate: 3600 } });
  // Un proxy caído responde HTML: comprobar el estado antes de parsear para que el error diga el HTTP y no "Unexpected token <".
  if (!res.ok) throw new Error(`No se pudo leer ${path}: HTTP ${res.status}`);
  const json = (await res.json()) as ApiEnvelope<T>;
  if (json.estado !== "exito") throw new Error(`No se pudo leer ${path}: ${json.codigo ?? "respuesta inválida"}`);
  return json.datos as T;
}

/** Todos los catálogos con sus entradas en una sola llamada (`completo=true`). */
export function listarCatalogosCompletos() { return publico<CatalogoSunat[]>("/v1/catalogos?completo=true"); }
