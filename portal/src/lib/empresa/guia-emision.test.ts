import { describe, expect, it } from "vitest";
import type { Domicilio } from "@/lib/api/empresas";
import type { Serie } from "@/lib/api/series";
import { hayPasosPendientes, pasosParaEmitir } from "./guia-emision";

const HOY = "2026-10-09";
const domicilio: Domicilio = {
  ubigeo: "150122",
  direccion: "AV. LARCO 1232",
  urbanizacion: null,
  distrito: "MIRAFLORES",
  provincia: "LIMA",
  departamento: "LIMA",
  codigo_establecimiento: "0000",
};
const lista = {
  tiene_certificado: true,
  certificado_vigencia_hasta: "2027-01-01",
  tiene_credenciales_sol: true,
  credenciales_sol_rechazadas: null,
  domicilio,
};
const vacia = { tiene_certificado: false, certificado_vigencia_hasta: null, tiene_credenciales_sol: false, credenciales_sol_rechazadas: null, domicilio: null };
const factura: Serie = { tipo: "01", serie: "F001", ultimo_numero: 0, activa: true };

const listos = (pasos: ReturnType<typeof pasosParaEmitir>) => Object.fromEntries(pasos.map((p) => [p.id, p.listo]));

/** #375: la guía «Para empezar a emitir» — los cuatro pasos, su estado y a dónde lleva cada uno. */
describe("pasosParaEmitir", () => {
  it("con la empresa recién creada, los cuatro pasos están pendientes y cada uno lleva a su sección", () => {
    const pasos = pasosParaEmitir(vacia, [], HOY);
    expect(pasos.map((p) => p.id)).toEqual(["certificado", "sol", "domicilio", "series"]);
    expect(pasos.every((p) => !p.listo)).toBe(true);
    expect(pasos.map((p) => p.enlace)).toEqual(["/empresa?seccion=certificado", "/empresa?seccion=sol", "/empresa?seccion=datos", "/series"]);
    expect(hayPasosPendientes(pasos)).toBe(true);
  });

  it("con todo hecho no queda nada pendiente", () => {
    const pasos = pasosParaEmitir(lista, [factura], HOY);
    expect(pasos.every((p) => p.listo)).toBe(true);
    expect(hayPasosPendientes(pasos)).toBe(false);
  });

  it("el certificado y SOL siguen las reglas de la pestaña de la empresa: vencido o rechazadas no cuentan como listos", () => {
    expect(listos(pasosParaEmitir({ ...lista, certificado_vigencia_hasta: "2026-10-08" }, [factura], HOY)).certificado).toBe(false);
    // El que vence hoy todavía firma.
    expect(listos(pasosParaEmitir({ ...lista, certificado_vigencia_hasta: HOY }, [factura], HOY)).certificado).toBe(true);
    expect(listos(pasosParaEmitir({ ...lista, credenciales_sol_rechazadas: { desde: "2026-10-08T10:00:00Z", motivo: "0102" } }, [factura], HOY)).sol).toBe(false);
  });

  it("dice si el certificado falta o está vencido, y si SOL falta o la rechazó SUNAT", () => {
    const [sinCert, sinSol] = pasosParaEmitir(vacia, [], HOY);
    expect(sinCert.accion).toBe("Cargar certificado");
    expect(sinSol.accion).toBe("Guardar credenciales");
    const [vencido, rechazadas] = pasosParaEmitir(
      { ...lista, certificado_vigencia_hasta: "2026-10-08", credenciales_sol_rechazadas: { desde: "2026-10-08T10:00:00Z", motivo: "0102" } },
      [factura],
      HOY,
    );
    expect(vencido.accion).toBe("Renovar certificado");
    expect(vencido.detalle).toMatch(/venció/);
    expect(rechazadas.accion).toBe("Corregir credenciales");
    expect(rechazadas.detalle).toMatch(/rechaz/);
  });

  it("el domicilio fiscal es recomendado, no obligatorio, y está listo cuando hay uno registrado", () => {
    const sin = pasosParaEmitir({ ...lista, domicilio: null }, [factura], HOY).find((p) => p.id === "domicilio")!;
    expect(sin).toMatchObject({ listo: false, recomendado: true });
    expect(pasosParaEmitir(lista, [factura], HOY).find((p) => p.id === "domicilio")!.listo).toBe(true);
    // Los obligatorios no son «recomendados».
    expect(pasosParaEmitir(vacia, [], HOY).filter((p) => p.recomendado).map((p) => p.id)).toEqual(["domicilio"]);
  });

  it("las series están listas con una de factura o de boleta activa; una inactiva o una nota sola no bastan", () => {
    const serieListo = (series: Serie[]) => listos(pasosParaEmitir(lista, series, HOY)).series;
    expect(serieListo([factura])).toBe(true);
    expect(serieListo([{ ...factura, tipo: "03", serie: "B001" }])).toBe(true);
    expect(serieListo([])).toBe(false);
    expect(serieListo([{ ...factura, activa: false }])).toBe(false);
    expect(serieListo([{ ...factura, tipo: "07", serie: "FC01" }])).toBe(false);
  });

  it("si la serie existe pero está inactiva, no aconseja crear la misma: repetirla da 409", () => {
    const detalle = (series: Serie[]) => pasosParaEmitir(lista, series, HOY).find((p) => p.id === "series")!.detalle;
    expect(detalle([{ ...factura, activa: false }])).toMatch(/inactiva/);
    expect(detalle([{ ...factura, activa: false }])).not.toMatch(/\(F001\)/);
    // Sin series de factura ni de boleta (o solo notas) sí se pide crear una.
    expect(detalle([])).toMatch(/Crea al menos una serie/);
    expect(detalle([{ ...factura, tipo: "07", serie: "FC01" }])).toMatch(/Crea al menos una serie/);
  });

  it("si las series no se pudieron leer, el paso se omite en vez de afirmar que faltan", () => {
    const pasos = pasosParaEmitir(lista, null, HOY);
    expect(pasos.map((p) => p.id)).toEqual(["certificado", "sol", "domicilio"]);
    expect(hayPasosPendientes(pasos)).toBe(false);
  });
});
