import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ACCESS } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({ aceptarTerminos: vi.fn() }));

import { aceptarTerminos } from "@/lib/api/auth";
import { POST } from "./route";

function postRequest(cookie?: string) {
  return new NextRequest("http://localhost/api/auth/terminos", { method: "POST", headers: cookie ? { cookie } : {} });
}

afterEach(() => vi.mocked(aceptarTerminos).mockReset());

describe("POST /api/auth/terminos (#342)", () => {
  it("acepta con el access de la cookie y devuelve lo guardado", async () => {
    vi.mocked(aceptarTerminos).mockResolvedValue({ version: "2026-10-20", aceptada_en: "2026-10-20T15:00:00Z" });

    const res = await POST(postRequest(`${COOKIE_ACCESS}=jwt-1`));

    expect(res.status).toBe(200);
    expect(aceptarTerminos).toHaveBeenCalledWith("jwt-1");
    expect((await res.json()).datos).toEqual({ version: "2026-10-20", aceptada_en: "2026-10-20T15:00:00Z" });
  });

  it("sin sesión es 401 y no llama al backend", async () => {
    const res = await POST(postRequest());

    expect(res.status).toBe(401);
    expect(aceptarTerminos).not.toHaveBeenCalled();
  });

  it("propaga el rechazo del backend, p. ej. una sesión de soporte", async () => {
    vi.mocked(aceptarTerminos).mockRejectedValue(new ApiError(403, "SOPORTE_SOLO_LECTURA", "solo se puede mirar"));

    const res = await POST(postRequest(`${COOKIE_ACCESS}=jwt-soporte`));

    expect(res.status).toBe(403);
    expect((await res.json()).codigo).toBe("SOPORTE_SOLO_LECTURA");
  });
});
