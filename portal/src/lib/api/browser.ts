import type { ApiEnvelope } from "./types";

async function toEnvelope<T>(res: Response): Promise<ApiEnvelope<T>> {
  const texto = await res.text();
  if (!texto) {
    return res.ok
      ? { estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }
      : { estado: "error", datos: null, mensaje: "Error de comunicación con la API", codigo: null, errores: null };
  }
  // Un 502/504 de un proxy intermedio llega como HTML: `JSON.parse` lanzaba y el llamador quedaba colgado (medido en la
  // auditoría de bajas: diálogo «Enviando a SUNAT…» sin salida). Se devuelve un sobre de error con el HTTP.
  try {
    return JSON.parse(texto);
  } catch {
    return { estado: "error", datos: null, mensaje: `Respuesta inválida del servidor (HTTP ${res.status}). Recargá la página para ver el estado real.`, codigo: null, errores: null };
  }
}

export async function postJson<T>(path: string, body: unknown): Promise<ApiEnvelope<T>> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  return toEnvelope<T>(res);
}

export async function apiRequest<T>(
  path: string,
  init: { method: string; body?: unknown },
): Promise<ApiEnvelope<T>> {
  const isFormData = init.body instanceof FormData;
  const res = await fetch(path, {
    method: init.method,
    headers: isFormData || init.body === undefined ? undefined : { "content-type": "application/json" },
    body: isFormData ? (init.body as FormData) : init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  return toEnvelope<T>(res);
}
