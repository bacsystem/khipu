// @vitest-environment node
import { AsyncLocalStorage } from "node:async_hooks";
import { afterEach, describe, expect, it } from "vitest";
import { aislarFetchDeLaPeticion } from "./fetch-fuera-de-la-peticion";

const fetchOriginal = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = fetchOriginal;
});

/** Lo que vio el fetch «de red»: el contexto en que corrió. */
function fetchQueAnotaElContexto(als: AsyncLocalStorage<string>, vistos: (string | undefined)[]): typeof fetch {
  return (async () => {
    vistos.push(als.getStore());
    return new Response("ok");
  }) as typeof fetch;
}

describe("aislarFetchDeLaPeticion", () => {
  it("el fetch de red corre sin el contexto de la petición, para que el socket keep-alive que abre no se quede con ella", async () => {
    const als = new AsyncLocalStorage<string>(), vistos: (string | undefined)[] = [];
    globalThis.fetch = fetchQueAnotaElContexto(als, vistos);
    aislarFetchDeLaPeticion();

    await als.run("peticion-1", () => fetch("http://backend/v1/empresas"));

    expect(vistos).toEqual([undefined]);
  });

  it("quien lo llamó sigue en su petición al volver, y recibe la misma respuesta", async () => {
    const als = new AsyncLocalStorage<string>();
    globalThis.fetch = fetchQueAnotaElContexto(als, []);
    aislarFetchDeLaPeticion();

    const [contexto, cuerpo] = await als.run("peticion-2", async () => {
      const r = await fetch("http://backend/v1/empresas");
      return [als.getStore(), await r.text()];
    });

    expect(contexto).toBe("peticion-2");
    expect(cuerpo).toBe("ok");
  });

  it("pasa los argumentos tal cual y no envuelve dos veces si register() corre de nuevo", async () => {
    const recibidos: unknown[][] = [];
    globalThis.fetch = (async (...args: unknown[]) => {
      recibidos.push(args);
      return new Response(null, { status: 204 });
    }) as typeof fetch;
    aislarFetchDeLaPeticion();
    const unaVez = globalThis.fetch;
    aislarFetchDeLaPeticion();

    const init = { method: "POST", body: "{}" };
    const r = await fetch("http://backend/v1/x", init);

    expect(globalThis.fetch).toBe(unaVez);
    expect(recibidos).toEqual([["http://backend/v1/x", init]]);
    expect(r.status).toBe(204);
  });
});
