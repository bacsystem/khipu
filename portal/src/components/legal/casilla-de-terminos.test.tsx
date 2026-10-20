import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { CasillaDeTerminos } from "./casilla-de-terminos";

afterEach(cleanup);

const casilla = () => screen.getByRole("checkbox", { name: /Acepto los términos del servicio y la política de privacidad/ });

/** #342 (H1): quien acepta un borrador lo sabe en la casilla misma, no solo si abre el enlace. */
describe("CasillaDeTerminos", () => {
  it("mientras los textos sean un borrador lo dice junto a la casilla y lo ata a ella", () => {
    render(<CasillaDeTerminos id="t" revisado={false} />);

    expect(screen.getByTestId("borrador-legal-casilla").textContent).toContain("Borrador pendiente de revisión legal");
    expect(casilla().getAttribute("aria-describedby")).toBe("t-borrador");
  });

  it("revisados, no hay aviso; el error sigue atado a la casilla", () => {
    render(<CasillaDeTerminos id="t" revisado error="Falta aceptar" />);

    expect(screen.queryByTestId("borrador-legal-casilla")).toBeNull();
    expect(casilla().getAttribute("aria-describedby")).toBe("t-error");
  });

  it("con error y borrador describe las dos cosas", () => {
    render(<CasillaDeTerminos id="t" revisado={false} error="Falta aceptar" />);

    expect(casilla().getAttribute("aria-describedby")).toBe("t-error t-borrador");
  });
});
