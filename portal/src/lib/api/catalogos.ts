import { apiBaseUrl } from "./client";
import type { ApiEnvelope } from "./types";

/** Catálogo oficial SUNAT tal como lo entrega `GET /v1/catalogos/{id}` (público, sin credenciales). */
export type CatalogoSunat = {
  id: string;
  nombre: string;
  columnas: string[];
  entradas: Array<{ codigo: string; descripcion: string; extra: Record<string, string> }>;
};

export type CatalogoResumen = { id: string; nombre: string; entradas: number };

async function publico<T>(path: string): Promise<T> {
  const res = await fetch(`${apiBaseUrl()}${path}`, { next: { revalidate: 3600 } });
  const json = (await res.json()) as ApiEnvelope<T>;
  if (!res.ok || json.estado !== "exito") throw new Error(`No se pudo leer ${path}: ${json.codigo ?? res.status}`);
  return json.datos as T;
}

export function listarCatalogos() { return publico<CatalogoResumen[]>("/v1/catalogos"); }
export function obtenerCatalogo(id: string) { return publico<CatalogoSunat>(`/v1/catalogos/${id}`); }
