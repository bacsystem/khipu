import { describe, expect, it } from "vitest";
import type { ResumenDeFacturas } from "@/lib/api/facturas";
import { formatearMonto } from "@/lib/formato";
import {
  alertasDeComprobantes,
  alertasDeLaEmpresa,
  alertasDelPanel,
  enlaceAComprobantes,
  esEmpresaNueva,
  facturadoEn,
  mesEnCurso,
  mismoTramoDelMesAnterior,
  monedasDeLaSerie,
  serieDiaria,
  sinLoQueYaDiceLaGuia,
  variacion,
  variacionEnPalabras,
  ventanaDeAtencion,
} from "./panel";

describe("ventanaDeAtencion (H1)", () => {
  it("del 8 en adelante es el mes en curso", () => {
    expect(ventanaDeAtencion("2026-10-20")).toEqual({ desde: "2026-10-01", hasta: "2026-10-20" });
    expect(ventanaDeAtencion("2026-10-08")).toEqual({ desde: "2026-10-01", hasta: "2026-10-08" });
  });

  it("los primeros días del mes mira también los últimos 7 días del anterior (y del año anterior en enero)", () => {
    expect(ventanaDeAtencion("2026-10-02")).toEqual({ desde: "2026-09-25", hasta: "2026-10-02" });
    expect(ventanaDeAtencion("2026-10-07")).toEqual({ desde: "2026-09-30", hasta: "2026-10-07" });
    expect(ventanaDeAtencion("2027-01-01")).toEqual({ desde: "2026-12-25", hasta: "2027-01-01" });
  });

  /**
   * El caso que se escapaba: hoy es 2 de octubre y una boleta FIRMADA del 28 de setiembre (en su 5.º día, con el plazo cerrando el 7.º) sigue sin enviar. Con el resumen de
   * la ventana el aviso sale, y su enlace lleva al listado con ese mismo rango, hasta la última fecha que ya cuenta (hoy − 5).
   */
  it("el 2 del mes, una boleta FIRMADA del 28 del mes anterior avisa y su enlace la incluye", () => {
    const hoy = "2026-10-02";
    const ventana = ventanaDeAtencion(hoy);
    const alertas = alertasDelPanel(EMPRESA_AL_DIA, { atencion_requerida: { ...SIN_ATENCION, total: 1, boletas_sin_enviar: 1 } }, ventana, hoy);

    expect(alertas).toEqual([expect.objectContaining({ id: "boletas-sin-enviar", titulo: "1 boleta sigue sin enviar" })]);
    expect(alertas[0].enlace).toBe("/comprobantes?estado=FIRMADO&desde=2026-09-25&hasta=2026-09-27&tipo=03");
  });
});

describe("esEmpresaNueva (H4)", () => {
  it("es nueva si todas sus series siguen en 0 o no tiene ninguna", () => {
    expect(esEmpresaNueva([])).toBe(true);
    expect(esEmpresaNueva([{ ultimo_numero: 0 }, { ultimo_numero: 0 }])).toBe(true);
  });

  it("con un solo comprobante emitido en cualquier serie ya no lo es, aunque hoy no haya emitido nada", () => {
    expect(esEmpresaNueva([{ ultimo_numero: 0 }, { ultimo_numero: 312 }])).toBe(false);
  });

  it("si las series no se pudieron leer no se afirma que sea nueva", () => {
    expect(esEmpresaNueva(null)).toBe(false);
  });
});

const HOY = "2026-10-20";
const MES = { desde: "2026-10-01", hasta: "2026-10-20" };
const SIN_ATENCION: ResumenDeFacturas["atencion_requerida"] = { total: 0, rechazados: 0, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 0 };
const EMPRESA_AL_DIA = { tiene_certificado: true, certificado_vigencia_hasta: "2036-01-01", tiene_credenciales_sol: true, credenciales_sol_rechazadas: null };

