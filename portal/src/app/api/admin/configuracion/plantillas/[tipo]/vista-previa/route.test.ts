import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-configuracion", async (original) => ({ ...(await original<object>()), vistaPreviaDePlantilla: vi.fn() }));

import { vistaPreviaDePlantilla } from "@/lib/api/admin-configuracion";
import { POST } from "./route";

function peticion(init: { sesion?: boolean; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  return new NextRequest("http://localhost/api/admin/configuracion/plantillas/BIENVENIDA/vista-previa", { method: "POST", headers, body: init.cuerpo ?? JSON.stringify({ asunto: "Hola", cuerpo: "Entra a {enlace}" }) });
}

const contexto = (tipo = "BIENVENIDA") => ({ params: Promise.resolve({ tipo }) });

afterEach(() => vi.mocked(vistaPreviaDePlantilla).mockReset());

/** BFF de la vista previa (#199): no guarda nada ni deja registro, pero pasa por las mismas reglas que guardar y exige la misma sesión. */
describe("POST /api/admin/configuracion/plantillas/[tipo]/vista-previa", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(vistaPreviaDePlantilla).not.toHaveBeenCalled();
  });

  it("un nombre de correo inválido o un cuerpo roto responden 400 sin llamar al backend", async () => {
    expect((await POST(peticion(), contexto("../x"))).status).toBe(400);
    for (const malo of ["{no es json", "[1]", ""]) expect((await POST(peticion({ cuerpo: malo }), contexto())).status, malo).toBe(400);

    expect(vistaPreviaDePlantilla).not.toHaveBeenCalled();
  });

  it("pide la vista previa de ese correo con el cuerpo tal cual y el JWT, y la devuelve sin caché", async () => {
    vi.mocked(vistaPreviaDePlantilla).mockResolvedValue({ asunto: "Hola", cuerpo: "Entra a https://app.khipu.pe" });

    const res = await POST(peticion(), contexto("RECUPERACION_CLAVE"));

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual({ asunto: "Hola", cuerpo: "Entra a https://app.khipu.pe" });
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(vi.mocked(vistaPreviaDePlantilla).mock.calls[0]).toEqual(["jwt-admin", "RECUPERACION_CLAVE", { asunto: "Hola", cuerpo: "Entra a {enlace}" }]);
  });

  it("un texto inválido vuelve con el mensaje de lo que hay que corregir", async () => {
    vi.mocked(vistaPreviaDePlantilla).mockRejectedValue(new ApiError(422, "PLANTILLA_INVALIDA", "El asunto no puede estar vacío"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(422);
    expect((await res.json()).mensaje).toBe("El asunto no puede estar vacío");
  });
});
