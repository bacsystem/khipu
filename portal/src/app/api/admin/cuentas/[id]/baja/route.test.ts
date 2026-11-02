import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-baja", () => ({ darDeBajaCuenta: vi.fn() }));

import { darDeBajaCuenta } from "@/lib/api/admin-baja";
import { POST } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const DE_BAJA = { cuenta_id: ID, baja_en: "2026-10-03T09:00:00Z" };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${ID}/baja`, { method: "POST", headers, body: init.cuerpo });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(darDeBajaCuenta).mockReset();
});

/** BFF de «dar de baja una cuenta» (#201): el navegador nunca ve el JWT del administrador y el id no llega sin validar a la URL del backend. */
describe("POST /api/admin/cuentas/[id]/baja", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(darDeBajaCuenta).not.toHaveBeenCalled();
  });

  /** Lo que llega por la URL no se pega en la del backend: `../` o un espacio no son un id de cuenta. */
  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(darDeBajaCuenta).not.toHaveBeenCalled();
  });

  it("reenvía el motivo con el JWT del administrador y devuelve cómo quedó", async () => {
    vi.mocked(darDeBajaCuenta).mockResolvedValue(DE_BAJA as never);

    const res = await POST(peticion({ cuerpo: JSON.stringify({ motivo: "no pagó septiembre" }) }), contexto());
    const json = await res.json();

    expect(res.status).toBe(200);
    expect(json.datos).toEqual(DE_BAJA);
    expect(darDeBajaCuenta).toHaveBeenCalledWith("jwt-admin", ID, "no pagó septiembre", {});
  });

  it("sin cuerpo o con un motivo en blanco da de baja sin motivo", async () => {
    vi.mocked(darDeBajaCuenta).mockResolvedValue(DE_BAJA as never);

    await POST(peticion(), contexto());
    await POST(peticion({ cuerpo: JSON.stringify({ motivo: "   " }) }), contexto());
    await POST(peticion({ cuerpo: JSON.stringify({}) }), contexto());

    expect(vi.mocked(darDeBajaCuenta).mock.calls.map((c) => c[2])).toEqual([undefined, undefined, undefined]);
  });

  it("un motivo que no es un texto se rechaza con 422 sin llamar al backend", async () => {
    const res = await POST(peticion({ cuerpo: JSON.stringify({ motivo: 42 }) }), contexto());

    expect(res.status).toBe(422);
    expect((await res.json()).codigo).toBe("MOTIVO_INVALIDO");
    expect(darDeBajaCuenta).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es JSON responde 400 sin llamar al backend", async () => {
    const res = await POST(peticion({ cuerpo: "{no es json" }), contexto());

    expect(res.status).toBe(400);
    expect((await res.json()).codigo).toBe("JSON_INVALIDO");
    expect(darDeBajaCuenta).not.toHaveBeenCalled();
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(darDeBajaCuenta).mockResolvedValue(DE_BAJA as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(darDeBajaCuenta).toHaveBeenCalledWith("jwt-admin", ID, undefined, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. una cuenta ya dada de baja)", async () => {
    vi.mocked(darDeBajaCuenta).mockRejectedValue(new ApiError(409, "CUENTA_YA_DE_BAJA", "La cuenta ya está dada de baja"));

    const res = await POST(peticion(), contexto());
    const json = await res.json();

    expect(res.status).toBe(409);
    expect(json.codigo).toBe("CUENTA_YA_DE_BAJA");
  });

  it("la respuesta no debe quedar en ninguna caché", async () => {
    vi.mocked(darDeBajaCuenta).mockResolvedValue(DE_BAJA as never);

    const res = await POST(peticion(), contexto());

    expect(res.headers.get("cache-control")).toContain("no-store");
  });
});
