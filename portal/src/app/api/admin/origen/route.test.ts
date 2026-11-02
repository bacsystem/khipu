import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-origen", () => ({
  origenDelBackend: vi.fn(),
}));

import { origenDelBackend } from "@/lib/api/admin-origen";
import { GET } from "./route";

function peticion(init: { sesion?: boolean; xff?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest("http://localhost/api/admin/origen", { headers });
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(origenDelBackend).mockReset();
});

/**
 * Herramienta de calibración de #208: pone lado a lado lo que llegó al portal, lo que el portal resolvió y lo que el backend
 * registraría, para fijar `TRUSTED_PROXY_HOPS` y `TRUSTED_PROXIES` mirando la cadena real en vez de adivinarla.
 */
describe("GET /api/admin/origen", () => {
  it("sin sesión de administrador responde 401 y no pregunta al backend", async () => {
    const res = await GET(peticion({ sesion: false }));

    expect(res.status).toBe(401);
    expect(origenDelBackend).not.toHaveBeenCalled();
  });

  it("muestra la cadena recibida, los saltos, la IP resuelta y la que ve el backend", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(origenDelBackend).mockResolvedValue({ ip: "203.0.113.7" });

    const res = await GET(peticion({ xff: "6.6.6.6, 203.0.113.7" }));
    const json = await res.json();

    expect(res.status).toBe(200);
    expect(json.datos).toEqual({
      cadena_recibida: "6.6.6.6, 203.0.113.7",
      saltos: 1,
      ip_resuelta: "203.0.113.7",
      ip_backend: "203.0.113.7",
    });
    // Al backend sale la IP ya resuelta, no la cadena: es lo que permite comparar `ip_backend` con `ip_resuelta`.
    expect(origenDelBackend).toHaveBeenCalledWith("jwt-admin", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("sin saltos de confianza no resuelve ninguna IP, no manda ninguna al backend, y lo dice", async () => {
    vi.mocked(origenDelBackend).mockResolvedValue({ ip: "172.18.0.4" });

    const json = await (await GET(peticion({ xff: "6.6.6.6" }))).json();

    expect(json.datos.saltos).toBe(0);
    expect(json.datos.ip_resuelta).toBeNull();
    expect(json.datos.ip_backend).toBe("172.18.0.4");
    expect(origenDelBackend).toHaveBeenCalledWith("jwt-admin", {});
  });

  it("sin cabecera de proxy la cadena recibida es null", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(origenDelBackend).mockResolvedValue({ ip: "172.18.0.4" });

    const json = await (await GET(peticion())).json();

    expect(json.datos.cadena_recibida).toBeNull();
    expect(json.datos.ip_resuelta).toBeNull();
  });

  it("propaga el status y código de ApiError del backend (p. ej. el JWT venció)", async () => {
    vi.mocked(origenDelBackend).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Sesión vencida"));

    const res = await GET(peticion());

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
  });
});
