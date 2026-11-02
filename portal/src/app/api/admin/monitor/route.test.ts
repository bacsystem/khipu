import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-monitor", () => ({ obtenerMonitor: vi.fn() }));

import { obtenerMonitor } from "@/lib/api/admin-monitor";
import { GET } from "./route";

function peticion(init: { sesion?: boolean } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  return new NextRequest("http://localhost/api/admin/monitor", { method: "GET", headers });
}

afterEach(() => vi.mocked(obtenerMonitor).mockReset());

/** BFF del monitor de emisión (#195): el navegador nunca ve el JWT y una lectura vieja nunca se guarda en caché. */
describe("GET /api/admin/monitor", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await GET(peticion({ sesion: false }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(obtenerMonitor).not.toHaveBeenCalled();
  });

  it("pide la lectura con el JWT del administrador y la devuelve sin caché", async () => {
    const lectura = { generado_en: "2026-10-15T15:20:00Z", horas: [], hoy: {}, outbox: { pendientes: 3, vencidos: 0, alerta: false }, sunat: [] };
    vi.mocked(obtenerMonitor).mockResolvedValue(lectura as never);

    const res = await GET(peticion());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(lectura);
    expect(obtenerMonitor).toHaveBeenCalledWith("jwt-admin");
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("propaga el status y el código del backend", async () => {
    vi.mocked(obtenerMonitor).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Token inválido"));

    const res = await GET(peticion());

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
  });

  it("un fallo que no es del backend responde 502", async () => {
    vi.mocked(obtenerMonitor).mockRejectedValue(new Error("caído"));

    expect((await GET(peticion())).status).toBe(502);
  });
});
