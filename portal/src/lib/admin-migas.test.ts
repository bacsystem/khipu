import { describe, expect, it } from "vitest";
import { migaAdmin } from "./admin-migas";
import es from "@/messages/es.json";

describe("migaAdmin", () => {
  it("el inicio del backoffice cuelga de «Backoffice»", () => {
    expect(migaAdmin("/admin", es)).toEqual({ seccion: "Backoffice", pagina: "Inicio" });
  });

  it("las cuentas cuelgan de «Clientes» y su lista ofrece crear una cuenta", () => {
    expect(migaAdmin("/admin/cuentas", es)).toEqual({ seccion: "Clientes", pagina: "Cuentas", accion: "nuevaCuenta" });
  });

  it("el detalle de una cuenta sigue bajo «Clientes / Cuentas» pero sin la acción de la lista", () => {
    expect(migaAdmin("/admin/cuentas/6b1d", es)).toEqual({ seccion: "Clientes", pagina: "Cuentas" });
  });

  it("las empresas cuelgan de «Clientes» y no ofrecen crear una cuenta", () => {
    expect(migaAdmin("/admin/empresas", es)).toEqual({ seccion: "Clientes", pagina: "Empresas" });
  });

  it("el detalle de una empresa sigue bajo «Clientes / Empresas»", () => {
    expect(migaAdmin("/admin/empresas/6b1d", es)).toEqual({ seccion: "Clientes", pagina: "Empresas" });
  });

  it("los planes cuelgan de «Comercial» y su acción principal es crear un plan, como «Nueva cuenta» en Cuentas", () => {
    expect(migaAdmin("/admin/planes", es)).toEqual({ seccion: "Comercial", pagina: "Planes", accion: "nuevoPlan" });
  });

  it("el consumo cuelga de «Comercial» y su acción principal es exportarlo", () => {
    expect(migaAdmin("/admin/consumo", es)).toEqual({ seccion: "Comercial", pagina: "Consumo", accion: "exportarConsumo" });
  });

  it("la integridad cuelga de «Operación» y no ofrece crear una cuenta", () => {
    expect(migaAdmin("/admin/integridad", es)).toEqual({ seccion: "Operación", pagina: "Integridad" });
  });

  it("el monitor cuelga de «Operación» y no ofrece crear una cuenta", () => {
    expect(migaAdmin("/admin/monitor", es)).toEqual({ seccion: "Operación", pagina: "Monitor" });
  });

  it("la cola de errores cuelga de «Operación» y no ofrece crear una cuenta", () => {
    expect(migaAdmin("/admin/errores", es)).toEqual({ seccion: "Operación", pagina: "Errores" });
  });

  it("los avisos a clientes cuelgan de «Operación» y no ofrecen crear una cuenta", () => {
    expect(migaAdmin("/admin/avisos", es)).toEqual({ seccion: "Operación", pagina: "Avisos" });
  });

  it("la configuración cuelga de «Plataforma» y no ofrece crear una cuenta", () => {
    expect(migaAdmin("/admin/configuracion", es)).toEqual({ seccion: "Plataforma", pagina: "Configuración" });
  });

  /** #286: la ficha de un comprobante era la única página sin miga en la barra superior. */
  it("la ficha de un comprobante cuelga de «Operación» y no ofrece acciones", () => {
    expect(migaAdmin("/admin/comprobantes/6b1d", es)).toEqual({ seccion: "Operación", pagina: "Comprobante" });
  });

  it("«/admin» solo es el inicio de forma exacta: no engulle las páginas que todavía no tienen miga", () => {
    expect(migaAdmin("/admin/operacion", es)).toBeNull();
  });

  it("un prefijo parecido no cuenta: «/admin/cuentas-viejas» no es «Cuentas»", () => {
    expect(migaAdmin("/admin/cuentas-viejas", es)).toBeNull();
  });

  it("fuera del backoffice no hay miga", () => {
    expect(migaAdmin("/comprobantes", es)).toBeNull();
  });
});
