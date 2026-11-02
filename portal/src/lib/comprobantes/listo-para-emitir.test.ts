import { describe, expect, it } from "vitest";
import { faltaParaEmitir } from "./listo-para-emitir";

const lista = { tiene_certificado: true, certificado_vigencia_hasta: "2027-01-01", tiene_credenciales_sol: true };

/** C2: las mismas reglas que `Tenant.exigirListoParaEmitir` y `exigirCredencialesSol`, para avisar antes de llenar nada. */
describe("faltaParaEmitir", () => {
  it("con certificado vigente y credenciales SOL no falta nada", () => {
    expect(faltaParaEmitir(lista, "2026-10-08")).toEqual([]);
  });

  it("sin certificado pide cargarlo", () => {
    expect(faltaParaEmitir({ ...lista, tiene_certificado: false, certificado_vigencia_hasta: null }, "2026-10-08")).toEqual(["certificado"]);
  });

  it("un certificado sin fecha de vigencia está cargado", () => {
    expect(faltaParaEmitir({ ...lista, certificado_vigencia_hasta: null }, "2026-10-08")).toEqual([]);
  });

  it("un certificado vencido pide renovarlo; el que vence hoy todavía sirve", () => {
    expect(faltaParaEmitir({ ...lista, certificado_vigencia_hasta: "2026-10-07" }, "2026-10-08")).toEqual(["certificado-vencido"]);
    expect(faltaParaEmitir({ ...lista, certificado_vigencia_hasta: "2026-10-08" }, "2026-10-08")).toEqual([]);
  });

  it("sin credenciales SOL las pide, y dice las dos cosas si faltan las dos", () => {
    expect(faltaParaEmitir({ ...lista, tiene_credenciales_sol: false }, "2026-10-08")).toEqual(["credenciales-sol"]);
    expect(faltaParaEmitir({ tiene_certificado: false, certificado_vigencia_hasta: null, tiene_credenciales_sol: false }, "2026-10-08")).toEqual([
      "certificado",
      "credenciales-sol",
    ]);
  });
});
