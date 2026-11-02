import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-pagos", () => ({ registrarPago: vi.fn() }));

import { registrarPago } from "@/lib/api/admin-pagos";
import { POST } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const CUERPO = { periodo_desde: "2026-10-01", periodo_hasta: "2026-10-31", monto: 29, medio: "YAPE", fecha_de_pago: "2026-10-14", referencia: "OP-1", extender_vencimiento: true };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${ID}/pagos`, { method: "POST", headers, body: init.cuerpo ?? JSON.stringify(CUERPO) });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(registrarPago).mockReset();
});

/** BFF de «registrar un pago» (#194): el navegador nunca ve el JWT del administrador y el id no llega sin validar a la URL del backend. */
describe("POST /api/admin/cuentas/[id]/pagos", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(registrarPago).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("ID_INVALIDO");
    }
    expect(registrarPago).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", ""]) {
      const res = await POST(peticion({ cuerpo: malo }), contexto());
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(registrarPago).not.toHaveBeenCalled();
  });

  it("reenvía el cuerpo tal cual con el JWT del administrador y devuelve el pago con 201 y sin caché", async () => {
    vi.mocked(registrarPago).mockResolvedValue({ id: "p1" } as never);

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(201);
    expect((await res.json()).datos).toEqual({ id: "p1" });
    expect(registrarPago).toHaveBeenCalledWith("jwt-admin", ID, CUERPO, {});
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("manda al backend la IP de confianza ya resuelta, no la que dice el cliente", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(registrarPago).mockResolvedValue({} as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(registrarPago).toHaveBeenCalledWith("jwt-admin", ID, CUERPO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. un pago repetido)", async () => {
    vi.mocked(registrarPago).mockRejectedValue(new ApiError(409, "PAGO_DUPLICADO", "Esa cuenta ya tiene un pago con esa referencia"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("PAGO_DUPLICADO");
  });

  it("un fallo que no es del backend responde 502", async () => {
    vi.mocked(registrarPago).mockRejectedValue(new Error("caído"));

    expect((await POST(peticion(), contexto())).status).toBe(502);
  });
});
