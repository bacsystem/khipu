import { describe, expect, it } from "vitest";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import { NOMBRE_MAX, pruebasDeLaCuenta, validarPlan, valoresDePlan, VALORES_NUEVO_PLAN, type ValoresDePlan } from "./planes-formulario";
import es from "@/messages/es.json";

const VALIDOS: ValoresDePlan = {
  nombre: "Estudio",
  precio: "49.90",
  documentos: "800",
  documentosIlimitado: false,
  periodo: "MENSUAL",
  rucs: "2",
  usuarios: "3",
  usuariosIlimitado: false,
  apiKeys: "5",
  apiKeysIlimitado: false,
  retencion: "6",
  pruebasPorRuc: "1000",
  pruebasPorRucIlimitado: false,
  visibleEnPublicidad: true,
};

function errores(cambios: Partial<ValoresDePlan>) {
  const r = validarPlan({ ...VALIDOS, ...cambios }, es);
  return "errores" in r ? r.errores : {};
}

/** El formulario de planes (#190): valida lo evidente antes de enviar; la autoridad sigue siendo el backend. */
describe("validarPlan", () => {
  it("con valores válidos arma el cuerpo que espera el backend", () => {
    const r = validarPlan(VALIDOS, es);

    expect(r).toEqual({
      cuerpo: {
        nombre: "Estudio",
        precio_mensual: 49.9,
        limites: {
          documentos_al_mes: { maximo: 800, ilimitado: false },
          rucs: 2,
          usuarios: { maximo: 3, ilimitado: false },
          api_keys: { maximo: 5, ilimitado: false },
          retencion_anios: 6,
          periodo_documentos: "MENSUAL",
          pruebas_beta_por_ruc: { maximo: 1000, ilimitado: false },
        },
        visible_en_publicidad: true,
      },
    });
  });

  it("«Ilimitado» manda el límite sin máximo, aunque el campo todavía tenga un número escrito", () => {
    const r = validarPlan({ ...VALIDOS, documentosIlimitado: true, usuariosIlimitado: true, apiKeysIlimitado: true }, es);

    expect(r).toMatchObject({
      cuerpo: {
        limites: { documentos_al_mes: { ilimitado: true }, usuarios: { ilimitado: true }, api_keys: { ilimitado: true } },
      },
    });
    if ("cuerpo" in r) {
      expect(r.cuerpo.limites.documentos_al_mes).not.toHaveProperty("maximo");
      expect(r.cuerpo.limites.usuarios).not.toHaveProperty("maximo");
      expect(r.cuerpo.limites.api_keys).not.toHaveProperty("maximo");
    }
  });

  it("un límite ilimitado no exige que el campo tenga un número", () => {
    expect(errores({ documentosIlimitado: true, documentos: "" })).toEqual({});
  });

  it("el nombre se recorta, es obligatorio y tiene tope", () => {
    const recortado = validarPlan({ ...VALIDOS, nombre: "  Estudio  " }, es);
    expect("cuerpo" in recortado && recortado.cuerpo.nombre).toBe("Estudio");
    expect(errores({ nombre: "   " }).nombre).toMatch(/Escribe el nombre/);
    expect(errores({ nombre: "" }).nombre).toMatch(/Escribe el nombre/);
    expect(errores({ nombre: "x".repeat(NOMBRE_MAX) })).toEqual({});
    expect(errores({ nombre: "x".repeat(NOMBRE_MAX + 1) }).nombre).toMatch(/hasta 40/);
  });

  it("el precio es cero o más, con hasta dos decimales", () => {
    for (const bueno of ["0", "29", "29.5", "29.50", "0.01", "1000"]) expect(errores({ precio: bueno }).precio, bueno).toBeUndefined();
    for (const malo of ["", " ", "-1", "-0.01", "29.999", "abc", "1e3", "29,50", "1.", ".5"]) expect(errores({ precio: malo }).precio, malo).toMatch(/precio/);
  });

  it("el precio cabe en la columna del backend (NUMERIC(10,2)): más alto, el backend no lo guarda", () => {
    expect(errores({ precio: "99999999.99" }).precio).toBeUndefined();
    for (const malo of ["100000000", "100000000.00", "999999999"]) expect(errores({ precio: malo }).precio, malo).toMatch(/precio/);
  });

  it("los números son enteros mayores que cero", () => {
    for (const campo of ["documentos", "rucs", "usuarios", "apiKeys", "retencion"] as const) {
      for (const bueno of ["1", "10", "2147483647"]) expect(errores({ [campo]: bueno })[campo], `${campo}=${bueno}`).toBeUndefined();
      for (const malo of ["", "0", "-1", "1.5", "abc", "1e3", "2147483648", "00"])
        expect(errores({ [campo]: malo })[campo], `${campo}=${malo}`).toBeDefined();
    }
  });

  it("los límites que pueden ser ilimitados dicen cómo; RUC y retención, solo el número", () => {
    expect(errores({ documentos: "0" }).documentos).toMatch(/Ilimitado/);
    expect(errores({ usuarios: "x" }).usuarios).toMatch(/Ilimitado/);
    expect(errores({ apiKeys: "" }).apiKeys).toMatch(/Ilimitado/);
    expect(errores({ rucs: "0" }).rucs).not.toMatch(/Ilimitado/);
    expect(errores({ retencion: "0" }).retencion).not.toMatch(/Ilimitado/);
  });

  it("junta todos los errores a la vez, para no corregirlos de uno en uno", () => {
    const e = errores({ nombre: "", precio: "-1", documentos: "0", rucs: "0", retencion: "0" });

    expect(Object.keys(e).sort()).toEqual(["documentos", "nombre", "precio", "retencion", "rucs"]);
  });

  it("con errores no devuelve cuerpo", () => {
    expect(validarPlan({ ...VALIDOS, nombre: "" }, es)).not.toHaveProperty("cuerpo");
  });
});

