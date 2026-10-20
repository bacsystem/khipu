import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SidebarNav } from "./sidebar-nav";

vi.mock("next/navigation", () => ({ usePathname: () => "/comprobantes" }));

afterEach(cleanup);

/** #21: lo que es solo de administradores (API keys, equipo) no aparece para los demás roles: el backend les respondería 403. */
describe("menú lateral según el rol", () => {
  it("el administrador ve API keys y Equipo", () => {
    render(<SidebarNav rol="ADMIN" />);
    expect(screen.getByRole("link", { name: /API keys/ })).toBeTruthy();
    expect(screen.getByRole("link", { name: /Equipo/ })).toBeTruthy();
  });

  for (const rol of ["EMISOR", "LECTURA"]) {
    it(`${rol} no ve API keys ni Equipo, pero sí lo que puede mirar`, () => {
      render(<SidebarNav rol={rol} />);
      expect(screen.queryByRole("link", { name: /API keys/ })).toBeNull();
      expect(screen.queryByRole("link", { name: /Equipo/ })).toBeNull();
      expect(screen.getByRole("link", { name: /Comprobantes/ })).toBeTruthy();
      expect(screen.getByRole("link", { name: /Series correlativas/ })).toBeTruthy();
      expect(screen.getByRole("link", { name: /Plan y consumo/ })).toBeTruthy();
    });
  }
});
