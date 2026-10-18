import { describe, expect, it } from "vitest";
import { redondeoDe } from "./redondeo";

describe("redondeoDe (#358, 3303)", () => {
  it("vacío y cero son «sin redondeo»", () => {
    expect(redondeoDe("")).toEqual({ valor: null, error: null });
    expect(redondeoDe("  ")).toEqual({ valor: null, error: null });
    expect(redondeoDe("0")).toEqual({ valor: null, error: null });
    expect(redondeoDe("-0.00")).toEqual({ valor: null, error: null });
  });

  it("acepta hasta 2 decimales, con signo, entre −1.00 y 1.00 incluidos", () => {
    expect(redondeoDe("0.05").valor).toBe(0.05);
    expect(redondeoDe("-0.05").valor).toBe(-0.05);
    expect(redondeoDe("-.5").valor).toBe(-0.5);
    expect(redondeoDe("1").valor).toBe(1);
    expect(redondeoDe("-1.00").valor).toBe(-1);
  });

  it("rechaza lo que el dominio rechaza", () => {
    expect(redondeoDe("1.01").error).toMatch(/no puede superar 1\.00/);
    expect(redondeoDe("-2").error).toMatch(/no puede superar 1\.00/);
    expect(redondeoDe("0.001").error).toMatch(/hasta 2 decimales/);
    expect(redondeoDe("abc").error).toMatch(/hasta 2 decimales/);
    expect(redondeoDe("1,5").error).toMatch(/hasta 2 decimales/);
  });
});
