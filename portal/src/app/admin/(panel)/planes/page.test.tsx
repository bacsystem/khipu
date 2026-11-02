import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect }));
vi.mock("@/lib/admin-session-server", () => ({ getAdminServerSession: vi.fn() }));
vi.mock("@/lib/api/admin-planes", () => ({ listarPlanesAdmin: vi.fn() }));
vi.mock("@/components/admin/planes-tabla", () => ({ PlanesTabla: ({ planes }: { planes: { nombre: string }[] }) => <p data-testid="tabla">{planes.map((p) => p.nombre).join(",")}</p> }));

import { getAdminServerSession } from "@/lib/admin-session-server";
import { listarPlanesAdmin } from "@/lib/api/admin-planes";
import AdminPlanesPage from "./page";

afterEach(() => {
  cleanup();
  redirect.mockClear();
  vi.mocked(getAdminServerSession).mockReset();
  vi.mocked(listarPlanesAdmin).mockReset();
});

const sesion = (access: string | null = "jwt-admin") => vi.mocked(getAdminServerSession).mockResolvedValue({ access } as never);

/** La página de planes (#190) corre en el servidor con el JWT del administrador, que el navegador nunca ve. */
describe("AdminPlanesPage", () => {
  it("sin sesión de administrador va al login y no llama al backend", async () => {
    sesion(null);

    await expect(AdminPlanesPage()).rejects.toThrow("REDIRECT:/admin/login");

    expect(listarPlanesAdmin).not.toHaveBeenCalled();
  });

  it("pide los planes con el JWT y los pone en la tabla, bajo el título y la explicación", async () => {
    sesion();
    vi.mocked(listarPlanesAdmin).mockResolvedValue([{ nombre: "Gratis" }, { nombre: "Pro" }] as never);

    render(await AdminPlanesPage());

    expect(listarPlanesAdmin).toHaveBeenCalledWith("jwt-admin");
    expect(screen.getByRole("heading", { name: "Planes" })).toBeTruthy();
    expect(screen.getByText(/Cambiar un límite afecta al ciclo siguiente/)).toBeTruthy();
    expect(screen.getByTestId("tabla").textContent).toBe("Gratis,Pro");
    expect(screen.queryByRole("alert")).toBeNull();
  });

  /** Si el backend no responde, la página lo dice y deja reintentar; no se cae ni muestra una tabla vacía como si no hubiera planes. */
  it("si no se pueden cargar muestra el error con un enlace para reintentar y ninguna tabla", async () => {
    sesion();
    vi.mocked(listarPlanesAdmin).mockRejectedValue(new Error("backend caído"));

    render(await AdminPlanesPage());

    expect(screen.getByRole("alert").textContent).toContain("No se pudo cargar el listado de planes.");
    expect(screen.getByRole("link", { name: "Reintentar" }).getAttribute("href")).toBe("/admin/planes");
    expect(screen.queryByTestId("tabla")).toBeNull();
  });
});
