import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-configuracion", async (original) => ({ ...(await original<object>()), guardarPlantilla: vi.fn(), restaurarPlantilla: vi.fn() }));

import { guardarPlantilla, restaurarPlantilla } from "@/lib/api/admin-configuracion";
import { DELETE, PUT } from "./route";

const PLANTILLA = { tipo: "BIENVENIDA", personalizada: true };

function peticion(metodo: "PUT" | "DELETE", init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest("http://localhost/api/admin/configuracion/plantillas/BIENVENIDA", { method: metodo, headers, body: metodo === "PUT" ? (init.cuerpo ?? JSON.stringify({ asunto: "Hola", cuerpo: "Entra a {enlace}" })) : undefined });
}

const contexto = (tipo = "BIENVENIDA") => ({ params: Promise.resolve({ tipo }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(guardarPlantilla).mockReset();
  vi.mocked(restaurarPlantilla).mockReset();
});

/** BFF del texto de un correo (#199): el nombre del correo va a la URL del backend, así que se valida antes; el JWT nunca llega al navegador. */
describe("PUT /api/admin/configuracion/plantillas/[tipo]", () => {
  it("sin sesión de administrador responde 401, aunque el tipo o el cuerpo estén rotos, y no llama al backend", async () => {
    const res = await PUT(peticion("PUT", { sesion: false, cuerpo: "{no es json" }), contexto("../auth"));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(guardarPlantilla).not.toHaveBeenCalled();
  });

  it("un nombre de correo que podría salirse de la ruta responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "bienvenida", "A/B", "A B", "", "A".repeat(41)]) {
      const res = await PUT(peticion("PUT"), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("TIPO_INVALIDO");
    }
    expect(guardarPlantilla).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", ""]) {
      const res = await PUT(peticion("PUT", { cuerpo: malo }), contexto());
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(guardarPlantilla).not.toHaveBeenCalled();
  });

  it("guarda ese correo con el cuerpo tal cual y el JWT del administrador, y devuelve la plantilla, sin caché", async () => {
    vi.mocked(guardarPlantilla).mockResolvedValue(PLANTILLA as never);

    const res = await PUT(peticion("PUT", { cuerpo: JSON.stringify({ asunto: "Hola", cuerpo: "Entra a {enlace}" }) }), contexto("RECUPERACION_CLAVE"));

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(PLANTILLA);
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(vi.mocked(guardarPlantilla).mock.calls[0].slice(0, 3)).toEqual(["jwt-admin", "RECUPERACION_CLAVE", { asunto: "Hola", cuerpo: "Entra a {enlace}" }]);
  });

  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(guardarPlantilla).mockResolvedValue(PLANTILLA as never);

    await PUT(peticion("PUT", { xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(vi.mocked(guardarPlantilla).mock.calls[0][3]).toEqual({ "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status, el código y el mensaje del backend (lo que hay que corregir)", async () => {
    vi.mocked(guardarPlantilla).mockRejectedValue(new ApiError(422, "PLANTILLA_INVALIDA", "El cuerpo tiene que incluir {enlace}"));

    const res = await PUT(peticion("PUT"), contexto());

    expect(res.status).toBe(422);
    const cuerpo = await res.json();
    expect(cuerpo.codigo).toBe("PLANTILLA_INVALIDA");
    expect(cuerpo.mensaje).toBe("El cuerpo tiene que incluir {enlace}");
  });

  it("un correo que el backend no conoce es 404", async () => {
    vi.mocked(guardarPlantilla).mockRejectedValue(new ApiError(404, "NO_ENCONTRADO", "Ese correo no existe"));

    expect((await PUT(peticion("PUT"), contexto("OTRO_CORREO"))).status).toBe(404);
  });
});

describe("DELETE /api/admin/configuracion/plantillas/[tipo]", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await DELETE(peticion("DELETE", { sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(restaurarPlantilla).not.toHaveBeenCalled();
  });

  it("un nombre de correo inválido responde 400 sin llamar al backend", async () => {
    const res = await DELETE(peticion("DELETE"), contexto("../x"));

    expect(res.status).toBe(400);
    expect(restaurarPlantilla).not.toHaveBeenCalled();
  });

  it("restaura ese correo con el JWT y la IP del administrador, sin caché", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(restaurarPlantilla).mockResolvedValue({ ...PLANTILLA, personalizada: false } as never);

    const res = await DELETE(peticion("DELETE", { xff: "6.6.6.6, 203.0.113.7" }), contexto("AVISO_CREDENCIALES_SOL"));

    expect(res.status).toBe(200);
    expect((await res.json()).datos.personalizada).toBe(false);
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(restaurarPlantilla).toHaveBeenCalledWith("jwt-admin", "AVISO_CREDENCIALES_SOL", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el error del backend", async () => {
    vi.mocked(restaurarPlantilla).mockRejectedValue(new ApiError(404, "NO_ENCONTRADO", "Ese correo no existe"));

    expect((await DELETE(peticion("DELETE"), contexto())).status).toBe(404);
  });
});
