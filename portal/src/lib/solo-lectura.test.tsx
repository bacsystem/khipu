import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RevisaTuCorreo } from "@/components/auth/revisa-tu-correo";
import { NuevaSerieDialog } from "@/components/series/nueva-serie-dialog";
import { FormulariosDeEscritura, motivosDeRestriccion, SoloLectura } from "./solo-lectura";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

afterEach(cleanup);

/**
 * H10: la sesión de soporte solo mira y el backend rechaza toda escritura, pero el portal dejaba abrir y llenar un comprobante entero (y pedir otro enlace de
 * verificación) y el rechazo llegaba recién al enviar.
 */
describe("modo solo lectura", () => {
  it("en una sesión de soporte las acciones que escriben se ven deshabilitadas", () => {
    render(
      <SoloLectura activa>
        <NuevaSerieDialog />
        <RevisaTuCorreo email="ana@sol.pe" />
      </SoloLectura>,
    );

    const nuevaSerie = screen.getByRole("button", { name: /Nueva serie/ }) as HTMLButtonElement;
    expect(nuevaSerie.disabled).toBe(true);
    expect(nuevaSerie.title).toBe("Modo soporte: solo se puede mirar, no cambiar nada.");
    expect((screen.getByRole("button", { name: /Enviarme otro enlace/ }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("fuera de una sesión de soporte no cambia nada", () => {
    render(
      <SoloLectura activa={false}>
        <NuevaSerieDialog />
      </SoloLectura>,
    );

    expect((screen.getByRole("button", { name: /Nueva serie/ }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("lo de la propia sesión solo lo impide el modo soporte, no el rol", () => {
    render(
      <SoloLectura activa={false} rol="LECTURA">
        <RevisaTuCorreo email="ana@sol.pe" />
      </SoloLectura>,
    );

    expect((screen.getByRole("button", { name: /Enviarme otro enlace/ }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("un grupo de formularios de escritura queda deshabilitado entero, campos y botones", () => {
    render(
      <SoloLectura activa>
        <FormulariosDeEscritura>
          <form>
            <input aria-label="Usuario SOL" />
            <button type="submit">Guardar</button>
          </form>
        </FormulariosDeEscritura>
      </SoloLectura>,
    );

    expect((screen.getByLabelText("Usuario SOL") as HTMLInputElement).matches(":disabled")).toBe(true);
    expect((screen.getByRole("button", { name: "Guardar" }) as HTMLButtonElement).matches(":disabled")).toBe(true);
  });
});

/** #21: el rol del usuario acota lo que se puede hacer; el backend lo vuelve a decidir, pero el portal no deja empezar lo que va a rechazar. */
describe("permisos por rol", () => {
  function Sondas() {
    return (
      <>
        <FormulariosDeEscritura>
          <button type="button">Emitir</button>
        </FormulariosDeEscritura>
        <FormulariosDeEscritura permiso="configurar">
          <button type="button">Configurar</button>
        </FormulariosDeEscritura>
      </>
    );
  }
  const deshabilitado = (nombre: string) => (screen.getByRole("button", { name: nombre }) as HTMLButtonElement).matches(":disabled");

  it("el administrador puede todo", () => {
    render(<SoloLectura activa={false} rol="ADMIN"><Sondas /></SoloLectura>);
    expect(deshabilitado("Emitir")).toBe(false);
    expect(deshabilitado("Configurar")).toBe(false);
  });

  it("el emisor emite pero no configura", () => {
    render(<SoloLectura activa={false} rol="EMISOR"><Sondas /><NuevaSerieDialog /></SoloLectura>);
    expect(deshabilitado("Emitir")).toBe(false);
    expect(deshabilitado("Configurar")).toBe(true);
    const nuevaSerie = screen.getByRole("button", { name: /Nueva serie/ }) as HTMLButtonElement;
    expect(nuevaSerie.disabled).toBe(true);
    expect(nuevaSerie.title).toBe("Solo un administrador de la cuenta puede cambiar la configuración.");
  });

  it("el de solo lectura no emite ni configura", () => {
    render(<SoloLectura activa={false} rol="LECTURA"><Sondas /></SoloLectura>);
    expect(deshabilitado("Emitir")).toBe(true);
    expect(deshabilitado("Configurar")).toBe(true);
  });

  it("un rol desconocido es el más restringido", () => {
    expect(motivosDeRestriccion(false, "DUENO").emitir).not.toBeNull();
    expect(motivosDeRestriccion(false, "DUENO").configurar).not.toBeNull();
    expect(motivosDeRestriccion(false, "DUENO").sesion).toBeNull();
  });
});
