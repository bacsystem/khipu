import { describe, expect, it } from "vitest";
import { confiarEnCloudflare, esDeCloudflare, RANGOS_CLOUDFLARE } from "./cloudflare";

describe("esDeCloudflare", () => {
  it.each([
    ["el borde de un rango IPv4 (primera dirección)", "173.245.48.0"],
    ["el borde de un rango IPv4 (última dirección)", "173.245.63.255"],
    ["una del /17 más grande", "198.41.200.1"],
    ["una del 104.16.0.0/13", "104.23.255.255"],
    ["IPv6 dentro de 2606:4700::/32", "2606:4700:3033::6815:1"],
    ["IPv6 dentro del /29", "2a06:98c7:ffff::1"],
  ])("%s es de Cloudflare", (_nombre, ip) => {
    expect(esDeCloudflare(ip)).toBe(true);
  });

  it.each([
    ["justo antes de un rango", "173.245.47.255"],
    ["justo después de un rango", "173.245.64.0"],
    ["una IP de documentación", "203.0.113.7"],
    ["una privada", "10.0.0.1"],
    ["IPv6 fuera del /29", "2a06:98c8::1"],
    ["IPv6 de documentación", "2001:db8::1"],
    ["IPv4 mapeada en IPv6 de una IP de Cloudflare: no cruza de familia (ip-cliente ya la deja en IPv4)", "::ffff:173.245.48.1"],
  ])("%s no es de Cloudflare", (_nombre, ip) => {
    expect(esDeCloudflare(ip)).toBe(false);
  });

  it.each([
    ["vacío", ""],
    ["texto", "cloudflare"],
    ["octeto fuera de rango", "173.245.48.999"],
    ["con puerto", "173.245.48.1:443"],
    ["con máscara", "173.245.48.0/20"],
    ["IPv6 rota", "2606:4700::zz"],
  ])("lo mal formado (%s) no es de Cloudflare", (_nombre, ip) => {
    expect(esDeCloudflare(ip)).toBe(false);
  });

  it("cada rango de la lista está bien escrito: su propia base cae dentro", () => {
    for (const rango of RANGOS_CLOUDFLARE) expect(esDeCloudflare(rango.split("/")[0]), rango).toBe(true);
  });
});

describe("confiarEnCloudflare", () => {
  it("apagado por defecto", () => {
    expect(confiarEnCloudflare(undefined)).toBe(false);
    expect(confiarEnCloudflare("")).toBe(false);
  });

  it("solo `true` lo enciende, sin importar mayúsculas ni espacios", () => {
    expect(confiarEnCloudflare("true")).toBe(true);
    expect(confiarEnCloudflare(" TRUE ")).toBe(true);
    for (const otro of ["1", "si", "yes", "false", "verdadero"]) expect(confiarEnCloudflare(otro), otro).toBe(false);
  });
});
