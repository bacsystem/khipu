import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-configuracion", async (original) => ({ ...(await original<object>()), publicarBanner: vi.fn(), retirarBanner: vi.fn() }));

import { publicarBanner, retirarBanner } from "@/lib/api/admin-configuracion";
import { DELETE, PUT } from "./route";

const BANNER = { texto: "Mantenimiento", desde: "2026-10-15T20:00:00Z", hasta: "2026-10-16T01:00:00Z", actualizado_en: "2026-10-15T15:00:00Z", vigente_ahora: false };

function peticion(metodo: "PUT" | "DELETE", init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest("http://localhost/api/admin/configuracion/banner", { method: metodo, headers, body: metodo === "PUT" ? (init.cuerpo ?? JSON.stringify({ texto: "Mantenimiento", desde: "a", hasta: "b" })) : undefined });
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(publicarBanner).mockReset();
  vi.mocked(retirarBanner).mockReset();
});

/** BFF del aviso de mantenimiento (#199): lo ven todos los clientes, así que solo un administrador con sesión lo cambia, y todo queda con su IP real. */
describe("PUT /api/admin/configuracion/banner", () => {
  it("sin sesión de administrador responde 401, aunque el cuerpo esté roto, y no llama al backend", async () => {
    const res = await PUT(peticion("PUT", { sesion: false, cuerpo: "{no es json" }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(publicarBanner).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", ""]) {
      const res = await PUT(peticion("PUT", { cuerpo: malo }));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(publicarBanner).not.toHaveBeenCalled();
  });

  it("publica con el cuerpo tal cual y el JWT del administrador, y devuelve el aviso, sin caché", async () => {
    vi.mocked(publicarBanner).mockResolvedValue(BANNER);

    const res = await PUT(peticion("PUT", { cuerpo: JSON.stringify({ texto: "Mantenimiento", desde: "2026-10-15T20:00:00Z", hasta: "2026-10-16T01:00:00Z" }) }));

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(BANNER);
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(vi.mocked(publicarBanner).mock.calls[0].slice(0, 2)).toEqual(["jwt-admin", { texto: "Mantenimiento", desde: "2026-10-15T20:00:00Z", hasta: "2026-10-16T01:00:00Z" }]);
  });

  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(publicarBanner).mockResolvedValue(BANNER);

    await PUT(peticion("PUT", { xff: "6.6.6.6, 203.0.113.7" }));

    expect(vi.mocked(publicarBanner).mock.calls[0][2]).toEqual({ "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status, el código y el mensaje del backend", async () => {
    vi.mocked(publicarBanner).mockRejectedValue(new ApiError(422, "BANNER_INVALIDO", "Un aviso puede durar hasta 90 días"));

    const res = await PUT(peticion("PUT"));

    expect(res.status).toBe(422);
    const cuerpo = await res.json();
    expect(cuerpo.codigo).toBe("BANNER_INVALIDO");
    expect(cuerpo.mensaje).toBe("Un aviso puede durar hasta 90 días");
  });
});

describe("DELETE /api/admin/configuracion/banner", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await DELETE(peticion("DELETE", { sesion: false }));

    expect(res.status).toBe(401);
    expect(retirarBanner).not.toHaveBeenCalled();
  });

  it("retira con el JWT y la IP del administrador y responde éxito con datos nulos, sin caché", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(retirarBanner).mockResolvedValue(undefined);

    const res = await DELETE(peticion("DELETE", { xff: "6.6.6.6, 203.0.113.7" }));

    expect(res.status).toBe(200);
    const cuerpo = await res.json();
    expect(cuerpo.estado).toBe("exito");
    expect(cuerpo.datos).toBeNull();
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(retirarBanner).toHaveBeenCalledWith("jwt-admin", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("si no había aviso, el backend dice 404 y se propaga", async () => {
    vi.mocked(retirarBanner).mockRejectedValue(new ApiError(404, "NO_ENCONTRADO", "No hay un aviso publicado"));

    const res = await DELETE(peticion("DELETE"));

    expect(res.status).toBe(404);
    expect((await res.json()).codigo).toBe("NO_ENCONTRADO");
  });
});
