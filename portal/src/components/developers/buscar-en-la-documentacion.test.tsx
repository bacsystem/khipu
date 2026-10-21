import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { entradas } from "@/lib/developers/entradas";
import { BuscarEnLaDocumentacion } from "./buscar-en-la-documentacion";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const ENTRADAS = entradas();

afterEach(() => {
  cleanup();
  push.mockReset();
});

describe("BuscarEnLaDocumentacion (#26)", () => {
  it("lista lo que coincide y elegirlo lleva a su página y su ancla", async () => {
    render(<BuscarEnLaDocumentacion entradas={ENTRADAS} />);

    fireEvent.change(screen.getByLabelText("Buscar en la documentación"), { target: { value: "error_envio" } });
    // Lo que se llama exactamente así va primero: el estado, antes que las filas de otras tablas que lo mencionan.
    const opciones = await screen.findAllByRole("option", { name: /ERROR_ENVIO/ }, { timeout: 5000 });
    expect(opciones.length).toBeGreaterThan(1);
    expect(opciones[0]).toHaveTextContent("Estado del comprobante");
    fireEvent.mouseDown(opciones[0]);

    expect(push).toHaveBeenCalledWith("/developers/errores#estados");
  });

  it("si nada coincide lo dice", async () => {
    render(<BuscarEnLaDocumentacion entradas={ENTRADAS} />);

    fireEvent.change(screen.getByLabelText("Buscar en la documentación"), { target: { value: "zzzzqq" } });

    expect(await screen.findByRole("option", { name: /Nada coincide/ }, { timeout: 3000 })).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
