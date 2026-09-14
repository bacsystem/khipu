import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest, postJson } from "./browser";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("apiRequest / postJson", () => {
  it("trata un 2xx sin body (p. ej. 201 de crear serie) como éxito sin datos", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 201 })));

    const res = await apiRequest("/api/proxy/series", { method: "POST", body: { tipo: "01", serie: "F001" } });

    expect(res).toEqual({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
  });

  it("trata un error sin body como error genérico en vez de reventar el JSON.parse", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 500 })));

    const res = await postJson("/api/auth/login", { email: "a@b.com", password: "x" });

    expect(res.estado).toBe("error");
  });

  it("parsea el sobre cuando el backend sí manda JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ estado: "exito", datos: { id: "1" }, mensaje: null, codigo: null, errores: null }), {
          status: 200,
        }),
      ),
    );

    const res = await apiRequest<{ id: string }>("/api/proxy/empresas", { method: "GET" });

    expect(res.datos).toEqual({ id: "1" });
  });

  it("envía FormData sin fijar Content-Type manualmente", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    const form = new FormData();
    form.set("archivo", new File(["x"], "cert.p12"));

    await apiRequest("/api/proxy/empresa/certificado", { method: "POST", body: form });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers).toBeUndefined();
    expect(init.body).toBe(form);
  });
});
