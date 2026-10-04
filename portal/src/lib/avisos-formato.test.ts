import { describe, expect, it } from "vitest";
import { atascadosEnPalabras, certificadoEnPalabras, mensajeDeAvisoEnviado, ultimoAvisoEnPalabras } from "./avisos-formato";

describe("certificadoEnPalabras", () => {
  it("un certificado por vencer dice cuándo vence y en cuántos días", () => {
    expect(certificadoEnPalabras({ vigente_hasta: "2026-10-25", dias_restantes: 10 })).toBe("Vence el 25 Oct 2026 (en 10 días)");
  });

  it("a un día dice «1 día» y no «1 días»", () => {
    expect(certificadoEnPalabras({ vigente_hasta: "2026-10-16", dias_restantes: 1 })).toBe("Vence el 16 Oct 2026 (en 1 día)");
    expect(certificadoEnPalabras({ vigente_hasta: "2026-10-17", dias_restantes: 2 })).toBe("Vence el 17 Oct 2026 (en 2 días)");
  });

  it("el último día que vale dice que vence hoy", () => {
    expect(certificadoEnPalabras({ vigente_hasta: "2026-10-15", dias_restantes: 0 })).toBe("Vence hoy (15 Oct 2026)");
  });

  it("uno vencido dice cuándo venció y hace cuántos días, sin signo negativo", () => {
    expect(certificadoEnPalabras({ vigente_hasta: "2026-10-10", dias_restantes: -5 })).toBe("Venció el 10 Oct 2026 (hace 5 días)");
    expect(certificadoEnPalabras({ vigente_hasta: "2026-10-14", dias_restantes: -1 })).toBe("Venció el 14 Oct 2026 (hace 1 día)");
  });
});

describe("atascadosEnPalabras", () => {
  it("dice cuántos comprobantes están atascados, en singular o plural", () => {
    expect(atascadosEnPalabras(1)).toBe("1 comprobante atascado");
    expect(atascadosEnPalabras(5)).toBe("5 comprobantes atascados");
  });
});

describe("ultimoAvisoEnPalabras", () => {
  it("dice cuándo y a quién se avisó, en hora de Lima", () => {
    expect(ultimoAvisoEnPalabras({ enviado_en: "2026-10-10T15:00:00Z", destinatario: "ana@negocio.pe" })).toBe("Avisado el 10 Oct 2026, 10:00 a ana@negocio.pe");
  });

  it("si nunca se avisó lo dice", () => {
    expect(ultimoAvisoEnPalabras(undefined)).toBe("Sin avisos");
  });
});

describe("mensajeDeAvisoEnviado", () => {
  it("dice a quién se le mandó y de qué empresa", () => {
    expect(mensajeDeAvisoEnviado({ destinatario: "ana@negocio.pe" }, "PANADERIA SOL SAC")).toBe("Se le avisó a ana@negocio.pe (PANADERIA SOL SAC).");
  });
});
