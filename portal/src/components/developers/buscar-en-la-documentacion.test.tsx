import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BuscarEnLaDocumentacion } from "./buscar-en-la-documentacion";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockReset();
});

describe("BuscarEnLaDocumentacion (#26)", () => {
  it("lista lo que coincide y elegirlo lleva a su página y su ancla", async () => {
    render(<BuscarEnLaDocumentacion />);

    fireEvent.change(screen.getByLabelText("Buscar en la documentación"), { target: { value: "error_envio" } });
    fireEvent.mouseDown(await screen.findByRole("option", { name: /ERROR_ENVIO/ }, { timeout: 5000 }));

    expect(push).toHaveBeenCalledWith("/developers/errores#estados");
  });

  it("si nada coincide lo dice", async () => {
    render(<BuscarEnLaDocumentacion />);

    fireEvent.change(screen.getByLabelText("Buscar en la documentación"), { target: { value: "zzzzqq" } });

    expect(await screen.findByRole("option", { name: /Nada coincide/ }, { timeout: 3000 })).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