describe("valoresDePlan", () => {
  const plan: PlanAdmin = {
    id: "p1",
    nombre: "Negocio",
    precio_mensual: 69,
    limites: { documentos_al_mes: { maximo: 1500, ilimitado: false }, rucs: 3, usuarios: { maximo: 3, ilimitado: false }, api_keys: { ilimitado: true }, retencion_anios: 5, periodo_documentos: "MENSUAL" as const, pruebas_beta_por_ruc: { maximo: 1000, ilimitado: false }, pruebas_beta_al_mes: { maximo: 1000, ilimitado: false } },
    estado: "ACTIVO",
    por_defecto: false, visible_en_publicidad: true,
    cuentas: 4,
  };

  it("un plan nuevo empieza con campos vacíos y nada marcado como ilimitado", () => {
    expect(VALORES_NUEVO_PLAN).toMatchObject({ nombre: "", precio: "", documentos: "", rucs: "", usuarios: "", apiKeys: "", retencion: "" });
    expect(VALORES_NUEVO_PLAN.documentosIlimitado).toBe(false);
    expect(VALORES_NUEVO_PLAN.usuariosIlimitado).toBe(false);
    expect(VALORES_NUEVO_PLAN.apiKeysIlimitado).toBe(false);
  });

  it("prellena el formulario con los límites del plan y el precio con dos decimales", () => {
    expect(valoresDePlan(plan)).toEqual({
      nombre: "Negocio",
      precio: "69.00",
      documentos: "1500",
      documentosIlimitado: false,
      rucs: "3",
      usuarios: "3",
      usuariosIlimitado: false,
      apiKeys: "",
      apiKeysIlimitado: true,
      retencion: "5",
      periodo: "MENSUAL",
      pruebasPorRuc: "1000",
      pruebasPorRucIlimitado: false,
      visibleEnPublicidad: true,
    });
  });

  it("si hay un cambio programado, prellena con los límites programados (lo último que se decidió)", () => {
    const conCambio: PlanAdmin = {
      ...plan,
      limites_programados: {
        aplica_desde: "2026-11-01T05:00:00Z",
        limites: { documentos_al_mes: { maximo: 3000, ilimitado: false }, rucs: 4, usuarios: { ilimitado: true }, api_keys: { maximo: 9, ilimitado: false }, retencion_anios: 7, periodo_documentos: "MENSUAL" as const, pruebas_beta_por_ruc: { maximo: 1000, ilimitado: false }, pruebas_beta_al_mes: { maximo: 1000, ilimitado: false } },
      },
    };

    expect(valoresDePlan(conCambio)).toMatchObject({ documentos: "3000", rucs: "4", usuariosIlimitado: true, apiKeys: "9", apiKeysIlimitado: false, retencion: "7", nombre: "Negocio", precio: "69.00" });
  });

  it("lo que se prellena pasa la validación y vuelve a armar los mismos límites", () => {
    const r = validarPlan(valoresDePlan(plan), es);

    expect(r).toMatchObject({ cuerpo: { nombre: "Negocio", precio_mensual: 69, limites: { rucs: 3, retencion_anios: 5, periodo_documentos: "MENSUAL", pruebas_beta_por_ruc: { maximo: 1000, ilimitado: false }, api_keys: { ilimitado: true } } } });
  });
});

