import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
vi.mock("@/lib/api/banner", () => ({ obtenerBannerVigente: vi.fn().mockResolvedValue(null) }));
vi.mock("@/components/auth/revisa-tu-correo", () => ({ RevisaTuCorreo: () => null }));
vi.mock("@/components/nav/sidebar-content", () => ({ SidebarContent: () => null }));
vi.mock("@/components/nav/top-bar", () => ({ TopBar: () => null }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn().mockResolvedValue({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }) }));

import { me } from "@/lib/api/auth";
import { obtenerBannerVigente } from "@/lib/api/banner";
import { listarEmpresas } from "@/lib/api/empresas";
import { getServerSession } from "@/lib/session-server";
import PrivadoLayout from "./layout";

const USUARIO = { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "ADMIN", correo_verificado: true };
const EMPRESA = { id: "e1", ruc: "20100066603", razon_social: "ANDINA SAC", entorno: "BETA" };

function sesion(access: string | null = "jwt") {
  vi.mocked(getServerSession).mockResolvedValue({ access, empresaId: null } as never);
}

afterEach(() => {
  cleanup();
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

/** Una sesión de soporte (#184) lo dice arriba de todo, en cada página, y una sesión normal no dice nada. */
describe("PrivadoLayout (#184)", () => {
  it("con una sesión de soporte muestra el aviso permanente, como quién se mira y hasta cuándo", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue({ ...USUARIO, soporte_hasta: "2026-10-04T17:15:00Z" } as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);

    render(await PrivadoLayout({ children: <p>contenido</p> }));

    const aviso = screen.getByTestId("aviso-de-soporte");
    expect(aviso.textContent).toContain("a@b.com");
    expect(aviso.textContent).toContain("12:15");
    expect(screen.getByText("contenido")).toBeTruthy();
  });

  it("el aviso va antes que el resto de la página", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue({ ...USUARIO, soporte_hasta: "2026-10-04T17:15:00Z" } as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);

    render(await PrivadoLayout({ children: <p>contenido</p> }));

    const aviso = screen.getByTestId("aviso-de-soporte");
    const contenido = screen.getByText("contenido");
    expect(aviso.compareDocumentPosition(contenido) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  /** Salir vuelve a la cuenta del usuario al que se mira (la que dice /me), no a otra: la empresa activa tiene su propio id y el administrador no mira «una cuenta cualquiera». */
  it("salir del modo soporte lleva a la cuenta del usuario al que se mira", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue({ ...USUARIO, soporte_hasta: "2026-10-04T17:15:00Z" } as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);
    const assign = vi.fn();
    const original = window.location;
    Object.defineProperty(window, "location", { configurable: true, value: { ...original, assign } });
    try {
      render(await PrivadoLayout({ children: null }));

      fireEvent.click(screen.getByTestId("salir-de-soporte"));

      await waitFor(() => expect(assign).toHaveBeenCalledWith("/admin/cuentas/c1"));
    } finally {
      Object.defineProperty(window, "location", { configurable: true, value: original });
    }
  });

  it("una sesión normal no muestra ningún aviso", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);

    render(await PrivadoLayout({ children: <p>contenido</p> }));

    expect(screen.queryByTestId("aviso-de-soporte")).toBeNull();
    expect(screen.getByText("contenido")).toBeTruthy();
  });
});

/** El aviso de mantenimiento que publica el backoffice (#199): lo ve todo cliente, en cada página, y nunca impide que la página cargue. */
describe("PrivadoLayout (#199)", () => {
  const BANNER = { texto: "Mantenimiento esta noche", desde: "2026-10-15T20:00:00Z", hasta: "2026-10-16T04:00:00Z" };

  afterEach(() => vi.mocked(obtenerBannerVigente).mockResolvedValue(null));

  it("con un aviso vigente lo muestra, con su texto y hasta cuándo, antes del resto de la página", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);
    vi.mocked(obtenerBannerVigente).mockResolvedValue(BANNER);

    render(await PrivadoLayout({ children: <p>contenido</p> }));

    const banner = screen.getByTestId("banner-de-mantenimiento");
    expect(banner.textContent).toContain("Mantenimiento esta noche");
    expect(banner.textContent).toContain("Hasta 15 Oct 2026, 23:00");
    expect(banner.compareDocumentPosition(screen.getByText("contenido")) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("sin aviso no muestra nada", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);

    render(await PrivadoLayout({ children: <p>contenido</p> }));

    expect(screen.queryByTestId("banner-de-mantenimiento")).toBeNull();
    expect(screen.getByText("contenido")).toBeTruthy();
  });

  it("el aviso va después del aviso de una sesión de soporte (lo más importante, arriba de todo)", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue({ ...USUARIO, soporte_hasta: "2026-10-04T17:15:00Z" } as never);
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);
    vi.mocked(obtenerBannerVigente).mockResolvedValue(BANNER);

    render(await PrivadoLayout({ children: null }));

    expect(screen.getByTestId("aviso-de-soporte").compareDocumentPosition(screen.getByTestId("banner-de-mantenimiento")) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("pedir el aviso no cambia los redirects: sin sesión sigue yendo al login", async () => {
    sesion(null);
    vi.mocked(obtenerBannerVigente).mockResolvedValue(BANNER);

    await expect(PrivadoLayout({ children: null })).rejects.toThrow("REDIRECT:/login");
  });
});

/** Un 401 al cargar el portal es una sesión muerta (p. ej. una sesión de soporte que venció entre el middleware y el render): al login, no a una página de error. */
describe("PrivadoLayout (sesión vencida)", () => {
  it("un 401 de cualquiera de las dos llamadas va al login", async () => {
    sesion();
    vi.mocked(me).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Token inválido o expirado"));
    vi.mocked(listarEmpresas).mockResolvedValue([EMPRESA] as never);
    await expect(PrivadoLayout({ children: null })).rejects.toThrow("REDIRECT:/login");

    vi.mocked(me).mockResolvedValue(USUARIO as never);
    vi.mocked(listarEmpresas).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Token inválido o expirado"));
    await expect(PrivadoLayout({ children: null })).rejects.toThrow("REDIRECT:/login");
  });

  it("un 403 por otra causa o un 500 siguen propagándose: no son una sesión muerta", async () => {
    sesion();
    vi.mocked(me).mockResolvedValue(USUARIO as never);
    for (const err of [new ApiError(500, "INTERNO", "Error interno"), new ApiError(403, "SOPORTE_SOLO_LECTURA", "solo se puede mirar")]) {
      vi.mocked(listarEmpresas).mockRejectedValue(err);
      await expect(PrivadoLayout({ children: null })).rejects.toBe(err);
    }
  });
});
