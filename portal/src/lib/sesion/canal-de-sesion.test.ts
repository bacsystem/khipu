import { afterEach, describe, expect, it, vi } from "vitest";
import { simularOtraPestana } from "@/test/otra-pestana";
import { avisarALasDemasPestanas, escucharAvisosDeSesion } from "./canal-de-sesion";

afterEach(() => vi.unstubAllGlobals());

/** #653: el canal por el que las pestañas se avisan del cierre de sesión. Uno solo por pestaña, para que quien publica no se reciba a sí mismo. */
describe("canal de sesión", () => {
  it("lo que esta pestaña publica llega a las demás", () => {
    const otra = simularOtraPestana();

    avisarALasDemasPestanas("salir");
    avisarALasDemasPestanas("inactividad");

    expect(otra.recibidos).toEqual(["salir", "inactividad"]);
  });

  it("lo que publica otra pestaña llega al oyente", () => {
    const otra = simularOtraPestana();
    const alRecibir = vi.fn();
    escucharAvisosDeSesion(alRecibir);

    otra.publicar("salir");

    expect(alRecibir).toHaveBeenCalledExactlyOnceWith("salir");
  });

  it("esta pestaña no se recibe a sí misma: publicar y escuchar comparten el mismo canal", () => {
    simularOtraPestana();
    const alRecibir = vi.fn();
    escucharAvisosDeSesion(alRecibir);

    avisarALasDemasPestanas("salir");

    expect(alRecibir).not.toHaveBeenCalled();
  });

  it.each([["otra-cosa"], [42], [null], [{ aviso: "salir" }]])("un valor desconocido (%j) se ignora", (dato) => {
    const otra = simularOtraPestana();
    const alRecibir = vi.fn();
    escucharAvisosDeSesion(alRecibir);

    otra.publicar(dato);

    expect(alRecibir).not.toHaveBeenCalled();
  });

  it("la función que devuelve quita el oyente", () => {
    const otra = simularOtraPestana();
    const alRecibir = vi.fn();
    const dejarDeEscuchar = escucharAvisosDeSesion(alRecibir);

    dejarDeEscuchar();
    otra.publicar("salir");

    expect(alRecibir).not.toHaveBeenCalled();
  });

  it("sin BroadcastChannel no hace nada y no lanza", () => {
    vi.stubGlobal("BroadcastChannel", undefined);

    expect(() => avisarALasDemasPestanas("salir")).not.toThrow();
    const dejarDeEscuchar = escucharAvisosDeSesion(vi.fn());
    expect(() => dejarDeEscuchar()).not.toThrow();
  });

  it("si crear el canal falla (p. ej. un contexto sin origen), tampoco lanza", () => {
    vi.stubGlobal(
      "BroadcastChannel",
      class {
        constructor() {
          throw new DOMException("sin origen", "SecurityError");
        }
      },
    );

    expect(() => avisarALasDemasPestanas("salir")).not.toThrow();
    expect(() => escucharAvisosDeSesion(vi.fn())()).not.toThrow();
  });
});
