import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect }));
vi.mock("@/lib/admin-session-server", () => ({ getAdminServerSession: vi.fn() }));
vi.mock("@/components/admin/verificar-integridad", () => ({ VerificarIntegridad: ({ hoy }: { hoy: string }) => <p data-testid="verificar">{hoy}</p> }));

import { getAdminServerSession } from "@/lib/admin-session-server";
import AdminIntegridadPage from "./page";

afterEach(() => {
  cleanup();
  redirect.mockClear();
  vi.mocked(getAdminServerSession).mockReset();
});

const sesion = (access: string | null = "jwt-admin") => vi.mocked(getAdminServerSession).mockResolvedValue({ access } as never);

/** La página de integridad (#198) no carga nada: el barrido lo pide el administrador desde el formulario. */
describe("AdminIntegridadPage", () => {
  it("sin sesión de administrador va al login", async () => {
    sesion(null);

    await expect(AdminIntegridadPage()).rejects.toThrow("REDIRECT:/admin/login");
  });

  it("con sesión muestra el título, la explicación y el formulario con la fecha de hoy en Lima", async () => {
    sesion();
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-16T03:00:00Z")); // todavía es el 15 en Lima (UTC-5)
    try {
      render(await AdminIntegridadPage());
    } finally {
      vi.useRealTimers();
    }

    expect(screen.getByRole("heading", { name: "Integridad del almacenamiento" })).toBeTruthy();
    expect(screen.getByText(/Solo lee: no repara nada/)).toBeTruthy();
    expect(screen.getByTestId("verificar").textContent).toBe("2026-10-15");
  });
});
