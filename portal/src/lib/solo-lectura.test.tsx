import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RevisaTuCorreo } from "@/components/auth/revisa-tu-correo";
import { NuevaSerieDialog } from "@/components/series/nueva-serie-dialog";
import { FormulariosDeEscritura, SoloLectura } from "./solo-lectura";

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
