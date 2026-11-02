import { describe, expect, it } from "vitest";
import { redondeoDe } from "./redondeo";
import es from "@/messages/es.json";

describe("redondeoDe (#358, 3303)", () => {
  it("vacío y cero son «sin redondeo»", () => {
    expect(redondeoDe("", es)).toEqual({ valor: null, error: null });
    expect(redondeoDe("  ", es)).toEqual({ valor: null, error: null });
    expect(redondeoDe("0", es)).toEqual({ valor: null, error: null });
    expect(redondeoDe("-0.00", es)).toEqual({ valor: null, error: null });
  });

  it("acepta hasta 2 decimales, con signo, entre −1.00 y 1.00 incluidos", () => {
    expect(redondeoDe("0.05", es).valor).toBe(0.05);
    expect(redondeoDe("-0.05", es).valor).toBe(-0.05);
    expect(redondeoDe("-.5", es).valor).toBe(-0.5);
    expect(redondeoDe("1", es).valor).toBe(1);
    expect(redondeoDe("-1.00", es).valor).toBe(-1);
  });

  it("rechaza lo que el dominio rechaza", () => {
    expect(redondeoDe("1.01", es).error).toMatch(/no puede superar 1\.00/);
    expect(redondeoDe("-2", es).error).toMatch(/no puede superar 1\.00/);
    expect(redondeoDe("0.001", es).error).toMatch(/hasta 2 decimales/);
    expect(redondeoDe("abc", es).error).toMatch(/hasta 2 decimales/);
    expect(redondeoDe("1,5", es).error).toMatch(/hasta 2 decimales/);
  });
});
