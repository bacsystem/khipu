import { afterEach, describe, expect, it, vi } from "vitest";
import {
  avisarSesionTerminada,
  CABECERA_SESION,
  empezarSesionNueva,
  esSesionTerminada,
  leerSesionTerminada,
  montarDialogo,
  rutaDeLoginDeVuelta,
  SESION_TERMINADA,
  suscribirseASesionTerminada,
} from "./sesion-terminada";

const asignar = vi.fn();

function estarEn(pathname: string, search = "") {
  vi.stubGlobal("location", { assign: asignar, pathname, search });
}

afterEach(() => {
  asignar.mockClear();
  vi.unstubAllGlobals();
});

describe("esSesionTerminada", () => {
  it("solo el 401 con la marca del proxy", () => {
    expect(esSesionTerminada(new Response(null, { status: 401, headers: { [CABECERA_SESION]: SESION_TERMINADA } }))).toBe(true);
    expect(esSesionTerminada(new Response(null, { status: 401 }))).toBe(false);
    expect(esSesionTerminada(new Response(null, { status: 403, headers: { [CABECERA_SESION]: SESION_TERMINADA } }))).toBe(false);
    expect(esSesionTerminada(new Response(null, { status: 401, headers: { [CABECERA_SESION]: "viva" } }))).toBe(false);
  });

  it("no toca las cabeceras de lo que no es un 401 (los mocks de fetch de otras pruebas no las traen)", () => {
    expect(esSesionTerminada({ status: 200 } as Response)).toBe(false);
  });
});

describe("rutaDeLoginDeVuelta", () => {
  it("lleva la ruta y la consulta actuales, codificadas, en next", () => {
    estarEn("/comprobantes", "?estado=ACEPTADO&pagina=2");

    expect(rutaDeLoginDeVuelta()).toBe("/login?next=%2Fcomprobantes%3Festado%3DACEPTADO%26pagina%3D2");
  });

  it("desde el propio login no manda next: volvería a él", () => {
    estarEn("/login", "?motivo=otra-pestana");

    expect(rutaDeLoginDeVuelta()).toBe("/login");
  });
});

/** #654: un booleano que, una vez activado, no vuelve atrás: varios 401 en vuelo abren un solo diálogo, sin importar el orden. */
describe("avisarSesionTerminada", () => {
  it("con un diálogo montado activa el estado y avisa a los oyentes, una sola vez aunque lleguen varios 401", () => {
    estarEn("/clientes");
    montarDialogo();
    const oyente = vi.fn();
    suscribirseASesionTerminada(oyente);

    avisarSesionTerminada();
    avisarSesionTerminada();
    avisarSesionTerminada();

    expect(leerSesionTerminada()).toBe(true);
    expect(oyente).toHaveBeenCalledTimes(1);
    expect(asignar).not.toHaveBeenCalled();
  });

  it("sin un diálogo montado navega directo al login con next, una sola vez", () => {
    estarEn("/developers", "?x=1");

    avisarSesionTerminada();
    avisarSesionTerminada();

    expect(asignar).toHaveBeenCalledExactlyOnceWith("/login?next=%2Fdevelopers%3Fx%3D1");
  });

  it("un diálogo que ya se desmontó no cuenta como montado", () => {
    estarEn("/clientes");
    const desmontar = montarDialogo();
    desmontar();
    desmontar(); // desmontarlo dos veces (el doble efecto de StrictMode) no lleva la cuenta a negativo

    avisarSesionTerminada();

    expect(asignar).toHaveBeenCalledExactlyOnceWith("/login?next=%2Fclientes");
  });

  it("dos diálogos montados a la vez (el layout y una página) siguen contando hasta desmontar los dos", () => {
    estarEn("/clientes");
    const primero = montarDialogo();
    montarDialogo();
    primero();

    avisarSesionTerminada();

    expect(asignar).not.toHaveBeenCalled();
  });

  it("quien se suscribe puede dejar de oír", () => {
    montarDialogo();
    const oyente = vi.fn();
    suscribirseASesionTerminada(oyente)();

    avisarSesionTerminada();

    expect(oyente).not.toHaveBeenCalled();
  });
});

describe("empezarSesionNueva", () => {
  it("desactiva el estado: una sesión nueva no hereda el diálogo de la anterior, y avisa a los oyentes", () => {
    montarDialogo();
    avisarSesionTerminada();
    const oyente = vi.fn();
    suscribirseASesionTerminada(oyente);

    empezarSesionNueva();

    expect(leerSesionTerminada()).toBe(false);
    expect(oyente).toHaveBeenCalledTimes(1);
  });

  it("sin nada que reiniciar no avisa a nadie", () => {
    const oyente = vi.fn();
    suscribirseASesionTerminada(oyente);

    empezarSesionNueva();

    expect(oyente).not.toHaveBeenCalled();
  });
});
