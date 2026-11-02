import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-avisos", () => ({ avisarAlCliente: vi.fn() }));

import { avisarAlCliente } from "@/lib/api/admin-avisos";
import { POST } from "./route";

const ID = "00000000-0000-4000-9000-000000000001";
const ENVIADO = { empresa_id: ID, motivo: "CERTIFICADO_POR_VENCER", destinatario: "ana@negocio.pe", enviado_en: "2026-10-15T15:00:00Z", avisar_desde: "2026-10-22T15:00:00Z" };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/empresas/${ID}/avisos`, { method: "POST", headers, body: init.cuerpo ?? JSON.stringify({ tipo: "CERTIFICADO" }) });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(avisarAlCliente).mockReset();
});

/** BFF de «avisarle a un cliente» (#197): manda un correo de verdad, así que el JWT nunca llega al navegador, el id y el tipo no llegan sin mirar al backend y todo queda con la IP real. */
describe("POST /api/admin/empresas/[id]/avisos", () => {
  it("sin sesión de administrador responde 401, aunque el cuerpo esté roto, y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false, cuerpo: "{no es json" }), contexto());

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(avisarAlCliente).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(avisarAlCliente).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", ""]) {
      const res = await POST(peticion({ cuerpo: malo }), contexto());
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(avisarAlCliente).not.toHaveBeenCalled();
  });

  it("sin tipo, o con uno que no existe o que no es texto, responde 422 sin llamar al backend", async () => {
    for (const malo of [{}, { tipo: null }, { tipo: "OTRO" }, { tipo: "certificado" }, { tipo: 1 }, { tipo: ["CERTIFICADO"] }, { tipo: "" }]) {
      const res = await POST(peticion({ cuerpo: JSON.stringify(malo) }), contexto());
      expect(res.status, JSON.stringify(malo)).toBe(422);
      expect((await res.json()).codigo).toBe("TIPO_INVALIDO");
    }
    expect(avisarAlCliente).not.toHaveBeenCalled();
  });

  it("avisa del certificado o de las credenciales con el JWT del administrador y devuelve el aviso, sin caché", async () => {
    vi.mocked(avisarAlCliente).mockResolvedValue(ENVIADO as never);

    const a = await POST(peticion({ cuerpo: JSON.stringify({ tipo: "CERTIFICADO" }) }), contexto());
    await POST(peticion({ cuerpo: JSON.stringify({ tipo: "CREDENCIALES_SOL" }) }), contexto());

    expect(a.status).toBe(200);
    expect((await a.json()).datos).toEqual(ENVIADO);
    expect(a.headers.get("cache-control")).toContain("no-store");
    expect(vi.mocked(avisarAlCliente).mock.calls.map((c) => [c[0], c[1], c[2]])).toEqual([
      ["jwt-admin", ID, "CERTIFICADO"],
      ["jwt-admin", ID, "CREDENCIALES_SOL"],
    ]);
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(avisarAlCliente).mockResolvedValue(ENVIADO as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(avisarAlCliente).toHaveBeenCalledWith("jwt-admin", ID, "CERTIFICADO", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (nada que avisar, sin cuenta, ya avisado, correo caído)", async () => {
    for (const [status, codigo] of [[409, "AVISO_SIN_MOTIVO"], [409, "EMPRESA_SIN_CUENTA"], [409, "AVISO_RECIENTE"], [503, "CORREO_NO_CONFIGURADO"], [502, "CORREO_NO_ENVIADO"], [404, "NO_ENCONTRADO"]] as const) {
      vi.mocked(avisarAlCliente).mockRejectedValue(new ApiError(status, codigo, "x"));
      const res = await POST(peticion(), contexto());
      expect(res.status, codigo).toBe(status);
      expect((await res.json()).codigo).toBe(codigo);
    }
  });

  it("un fallo que no es del backend responde 502", async () => {
    vi.mocked(avisarAlCliente).mockRejectedValue(new Error("caído"));

    expect((await POST(peticion(), contexto())).status).toBe(502);
  });
});
