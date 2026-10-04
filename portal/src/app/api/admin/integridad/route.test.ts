import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-integridad", () => ({ verificarIntegridad: vi.fn() }));

import { verificarIntegridad } from "@/lib/api/admin-integridad";
import { POST } from "./route";

const CUERPO = { desde: "2026-09-01", hasta: "2026-09-30" };

function peticion(init: { sesion?: boolean; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  return new NextRequest("http://localhost/api/admin/integridad", { method: "POST", headers, body: init.cuerpo ?? JSON.stringify(CUERPO) });
}

afterEach(() => vi.mocked(verificarIntegridad).mockReset());

/** BFF de la verificación de integridad (#198): el navegador nunca ve el JWT y las fechas no llegan sin validar a la URL del backend. */
describe("POST /api/admin/integridad", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(verificarIntegridad).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", ""]) {
      const res = await POST(peticion({ cuerpo: malo }));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(verificarIntegridad).not.toHaveBeenCalled();
  });

  it("sin fechas, o con fechas que no son texto, responde 400 sin llamar al backend", async () => {
    for (const malo of [{}, { desde: "2026-09-01" }, { hasta: "2026-09-30" }, { desde: 20260901, hasta: 20260930 }, { desde: null, hasta: null }, { desde: ["2026-09-01"], hasta: "2026-09-30" }]) {
      const res = await POST(peticion({ cuerpo: JSON.stringify(malo) }));
      expect(res.status, JSON.stringify(malo)).toBe(400);
      expect((await res.json()).codigo).toBe("PARAMETRO_INVALIDO");
    }
    expect(verificarIntegridad).not.toHaveBeenCalled();
  });

  it("fechas que no existen, al revés o un rango demasiado largo responden 400 con el motivo y sin llamar al backend", async () => {
    const casos: Array<[Record<string, string>, string]> = [
      [{ desde: "2026-02-30", hasta: "2026-03-01" }, "Esa fecha no existe."],
      [{ desde: "2026-09-30", hasta: "2026-09-01" }, "El rango no puede terminar antes de empezar."],
      [{ desde: "2026-01-01", hasta: "2026-12-31" }, "El rango no puede pasar de 92 días."],
      [{ desde: "", hasta: "" }, "Indica desde cuándo verificar. Indica hasta cuándo verificar."],
    ];
    for (const [cuerpo, mensaje] of casos) {
      const res = await POST(peticion({ cuerpo: JSON.stringify(cuerpo) }));
      expect(res.status, JSON.stringify(cuerpo)).toBe(400);
      const json = await res.json();
      expect(json.codigo).toBe("PARAMETRO_INVALIDO");
      expect(json.mensaje).toBe(mensaje);
    }
    expect(verificarIntegridad).not.toHaveBeenCalled();
  });

  /** Lo que llega a la URL del backend son las fechas validadas y recortadas, nunca el texto crudo. */
  it("una fecha con un parámetro colado no llega a la URL del backend", async () => {
    const res = await POST(peticion({ cuerpo: JSON.stringify({ desde: "2026-09-01&x=1", hasta: "2026-09-30" }) }));

    expect(res.status).toBe(400);
    expect(verificarIntegridad).not.toHaveBeenCalled();
  });

  it("pide la verificación con el JWT y las fechas recortadas, y devuelve el informe sin caché", async () => {
    vi.mocked(verificarIntegridad).mockResolvedValue({ desde: "2026-09-01", hasta: "2026-09-30", verificados: 12, problemas: [] });

    const res = await POST(peticion({ cuerpo: JSON.stringify({ desde: " 2026-09-01 ", hasta: "2026-09-30" }) }));

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual({ desde: "2026-09-01", hasta: "2026-09-30", verificados: 12, problemas: [] });
    expect(verificarIntegridad).toHaveBeenCalledWith("jwt-admin", "2026-09-01", "2026-09-30");
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("propaga el status y el código del backend", async () => {
    vi.mocked(verificarIntegridad).mockRejectedValue(new ApiError(400, "RANGO_INVALIDO", "El rango de fechas es obligatorio"));

    const res = await POST(peticion());

    expect(res.status).toBe(400);
    expect((await res.json()).codigo).toBe("RANGO_INVALIDO");
  });

  it("un fallo que no es del backend responde 502", async () => {
    vi.mocked(verificarIntegridad).mockRejectedValue(new Error("caído"));

    expect((await POST(peticion())).status).toBe(502);
  });
});
