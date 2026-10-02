import { describe, expect, it } from "vitest";
import { migaAdmin } from "./admin-migas";

describe("migaAdmin", () => {
  it("el inicio del backoffice cuelga de «Backoffice»", () => {
    expect(migaAdmin("/admin")).toEqual({ seccion: "Backoffice", pagina: "Inicio" });
  });

  it("las cuentas cuelgan de «Clientes»", () => {
    expect(migaAdmin("/admin/cuentas")).toEqual({ seccion: "Clientes", pagina: "Cuentas" });
  });

  it("el detalle de una cuenta sigue bajo «Clientes / Cuentas»", () => {
    expect(migaAdmin("/admin/cuentas/6b1d")).toEqual({ seccion: "Clientes", pagina: "Cuentas" });
  });

  it("«/admin» solo es el inicio de forma exacta: no engulle las páginas que todavía no tienen miga", () => {
    expect(migaAdmin("/admin/empresas")).toBeNull();
  });

  it("un prefijo parecido no cuenta: «/admin/cuentas-viejas» no es «Cuentas»", () => {
    expect(migaAdmin("/admin/cuentas-viejas")).toBeNull();
  });

  it("fuera del backoffice no hay miga", () => {
    expect(migaAdmin("/comprobantes")).toBeNull();
  });
});
