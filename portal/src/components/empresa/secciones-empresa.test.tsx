import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { SeccionesEmpresa } from "./secciones-empresa";

const paneles = {
  datos: <p>panel datos</p>,
  certificado: <p>panel certificado</p>,
  sol: <p>panel sol</p>,
  pdf: <p>panel pdf</p>,
  empresas: <p>panel empresas</p>,
};

function renderizar(inicial: "datos" | "certificado" | "sol" | "pdf" | "empresas", pendientes: Array<"certificado" | "sol"> = []) {
  return render(<SeccionesEmpresa inicial={inicial} pendientes={pendientes} empresas={2} paneles={paneles} />);
}

/** #276: la pestaña vive en la URL (`?seccion=`) para que los enlaces del portal abran la que corresponde y recargar no la pierda. */
describe("SeccionesEmpresa", () => {
  beforeEach(() => window.history.replaceState(null, "", "/empresa"));
  afterEach(cleanup);

  it("abre la pestaña inicial y la deja escrita en la URL", () => {
    renderizar("sol");
    expect(screen.getByRole("tab", { name: /Credenciales SOL/ })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("panel sol")).toBeInTheDocument();
    expect(screen.queryByText("panel datos")).not.toBeInTheDocument();
    expect(window.location.search).toBe("?seccion=sol");
  });

  it("al cambiar de pestaña cambia el panel y la URL, sin perder otros parámetros", () => {
    window.history.replaceState(null, "", "/empresa?seccion=datos&x=1");
    renderizar("datos");
    fireEvent.click(screen.getByRole("tab", { name: "PDF" }));
    expect(screen.getByText("panel pdf")).toBeInTheDocument();
    expect(new URLSearchParams(window.location.search).get("seccion")).toBe("pdf");
    expect(new URLSearchParams(window.location.search).get("x")).toBe("1");
  });

  it("si llega otra inicial (un enlace a otra sección), la abre", () => {
    const { rerender } = renderizar("datos");
    act(() => rerender(<SeccionesEmpresa inicial="certificado" pendientes={[]} empresas={2} paneles={paneles} />));
    expect(screen.getByText("panel certificado")).toBeInTheDocument();
  });

  it("marca las pestañas pendientes y cuenta las empresas", () => {
    renderizar("datos", ["certificado"]);
    expect(screen.getByRole("tab", { name: /Certificado/ })).toHaveAccessibleName("Certificado, pendiente");
    expect(screen.getByRole("tab", { name: /Credenciales SOL/ })).toHaveAccessibleName("Credenciales SOL");
    expect(screen.getByRole("tab", { name: /Empresas/ })).toHaveTextContent("2");
  });
});
