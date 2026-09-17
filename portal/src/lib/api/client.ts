import { ApiError, type ApiEnvelope } from "./types";

export function apiBaseUrl(): string {
  return process.env.API_BASE_URL ?? "http://localhost:8080";
}

export type BackendRequestInit = Omit<RequestInit, "body"> & { body?: unknown };

export async function backendFetch<T>(path: string, init: BackendRequestInit = {}): Promise<T> {
  const { datos } = await backendFetchConHeaders<T>(path, init);
  return datos;
}

export async function backendFetchConHeaders<T>(
  path: string,
  init: BackendRequestInit = {},
): Promise<{ datos: T; headers: Headers }> {
  const headers = new Headers(init.headers);
  let body: BodyInit | undefined;
  if (init.body !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.body);
  }

  const res = await fetch(`${apiBaseUrl()}${path}`, { ...init, headers, body, cache: "no-store" });
  const texto = await res.text();
  if (!texto) {
    if (res.ok) return { datos: undefined as T, headers: res.headers };
    throw new ApiError(res.status, null, "Error de comunicación con la API", null);
  }

  const json = (() => {
    try {
      return JSON.parse(texto) as ApiEnvelope<T>;
    } catch {
      return null;
    }
  })();
  if (!res.ok || !json || json.estado === "error") {
    throw new ApiError(
      res.status,
      json?.codigo ?? null,
      json?.mensaje ?? "Error de comunicación con la API",
      json?.errores ?? null,
    );
  }
  return { datos: json.datos as T, headers: res.headers };
}
