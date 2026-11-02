import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AyudaDeSoporte, SoporteProvider } from "./ayuda-de-soporte";

afterEach(() => cleanup());

function conSoporte(url: string | null, email: string | null) {
  return render(
    <SoporteProvider soporte={{ url, email }}>
      <AyudaDeSoporte />
    </SoporteProvider>,
  );
}

describe("AyudaDeSoporte (#250)", () => {
  it("sin soporte configurado no muestra nada", () => {
    const { container } = conSoporte(null, null);
    expect(container).toBeEmptyDOMElement();
  });

  it("fuera del proveedor tampoco: una pantalla sin él no inventa un soporte", () => {
    const { container } = render(<AyudaDeSoporte />);
    expect(container).toBeEmptyDOMElement();
  });

  it("muestra el enlace y el correo cuando están", () => {
    conSoporte("https://ayuda.khipu.pe", "soporte@khipu.pe");
    expect(screen.getByText("¿Necesitas ayuda?")).toBeInTheDocument();
    const enlace = screen.getByRole("link", { name: /centro de ayuda/i });
    expect(enlace).toHaveAttribute("href", "https://ayuda.khipu.pe");
    expect(enlace).toHaveAttribute("target", "_blank");
    expect(enlace).toHaveAttribute("rel", expect.stringContaining("noopener"));
    expect(screen.getByRole("link", { name: "soporte@khipu.pe" })).toHaveAttribute("href", "mailto:soporte@khipu.pe");
  });

  it("con solo el correo no muestra un enlace vacío", () => {
    conSoporte(null, "soporte@khipu.pe");
    expect(screen.queryByRole("link", { name: /centro de ayuda/i })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "soporte@khipu.pe" })).toBeInTheDocument();
  });
});
