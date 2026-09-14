import { ApiError, type ApiEnvelope } from "./types";

export function apiBaseUrl(): string {
  return process.env.API_BASE_URL ?? "http://localhost:8080";
}

export type BackendRequestInit = Omit<RequestInit, "body"> & { body?: unknown };

export async function backendFetch<T>(path: string, init: BackendRequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  let body: BodyInit | undefined;
  if (init.body !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.body);
  }

  const res = await fetch(`${apiBaseUrl()}${path}`, { ...init, headers, body, cache: "no-store" });
  if (res.status === 204) return undefined as T;

  const json = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!res.ok || !json || json.estado === "error") {
    throw new ApiError(
      res.status,
      json?.codigo ?? null,
      json?.mensaje ?? "Error de comunicación con la API",
      json?.errores ?? null,
    );
  }
  return json.datos as T;
}
