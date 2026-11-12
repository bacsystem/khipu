import { describe, expect, it } from "vitest";
import { leerCuerpoJson } from "./cuerpo-json";

const peticion = (cuerpo?: string) => new Request("http://localhost/api/x", { method: "POST", body: cuerpo });

const SOBRE_JSON_INVALIDO = { estado: "error", datos: null, mensaje: null, codigo: "JSON_INVALIDO", errores: null };

/** Lo que nunca es un objeto JSON: en los dos modos se rechaza. */
const NO_ES_UN_OBJETO = ["esto-no-es-json", "{no es json", "null", "[]", "[1,2]", '"x"', "5", "true"];
const VACIOS = ["", "   ", "\n"];

async function rechazo(r: Awaited<ReturnType<typeof leerCuerpoJson>>) {
  if (!("error" in r)) throw new Error("se esperaba un rechazo");
  return { status: r.error.status, sobre: await r.error.json() };
}

describe("leerCuerpoJson — modo normal", () => {
  it.each([...VACIOS, ...NO_ES_UN_OBJETO])("rechaza %j con 400 JSON_INVALIDO y sin texto", async (cuerpo) => {
    const { status, sobre } = await rechazo(await leerCuerpoJson(peticion(cuerpo)));

    expect(status).toBe(400);
    expect(sobre).toEqual(SOBRE_JSON_INVALIDO);
  });

  it("una petición sin cuerpo se rechaza igual", async () => {
    expect((await rechazo(await leerCuerpoJson(peticion()))).status).toBe(400);
  });

  it.each(["{}", '{"email":"a@b.pe","password":"x"}', '{"limites":{"rucs":2}}'])("acepta el objeto %s tal cual", async (cuerpo) => {
    expect(await leerCuerpoJson(peticion(cuerpo))).toEqual({ cuerpo: JSON.parse(cuerpo) });
  });

  it("no mira los campos: un tipo equivocado pasa y lo rechaza el backend", async () => {
    expect(await leerCuerpoJson(peticion('{"email":5}'))).toEqual({ cuerpo: { email: 5 } });
  });

  it("un cuerpo que no se puede leer se rechaza en vez de lanzar", async () => {
    const roto = { text: () => Promise.reject(new TypeError("body used already")) } as unknown as Request;

    expect((await rechazo(await leerCuerpoJson(roto))).sobre).toEqual(SOBRE_JSON_INVALIDO);
  });
});

describe("leerCuerpoJson — modo opcional", () => {
  it.each(VACIOS)("un cuerpo vacío (%j) es {}", async (cuerpo) => {
    expect(await leerCuerpoJson(peticion(cuerpo), { opcional: true })).toEqual({ cuerpo: {} });
  });

  it("una petición sin cuerpo es {}", async () => {
    expect(await leerCuerpoJson(peticion(), { opcional: true })).toEqual({ cuerpo: {} });
  });

  it.each(NO_ES_UN_OBJETO)("sigue rechazando %j", async (cuerpo) => {
    const { status, sobre } = await rechazo(await leerCuerpoJson(peticion(cuerpo), { opcional: true }));

    expect(status).toBe(400);
    expect(sobre).toEqual(SOBRE_JSON_INVALIDO);
  });

  it("un objeto se devuelve tal cual", async () => {
    expect(await leerCuerpoJson(peticion('{"motivo":"no pagó"}'), { opcional: true })).toEqual({ cuerpo: { motivo: "no pagó" } });
  });
});
