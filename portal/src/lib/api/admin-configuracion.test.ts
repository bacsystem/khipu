import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import {
  cambiarRemitente,
  esTipoDeCorreo,
  guardarPlantilla,
  hrefConfiguracion,
  listarPlantillas,
  obtenerBanner,
  obtenerRemitente,
  paramsConfiguracionDesdeUrl,
  publicarBanner,
  restablecerRemitente,
  restaurarPlantilla,
  retirarBanner,
  SECCIONES_DE_CONFIGURACION,
  vistaPreviaDePlantilla,
} from "./admin-configuracion";

afterEach(() => vi.unstubAllGlobals());

const sobre = (datos: unknown, status = 200) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status });

function simular(respuesta: Response) {
  const fetchMock = vi.fn().mockResolvedValue(respuesta);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const llamada = (fetchMock: ReturnType<typeof vi.fn>) => {
  const [url, init] = fetchMock.mock.calls[0];
  return { url: url as string, init, autorizacion: new Headers(init.headers).get("Authorization"), cabecera: (n: string) => new Headers(init.headers).get(n) };
};

describe("SECCIONES_DE_CONFIGURACION", () => {
  it("son las tres cosas que se configuran, en ese orden", () => {
    expect([...SECCIONES_DE_CONFIGURACION]).toEqual(["correo", "plantillas", "aviso"]);
  });
});

describe("esTipoDeCorreo", () => {
  it("acepta el nombre de un correo (mayúsculas y guion bajo)", () => {
    for (const bueno of ["RECUPERACION_CLAVE", "BIENVENIDA", "AVISO_CERTIFICADO_POR_VENCER", "A"]) expect(esTipoDeCorreo(bueno), bueno).toBe(true);
  });

  it("rechaza todo lo que podría salirse de la ruta o no es un nombre", () => {
    for (const malo of ["", "recuperacion_clave", "Recuperacion", "A/B", "../x", "A B", "A.B", "A-B", "A1", "X".repeat(41), "A\n", "%2F", "A?x=1"]) expect(esTipoDeCorreo(malo), malo).toBe(false);
  });

  it("40 caracteres es lo más largo", () => {
    expect(esTipoDeCorreo("X".repeat(40))).toBe(true);
  });
});

describe("paramsConfiguracionDesdeUrl", () => {
  it("sin nada es el correo saliente", () => {
    expect(paramsConfiguracionDesdeUrl({})).toEqual({ seccion: "correo" });
  });

  it("toma una sección válida y el correo elegido", () => {
    expect(paramsConfiguracionDesdeUrl({ seccion: "plantillas", plantilla: "BIENVENIDA" })).toEqual({ seccion: "plantillas", plantilla: "BIENVENIDA" });
    expect(paramsConfiguracionDesdeUrl({ seccion: "aviso" })).toEqual({ seccion: "aviso" });
  });

  it("una sección que no existe, en cualquier caja, es el correo saliente", () => {
    for (const mala of ["otra", "AVISO", "Plantillas", ""]) expect(paramsConfiguracionDesdeUrl({ seccion: mala }).seccion, mala).toBe("correo");
  });

  it("un correo con un nombre imposible se ignora en lugar de llegar a la URL del backend", () => {
    for (const malo of ["../auth", "bienvenida", "", "A/B"]) expect(paramsConfiguracionDesdeUrl({ seccion: "plantillas", plantilla: malo }), malo).toEqual({ seccion: "plantillas" });
  });
});

describe("hrefConfiguracion", () => {
  it("la ruta base queda limpia para el correo saliente", () => {
    expect(hrefConfiguracion({ seccion: "correo" })).toBe("/admin/configuracion");
  });

  it("las otras secciones van en la URL", () => {
    expect(hrefConfiguracion({ seccion: "aviso" })).toBe("/admin/configuracion?seccion=aviso");
    expect(hrefConfiguracion({ seccion: "plantillas" })).toBe("/admin/configuracion?seccion=plantillas");
  });

  it("el correo elegido solo cuenta en «Plantillas»", () => {
    expect(hrefConfiguracion({ seccion: "plantillas", plantilla: "BIENVENIDA" })).toBe("/admin/configuracion?seccion=plantillas&plantilla=BIENVENIDA");
    expect(hrefConfiguracion({ seccion: "aviso", plantilla: "BIENVENIDA" })).toBe("/admin/configuracion?seccion=aviso");
    expect(hrefConfiguracion({ seccion: "correo", plantilla: "BIENVENIDA" })).toBe("/admin/configuracion");
  });
});

describe("las lecturas", () => {
  it("el remitente se pide con el JWT del administrador", async () => {
    const fetchMock = simular(sobre({ vigente: { email: "a@khipu.pe" }, personalizado: false, predeterminado: { email: "a@khipu.pe" } }));

    const r = await obtenerRemitente("jwt-admin");

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/correo`);
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(l.init.method).toBeUndefined();
    expect(r.vigente.email).toBe("a@khipu.pe");
  });

  it("las plantillas se piden con el JWT del administrador", async () => {
    const fetchMock = simular(sobre([{ tipo: "BIENVENIDA" }]));

    const r = await listarPlantillas("jwt-admin");

    expect(llamada(fetchMock).url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/plantillas`);
    expect(llamada(fetchMock).autorizacion).toBe("Bearer jwt-admin");
    expect(r).toHaveLength(1);
  });

  it("el aviso se pide con el JWT y, si no hay ninguno, es null (nunca undefined)", async () => {
    const fetchMock = simular(sobre(null));

    expect(await obtenerBanner("jwt-admin")).toBeNull();
    expect(llamada(fetchMock).url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/banner`);
    expect(llamada(fetchMock).autorizacion).toBe("Bearer jwt-admin");
  });

  it("sin aviso la API omite el dato: sigue siendo null, nunca undefined", async () => {
    simular(new Response(JSON.stringify({ estado: "exito", mensaje: null, codigo: null, errores: null }), { status: 200 }));

    expect(await obtenerBanner("jwt-admin")).toBeNull();
  });

  it("el aviso publicado llega tal cual", async () => {
    simular(sobre({ texto: "Mantenimiento", desde: "a", hasta: "b", actualizado_en: "c", vigente_ahora: true }));

    expect((await obtenerBanner("jwt"))?.texto).toBe("Mantenimiento");
  });

  it("un rechazo del backend sube con su status y su código", async () => {
    simular(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "no", codigo: "NO_AUTORIZADO", errores: null }), { status: 401 }));

    await expect(obtenerRemitente("jwt-vencido")).rejects.toMatchObject({ status: 401, codigo: "NO_AUTORIZADO" });
  });
});

describe("los cambios", () => {
  const ORIGEN = { "x-forwarded-for": "203.0.113.7" };

  it("cambiar el remitente es un PUT con el cuerpo tal cual, el JWT y el origen del administrador", async () => {
    const fetchMock = simular(sobre({ vigente: { email: "a@khipu.pe" } }));

    await cambiarRemitente("jwt-admin", { nombre: "khipu", email: "a@khipu.pe" }, ORIGEN);

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/correo`);
    expect(l.init.method).toBe("PUT");
    expect(JSON.parse(l.init.body)).toEqual({ nombre: "khipu", email: "a@khipu.pe" });
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(l.cabecera("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("restablecer el remitente es un DELETE sin cuerpo, con el JWT y el origen", async () => {
    const fetchMock = simular(sobre({ vigente: { email: "a@khipu.pe" } }));

    await restablecerRemitente("jwt-admin", ORIGEN);

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/correo`);
    expect(l.init.method).toBe("DELETE");
    expect(l.init.body).toBeUndefined();
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(l.cabecera("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("guardar una plantilla es un PUT a la ruta de ese correo, con el cuerpo, el JWT y el origen", async () => {
    const fetchMock = simular(sobre({ tipo: "BIENVENIDA" }));

    await guardarPlantilla("jwt-admin", "BIENVENIDA", { asunto: "Hola", cuerpo: "Entra a {enlace}" }, ORIGEN);

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/plantillas/BIENVENIDA`);
    expect(l.init.method).toBe("PUT");
    expect(JSON.parse(l.init.body)).toEqual({ asunto: "Hola", cuerpo: "Entra a {enlace}" });
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(l.cabecera("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("restaurar una plantilla es un DELETE a la ruta de ese correo, con el JWT y el origen", async () => {
    const fetchMock = simular(sobre({ tipo: "BIENVENIDA" }));

    await restaurarPlantilla("jwt-admin", "BIENVENIDA", ORIGEN);

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/plantillas/BIENVENIDA`);
    expect(l.init.method).toBe("DELETE");
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(l.cabecera("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("la vista previa es un POST a la ruta de ese correo, con el cuerpo y el JWT", async () => {
    const fetchMock = simular(sobre({ asunto: "Hola", cuerpo: "Entra" }));

    const r = await vistaPreviaDePlantilla("jwt-admin", "RECUPERACION_CLAVE", { asunto: "Hola", cuerpo: "Entra a {enlace}" });

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/plantillas/RECUPERACION_CLAVE/vista-previa`);
    expect(l.init.method).toBe("POST");
    expect(JSON.parse(l.init.body)).toEqual({ asunto: "Hola", cuerpo: "Entra a {enlace}" });
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(r.asunto).toBe("Hola");
  });

  it("publicar el aviso es un PUT con el cuerpo tal cual, el JWT y el origen", async () => {
    const fetchMock = simular(sobre({ texto: "x" }));

    await publicarBanner("jwt-admin", { texto: "x", desde: "a", hasta: "b" }, ORIGEN);

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/banner`);
    expect(l.init.method).toBe("PUT");
    expect(JSON.parse(l.init.body)).toEqual({ texto: "x", desde: "a", hasta: "b" });
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(l.cabecera("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("retirar el aviso es un DELETE con el JWT y el origen", async () => {
    const fetchMock = simular(sobre(null));

    await retirarBanner("jwt-admin", ORIGEN);

    const l = llamada(fetchMock);
    expect(l.url).toBe(`${apiBaseUrl()}/v1/admin/configuracion/banner`);
    expect(l.init.method).toBe("DELETE");
    expect(l.autorizacion).toBe("Bearer jwt-admin");
    expect(l.cabecera("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un rechazo de dominio sube con su código y su mensaje", async () => {
    simular(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "El cuerpo tiene que incluir {enlace}", codigo: "PLANTILLA_INVALIDA", errores: null }), { status: 422 }));

    await expect(guardarPlantilla("jwt", "BIENVENIDA", {})).rejects.toMatchObject({ status: 422, codigo: "PLANTILLA_INVALIDA", message: "El cuerpo tiene que incluir {enlace}" });
  });
});
