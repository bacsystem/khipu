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

  // Los 23 formularios que usan este cliente hacen `await` sin try/catch y apagan el «Enviando…»
  // mirando `res.estado`. Si algo de acá lanzara, el botón quedaría deshabilitado para siempre.
  describe("nunca lanza: el error llega como sobre", () => {
    it("corte de red al enviar", async () => {
      vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

      const res = await apiRequest("/api/proxy/empresa/certificado", { method: "POST", body: new FormData() });

      expect(res.estado).toBe("error");
      expect(res.codigo).toBe("RED");
      expect(res.mensaje).toBeTruthy();
    });

    it("corte de red al leer el cuerpo de la respuesta", async () => {
      const cuerpoRoto = new Response("x", { status: 200 });
      vi.spyOn(cuerpoRoto, "text").mockRejectedValue(new TypeError("network error"));
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(cuerpoRoto));

      const res = await postJson("/api/auth/login", { email: "a@b.com", password: "x" });

      expect(res.estado).toBe("error");
      expect(res.codigo).toBe("RED");
    });

    it("respuesta que no es JSON (el HTML de un 502 de proxy)", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue(new Response("<html><body>502 Bad Gateway</body></html>", { status: 502 })),
      );

      const res = await postJson("/api/auth/recuperar", { email: "a@b.com" });

      expect(res.estado).toBe("error");
      expect(res.codigo).toBe("RESPUESTA_INVALIDA");
    });
  });
});
