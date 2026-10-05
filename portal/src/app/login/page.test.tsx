import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/banner", () => ({ obtenerBannerVigente: vi.fn() }));
vi.mock("@/lib/acceso", () => ({ registroAbierto: () => true }));
vi.mock("@/components/auth/login-form", () => ({ LoginForm: () => <form data-testid="login-form" /> }));

import { obtenerBannerVigente } from "@/lib/api/banner";
import LoginPage from "./page";

afterEach(() => {
  cleanup();
  vi.mocked(obtenerBannerVigente).mockReset();
});

/** El aviso de mantenimiento (#199) se ve también antes de iniciar sesión: es cuando un cliente se entera de que hoy no va a poder emitir. */
describe("LoginPage (#199)", () => {
  it("con un aviso vigente lo muestra arriba del inicio de sesión", async () => {
    vi.mocked(obtenerBannerVigente).mockResolvedValue({ texto: "Mantenimiento esta noche", desde: "2026-10-15T20:00:00Z", hasta: "2026-10-16T04:00:00Z" });

    render(await LoginPage());

    const banner = screen.getByTestId("banner-de-mantenimiento");
    expect(banner.textContent).toContain("Mantenimiento esta noche");
    expect(banner.textContent).toContain("Hasta 15 Oct 2026, 23:00");
    expect(screen.getByTestId("login-form")).toBeTruthy();
    expect(banner.compareDocumentPosition(screen.getByTestId("login-form")) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("sin aviso no muestra nada extra y el formulario sigue ahí", async () => {
    vi.mocked(obtenerBannerVigente).mockResolvedValue(null);

    render(await LoginPage());

    expect(screen.queryByTestId("banner-de-mantenimiento")).toBeNull();
    expect(screen.getByTestId("login-form")).toBeTruthy();
  });
});
