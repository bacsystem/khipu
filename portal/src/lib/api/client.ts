import { ApiError, CODIGO_COMUNICACION, type ApiEnvelope } from "./types";

/** URL con la que el servidor Next (BFF, Server Components) habla con la API; puede ser interna (Docker, red privada). */
export function apiBaseUrl(): string {
  return process.env.API_BASE_URL ?? "http://localhost:8001";
}

/**
 * URL de la API que se muestra al usuario (snippets de integración, "Try it" de la referencia):
 * la que alcanza desde su navegador, no la interna del BFF. Sin `API_PUBLIC_URL` se asume que coinciden.
 */
export function apiPublicUrl(): string {
  return process.env.API_PUBLIC_URL ?? apiBaseUrl();
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
    throw new ApiError(res.status, CODIGO_COMUNICACION, "API communication error", null);
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
      json?.codigo ?? CODIGO_COMUNICACION,
      json?.mensaje ?? "API communication error",
      json?.errores ?? null,
    );
  }
  return { datos: json.datos as T, headers: res.headers };
}
