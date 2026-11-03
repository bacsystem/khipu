import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-consulta-ruc", () => ({
  consultarRucComoAdmin: vi.fn(),
}));

import { consultarRucComoAdmin } from "@/lib/api/admin-consulta-ruc";
import { GET } from "./route";

const CONTRIBUYENTE = {
  ruc: "20100066603", razon_social: "TIENDAS DEL SUR S.A.C.", estado: "ACTIVO", condicion: "HABIDO", activo: true, habido: true,
  actualizado_al: "2026-10-16T09:30:00Z",
};

function peticion(ruc: string, sesion = true) {
  const headers: Record<string, string> = sesion ? { cookie: `${COOKIE_ADMIN_ACCESS}=jwt-admin` } : {};
  return [new NextRequest(`http://localhost/api/admin/consultas/ruc/${ruc}`, { headers }), { params: Promise.resolve({ ruc }) }] as const;
}

afterEach(() => vi.mocked(consultarRucComoAdmin).mockReset());

/** #575: el alta asistida consulta el padrón con la sesión del administrador. */
describe("GET /api/admin/consultas/ruc/[ruc]", () => {
  it("sin sesión de administrador responde 401 y no pregunta al backend", async () => {
    const res = await GET(...peticion("20100066603", false));

    expect(res.status).toBe(401);
    expect(consultarRucComoAdmin).not.toHaveBeenCalled();
  });

  it("devuelve el contribuyente con el JWT del administrador y sin caché", async () => {
    vi.mocked(consultarRucComoAdmin).mockResolvedValue(CONTRIBUYENTE);

    const res = await GET(...peticion("20100066603"));

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(CONTRIBUYENTE);
    expect(res.headers.get("cache-control")).toBe("no-store");
    expect(consultarRucComoAdmin).toHaveBeenCalledWith("jwt-admin", "20100066603");
  });

  it("lo que no son 11 dígitos no llega a la URL del backend", async () => {
    for (const malo of ["2010006660", "..%2F..%2Fcuentas", "20100066603x"]) {
      const res = await GET(...peticion(malo));
      expect(res.status, malo).toBe(400);
    }
    expect(consultarRucComoAdmin).not.toHaveBeenCalled();
  });

  it("propaga el status y el código del backend (no figura, cupo, padrón sin cargar)", async () => {
    for (const [status, codigo] of [[404, "NO_ENCONTRADO"], [429, "DEMASIADAS_CONSULTAS"], [503, "PADRON_NO_CARGADO"]] as const) {
      vi.mocked(consultarRucComoAdmin).mockRejectedValueOnce(new ApiError(status, codigo, "x"));
      const res = await GET(...peticion("20100066603"));
      expect(res.status).toBe(status);
      expect((await res.json()).codigo).toBe(codigo);
    }
  });
});
