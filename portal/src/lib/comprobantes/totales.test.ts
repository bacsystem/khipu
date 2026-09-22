import { describe, expect, it } from "vitest";
import { calcularTotales, esGratuita, factorIgv, redondear } from "./totales";

const gravado = (cantidad: number, precioUnitario: number) => ({ cantidad, precioUnitario, tipoAfectacionIgv: "10" });

describe("calcularTotales", () => {
  it("separa el IGV del precio por línea, como el dominio", () => {
    // 1000 / 1.18 = 847.4576271186 (10 dec) → × 2 = 1694.92 → IGV 305.09.
    expect(calcularTotales([gravado(2, 1000)], 18)).toEqual({
      gravado: 1694.92,
      exonerado: 0,
      inafecto: 0,
      gratuito: 0,
      igv: 305.09,
      total: 2000.01,
    });
  });

  it("el total puede no coincidir con precio × cantidad: el redondeo es por línea", () => {
    // Calcular sobre el total (2000 / 1.18 = 1694.92, IGV 305.08, total 2000.00) da un céntimo menos que el
    // comprobante real, porque SUNAT exige el importe de cada línea redondeado a 2 decimales.
    const { total } = calcularTotales([gravado(2, 1000)], 18);
    expect(total).toBe(2000.01);
    expect(total).not.toBe(2000);
  });

  it("suma varias líneas gravadas acumulando línea por línea", () => {
    expect(calcularTotales([gravado(2, 1000), gravado(1, 500)], 18)).toMatchObject({
      gravado: 2118.65,
      igv: 381.36,
      total: 2500.01,
    });
  });

  it("en exoneradas e inafectas el precio ya viene sin IGV y no genera impuesto", () => {
    const items = [
      { cantidad: 3, precioUnitario: 150, tipoAfectacionIgv: "20" },
      { cantidad: 1, precioUnitario: 80.5, tipoAfectacionIgv: "30" },
    ];
    expect(calcularTotales(items, 18)).toMatchObject({ gravado: 0, exonerado: 450, inafecto: 80.5, igv: 0, total: 530.5 });
  });

  /** Caso contrastado contra `POST /v1/facturas` del backend real: devolvió exactamente estos seis importes. */
  it("coincide con el comprobante emitido en una factura mixta", () => {
    const items = [
      gravado(3, 33.9),
      { cantidad: 3, precioUnitario: 150, tipoAfectacionIgv: "20" },
      { cantidad: 2, precioUnitario: 50, tipoAfectacionIgv: "11" },
    ];
    expect(calcularTotales(items, 18)).toEqual({
      gravado: 86.19,
      exonerado: 450,
      inafecto: 0,
      gratuito: 100,
      igv: 15.51,
      total: 551.7,
    });
  });

  it("una línea gratuita suma al total gratuito y nunca al importe a pagar", () => {
    const con = calcularTotales([gravado(1, 118), { cantidad: 2, precioUnitario: 50, tipoAfectacionIgv: "11" }], 18);
    expect(con).toMatchObject({ gravado: 100, igv: 18, gratuito: 100, total: 118 });
  });

  it("usa la tasa reducida del padrón cuando corresponde", () => {
    // 110.50 / 1.105 = 100.00 exacto.
    expect(calcularTotales([gravado(1, 110.5)], 10.5)).toMatchObject({ gravado: 100, igv: 10.5, total: 110.5 });
  });

  /**
   * Con cantidad fraccionaria el resultado depende del dígito n.º 11 del valor referencial, que en punto flotante
   * se pierde: `1208.79 / 1.18` es 1024.39830508474576…, y escalar ese `double` por 1e10 redondeaba a …0848 en vez
   * de …0847. Un diezmilmillonésimo que acá se vuelve un céntimo. Contrastado contra la cadena de `ItemCalculado`.
   */
  it("acierta el céntimo con cantidades fraccionarias, donde el punto flotante fallaba", () => {
    expect(calcularTotales([gravado(0.59, 1208.79)], 18)).toMatchObject({
      gravado: 604.39,
      igv: 108.79,
      total: 713.18,
    });
  });

  it("mantiene la paridad con el dominio en cantidades de tres decimales", () => {
    expect(calcularTotales([gravado(1.125, 47.9)], 18)).toMatchObject({ gravado: 45.67, igv: 8.22, total: 53.89 });
    expect(calcularTotales([gravado(0.001, 9999.99)], 10.5)).toMatchObject({ gravado: 9.05, igv: 0.95, total: 10 });
  });

  it("el 17 es IVAP, una venta gravada: no se cuenta como gratuita", () => {
    // `TipoAfectacionIgv.IVAP("17", Tributo.IVAP, gravada=true, gratuita=false)`. Tratarlo como gratuita
    // previsualizaba total S/ 0 sobre una línea que el backend sí cobra.
    expect(esGratuita("17")).toBe(false);
    expect(esGratuita("16")).toBe(true);
    expect(esGratuita("21")).toBe(true);
    expect(esGratuita("10")).toBe(false);
  });

  it("ignora líneas incompletas mientras se escribe el formulario", () => {
    const items = [gravado(0, 100), gravado(1, 0), { cantidad: Number.NaN, precioUnitario: 10, tipoAfectacionIgv: "10" }];
    expect(calcularTotales(items, 18)).toMatchObject({ gravado: 0, igv: 0, total: 0 });
  });
});

describe("redondear", () => {
  it("desempata alejándose del cero, como BigDecimal.HALF_UP", () => {
    expect(redondear(2.345, 2)).toBe(2.35);
    expect(redondear(-2.345, 2)).toBe(-2.35);
    expect(redondear(2.344, 2)).toBe(2.34);
  });

  it("no se deja engañar por la representación binaria", () => {
    // (1.005).toFixed(2) === "1.00" porque 1.005 es en realidad 1.00499999...
    expect(redondear(1.005, 2)).toBe(1.01);
    expect(redondear(8.575, 2)).toBe(8.58);
  });
});

describe("factorIgv", () => {
  it("lleva el porcentaje a fracción con 6 decimales, como TasaIgv.factor", () => {
    expect(factorIgv(18)).toBe(0.18);
    expect(factorIgv(10.5)).toBe(0.105);
    expect(factorIgv(4)).toBe(0.04);
  });
});
