import type { ApiEnvelope } from "./types";

export async function postJson<T>(path: string, body: unknown): Promise<ApiEnvelope<T>> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (res.status === 202 || res.status === 204) {
    return { estado: "exito", datos: null, mensaje: null, codigo: null, errores: null };
  }
  return res.json();
}
