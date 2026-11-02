import { describe, expect, it } from "vitest";
import { esIdDeCuenta, estadoCertificado, hrefDetalleCuenta } from "./admin-cuenta-detalle";

describe("estadoCertificado (#181)", () => {
  const hoy = "2026-10-03";
  const con = (hasta: string | undefined) => ({ tiene_certificado: true, certificado_vigente_hasta: hasta });

  it("sin certificado cargado", () => {
    expect(estadoCertificado({ tiene_certificado: false }, hoy)).toEqual({ tipo: "ninguno" });
  });

  it("vigente con más de 30 días", () => {
    expect(estadoCertificado(con("2027-03-01"), hoy)).toEqual({ tipo: "vigente", dias: 149, hasta: "2027-03-01" });
  });

  /** La épica #11 dice «por vencer (< 30 días)»: con 30 días justos todavía es vigente; con 29 ya no. El último día aún vale. */
  it("por vencer con menos de 30 días, hasta el último día", () => {
    expect(estadoCertificado(con("2026-11-02"), hoy)).toEqual({ tipo: "vigente", dias: 30, hasta: "2026-11-02" });
    expect(estadoCertificado(con("2026-11-01"), hoy)).toEqual({ tipo: "por_vencer", dias: 29, hasta: "2026-11-01" });
    expect(estadoCertificado(con("2026-10-03"), hoy)).toEqual({ tipo: "por_vencer", dias: 0, hasta: "2026-10-03" });
  });

  it("vencido desde el día siguiente a su fecha", () => {
    expect(estadoCertificado(con("2026-10-02"), hoy)).toEqual({ tipo: "vencido", dias: -1, hasta: "2026-10-02" });
  });

  it("cargado pero sin fecha de vigencia: no se inventa una", () => {
    expect(estadoCertificado(con(undefined), hoy)).toEqual({ tipo: "sin_fecha" });
  });
});

describe("esIdDeCuenta", () => {
  it("acepta un UUID en cualquier caja", () => {
    expect(esIdDeCuenta("0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11")).toBe(true);
    expect(esIdDeCuenta("0B1F1C3E-0F1C-4B53-9A1E-2F6F6D0C7A11")).toBe(true);
  });

  /** Lo que llega por la URL no se pega en la llamada al backend sin mirarlo: `../`, espacios o rutas no son un id. */
  it("rechaza todo lo demás", () => {
    for (const malo of ["", "nueva", "ca-01", "../auth/me", "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11/x", "0b1f1c3e0f1c4b539a1e2f6f6d0c7a11", " 0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11"])
      expect(esIdDeCuenta(malo), malo).toBe(false);
  });
});

describe("hrefDetalleCuenta", () => {
  it("lleva a la página de la cuenta", () => {
    expect(hrefDetalleCuenta("abc")).toBe("/admin/cuentas/abc");
  });
});
