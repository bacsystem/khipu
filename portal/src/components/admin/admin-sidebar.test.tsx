import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Administrador } from "@/lib/api/admin-auth";

const ruta = vi.hoisted(() => ({ actual: "/admin" }));
vi.mock("next/navigation", () => ({
  usePathname: () => ruta.actual,
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

import { AdminSidebar } from "./admin-sidebar";

const ADMINISTRADOR: Administrador = { id: "a1", email: "root@khipu.pe" };

function marcado(pathname: string) {
  ruta.actual = pathname;
  render(<AdminSidebar administrador={ADMINISTRADOR} />);
  const menu = screen.getByRole("navigation", { name: "Menú del backoffice" });
  return within(menu)
    .queryAllByRole("link")
    .filter((l) => l.getAttribute("aria-current") === "page")
    .map((l) => l.textContent);
}

describe("AdminSidebar (#519)", () => {
  afterEach(cleanup);

  it("en /admin marca solo «Inicio»", () => {
    expect(marcado("/admin")).toEqual(["Inicio"]);
  });

  /** «Inicio» es `/admin` y todas las páginas cuelgan de él: por prefijo quedaría marcado junto con la sección, por eso es `exacto`. */
  it.each([
    ["/admin/cuentas", "Cuentas"],
    ["/admin/cuentas/c-1", "Cuentas"],
    ["/admin/empresas/e-1", "Empresas"],
    ["/admin/configuracion", "Configuración"],
  ])("en %s marca «%s» y no «Inicio»", (pathname, item) => {
    expect(marcado(pathname)).toEqual([item]);
  });

  it("lleva los grupos de la miga, con «Inicio» en «Panel»", () => {
    ruta.actual = "/admin";
    render(<AdminSidebar administrador={ADMINISTRADOR} />);
    const menu = screen.getByRole("navigation", { name: "Menú del backoffice" });
    for (const grupo of ["Panel", "Clientes", "Comercial", "Operación", "Plataforma"]) expect(within(menu).getByText(grupo)).toBeTruthy();
  });
});
