import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { VerificarSegundoFactor } from "./segundo-factor";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function respuesta(status: number, cuerpo: object) {
  return new Response(JSON.stringify({ datos: null, mensaje: null, codigo: null, errores: null, ...cuerpo }), { status });
}

/** El segundo paso del login del backoffice usa las mismas piezas que el resto de las pantallas de acceso (#12): `Campo`, `Entrada` y `BotonAsync`. */
describe("VerificarSegundoFactor", () => {
  it("mientras verifica, el botón queda ocupado; un código malo se anuncia y queda atado al campo", async () => {
    let responder: (r: Response) => void = () => undefined;
    vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>((resolve) => (responder = resolve))));
    render(<VerificarSegundoFactor alEntrar={vi.fn()} alFallar={vi.fn()} />);

    const campo = screen.getByLabelText("Código de 6 dígitos");
    fireEvent.change(campo, { target: { value: "12a3456" } });
    expect((campo as HTMLInputElement).value).toBe("123456");
    fireEvent.click(screen.getByRole("button", { name: "Verificar" }));

    const boton = await screen.findByRole("button", { name: /Verificando…/ });
    expect(boton.getAttribute("aria-busy")).toBe("true");
    expect((boton as HTMLButtonElement).disabled).toBe(true);

    responder(respuesta(401, { estado: "error", codigo: "SEGUNDO_FACTOR_INVALIDO" }));
    const alerta = await screen.findByRole("alert");
    expect(campo.getAttribute("aria-invalid")).toBe("true");
    expect(campo.getAttribute("aria-describedby")).toBe(alerta.getAttribute("id"));
    await waitFor(() => expect((screen.getByRole("button", { name: "Verificar" }) as HTMLButtonElement).disabled).toBe(false));
  });
});
