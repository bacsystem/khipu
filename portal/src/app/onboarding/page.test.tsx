import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect, useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/session-server", () => ({ getServerSession: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ me: vi.fn() }));
vi.mock("@/components/auth/revisa-tu-correo", () => ({ RevisaTuCorreo: () => null }));
vi.mock("@/components/onboarding/onboarding-empresa-form", () => ({ OnboardingEmpresaForm: () => <form data-testid="onboarding-form" /> }));
// El vigilante se prueba aparte; acá solo importa que la página lo monte con el N que informa /me.
vi.mock("@/components/sesion/vigilante-de-inactividad", () => ({
  VigilanteDeInactividad: ({ minutos }: { minutos?: number }) => <span data-testid="vigilante" data-minutos={String(minutos)} />,
}));

import { me } from "@/lib/api/auth";
import { getServerSession } from "@/lib/session-server";
import OnboardingPage from "./page";

const USUARIO = { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "ADMIN", correo_verificado: true };

afterEach(() => {
  cleanup();
  redirect.mockClear();
  vi.mocked(getServerSession).mockReset();
  vi.mocked(me).mockReset();
});

/** #637: el onboarding no cuelga de `(privado)`, pero su sesión está viva: lleva el mismo corte por inactividad. */
describe("OnboardingPage (#637)", () => {
  function conSesion(usuario: object) {
    vi.mocked(getServerSession).mockResolvedValue({ access: "jwt", empresaId: null } as never);
    vi.mocked(me).mockResolvedValue({ ...USUARIO, ...usuario } as never);
  }

  it("monta el vigilante con el N que informa /me", async () => {
    conSesion({ sesion_inactividad_minutos: 30 });

    render(await OnboardingPage());

    expect(screen.getByTestId("vigilante").getAttribute("data-minutos")).toBe("30");
    expect(screen.getByTestId("onboarding-form")).toBeTruthy();
  });

  it("también sobre la pantalla de aceptar los términos", async () => {
    conSesion({ terminos_pendientes: true, sesion_inactividad_minutos: 30 });

    render(await OnboardingPage());

    expect(screen.getByTestId("aceptar-terminos")).toBeTruthy();
    expect(screen.getByTestId("vigilante").getAttribute("data-minutos")).toBe("30");
  });

  it("sin el campo no hay N que pasar", async () => {
    conSesion({});

    render(await OnboardingPage());

    expect(screen.getByTestId("vigilante").getAttribute("data-minutos")).toBe("undefined");
  });

  it("sin sesión va al login, como siempre", async () => {
    vi.mocked(getServerSession).mockResolvedValue({ access: null, empresaId: null } as never);

    await expect(OnboardingPage()).rejects.toThrow("REDIRECT:/login");
  });
});
