import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect }));
vi.mock("@/lib/session-server", () => ({ getServerSession: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ me: vi.fn() }));
vi.mock("@/lib/api/empresas", () => ({ listarEmpresas: vi.fn() }));
vi.mock("@/components/auth/revisa-tu-correo", () => ({ RevisaTuCorreo: () => null }));
vi.mock("@/components/nav/sidebar-content", () => ({ SidebarContent: () => null }));
vi.mock("@/components/nav/top-bar", () => ({ TopBar: () => null }));

import { me } from "@/lib/api/auth";
import { listarEmpresas } from "@/lib/api/empresas";
import { getServerSession } from "@/lib/session-server";
import PrivadoLayout from "./layout";

const USUARIO = { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "ADMIN", correo_verificado: true };
const EMPRESA = { id: "e1", ruc: "20100066603", razon_social: "ANDINA SAC", entorno: "BETA" };

function sesion(access: string | null = "jwt") {
  vi.mocked(getServerSession).mockResolvedValue({ access, empresaId: null } as never);
}

afterEach(() => {
  redirect.mockClear();
  vi.mocked(getServerSession).mockReset();
  vi.mocked(me).mockReset();
  vi.mocked(listarEmpresas).mockReset();
});

describe("PrivadoLayout (#182)", () => {
  it("sin sesión va al login", async () => {
    sesion(null);

    await expect(PrivadoLayout({ children: null })).rejects.toThrow("REDIRECT:/login");
  });

  it("una cuenta suspendida (403 CUENTA_SUSPENDIDA al listar sus empresas) va a la página que se lo explica", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);
    vi.mocked(listarEmpresas).mockRejectedValue(new ApiError(403, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida"));

    await expect(PrivadoLayout({ children: null })).rejects.toThrow("REDIRECT:/cuenta-suspendida");
  });

  it("también si es la otra llamada la que responde la suspensión", async () => {
    sesion();
    vi.mocked(me).mockRejectedValue(new ApiError(403, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida"));
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);

    await expect(PrivadoLayout({ children: null })).rejects.toThrow("REDIRECT:/cuenta-suspendida");
  });

  /** Un 403 por otra causa o un 500 no son una suspensión: no deben mandar a una página que dice algo falso, ni esconderse. */
  it("cualquier otro error se propaga y no lleva a la página de suspensión", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);

    for (const err of [new ApiError(500, "INTERNO", "Error interno"), new ApiError(403, "EMPRESA_AJENA", "No es tu empresa")]) {
      vi.mocked(listarEmpresas).mockRejectedValue(err);
      await expect(PrivadoLayout({ children: null })).rejects.toBe(err);
    }
    expect(redirect).not.toHaveBeenCalled();
  });

  it("sin empresas va al onboarding, como siempre", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);
    vi.mocked(listarEmpresas).mockResolvedValue([]);

    await expect(PrivadoLayout({ children: null })).rejects.toThrow("REDIRECT:/onboarding");
  });

  it("con la cuenta al día renderiza sin redirigir", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);

    const arbol = await PrivadoLayout({ children: null });

    expect(arbol).toBeTruthy();
    expect(redirect).not.toHaveBeenCalled();
  });
});
