import { afterEach, describe, expect, it, vi } from "vitest";
import { cabecerasDeOrigen, origenDe } from "./origen";

afterEach(() => {
  vi.unstubAllEnvs();
});

const entrantes = (xff?: string, cf?: string) =>
  new Headers({ ...(xff === undefined ? {} : { "x-forwarded-for": xff }), ...(cf === undefined ? {} : { "cf-connecting-ip": cf }) });

// La forma medida en producción (#594): el borde de Railway reemplaza la cadena; a su izquierda queda quien se le conectó.
const CLOUDFLARE_V4 = "198.41.230.10";
const CLOUDFLARE_V6 = "2606:4700:3033::6815:1";
const BORDE = "100.64.0.5";

describe("origenDe con Cloudflare delante (#594)", () => {
  it("si la conexión vino de Cloudflare, el cliente es el de CF-Connecting-IP", () => {
    expect(origenDe(entrantes(`${CLOUDFLARE_V4}, ${BORDE}`, "203.0.113.7"), 2, true)).toEqual({ ip: "203.0.113.7", deCloudflare: true });
    expect(origenDe(entrantes(`${CLOUDFLARE_V6}, ${BORDE}`, "2001:DB8::1"), 2, true)).toEqual({ ip: "2001:db8::1", deCloudflare: true });
  });

  it("entrando directo al dominio de Railway, un CF-Connecting-IP inventado se ignora y queda la IP de la conexión", () => {
    expect(origenDe(entrantes(`203.0.113.9, ${BORDE}`, "6.6.6.6"), 2, true)).toEqual({ ip: "203.0.113.9", deCloudflare: false });
    expect(origenDe(entrantes(`2001:db8::9, ${BORDE}`, "6.6.6.6"), 2, true)).toEqual({ ip: "2001:db8::9", deCloudflare: false });
  });

  it("si la conexión vino de Cloudflare pero la cabecera falta o no es una IP, no sale ninguna: la de Cloudflare no es la del cliente", () => {
    for (const cf of [undefined, "", "unknown", "6.6.6.6, 7.7.7.7", "999.1.1.1", "203.0.113.7/24"]) {
      expect(origenDe(entrantes(`${CLOUDFLARE_V4}, ${BORDE}`, cf), 2, true), String(cf)).toEqual({ ip: undefined, deCloudflare: false });
    }
  });

  it("apagado (por defecto), CF-Connecting-IP no se mira aunque la conexión venga de Cloudflare", () => {
    expect(origenDe(entrantes(`${CLOUDFLARE_V4}, ${BORDE}`, "203.0.113.7"), 2, false)).toEqual({ ip: CLOUDFLARE_V4, deCloudflare: false });
    vi.stubEnv("TRUSTED_CLOUDFLARE", "");
    expect(origenDe(entrantes(`${CLOUDFLARE_V4}, ${BORDE}`, "203.0.113.7"), 2)).toEqual({ ip: CLOUDFLARE_V4, deCloudflare: false });
  });

  it("sin saltos de confianza no se cree ni en Cloudflare: no hay conexión conocida que comprobar", () => {
    expect(origenDe(entrantes(`${CLOUDFLARE_V4}, ${BORDE}`, "203.0.113.7"), 0, true)).toEqual({ ip: undefined, deCloudflare: false });
  });

  it("por defecto lee TRUSTED_CLOUDFLARE y TRUSTED_PROXY_HOPS, y cabecerasDeOrigen manda la IP de Cloudflare al backend", () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "2");
    vi.stubEnv("TRUSTED_CLOUDFLARE", "true");

    expect(cabecerasDeOrigen(entrantes(`${CLOUDFLARE_V4}, ${BORDE}`, "203.0.113.7"))).toEqual({ "X-Forwarded-For": "203.0.113.7" });
    expect(cabecerasDeOrigen(entrantes(`${CLOUDFLARE_V4}, ${BORDE}`))).toEqual({});
  });
});

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
