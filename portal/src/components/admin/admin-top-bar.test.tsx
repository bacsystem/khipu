import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { Administrador } from "@/lib/api/admin-auth";
import { AdminTopBar } from "./admin-top-bar";

const pathname = vi.hoisted(() => ({ actual: "/admin", query: "" }));
vi.mock("next/navigation", () => ({
  usePathname: () => pathname.actual,
  useSearchParams: () => new URLSearchParams(pathname.query),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const ADMINISTRADOR: Administrador = { id: "a1", email: "root@khipu.pe" };

/** `ruta` puede traer su query string, como la URL del navegador. */
function pintar(ruta: string) {
  const [camino, query = ""] = ruta.split("?");
  pathname.actual = camino;
  pathname.query = query;
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
    pintar("/admin/operacion");

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

  it("en Planes ofrece «Nuevo plan» en la cabecera, que abre el formulario del plan", () => {
    pintar("/admin/planes");

    const boton = screen.getByTestId("plan-nuevo");
    expect(boton.textContent).toContain("Nuevo plan");
    expect(screen.queryByRole("link", { name: "Nueva cuenta" })).toBeNull();
  });

  /** Lo que se baja es lo que se ve: el mes, el filtro y el orden de la URL, sin la página. */
  it("en Consumo ofrece «Exportar CSV» en la cabecera, con el mes, el filtro y el orden de la página", () => {
    pintar("/admin/consumo?mes=2026-10&filtro=PLAN_VENCIDO&orden=DOCUMENTOS&pagina=3");

    const enlace = screen.getByRole("link", { name: "Exportar CSV" });
    expect(enlace.getAttribute("href")).toBe("/api/admin/consumo/exportacion?mes=2026-10&filtro=PLAN_VENCIDO&orden=DOCUMENTOS");
    expect(enlace.hasAttribute("download")).toBe(true);
  });

  /** Sin mes en la URL la página mide el mes en curso de Lima: se exporta ese, no otro. */
  it("sin mes en la URL exporta el mes en curso de Lima, con la vista por defecto", () => {
    pintar("/admin/consumo");

    const mes = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }).slice(0, 7);
    expect(screen.getByRole("link", { name: "Exportar CSV" }).getAttribute("href")).toBe(`/api/admin/consumo/exportacion?mes=${mes}&filtro=TODAS&orden=PORCENTAJE`);
  });

  it("fuera de Consumo no ofrece exportar", () => {
    pintar("/admin/planes");

    expect(screen.queryByRole("link", { name: "Exportar CSV" })).toBeNull();
  });

  it("fuera de Planes no ofrece «Nuevo plan»", () => {
    pintar("/admin/cuentas");

    expect(screen.queryByTestId("plan-nuevo")).toBeNull();
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
