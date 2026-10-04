import { describe, expect, it } from "vitest";
import { migaAdmin } from "./admin-migas";

describe("migaAdmin", () => {
  it("el inicio del backoffice cuelga de «Backoffice»", () => {
    expect(migaAdmin("/admin")).toEqual({ seccion: "Backoffice", pagina: "Inicio" });
  });

  it("las cuentas cuelgan de «Clientes» y su lista ofrece crear una cuenta", () => {
    expect(migaAdmin("/admin/cuentas")).toEqual({ seccion: "Clientes", pagina: "Cuentas", accion: "nuevaCuenta" });
  });

  it("el detalle de una cuenta sigue bajo «Clientes / Cuentas» pero sin la acción de la lista", () => {
    expect(migaAdmin("/admin/cuentas/6b1d")).toEqual({ seccion: "Clientes", pagina: "Cuentas" });
  });

  it("el alta de una cuenta es «Clientes / Nueva cuenta» y no se ofrece a sí misma", () => {
    expect(migaAdmin("/admin/cuentas/nueva")).toEqual({ seccion: "Clientes", pagina: "Nueva cuenta" });
  });

  it("las empresas cuelgan de «Clientes» y no ofrecen crear una cuenta", () => {
    expect(migaAdmin("/admin/empresas")).toEqual({ seccion: "Clientes", pagina: "Empresas" });
  });

  it("el detalle de una empresa sigue bajo «Clientes / Empresas»", () => {
    expect(migaAdmin("/admin/empresas/6b1d")).toEqual({ seccion: "Clientes", pagina: "Empresas" });
  });

  it("los planes cuelgan de «Comercial» y no ofrecen crear una cuenta", () => {
    expect(migaAdmin("/admin/planes")).toEqual({ seccion: "Comercial", pagina: "Planes" });
  });

  it("«/admin» solo es el inicio de forma exacta: no engulle las páginas que todavía no tienen miga", () => {
    expect(migaAdmin("/admin/operacion")).toBeNull();
  });

  it("un prefijo parecido no cuenta: «/admin/cuentas-viejas» no es «Cuentas»", () => {
    expect(migaAdmin("/admin/cuentas-viejas")).toBeNull();
  });

  it("fuera del backoffice no hay miga", () => {
    expect(migaAdmin("/comprobantes")).toBeNull();
  });
});
