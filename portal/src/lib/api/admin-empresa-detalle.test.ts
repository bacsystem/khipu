import { describe, expect, it } from "vitest";
import { esIdDeEmpresa, hrefDetalleEmpresa } from "./admin-empresa-detalle";

describe("esIdDeEmpresa (#186)", () => {
  it("acepta un UUID", () => {
    expect(esIdDeEmpresa("0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11")).toBe(true);
  });

  /** Lo que llega por la URL no se pega en la llamada al backend sin mirarlo. */
  it("rechaza todo lo demás", () => {
    for (const malo of ["", "nueva", "ea-01", "../auth/me", "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11/x", " 0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11"])
      expect(esIdDeEmpresa(malo), malo).toBe(false);
  });
});

describe("hrefDetalleEmpresa", () => {
  it("lleva a la página de la empresa", () => {
    expect(hrefDetalleEmpresa("abc")).toBe("/admin/empresas/abc");
  });
});
