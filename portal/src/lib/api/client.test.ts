import { afterEach, describe, expect, it, vi } from "vitest";
import { backendFetch } from "./client";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("backendFetch", () => {
  it("devuelve datos cuando el sobre es exito", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse({ estado: "exito", datos: { ok: true }, mensaje: null, codigo: null, errores: null })),
    );

    const datos = await backendFetch<{ ok: boolean }>("/v1/algo");

    expect(datos).toEqual({ ok: true });
  });

  it("envía el body como JSON con Content-Type", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await backendFetch("/v1/algo", { method: "POST", body: { a: 1 } });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.body).toBe(JSON.stringify({ a: 1 }));
    expect(init.headers.get("content-type")).toBe("application/json");
  });

  it("lanza ApiError con el código y mensaje del sobre de error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse({ estado: "error", datos: null, mensaje: "Credenciales inválidas", codigo: "NO_AUTORIZADO", errores: null }, 401),
      ),
    );

    await expect(backendFetch("/v1/auth/login")).rejects.toMatchObject({
      status: 401,
      codigo: "NO_AUTORIZADO",
      message: "Credenciales inválidas",
    });
  });

  it("devuelve undefined en 204 sin intentar parsear el body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(backendFetch("/v1/auth/logout", { method: "POST" })).resolves.toBeUndefined();
  });
});