describe("mesEnCurso y mismoTramoDelMesAnterior", () => {
  it("el mes en curso va del día 1 a hoy", () => {
    expect(mesEnCurso(HOY)).toEqual(MES);
    expect(mesEnCurso("2026-10-01")).toEqual({ desde: "2026-10-01", hasta: "2026-10-01" });
  });

  it("el mes anterior se compara con el mismo tramo, del 1 al mismo día", () => {
    expect(mismoTramoDelMesAnterior(HOY)).toEqual({ desde: "2026-09-01", hasta: "2026-09-20" });
  });

  it("si el mes anterior es más corto, llega hasta su último día (bisiesto incluido)", () => {
    expect(mismoTramoDelMesAnterior("2026-03-31")).toEqual({ desde: "2026-02-01", hasta: "2026-02-28" });
    expect(mismoTramoDelMesAnterior("2028-03-30")).toEqual({ desde: "2028-02-01", hasta: "2028-02-29" });
    expect(mismoTramoDelMesAnterior("2026-10-31")).toEqual({ desde: "2026-09-01", hasta: "2026-09-30" });
  });

  it("en enero el mes anterior es diciembre del año pasado", () => {
    expect(mismoTramoDelMesAnterior("2027-01-15")).toEqual({ desde: "2026-12-01", hasta: "2026-12-15" });
  });
});

describe("serieDiaria", () => {
  it("rellena con cero los días sin comprobantes y deja un punto por día, en orden", () => {
    const serie = serieDiaria({ dias: [{ fecha: "2026-10-03", emitidos: 4, facturado: [{ moneda: "PEN", total: 1180 }, { moneda: "USD", total: 50.5 }] }] }, { desde: "2026-10-01", hasta: "2026-10-05" });

    expect(serie.map((p) => p.fecha)).toEqual(["2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05"]);
    expect(serie.map((p) => p.emitidos)).toEqual([0, 0, 4, 0, 0]);
    expect(serie[2].facturado).toEqual({ PEN: 1180, USD: 50.5 });
    expect(serie[0].facturado).toEqual({});
  });

  it("sin días es una serie de ceros del largo del rango, y un día fuera del rango no estira el eje", () => {
    expect(serieDiaria({ dias: [] }, MES)).toHaveLength(20);
    expect(serieDiaria({ dias: [{ fecha: "2026-09-30", emitidos: 9, facturado: [] }] }, MES).every((p) => p.emitidos === 0)).toBe(true);
  });

  it("un rango de un solo día es un punto, y cruza bien el fin de mes", () => {
    expect(serieDiaria({ dias: [] }, { desde: "2026-10-01", hasta: "2026-10-01" })).toHaveLength(1);
    expect(serieDiaria({ dias: [] }, { desde: "2026-02-27", hasta: "2026-03-02" }).map((p) => p.fecha)).toEqual(["2026-02-27", "2026-02-28", "2026-03-01", "2026-03-02"]);
  });

  it("las monedas de la serie van sin repetir y ordenadas", () => {
    const serie = serieDiaria(
      { dias: [{ fecha: "2026-10-02", emitidos: 1, facturado: [{ moneda: "USD", total: 1 }] }, { fecha: "2026-10-04", emitidos: 1, facturado: [{ moneda: "PEN", total: 1 }, { moneda: "USD", total: 2 }] }] },
      MES,
    );
    expect(monedasDeLaSerie(serie)).toEqual(["PEN", "USD"]);
  });
});

describe("variacion", () => {
  it("dice cuánto cambió en por ciento entero, arriba o abajo", () => {
    expect(variacion(120, 100)).toBe(20);
    expect(variacion(50, 200)).toBe(-75);
    expect(variacion(5, 1)).toBe(400);
    expect(variacion(100, 100)).toBe(0);
  });

  it("sin base (cero, o el mes anterior no cargó) no inventa un porcentaje", () => {
    expect(variacion(10, 0)).toBeNull();
    expect(variacion(10, null)).toBeNull();
    expect(variacion(10, undefined)).toBeNull();
  });

  it("con un neto anterior negativo la dirección sigue el cambio", () => {
    expect(variacion(50, -100)).toBe(150);
  });

  it("se dice con su signo", () => {
    expect(variacionEnPalabras(12)).toBe("+12 % vs. el mes pasado");
    expect(variacionEnPalabras(-5)).toBe("−5 % vs. el mes pasado");
    expect(variacionEnPalabras(0)).toBe("igual que el mes pasado");
  });
});

