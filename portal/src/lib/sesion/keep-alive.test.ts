import { afterEach, describe, expect, it, vi } from "vitest";
import { pedirKeepAlive } from "./keep-alive";

afterEach(() => vi.unstubAllGlobals());

const responde = (status: number) => vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status })));

describe("pedirKeepAlive", () => {
  it("llama a la ruta del BFF con POST", async () => {
    responde(204);

    await pedirKeepAlive();

    expect(fetch).toHaveBeenCalledWith("/api/auth/sesion", expect.objectContaining({ method: "POST" }));
  });

  it.each([
    [204, "vivo"],
    [401, "muerta"],
    [403, "suspendida"],
    [500, "fallo"],
    [502, "fallo"],
    [404, "fallo"],
  ] as const)("un %i es %s", async (status, esperado) => {
    responde(status);

    expect(await pedirKeepAlive()).toBe(esperado);
  });

  it.each([["sin red", new TypeError("Failed to fetch")], ["una espera vencida", new DOMException("tiempo", "TimeoutError")]])("%s es un fallo, no una sesión muerta", async (_t, error) => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(error));

    expect(await pedirKeepAlive()).toBe("fallo");
  });
});
