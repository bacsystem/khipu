import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { Administrador } from "@/lib/api/admin-auth";
import { AdminTopBar } from "./admin-top-bar";

const pathname = vi.hoisted(() => ({ actual: "/admin" }));
vi.mock("next/navigation", () => ({ usePathname: () => pathname.actual, useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const ADMINISTRADOR: Administrador = { id: "a1", email: "root@khipu.pe" };

function pintar(ruta: string) {
  pathname.actual = ruta;
  render(<AdminTopBar administrador={ADMINISTRADOR} />);
}

afterEach(cleanup);

describe("AdminTopBar", () => {
  it("muestra la miga «Clientes / Cuentas» y marca la página actual", () => {
    pintar("/admin/cuentas");

    const miga = screen.getByRole("navigation", { name: "Ubicación" });
    expect(miga.textContent).toContain("Clientes");
    expect(screen.getByText("Cuentas").getAttribute("aria-current")).toBe("page");
  });

  it("la miga no es un encabezado: cada página del backoffice ya trae su propio h1", () => {
    pintar("/admin/cuentas");

    expect(screen.queryByRole("heading")).toBeNull();
  });

  it("sin miga conocida no pinta la navegación de ubicación", () => {
    pintar("/admin/planes");

    expect(screen.queryByRole("navigation", { name: "Ubicación" })).toBeNull();
  });

  it("en Cuentas ofrece «Nueva cuenta» como un enlace al alta asistida", () => {
    pintar("/admin/cuentas");

    const enlace = screen.getByRole("link", { name: "Nueva cuenta" });
    expect(enlace.getAttribute("href")).toBe("/admin/cuentas/nueva");
    expect(screen.queryByRole("button", { name: "Nueva cuenta" })).toBeNull();
  });

  it("en el detalle de una cuenta conserva la miga pero no la acción de la lista", () => {
    pintar("/admin/cuentas/6b1d");

    expect(screen.getByRole("navigation", { name: "Ubicación" }).textContent).toContain("Clientes");
    expect(screen.queryByRole("link", { name: "Nueva cuenta" })).toBeNull();
  });

  it("en la propia página de alta marca la miga y no se ofrece a sí misma", () => {
    pintar("/admin/cuentas/nueva");

    expect(screen.getByRole("navigation", { name: "Ubicación" }).textContent).toContain("Clientes");
    expect(screen.getByText("Nueva cuenta").getAttribute("aria-current")).toBe("page");
    expect(screen.queryByRole("link", { name: "Nueva cuenta" })).toBeNull();
  });

  it("en el inicio no hay acción de crear cuentas", () => {
    pintar("/admin");

    expect(screen.queryByRole("link", { name: "Nueva cuenta" })).toBeNull();
  });

  it("trae el botón del menú, que es la única navegación del backoffice en móvil", () => {
    pintar("/admin");

    expect(screen.getByRole("button", { name: "Abrir menú" })).toBeTruthy();
  });
});
