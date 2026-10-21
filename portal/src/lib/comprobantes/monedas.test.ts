import { describe, expect, it } from "vitest";
import { errorDeTipoCambio, monedas, superaElTopeSinDocumento, tipoCambioAApi } from "./monedas";
import es from "@/messages/es.json";

describe("monedas y tipo de cambio (#316)", () => {
  it("ofrece las 17 monedas que admite el dominio (Monedas.java), soles primero y sin repetir", () => {
    const admitidas = ["ARS", "AUD", "BOB", "BRL", "CAD", "CHF", "CLP", "CNY", "COP", "EUR", "GBP", "JPY", "MXN", "PEN", "PYG", "USD", "UYU"];
    expect(monedas(es).map((m) => m.codigo).sort()).toEqual(admitidas);
    expect(monedas(es).map((m) => m.codigo).slice(0, 3)).toEqual(["PEN", "USD", "EUR"]);
    expect(new Set(monedas(es).map((m) => m.codigo)).size).toBe(monedas(es).length);
  });

  it("el tipo de cambio es opcional, positivo, de hasta 3 enteros y 3 decimales, y solo fuera de soles", () => {
    for (const bueno of ["", "3.756", "3", "0.5", "999.999", " 3.75 "]) expect(errorDeTipoCambio(bueno, "USD", es), bueno).toBeNull();
    for (const malo of ["0", "-3", "abc", "3.7567", "1000", "1000.5"]) expect(errorDeTipoCambio(malo, "USD", es), malo).toMatch(/soles por 1 USD/);
    expect(errorDeTipoCambio("abc", "PEN", es)).toBeNull();
  });

  it("se manda como número solo si vale y la moneda no es soles", () => {
    expect(tipoCambioAApi("3.756", "USD", es)).toBe(3.756);
    expect(tipoCambioAApi(" 3.756 ", "EUR", es)).toBe(3.756);
    expect(tipoCambioAApi("", "USD", es)).toBeUndefined();
    expect(tipoCambioAApi("3.756", "PEN", es)).toBeUndefined();
    expect(tipoCambioAApi("0", "USD", es)).toBeUndefined();
  });

  it("el tope de S/ 700 sin documento se mide en soles al tipo de cambio; sin él, una moneda extranjera siempre lo pasa", () => {
    expect(superaElTopeSinDocumento(700, "PEN", undefined)).toBe(false);
    expect(superaElTopeSinDocumento(700.01, "PEN", undefined)).toBe(true);
    expect(superaElTopeSinDocumento(100, "USD", 3.75)).toBe(false);
    expect(superaElTopeSinDocumento(200, "USD", 3.75)).toBe(true);
    expect(superaElTopeSinDocumento(1, "USD", undefined)).toBe(true);
    expect(superaElTopeSinDocumento(186.66, "USD", 3.75)).toBe(false); // 699.975 → 699.98
    expect(superaElTopeSinDocumento(186.67, "USD", 3.75)).toBe(true);  // 700.0125 → 700.01
    // Ni redondear de menos ni de más: 700.004 → 700.00 pasa; 700.005 → 700.01 no (HALF_UP, como `totalEnSoles`). Con `floor` el segundo pasaría; sin redondeo, el primero no.
    expect(superaElTopeSinDocumento(201.44, "USD", 3.475)).toBe(false);
    expect(superaElTopeSinDocumento(172.5, "USD", 4.058)).toBe(true);
  });
});
