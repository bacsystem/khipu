import { cleanup, render, screen } from "@testing-library/react";
import { SidebarNav } from "khipu-design-system/nav/sidebar-nav";
import { afterEach, describe, expect, it, vi } from "vitest";
import es from "@/messages/es.json";
import { gruposDeNavegacion } from "./grupos-de-navegacion";

vi.mock("next/navigation", () => ({ usePathname: () => "/comprobantes" }));

afterEach(cleanup);

function menu(rol: string) {
  render(<SidebarNav grupos={gruposDeNavegacion(es.nav, rol)} />);
}

/** #21: lo que es solo de administradores (API keys, equipo) no aparece para los demás roles: el backend les respondería 403. */
describe("menú lateral según el rol", () => {
  it("el administrador ve API keys y Equipo", () => {
    menu("ADMIN");
    expect(screen.getByRole("link", { name: /API keys/ })).toBeTruthy();
    expect(screen.getByRole("link", { name: /Equipo/ })).toBeTruthy();
  });

  for (const rol of ["EMISOR", "LECTURA"]) {
    it(`${rol} no ve API keys ni Equipo, pero sí lo que puede mirar`, () => {
      menu(rol);
      expect(screen.queryByRole("link", { name: /API keys/ })).toBeNull();
      expect(screen.queryByRole("link", { name: /Equipo/ })).toBeNull();
      expect(screen.getByRole("link", { name: /Comprobantes/ })).toBeTruthy();
      expect(screen.getByRole("link", { name: /Series correlativas/ })).toBeTruthy();
      expect(screen.getByRole("link", { name: /Plan y consumo/ })).toBeTruthy();
    });
  }

  it("el grupo activo marca su página con aria-current, como lo hacía el menú propio", () => {
    menu("ADMIN");
    expect(screen.getByRole("link", { name: /Comprobantes/ }).getAttribute("aria-current")).toBe("page");
    expect(screen.getByRole("link", { name: /Series correlativas/ }).getAttribute("aria-current")).toBeNull();
  });

  it("cada grupo lleva el título traducido y todas las páginas son enlaces", () => {
    const grupos = gruposDeNavegacion(es.nav, "ADMIN");
    expect(grupos.map((g) => g.titulo)).toEqual([es.nav.grupos.panel, es.nav.grupos.emision, es.nav.grupos.configuracion, es.nav.grupos.cuenta]);
    expect(grupos.flatMap((g) => g.items).every((i) => i.href?.startsWith("/"))).toBe(true);
  });
});