describe("facturadoEn y el formato de los importes por moneda", () => {
  it("lo facturado de una moneda que no está es cero", () => {
    const r = { facturado: [{ moneda: "PEN", total: 418 }] };
    expect(facturadoEn(r, "PEN")).toBe(418);
    expect(facturadoEn(r, "USD")).toBe(0);
  });

  it("cada moneda con su símbolo, sus miles y dos decimales", () => {
    expect(formatearMonto("PEN", 12345.6)).toBe("S/ 12,345.60");
    expect(formatearMonto("USD", 50.5)).toBe("$ 50.50");
    expect(formatearMonto("PEN", -150)).toBe("S/ -150.00");
  });
});

describe("alertasDeLaEmpresa (#24)", () => {
  it("una empresa al día no avisa nada", () => {
    expect(alertasDeLaEmpresa(EMPRESA_AL_DIA, HOY)).toEqual([]);
  });

  it("sin certificado es un error que lleva a cargarlo", () => {
    const [a] = alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, tiene_certificado: false, certificado_vigencia_hasta: null }, HOY);
    expect(a).toMatchObject({ id: "certificado-ausente", tono: "error", enlace: "/empresa?seccion=certificado", accion: "Cargar certificado" });
  });

  it("un certificado vencido es un error distinto del que está por vencer", () => {
    const [a] = alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: "2026-10-19" }, HOY);
    expect(a).toMatchObject({ id: "certificado-vencido", tono: "error", titulo: "El certificado digital venció el 19 Oct 2026", accion: "Renovar certificado" });
  });

  it("por vencer en menos de 30 días es un aviso con los días que quedan; con 30 o más, nada", () => {
    expect(alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: "2026-10-30" }, HOY)[0]).toMatchObject({
      id: "certificado-por-vencer",
      tono: "aviso",
      titulo: "El certificado digital vence en 10 días",
      detalle: "Vence el 30 Oct 2026. Renuévalo antes para no cortar la emisión.",
    });
    expect(alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: "2026-10-21" }, HOY)[0].titulo).toBe("El certificado digital vence en 1 día");
    expect(alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: "2026-11-18" }, HOY)[0].titulo).toBe("El certificado digital vence en 29 días");
    expect(alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: "2026-11-19" }, HOY)).toEqual([]);
  });

  it("el certificado que vence hoy todavía firma: avisa que vence hoy, no que venció", () => {
    expect(alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: HOY }, HOY)[0]).toMatchObject({ id: "certificado-por-vencer", titulo: "El certificado digital vence hoy" });
  });

  it("un certificado sin fecha de vigencia no avisa: no se sabe cuándo vence", () => {
    expect(alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: null }, HOY)).toEqual([]);
  });

  it("credenciales SOL rechazadas son un error con el motivo de SUNAT; ausentes, un aviso", () => {
    const rechazadas = alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, credenciales_sol_rechazadas: { desde: "2026-10-09T15:00:00Z", motivo: "0102 - Usuario o contrasena incorrectos" } }, HOY);
    expect(rechazadas).toEqual([expect.objectContaining({ id: "sol-rechazadas", tono: "error", enlace: "/empresa?seccion=sol", accion: "Corregir credenciales" })]);
    expect(rechazadas[0].detalle).toContain("0102 - Usuario o contrasena incorrectos");

    expect(alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, tiene_credenciales_sol: false }, HOY)).toEqual([
      expect.objectContaining({ id: "sol-ausentes", tono: "aviso", enlace: "/empresa?seccion=sol", accion: "Guardar credenciales" }),
    ]);
  });
});

