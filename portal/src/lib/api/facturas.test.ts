import { describe, expect, it } from "vitest";
import { admiteBaja, admiteNotas, normalizarComprobante, totalDesdeHeaders, type Baja, type Comprobante } from "./facturas";

/**
 * `admiteNotas` es la única puerta al flujo irreversible de notas (la ficha ofrece «Emitir nota» y `/nota` deja
 * pasar según ella) y no tenía ningún test: la auditoría la mutó a `return true` y 40/40 e2e siguieron en verde.
 */
describe("admiteNotas", () => {
  const f = (tipo: string, estado_documento: string) => ({ tipo, estado_documento }) as Pick<Comprobante, "tipo" | "estado_documento">;

  it("solo una factura aceptada por SUNAT, con o sin observaciones", () => {
    expect(admiteNotas(f("01", "ACEPTADO"))).toBe(true);
    expect(admiteNotas(f("01", "ACEPTADO_CON_OBS"))).toBe(true);
  });

  it("no sobre boletas ni sobre otras notas: las boletas van en el resumen diario (#20) y el backend responde 2116", () => {
    expect(admiteNotas(f("03", "ACEPTADO"))).toBe(false);
    expect(admiteNotas(f("07", "ACEPTADO"))).toBe(false);
    expect(admiteNotas(f("08", "ACEPTADO"))).toBe(false);
  });

  it("no sobre una factura que SUNAT no aceptó o que ya está anulada (2119/2120)", () => {
    for (const estado of ["FIRMADO", "ENVIADA", "ERROR_ENVIO", "RECHAZADO", "INVALIDO", "ANULADO"]) {
      expect(admiteNotas(f("01", estado)), estado).toBe(false);
    }
  });

  it("tampoco con una baja en curso: si SUNAT la acepta, la nota cae sobre una factura anulada (2120)", () => {
    const conBaja = (estado: string) => ({ ...f("01", "ACEPTADO"), baja: { estado } }) as unknown as Parameters<typeof admiteNotas>[0];
    for (const estado of ["GENERADA", "ENVIADA", "ERROR_ENVIO"]) expect(admiteNotas(conBaja(estado)), estado).toBe(false);
    // Una baja rechazada por SUNAT no cambia nada: la factura sigue aceptada.
    expect(admiteNotas(conBaja("RECHAZADA"))).toBe(true);
  });
});

const base = {
  id: "abc",
  tipo: "01",
  serie: "F001",
  numero: 1,
  fecha_emision: "2026-09-01",
  moneda: "PEN",
  estado_documento: "ACEPTADO",
  hash: "h",
  intentos: 1,
  ultimo_error: null,
  cdr: { codigo: "0", descripcion: "ok" },
  totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
  enlaces: { xml: "/x", cdr: "/c" },
} as unknown as Partial<Comprobante> & Pick<Comprobante, "id">;

describe("normalizarComprobante", () => {
  it("rellena los campos que un backend anterior no envía", () => {
    const c = normalizarComprobante(base);
    expect(c.items).toEqual([]);
    expect(c.receptor).toBeNull();
    expect(c.tipo_operacion).toBeNull();
    expect(c.nombre_archivo).toBeNull();
    expect(c.cdr?.observaciones).toEqual([]);
    expect(c.forma_pago).toEqual({ tipo: "contado", monto_pendiente: null, cuotas: [] });
  });

  it("conserva los campos cuando vienen", () => {
    const c = normalizarComprobante({
      ...base,
      items: [{ codigo: "A", descripcion: "d", unidad: "ZZ", cantidad: 1, precio_unitario: 2, tipo_afectacion_igv: "10" }],
      receptor: { tipo_doc: "6", num_doc: "20123456786", razon_social: "X", direccion: null },
    });
    expect(c.items).toHaveLength(1);
    expect(c.receptor?.num_doc).toBe("20123456786");
  });
});

describe("totalDesdeHeaders", () => {
  it("usa x-total-count cuando existe y el fallback cuando no", () => {
    expect(totalDesdeHeaders(new Headers({ "x-total-count": "126" }), 2)).toBe(126);
    expect(totalDesdeHeaders(new Headers(), 2)).toBe(2);
  });
});

describe("admiteBaja", () => {
  const hoy = "2026-09-18";
  const aceptada: Parameters<typeof admiteBaja>[0] = { tipo: "01", estado_documento: "ACEPTADO", fecha_emision: "2026-09-15", baja: null };
  const bajaEn = (estado: Baja["estado"]): Baja => ({ id: "b", identificador: "RA-20260918-1", comprobante: "F001-1", tipo_comprobante: "01", fecha_generacion: "2026-09-18", motivo: "m", estado, ticket: null, cdr: null, intentos: 1, ultimo_error: null });

  it("acepta facturas y notas aceptadas (con o sin observaciones) dentro de los 7 días", () => {
    expect(admiteBaja(aceptada, hoy)).toBe(true);
    expect(admiteBaja({ ...aceptada, tipo: "07", estado_documento: "ACEPTADO_CON_OBS" }, hoy)).toBe(true);
    expect(admiteBaja({ ...aceptada, fecha_emision: "2026-09-11" }, hoy)).toBe(true); // justo 7 días (regla 2957)
  });

  it("rechaza fuera de plazo, boletas, estados no aceptados y bajas en curso", () => {
    expect(admiteBaja({ ...aceptada, fecha_emision: "2026-09-10" }, hoy)).toBe(false);
    expect(admiteBaja({ ...aceptada, tipo: "03" }, hoy)).toBe(false);
    expect(admiteBaja({ ...aceptada, estado_documento: "ANULADO" }, hoy)).toBe(false);
    expect(admiteBaja({ ...aceptada, estado_documento: "FIRMADO" }, hoy)).toBe(false);
    expect(admiteBaja({ ...aceptada, baja: bajaEn("ENVIADA") }, hoy)).toBe(false);
    expect(admiteBaja({ ...aceptada, baja: bajaEn("ERROR_ENVIO") }, hoy)).toBe(false);
    // Una baja rechazada por SUNAT no bloquea un nuevo intento.
    expect(admiteBaja({ ...aceptada, baja: bajaEn("RECHAZADA") }, hoy)).toBe(true);
  });
});

describe("filtros del listado (#6)", async () => {
  const { filtrosDesdeParams, paramsDeFiltros } = await import("./facturas");

  it("acepta solo lo que la API aceptaría y normaliza la serie", () => {
    expect(filtrosDesdeParams({ estado: "ACEPTADO", desde: "2026-09-01", hasta: "2026-09-13", serie: "f001" })).toEqual({
      estado: "ACEPTADO",
      desde: "2026-09-01",
      hasta: "2026-09-13",
      serie: "F001",
    });
    expect(filtrosDesdeParams({ estado: "OTRO", desde: "13/09/2026", hasta: "", serie: "F0001" })).toEqual({});
    expect(filtrosDesdeParams({})).toEqual({});
  });

  it("descarta hasta si es anterior a desde para no pedir un rango inválido", () => {
    expect(filtrosDesdeParams({ desde: "2026-09-13", hasta: "2026-09-01" })).toEqual({ desde: "2026-09-13" });
  });

  it("serializa los filtros como query string de la API", () => {
    expect(paramsDeFiltros({ estado: "ERROR_ENVIO", serie: "F001" }).toString()).toBe("estado=ERROR_ENVIO&serie=F001");
    expect(paramsDeFiltros({}).toString()).toBe("");
  });
});
