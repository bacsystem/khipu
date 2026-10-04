import { describe, expect, it } from "vitest";
import { validarPago, type ContextoDePago, type ValoresDePago } from "./pago-formulario";

const HOY = "2026-10-15";
const CTX: ContextoDePago = { hoy: HOY, planVence: true, pagadoHastaActual: "2026-10-20" };

const OK: ValoresDePago = { desde: "2026-10-01", hasta: "2026-10-31", monto: "29.00", medio: "YAPE", fecha: "2026-10-14", referencia: "OP-123", nota: "Pagó por Yape", extender: true };

const con = (cambios: Partial<ValoresDePago>) => ({ ...OK, ...cambios });
const errores = (v: ValoresDePago, ctx: ContextoDePago = CTX) => {
  const r = validarPago(v, ctx);
  if (!("errores" in r)) throw new Error("se esperaban errores");
  return r.errores;
};

describe("validarPago", () => {
  it("con todo bien arma el cuerpo que espera el backend", () => {
    expect(validarPago(OK, CTX)).toEqual({
      cuerpo: { periodo_desde: "2026-10-01", periodo_hasta: "2026-10-31", monto: 29, medio: "YAPE", fecha_de_pago: "2026-10-14", referencia: "OP-123", nota: "Pagó por Yape", extender_vencimiento: true },
    });
  });

  it("la referencia y la nota solo van si se escribieron, recortadas", () => {
    expect(validarPago(con({ referencia: "  OP-1  ", nota: "  ok  " }), CTX)).toMatchObject({ cuerpo: { referencia: "OP-1", nota: "ok" } });
    const sin = validarPago(con({ referencia: "   ", nota: "" }), CTX);
    expect("cuerpo" in sin && Object.keys(sin.cuerpo)).not.toContain("referencia");
    expect("cuerpo" in sin && Object.keys(sin.cuerpo)).not.toContain("nota");
  });

  it("sin extender no hace falta que el periodo adelante nada ni que el plan venza", () => {
    const r = validarPago(con({ extender: false, hasta: "2026-10-05" }), { ...CTX, planVence: false });
    expect(r).toMatchObject({ cuerpo: { extender_vencimiento: false } });
  });

  // --- el periodo ------------------------------------------------------------------------------------------------------------------------

  it("el periodo pide las dos fechas y que existan", () => {
    expect(errores(con({ desde: "" })).desde).toBe("Indica desde cuándo cubre el pago.");
    expect(errores(con({ hasta: "" })).hasta).toBe("Indica hasta cuándo cubre el pago.");
    expect(errores(con({ desde: "2026-02-30" })).desde).toBe("Esa fecha no existe.");
    expect(errores(con({ hasta: "2026-13-01" })).hasta).toBe("Esa fecha no existe.");
    expect(errores(con({ hasta: "31/10/2026" })).hasta).toBe("Esa fecha no existe.");
  });

  it("el periodo no puede terminar antes de empezar, pero puede ser de un solo día", () => {
    expect(errores(con({ desde: "2026-10-31", hasta: "2026-10-01", extender: false })).hasta).toBe("El periodo no puede terminar antes de empezar.");
    expect(validarPago(con({ desde: "2026-10-31", hasta: "2026-10-31", extender: false }), CTX)).toHaveProperty("cuerpo");
  });

  it("el periodo no pasa de un año: justo un año todavía vale, un día más no", () => {
    expect(validarPago(con({ desde: "2026-10-01", hasta: "2027-09-30", extender: false }), CTX)).toHaveProperty("cuerpo");
    expect(errores(con({ desde: "2026-10-01", hasta: "2027-10-01", extender: false })).hasta).toBe("El periodo no puede pasar de un año.");
  });

  it("el límite de un año sigue la regla del backend también con 29 de febrero", () => {
    // 2028-02-29 + 1 año = 2029-02-28 (se ajusta al último día), menos un día: 2029-02-27.
    expect(validarPago(con({ desde: "2028-02-29", hasta: "2029-02-27", extender: false }), CTX)).toHaveProperty("cuerpo");
    expect(errores(con({ desde: "2028-02-29", hasta: "2029-02-28", extender: false })).hasta).toBe("El periodo no puede pasar de un año.");
  });

  // --- el monto --------------------------------------------------------------------------------------------------------------------------

  it("el monto es obligatorio", () => {
    expect(errores(con({ monto: "" })).monto).toBe("Escribe el monto.");
    expect(errores(con({ monto: "   " })).monto).toBe("Escribe el monto.");
  });

  it("el monto es un número positivo con hasta dos decimales, con punto o coma", () => {
    expect(validarPago(con({ monto: "29" }), CTX)).toMatchObject({ cuerpo: { monto: 29 } });
    expect(validarPago(con({ monto: "29,5" }), CTX)).toMatchObject({ cuerpo: { monto: 29.5 } });
    expect(validarPago(con({ monto: " 0.01 " }), CTX)).toMatchObject({ cuerpo: { monto: 0.01 } });
    for (const malo of ["0", "0.00", "-5", "29.999", "29.", ".5", "abc", "1e3", "29 soles", "1,000.50", "S/ 29"]) {
      expect(errores(con({ monto: malo })).monto, malo).toBe("El monto debe ser mayor que cero, con hasta dos decimales.");
    }
  });

  it("el monto tiene un tope de S/ 9,999,999.99", () => {
    expect(validarPago(con({ monto: "9999999.99" }), CTX)).toHaveProperty("cuerpo");
    expect(errores(con({ monto: "10000000" })).monto).toBe("El monto no puede pasar de S/ 9,999,999.99.");
  });

  // --- el medio y la fecha -----------------------------------------------------------------------------------------------------------------

  it("el medio es obligatorio y de los conocidos", () => {
    expect(errores(con({ medio: "" })).medio).toBe("Elige el medio de pago.");
    expect(errores(con({ medio: "BITCOIN" })).medio).toBe("Elige el medio de pago.");
    for (const m of ["TRANSFERENCIA", "DEPOSITO", "YAPE", "PLIN", "TARJETA", "EFECTIVO", "OTRO"]) expect(validarPago(con({ medio: m }), CTX), m).toHaveProperty("cuerpo");
  });

  it("la fecha de pago es obligatoria, existe y no es futura: hoy sí vale", () => {
    expect(errores(con({ fecha: "" })).fecha).toBe("Indica la fecha de pago.");
    expect(errores(con({ fecha: "2026-02-30" })).fecha).toBe("Esa fecha no existe.");
    expect(errores(con({ fecha: "2026-10-16" })).fecha).toBe("La fecha de pago no puede ser futura.");
    expect(validarPago(con({ fecha: HOY }), CTX)).toHaveProperty("cuerpo");
    expect(validarPago(con({ fecha: "2020-01-01" }), CTX)).toHaveProperty("cuerpo");
  });

  // --- referencia y nota -------------------------------------------------------------------------------------------------------------------

  it("la referencia admite 100 caracteres y la nota 200, ya recortadas", () => {
    expect(validarPago(con({ referencia: "r".repeat(100), nota: "n".repeat(200) }), CTX)).toHaveProperty("cuerpo");
    expect(validarPago(con({ referencia: ` ${"r".repeat(100)} ` }), CTX)).toHaveProperty("cuerpo");
    expect(errores(con({ referencia: "r".repeat(101) })).referencia).toBe("La referencia no puede pasar de 100 caracteres.");
    expect(errores(con({ nota: "n".repeat(201) })).nota).toBe("La nota no puede pasar de 200 caracteres.");
  });

  // --- extender ---------------------------------------------------------------------------------------------------------------------------

  it("extender en un plan que no vence es un error", () => {
    expect(errores(OK, { ...CTX, planVence: false }).extender).toBe("El plan de esta cuenta no vence: no hay vencimiento que extender.");
  });

  it("extender con un periodo que no adelanta el vencimiento es un error: el mismo día o antes", () => {
    expect(errores(con({ hasta: "2026-10-20" })).extender).toBe("Este periodo no adelanta el vencimiento: desmarca la extensión o corrige el periodo.");
    expect(errores(con({ hasta: "2026-10-19" })).extender).toBe("Este periodo no adelanta el vencimiento: desmarca la extensión o corrige el periodo.");
    expect(validarPago(con({ hasta: "2026-10-21" }), CTX)).toHaveProperty("cuerpo");
  });

  it("sin saber hasta cuándo está pagada la cuenta no se compara", () => {
    expect(validarPago(con({ hasta: "2026-10-05" }), { hoy: HOY, planVence: true })).toHaveProperty("cuerpo");
  });

  it("todos los errores se informan juntos", () => {
    const e = errores({ desde: "", hasta: "", monto: "", medio: "", fecha: "", referencia: "", nota: "", extender: false });
    expect(Object.keys(e).sort()).toEqual(["desde", "fecha", "hasta", "medio", "monto"]);
  });
});
