import { afterEach, describe, expect, it, vi } from "vitest";
import { cabecerasDeOrigen } from "./origen";

afterEach(() => {
  vi.unstubAllEnvs();
});

const entrantes = (xff?: string) => new Headers(xff === undefined ? {} : { "x-forwarded-for": xff });

describe("cabecerasDeOrigen", () => {
  it("manda al backend una sola IP, ya resuelta, y no la cadena que llegó", () => {
    const salientes = cabecerasDeOrigen(entrantes("6.6.6.6, 203.0.113.7"), 1);

    expect(salientes).toEqual({ "X-Forwarded-For": "203.0.113.7" });
    expect(JSON.stringify(salientes)).not.toContain("6.6.6.6");
  });

  it("sin saltos de confianza no manda nada: el backend ve la IP del portal, como antes", () => {
    expect(cabecerasDeOrigen(entrantes("203.0.113.7"), 0)).toEqual({});
  });

  it("sin cabecera, o con una que no es una IP, no manda nada", () => {
    expect(cabecerasDeOrigen(entrantes(), 1)).toEqual({});
    expect(cabecerasDeOrigen(entrantes("unknown"), 1)).toEqual({});
  });

  it("por defecto toma los saltos de TRUSTED_PROXY_HOPS", () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "2");

    expect(cabecerasDeOrigen(entrantes("203.0.113.7, 100.64.0.5"))).toEqual({ "X-Forwarded-For": "203.0.113.7" });
  });

  it("sin TRUSTED_PROXY_HOPS definido no se confía en ningún proxy", () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "");

    expect(cabecerasDeOrigen(entrantes("203.0.113.7"))).toEqual({});
  });
});