describe("alertasDeComprobantes (#24)", () => {
  it("sin atención requerida no avisa nada", () => {
    expect(alertasDeComprobantes(SIN_ATENCION, MES)).toEqual([]);
  });

  it("cada clase avisa con su cantidad y enlaza al listado filtrado por su estado y el rango del mes", () => {
    const alertas = alertasDeComprobantes({ total: 10, rechazados: 3, errores_de_envio: 2, fuera_de_plazo: 1, boletas_sin_enviar: 4 }, MES);

    expect(alertas.map((a) => a.id)).toEqual(["fuera-de-plazo", "rechazados", "errores-de-envio", "boletas-sin-enviar"]);
    expect(alertas[0]).toMatchObject({ tono: "error", titulo: "1 comprobante quedó fuera de plazo", enlace: "/comprobantes?estado=FUERA_DE_PLAZO&desde=2026-10-01&hasta=2026-10-20" });
    expect(alertas[1]).toMatchObject({ tono: "error", titulo: "SUNAT rechazó 3 comprobantes", enlace: "/comprobantes?estado=RECHAZADO&desde=2026-10-01&hasta=2026-10-20" });
    expect(alertas[2]).toMatchObject({ tono: "aviso", titulo: "2 comprobantes tienen error de envío", enlace: "/comprobantes?estado=ERROR_ENVIO&desde=2026-10-01&hasta=2026-10-20" });
    expect(alertas[3]).toMatchObject({ tono: "aviso", titulo: "4 boletas siguen sin enviar", enlace: "/comprobantes?estado=FIRMADO&desde=2026-10-01&hasta=2026-10-15&tipo=03" });
  });

  it("en singular y en plural", () => {
    const alertas = alertasDeComprobantes({ total: 4, rechazados: 1, errores_de_envio: 1, fuera_de_plazo: 2, boletas_sin_enviar: 1 }, MES);
    expect(alertas.map((a) => a.titulo)).toEqual(["2 comprobantes quedaron fuera de plazo", "SUNAT rechazó 1 comprobante", "1 comprobante tiene error de envío", "1 boleta sigue sin enviar"]);
  });

  it("el enlace lleva los filtros que el listado acepta", () => {
    expect(enlaceAComprobantes("RECHAZADO", MES)).toBe("/comprobantes?estado=RECHAZADO&desde=2026-10-01&hasta=2026-10-20");
  });
});

describe("alertasDelPanel", () => {
  it("van primero los errores y después los avisos", () => {
    const alertas = alertasDelPanel(
      { ...EMPRESA_AL_DIA, certificado_vigencia_hasta: "2026-10-25", tiene_credenciales_sol: false },
      { atencion_requerida: { ...SIN_ATENCION, total: 1, rechazados: 1 } },
      MES,
      HOY,
    );
    expect(alertas.map((a) => [a.id, a.tono])).toEqual([
      ["rechazados", "error"],
      ["certificado-por-vencer", "aviso"],
      ["sol-ausentes", "aviso"],
    ]);
  });

  it("lo que no se pudo leer no avisa nada, y lo que sí, sigue avisando", () => {
    expect(alertasDelPanel(null, null, MES, HOY)).toEqual([]);
    expect(alertasDelPanel(null, { atencion_requerida: { ...SIN_ATENCION, total: 1, fuera_de_plazo: 1 } }, MES, HOY).map((a) => a.id)).toEqual(["fuera-de-plazo"]);
    expect(alertasDelPanel({ ...EMPRESA_AL_DIA, tiene_certificado: false, certificado_vigencia_hasta: null }, null, MES, HOY).map((a) => a.id)).toEqual(["certificado-ausente"]);
  });
});

describe("sinLoQueYaDiceLaGuia", () => {
  const empresaSinNada = { tiene_certificado: false, certificado_vigencia_hasta: null, tiene_credenciales_sol: false, credenciales_sol_rechazadas: null };
  const alertas = [...alertasDeLaEmpresa(empresaSinNada, HOY), ...alertasDeComprobantes({ ...SIN_ATENCION, total: 1, rechazados: 1 }, MES)];

  it("quita los avisos de certificado y SOL cuyo paso la guía ya muestra pendiente, y deja los de comprobantes", () => {
    const pasos = [
      { id: "certificado" as const, listo: false },
      { id: "sol" as const, listo: false },
    ];
    expect(sinLoQueYaDiceLaGuia(alertas, pasos).map((a) => a.id)).toEqual(["rechazados"]);
  });

  it("si el paso está listo o no hay guía (no se pudo leer la empresa), el aviso queda", () => {
    expect(sinLoQueYaDiceLaGuia(alertas, [{ id: "certificado", listo: false }, { id: "sol", listo: true }]).map((a) => a.id)).toEqual(["sol-ausentes", "rechazados"]);
    expect(sinLoQueYaDiceLaGuia(alertas, null)).toEqual(alertas);
  });

  it("el certificado por vencer no es un paso pendiente: su aviso queda aunque la guía esté a la vista", () => {
    const porVencer = alertasDeLaEmpresa({ ...EMPRESA_AL_DIA, certificado_vigencia_hasta: "2026-10-25", tiene_credenciales_sol: false }, HOY);
    expect(sinLoQueYaDiceLaGuia(porVencer, [{ id: "certificado", listo: true }, { id: "sol", listo: false }]).map((a) => a.id)).toEqual(["certificado-por-vencer"]);
  });
});
