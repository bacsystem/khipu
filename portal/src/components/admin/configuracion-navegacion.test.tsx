import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlantillaDeCorreo } from "@/lib/api/admin-configuracion";
import { ListaDePlantillas, SeccionesDeConfiguracion } from "./configuracion-navegacion";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockClear();
});

function plantilla(tipo: string, etiqueta: string, personalizada = false): PlantillaDeCorreo {
  return { tipo, etiqueta, cuando_se_manda: "x", vigente: { asunto: "a", cuerpo: "b" }, defecto: { asunto: "a", cuerpo: "b" }, personalizada, variables: [] };
}

describe("SeccionesDeConfiguracion (#199)", () => {
  it("son las pestañas del design system, una por sección, con el panel de la actual", () => {
    render(
      <SeccionesDeConfiguracion actual="plantillas">
        <p>contenido de plantillas</p>
      </SeccionesDeConfiguracion>,
    );

    expect(screen.getAllByRole("tab").map((t) => t.textContent)).toEqual(["Correo saliente", "Plantillas", "Aviso de mantenimiento"]);
    expect(within(screen.getByRole("tabpanel")).getByText("contenido de plantillas")).toBeTruthy();
  });

  it("marca solo la sección actual", () => {
    for (const [actual, nombre] of [["correo", "Correo saliente"], ["plantillas", "Plantillas"], ["aviso", "Aviso de mantenimiento"]] as const) {
      const { unmount } = render(<SeccionesDeConfiguracion actual={actual}>x</SeccionesDeConfiguracion>);
      expect(screen.getAllByRole("tab").filter((t) => t.getAttribute("aria-selected") === "true").map((t) => t.textContent), actual).toEqual([nombre]);
      unmount();
    }
  });

  /** La sección vive en la URL (se comparte y el servidor solo pide lo que se ve): elegir otra pestaña navega, y la del correo es la ruta limpia. */
  it("elegir una pestaña navega a la URL de esa sección", () => {
    render(<SeccionesDeConfiguracion actual="aviso">x</SeccionesDeConfiguracion>);

    fireEvent.click(screen.getByRole("tab", { name: "Plantillas" }));
    expect(push).toHaveBeenLastCalledWith("/admin/configuracion?seccion=plantillas");
    fireEvent.click(screen.getByRole("tab", { name: "Correo saliente" }));
    expect(push).toHaveBeenLastCalledWith("/admin/configuracion");
  });
});

describe("ListaDePlantillas (#199)", () => {
  const PLANTILLAS = [plantilla("VERIFICACION_CORREO", "Verificación de correo"), plantilla("RECUPERACION_CLAVE", "Restablecer la contraseña", true), plantilla("BIENVENIDA", "Bienvenida")];

  it("un enlace por correo, con el elegido en la URL", () => {
    render(<ListaDePlantillas plantillas={PLANTILLAS} actual="RECUPERACION_CLAVE" />);

    const enlaces = screen.getAllByTestId("plantilla-enlace");
    expect(enlaces.map((e) => [e.getAttribute("data-tipo"), e.getAttribute("href")])).toEqual([
      ["VERIFICACION_CORREO", "/admin/configuracion?seccion=plantillas&plantilla=VERIFICACION_CORREO"],
      ["RECUPERACION_CLAVE", "/admin/configuracion?seccion=plantillas&plantilla=RECUPERACION_CLAVE"],
      ["BIENVENIDA", "/admin/configuracion?seccion=plantillas&plantilla=BIENVENIDA"],
    ]);
  });

  it("marca el correo actual", () => {
    render(<ListaDePlantillas plantillas={PLANTILLAS} actual="BIENVENIDA" />);

    expect(screen.getAllByTestId("plantilla-enlace").filter((e) => e.getAttribute("aria-current") === "page").map((e) => e.getAttribute("data-tipo"))).toEqual(["BIENVENIDA"]);
  });

  it("marca como «Editado» solo los que alguien cambió, con el chip del design system", () => {
    render(<ListaDePlantillas plantillas={PLANTILLAS} actual="BIENVENIDA" />);

    const conMarca = screen.getAllByTestId("plantilla-enlace").filter((e) => e.textContent?.includes("Editado")).map((e) => e.getAttribute("data-tipo"));
    expect(conMarca).toEqual(["RECUPERACION_CLAVE"]);
    expect(screen.getByText("Editado").className).toContain("rounded-full");
  });

  it("dice su etiqueta, no el nombre interno", () => {
    render(<ListaDePlantillas plantillas={PLANTILLAS} actual="BIENVENIDA" />);

    expect(screen.getByRole("link", { name: /Restablecer la contraseña/ })).toBeTruthy();
    expect(screen.queryByText("RECUPERACION_CLAVE")).toBeNull();
  });
});
