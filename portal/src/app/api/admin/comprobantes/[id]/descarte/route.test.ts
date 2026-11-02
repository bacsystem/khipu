import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-errores", () => ({ descartarComprobante: vi.fn() }));

import { descartarComprobante } from "@/lib/api/admin-errores";
import { POST } from "./route";

const ID = "00000000-0000-4000-d000-000000000105";
const DESCARTADO = { comprobante_id: ID, estado: "DESCARTADO" };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/comprobantes/${ID}/descarte`, { method: "POST", headers, body: init.cuerpo ?? JSON.stringify({ motivo: "El cliente lo reemitió" }) });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(descartarComprobante).mockReset();
});

/** BFF de «descartar un comprobante» (#196): irreversible, así que el JWT nunca llega al navegador, el id y el motivo no llegan sin mirar al backend y todo queda con la IP real. */
describe("POST /api/admin/comprobantes/[id]/descarte", () => {
  it("sin sesión de administrador responde 401, aunque el cuerpo esté roto, y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false, cuerpo: "{no es json" }), contexto());

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(descartarComprobante).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(descartarComprobante).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", ""]) {
      const res = await POST(peticion({ cuerpo: malo }), contexto());
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(descartarComprobante).not.toHaveBeenCalled();
  });

  it("un motivo que no es un texto se rechaza con 422 sin llamar al backend", async () => {
    for (const malo of [42, true, ["x"], { a: 1 }]) {
      const res = await POST(peticion({ cuerpo: JSON.stringify({ motivo: malo }) }), contexto());
      expect(res.status, JSON.stringify(malo)).toBe(422);
      expect((await res.json()).codigo).toBe("MOTIVO_INVALIDO");
    }
    expect(descartarComprobante).not.toHaveBeenCalled();
  });

  it("reenvía el motivo con el JWT del administrador y devuelve el nuevo estado, sin caché", async () => {
    vi.mocked(descartarComprobante).mockResolvedValue(DESCARTADO as never);

    const res = await POST(peticion({ cuerpo: JSON.stringify({ motivo: "El cliente lo reemitió con otra serie" }) }), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(DESCARTADO);
    expect(descartarComprobante).toHaveBeenCalledWith("jwt-admin", ID, "El cliente lo reemitió con otra serie", {});
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  /** Si el motivo falta, el backend dice por qué (una sola regla, la suya): acá no se inventa uno ni se rechaza por él. */
  it("sin motivo, o con uno nulo, se lo pasa vacío al backend para que lo rechace él", async () => {
    vi.mocked(descartarComprobante).mockRejectedValue(new ApiError(422, "MOTIVO_REQUERIDO", "Indica por qué se descarta el comprobante"));

    const sin = await POST(peticion({ cuerpo: JSON.stringify({}) }), contexto());
    const nulo = await POST(peticion({ cuerpo: JSON.stringify({ motivo: null }) }), contexto());

    expect(sin.status).toBe(422);
    expect((await nulo.json()).codigo).toBe("MOTIVO_REQUERIDO");
    expect(vi.mocked(descartarComprobante).mock.calls.map((c) => c[2])).toEqual(["", ""]);
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(descartarComprobante).mockResolvedValue(DESCARTADO as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(descartarComprobante).toHaveBeenCalledWith("jwt-admin", ID, "El cliente lo reemitió", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (un estado que no se descarta, o un conflicto)", async () => {
    vi.mocked(descartarComprobante).mockRejectedValue(new ApiError(409, "ESTADO_NO_DESCARTABLE", "Solo se descarta un comprobante en error de envío"));
    const res = await POST(peticion(), contexto());
    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("ESTADO_NO_DESCARTABLE");

    vi.mocked(descartarComprobante).mockRejectedValue(new ApiError(409, "ESTADO_CONFLICTO", "El comprobante cambió de estado"));
    expect((await (await POST(peticion(), contexto())).json()).codigo).toBe("ESTADO_CONFLICTO");
  });

  it("un fallo que no es del backend responde 502", async () => {
    vi.mocked(descartarComprobante).mockRejectedValue(new Error("caído"));

    expect((await POST(peticion(), contexto())).status).toBe(502);
  });
});
