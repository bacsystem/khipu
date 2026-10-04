import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiEnvelope } from "@/lib/api/types";
import { AvisoDeSoporte } from "./aviso-de-soporte";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const HASTA = "2026-10-04T17:15:00Z";

afterEach(() => {
  cleanup();
  apiRequest.mockReset();
});

const exito = (): ApiEnvelope<null> => ({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
const falla = (): ApiEnvelope<null> => ({ estado: "error", datos: null, mensaje: "No se pudo conectar", codigo: "RED", errores: null });

describe("AvisoDeSoporte (#184)", () => {
  it("dice como quién se está viendo el portal, que solo se puede mirar y cuándo termina la sesión", () => {
    render(<AvisoDeSoporte email="ana@negocio.pe" hasta={HASTA} cuentaId={CUENTA} />);

    const aviso = screen.getByTestId("aviso-de-soporte");
    expect(aviso.textContent).toContain("Modo soporte");
    expect(aviso.textContent).toContain("ana@negocio.pe");
    expect(aviso.textContent).toContain("Solo puedes mirar: no puedes cambiar nada");
    expect(aviso.textContent).toContain("12:15");
  });

  it("es una región con nombre, para que un lector de pantalla la anuncie", () => {
    render(<AvisoDeSoporte email="ana@negocio.pe" hasta={HASTA} cuentaId={CUENTA} />);

    expect(screen.getByRole("region", { name: "Modo soporte" })).toBeTruthy();
  });

  /** «Permanente»: fijo arriba al hacer scroll y por encima del resto, no un banner que se va con el contenido. */
  it("queda fijo arriba al hacer scroll", () => {
    render(<AvisoDeSoporte email="ana@negocio.pe" hasta={HASTA} cuentaId={CUENTA} />);

    const clases = screen.getByTestId("aviso-de-soporte").className;
    expect(clases).toContain("sticky");
    expect(clases).toContain("top-0");
    expect(clases).toContain("z-40");
  });

  it("salir borra las cookies de cliente y vuelve a la cuenta en el backoffice", async () => {
    apiRequest.mockResolvedValue(exito());
    const irA = vi.fn();
    render(<AvisoDeSoporte email="ana@negocio.pe" hasta={HASTA} cuentaId={CUENTA} irA={irA} />);

    fireEvent.click(screen.getByTestId("salir-de-soporte"));

    await waitFor(() => expect(irA).toHaveBeenCalledWith(`/admin/cuentas/${CUENTA}`));
    expect(apiRequest).toHaveBeenCalledWith("/api/soporte/salir", { method: "POST" });
  });

  it("si no se pudo salir lo dice, no navega y se puede reintentar", async () => {
    apiRequest.mockResolvedValueOnce(falla()).mockResolvedValueOnce(exito());
    const irA = vi.fn();
    render(<AvisoDeSoporte email="ana@negocio.pe" hasta={HASTA} cuentaId={CUENTA} irA={irA} />);

    fireEvent.click(screen.getByTestId("salir-de-soporte"));
    expect((await screen.findByRole("alert")).textContent).toContain("No se pudo salir del modo soporte");
    expect(irA).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId("salir-de-soporte"));

    await waitFor(() => expect(irA).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("un doble clic en salir hace una sola llamada, y mientras sale el botón lo dice", async () => {
    let resolver: (v: ApiEnvelope<null>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<null>>((r) => (resolver = r)));
    render(<AvisoDeSoporte email="ana@negocio.pe" hasta={HASTA} cuentaId={CUENTA} irA={vi.fn()} />);

    const boton = screen.getByTestId("salir-de-soporte");
    act(() => {
      fireEvent.click(boton);
      fireEvent.click(boton);
    });

    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(boton.textContent).toContain("Saliendo…");
    expect((boton as HTMLButtonElement).disabled).toBe(true);
    await act(async () => resolver(exito()));
  });
});
