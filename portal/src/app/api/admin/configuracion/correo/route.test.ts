import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-configuracion", async (original) => ({ ...(await original<object>()), cambiarRemitente: vi.fn(), restablecerRemitente: vi.fn() }));

import { cambiarRemitente, restablecerRemitente } from "@/lib/api/admin-configuracion";
import { DELETE, PUT } from "./route";

const REMITENTE = { vigente: { email: "avisos@khipu.pe" }, personalizado: true, predeterminado: { email: "no-responder@khipu.pe" } };

function peticion(metodo: "PUT" | "DELETE", init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest("http://localhost/api/admin/configuracion/correo", { method: metodo, headers, body: metodo === "PUT" ? (init.cuerpo ?? JSON.stringify({ email: "avisos@khipu.pe" })) : undefined });
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(cambiarRemitente).mockReset();
  vi.mocked(restablecerRemitente).mockReset();
});

/** BFF del remitente (#199): afecta a todos los correos de la plataforma, así que el JWT nunca llega al navegador, nada llega al backend sin sesión y todo queda con la IP real. */
describe("PUT /api/admin/configuracion/correo", () => {
  it("sin sesión de administrador responde 401, aunque el cuerpo esté roto, y no llama al backend", async () => {
    const res = await PUT(peticion("PUT", { sesion: false, cuerpo: "{no es json" }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(cambiarRemitente).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", "", "null"]) {
      const res = await PUT(peticion("PUT", { cuerpo: malo }));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(cambiarRemitente).not.toHaveBeenCalled();
  });

  it("reenvía el cuerpo tal cual con el JWT del administrador y devuelve el remitente, sin caché", async () => {
    vi.mocked(cambiarRemitente).mockResolvedValue(REMITENTE as never);

    const res = await PUT(peticion("PUT", { cuerpo: JSON.stringify({ nombre: "khipu", email: "avisos@khipu.pe", responder_a: "x@y.pe", otro: 1 }) }));

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(REMITENTE);
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(vi.mocked(cambiarRemitente).mock.calls[0][0]).toBe("jwt-admin");
    expect(vi.mocked(cambiarRemitente).mock.calls[0][1]).toEqual({ nombre: "khipu", email: "avisos@khipu.pe", responder_a: "x@y.pe", otro: 1 });
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(cambiarRemitente).mockResolvedValue(REMITENTE as never);

    await PUT(peticion("PUT", { xff: "6.6.6.6, 203.0.113.7" }));

    expect(vi.mocked(cambiarRemitente).mock.calls[0][2]).toEqual({ "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status, el código y el mensaje del backend", async () => {
    vi.mocked(cambiarRemitente).mockRejectedValue(new ApiError(422, "REMITENTE_INVALIDO", "El correo del remitente es obligatorio"));

    const res = await PUT(peticion("PUT"));

    expect(res.status).toBe(422);
    const cuerpo = await res.json();
    expect(cuerpo.codigo).toBe("REMITENTE_INVALIDO");
    expect(cuerpo.mensaje).toBe("El correo del remitente es obligatorio");
  });

  it("un fallo que no es del backend responde 502", async () => {
    vi.mocked(cambiarRemitente).mockRejectedValue(new Error("caído"));

    expect((await PUT(peticion("PUT"))).status).toBe(502);
  });
});

describe("DELETE /api/admin/configuracion/correo", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await DELETE(peticion("DELETE", { sesion: false }));

    expect(res.status).toBe(401);
    expect(restablecerRemitente).not.toHaveBeenCalled();
  });

  it("restablece con el JWT del administrador y su IP, y devuelve el remitente, sin caché", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(restablecerRemitente).mockResolvedValue({ ...REMITENTE, personalizado: false } as never);

    const res = await DELETE(peticion("DELETE", { xff: "6.6.6.6, 203.0.113.7" }));

    expect(res.status).toBe(200);
    expect((await res.json()).datos.personalizado).toBe(false);
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(restablecerRemitente).toHaveBeenCalledWith("jwt-admin", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el error del backend", async () => {
    vi.mocked(restablecerRemitente).mockRejectedValue(new ApiError(503, "CORREO_NO_CONFIGURADO", "x"));

    const res = await DELETE(peticion("DELETE"));

    expect(res.status).toBe(503);
    expect((await res.json()).codigo).toBe("CORREO_NO_CONFIGURADO");
  });
});
