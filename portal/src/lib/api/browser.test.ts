import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import es from "@/messages/es.json";
import { apiRequest, noSeSabeSiLlego, postJson, seVencioLaEspera } from "./browser";

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
      // Un error de transporte viaja sin texto: el formulario lo toma del catálogo, en el idioma de la sesión (#25).
      expect(res.mensaje).toBeNull();
    });

    it("corte de red al leer el cuerpo de la respuesta", async () => {
      const cuerpoRoto = new Response("x", { status: 200 });
      vi.spyOn(cuerpoRoto, "text").mockRejectedValue(new TypeError("network error"));
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(cuerpoRoto));

      const res = await postJson("/api/auth/login", { email: "a@b.com", password: "x" });

      expect(res.estado).toBe("error");
      expect(res.codigo).toBe("RED_CORTADA");
      expect(noSeSabeSiLlego(res)).toBe(true);
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

describe("tope de espera (#117)", () => {
  it("cada pedido lleva una señal que vence: 30 s la lectura y 2 min lo que escribe", async () => {
    const espera = vi.spyOn(AbortSignal, "timeout");
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);

    await apiRequest("/api/proxy/series", { method: "GET" });
    await apiRequest("/api/proxy/facturas", { method: "POST", body: {} });
    await apiRequest("/api/proxy/series/F001", { method: "DELETE" });

    expect(espera.mock.calls.map(([ms]) => ms)).toEqual([30_000, 120_000, 120_000]);
    for (const [, init] of fetch.mock.calls) expect((init as RequestInit).signal).toBeInstanceOf(AbortSignal);
    espera.mockRestore();
  });

  it("un pedido que vence cae en el corte de red, con su propio código, y no cuelga al llamador", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new DOMException("The operation timed out.", "TimeoutError")));

    const res = await apiRequest("/api/proxy/facturas", { method: "POST", body: {} });

    expect(res.estado).toBe("error");
    expect(res.codigo).toBe("ESPERA_VENCIDA");
    expect(noSeSabeSiLlego(res)).toBe(true);
    expect(res.mensaje).toBeNull();
  });

  it("un corte de red que no es de tiempo se queda en el código de conexión", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    const res = await apiRequest("/api/proxy/series", { method: "GET" });

    expect(res.codigo).toBe("RED");
    expect(res.mensaje).toBeNull();
    expect(seVencioLaEspera(res)).toBe(false);
  });

  it("cada código de transporte tiene su texto en español y en inglés, y ninguno viaja como literal", () => {
    for (const codigo of ["RED", "RED_CORTADA", "ESPERA_VENCIDA", "RESPUESTA_INVALIDA", "ERROR_DE_COMUNICACION", "ERROR_INESPERADO"]) {
      expect((es.errores as Record<string, string>)[codigo], `es: ${codigo}`).toBeTruthy();
      expect((en.errores as Record<string, string>)[codigo], `en: ${codigo}`).toBeTruthy();
    }
  });

  it("una llamada puede pedir su propia espera (el barrido de integridad), y un tiempo vencido se distingue de un corte", async () => {
    const espera = vi.spyOn(AbortSignal, "timeout");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await apiRequest("/api/admin/integridad", { method: "POST", body: {}, esperaMaximaMs: 300_000 });
    expect(espera).toHaveBeenLastCalledWith(300_000);
    espera.mockRestore();

    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new DOMException("timed out", "TimeoutError")));
    const vencido = await apiRequest("/api/proxy/facturas", { method: "POST", body: {} });
    expect(seVencioLaEspera(vencido)).toBe(true);
    expect(vencido.codigo).toBe("ESPERA_VENCIDA");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    expect(seVencioLaEspera(await apiRequest("/api/proxy/facturas", { method: "POST", body: {} }))).toBe(false);
  });
});