/** H20: un plan a medida para un cliente se vende igual pero no sale en la página de precios. */
describe("visible en publicidad", () => {
  it("un plan nuevo se publica salvo que se diga otra cosa", () => {
    expect(VALORES_NUEVO_PLAN.visibleEnPublicidad).toBe(true);
  });

  it("viaja en el cuerpo tal como se marcó", () => {
    expect(validarPlan({ ...VALIDOS, visibleEnPublicidad: false }, es)).toMatchObject({ cuerpo: { visible_en_publicidad: false } });
  });

  it("al editar se prellena con lo que tiene el plan", () => {
    const plan = { visible_en_publicidad: false, nombre: "A medida", precio_mensual: 80, limites: { documentos_al_mes: { maximo: 900, ilimitado: false }, rucs: 1, usuarios: { maximo: 1, ilimitado: false }, api_keys: { maximo: 1, ilimitado: false }, retencion_anios: 5, periodo_documentos: "MENSUAL" as const, pruebas_beta_por_ruc: { maximo: 1000, ilimitado: false }, pruebas_beta_al_mes: { maximo: 1000, ilimitado: false } } } as PlanAdmin;
    expect(valoresDePlan(plan).visibleEnPublicidad).toBe(false);
  });
});

/** #613: el tope de documentos puede ser mensual o total, y cada plan da pruebas en Beta por RUC. */
describe("periodo del tope y pruebas en Beta", () => {
  it("un plan nuevo empieza mensual y con 1 000 pruebas por RUC, lo que dio el dueño a todos los planes", () => {
    expect(VALORES_NUEVO_PLAN).toMatchObject({ periodo: "MENSUAL", pruebasPorRuc: "1000", pruebasPorRucIlimitado: false });
  });

  it("el periodo y las pruebas por RUC viajan en el cuerpo; las pruebas de la cuenta no, las calcula el backend", () => {
    const r = validarPlan({ ...VALIDOS, periodo: "TOTAL", documentos: "10", pruebasPorRuc: "500" }, es);

    expect(r).toMatchObject({ cuerpo: { limites: { documentos_al_mes: { maximo: 10, ilimitado: false }, periodo_documentos: "TOTAL", pruebas_beta_por_ruc: { maximo: 500, ilimitado: false } } } });
    if ("cuerpo" in r) expect(r.cuerpo.limites).not.toHaveProperty("pruebas_beta_al_mes");
  });

  it("un tope en total necesita una cifra: con «Ilimitado» es un error del campo, como lo rechazaría el backend", () => {
    expect(errores({ periodo: "TOTAL", documentosIlimitado: true }).documentos).toMatch(/en total necesita una cifra/);
    expect(errores({ periodo: "MENSUAL", documentosIlimitado: true })).toEqual({});
  });

  it("las pruebas por RUC son un entero mayor que cero, o ilimitadas", () => {
    for (const malo of ["", "0", "-1", "1.5", "abc"]) expect(errores({ pruebasPorRuc: malo }).pruebasPorRuc, malo).toMatch(/Ilimitado/);
    expect(errores({ pruebasPorRuc: "", pruebasPorRucIlimitado: true })).toEqual({});
    expect(validarPlan({ ...VALIDOS, pruebasPorRucIlimitado: true }, es)).toMatchObject({ cuerpo: { limites: { pruebas_beta_por_ruc: { ilimitado: true } } } });
  });

  it("al editar se prellena con el periodo y las pruebas del plan (las programadas primero)", () => {
    const plan = {
      nombre: "Gratis",
      precio_mensual: 0,
      visible_en_publicidad: true,
      limites: { ...VALORES_LIMITES, periodo_documentos: "MENSUAL" },
      limites_programados: { aplica_desde: "2026-12-01T05:00:00Z", limites: { ...VALORES_LIMITES, periodo_documentos: "TOTAL", pruebas_beta_por_ruc: { ilimitado: true } } },
    } as unknown as PlanAdmin;

    expect(valoresDePlan(plan)).toMatchObject({ periodo: "TOTAL", pruebasPorRuc: "", pruebasPorRucIlimitado: true });
  });

  it("dice en vivo cuántas pruebas al mes tiene la cuenta: las de cada RUC por los RUC", () => {
    expect(pruebasDeLaCuenta({ pruebasPorRuc: "1000", pruebasPorRucIlimitado: false, rucs: "3" }, es)).toBe(
      "= 3,000 pruebas al mes para la cuenta con 3 RUC, compartidas entre sus empresas.",
    );
    expect(pruebasDeLaCuenta({ pruebasPorRuc: "1000", pruebasPorRucIlimitado: true, rucs: "3" }, es)).toBe("Sin tope de pruebas en Beta.");
    expect(pruebasDeLaCuenta({ pruebasPorRuc: "1000", pruebasPorRucIlimitado: false, rucs: "" }, es)).toMatch(/para ver el total/);
    expect(pruebasDeLaCuenta({ pruebasPorRuc: "x", pruebasPorRucIlimitado: false, rucs: "3" }, es)).toMatch(/para ver el total/);
  });
});

const VALORES_LIMITES = {
  documentos_al_mes: { maximo: 10, ilimitado: false },
  rucs: 1,
  usuarios: { maximo: 1, ilimitado: false },
  api_keys: { maximo: 1, ilimitado: false },
  retencion_anios: 1,
  pruebas_beta_por_ruc: { maximo: 1000, ilimitado: false },
  pruebas_beta_al_mes: { maximo: 1000, ilimitado: false },
};
