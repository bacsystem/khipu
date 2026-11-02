import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { PlantillaDeCorreo } from "@/lib/api/admin-configuracion";
import { ListaDePlantillas, SeccionesDeConfiguracion } from "./configuracion-navegacion";

afterEach(cleanup);

function plantilla(tipo: string, etiqueta: string, personalizada = false): PlantillaDeCorreo {
  return { tipo, etiqueta, cuando_se_manda: "x", vigente: { asunto: "a", cuerpo: "b" }, defecto: { asunto: "a", cuerpo: "b" }, personalizada, variables: [] };
}

describe("SeccionesDeConfiguracion (#199)", () => {
  it("son enlaces a cada sección, y la del correo es la ruta limpia", () => {
    render(<SeccionesDeConfiguracion actual="correo" />);

    const nav = within(screen.getByRole("navigation", { name: "Qué configurar" }));
    expect(nav.getByRole("link", { name: "Correo saliente" }).getAttribute("href")).toBe("/admin/configuracion");
    expect(nav.getByRole("link", { name: "Plantillas" }).getAttribute("href")).toBe("/admin/configuracion?seccion=plantillas");
    expect(nav.getByRole("link", { name: "Aviso de mantenimiento" }).getAttribute("href")).toBe("/admin/configuracion?seccion=aviso");
  });

  it("marca solo la sección actual", () => {
    for (const [actual, nombre] of [["correo", "Correo saliente"], ["plantillas", "Plantillas"], ["aviso", "Aviso de mantenimiento"]] as const) {
      const { unmount } = render(<SeccionesDeConfiguracion actual={actual} />);
      const enlaces = screen.getAllByRole("link");
      expect(enlaces.filter((e) => e.getAttribute("aria-current") === "page").map((e) => e.textContent), actual).toEqual([nombre]);
      unmount();
    }
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

  it("marca como «Editado» solo los que alguien cambió", () => {
    render(<ListaDePlantillas plantillas={PLANTILLAS} actual="BIENVENIDA" />);

    const conMarca = screen.getAllByTestId("plantilla-enlace").filter((e) => e.textContent?.includes("Editado")).map((e) => e.getAttribute("data-tipo"));
    expect(conMarca).toEqual(["RECUPERACION_CLAVE"]);
  });

  it("dice su etiqueta, no el nombre interno", () => {
    render(<ListaDePlantillas plantillas={PLANTILLAS} actual="BIENVENIDA" />);

    expect(screen.getByRole("link", { name: /Restablecer la contraseña/ })).toBeTruthy();
    expect(screen.queryByText("RECUPERACION_CLAVE")).toBeNull();
  });
});
