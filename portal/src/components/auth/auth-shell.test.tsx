import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { SoporteProvider } from "@/components/soporte/ayuda-de-soporte";
import { AuthShell } from "./auth-shell";

afterEach(cleanup);

/** El marco de las pantallas de acceso usa el `LogoMarca` del panel (#12): una sola marca, no un texto suelto que se parezca. */
describe("AuthShell", () => {
  it("pone el logo de la marca en los dos lados y lo lleva al inicio", () => {
    render(<AuthShell title="Inicia sesión" subtitle="Ingresa a tu cuenta">contenido</AuthShell>);

    // Uno en el panel de marca (escritorio) y otro sobre el formulario (móvil): el CSS muestra uno u otro.
    const logos = screen.getAllByRole("link", { name: /khipu/i });
    expect(logos).toHaveLength(2);
    for (const logo of logos) expect(logo.getAttribute("href")).toBe("/");
  });

  it("muestra el título, el subtítulo, el contenido y el pie", () => {
    render(
      <AuthShell title="Inicia sesión" subtitle="Ingresa a tu cuenta" footer={<span>¿No tienes cuenta?</span>}>
        <p>contenido</p>
      </AuthShell>,
    );

    expect(screen.getByRole("heading", { level: 2, name: "Inicia sesión" })).toBeTruthy();
    expect(screen.getByText("Ingresa a tu cuenta")).toBeTruthy();
    expect(screen.getByText("contenido")).toBeTruthy();
    expect(screen.getByText("¿No tienes cuenta?")).toBeTruthy();
  });

  it("ofrece el soporte cuando está configurado (#250)", () => {
    render(
      <SoporteProvider soporte={{ url: null, email: "soporte@khipu.pe" }}>
        <AuthShell title="Inicia sesión" subtitle="Ingresa a tu cuenta">contenido</AuthShell>
      </SoporteProvider>,
    );

    expect(screen.getByText("soporte@khipu.pe")).toBeTruthy();
  });
});
