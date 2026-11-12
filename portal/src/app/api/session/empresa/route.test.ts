import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { COOKIE_EMPRESA } from "@/lib/session";
import { POST } from "./route";

const peticion = (cuerpo: string) =>
  new NextRequest("http://localhost/api/session/empresa", { method: "POST", body: cuerpo, headers: { "content-type": "application/json" } });

describe("POST /api/session/empresa", () => {
  it("fija la cookie de empresa activa", async () => {
    const res = await POST(peticion(JSON.stringify({ empresaId: "e1" })));

    expect(res.status).toBe(200);
    expect(res.cookies.get(COOKIE_EMPRESA)?.value).toBe("e1");
  });

  /** Sin backend detrás, nada más revisa el id: antes `{}` daba 200 y escribía la cookie con `undefined`. */
  it.each([
    ["sin empresaId", "{}"],
    ["con un número", '{"empresaId":5}'],
    ["vacío", '{"empresaId":""}'],
    ["en blanco", '{"empresaId":"   "}'],
    ["null", '{"empresaId":null}'],
  ])("un empresaId %s responde 400 ID_INVALIDO y no escribe la cookie", async (_titulo, cuerpo) => {
    const res = await POST(peticion(cuerpo));

    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ estado: "error", datos: null, mensaje: null, codigo: "ID_INVALIDO", errores: null });
    expect(res.cookies.get(COOKIE_EMPRESA)).toBeUndefined();
  });
});
