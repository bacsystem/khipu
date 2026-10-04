import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";

const redirect = vi.hoisted(() => vi.fn((url: string) => { throw new Error(`REDIRECT:${url}`); }));
const notFound = vi.hoisted(() => vi.fn(() => { throw new Error("NOT_FOUND"); }));
vi.mock("next/navigation", () => ({ redirect, notFound }));
vi.mock("@/lib/admin-session-server", () => ({ getAdminServerSession: vi.fn() }));
vi.mock("@/lib/api/admin-cuenta-detalle", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/api/admin-cuenta-detalle")>()), obtenerCuentaAdmin: vi.fn() }));
vi.mock("@/lib/api/admin-plan-de-cuenta", () => ({ obtenerPlanDeCuenta: vi.fn() }));
vi.mock("@/lib/api/admin-planes", () => ({ listarPlanesAdmin: vi.fn() }));
vi.mock("@/components/admin/cuenta-detalle", () => ({ CuentaDetalle: ({ cuenta }: { cuenta: { nombre: string } }) => <p data-testid="detalle">{cuenta.nombre}</p> }));
vi.mock("@/components/admin/plan-de-cuenta", () => ({
  PlanDeCuenta: ({ cuentaId, cuentaNombre, plan, planes }: { cuentaId: string; cuentaNombre: string; plan: { estado: string }; planes: unknown[] }) => (
    <p data-testid="plan">{`${cuentaId}|${cuentaNombre}|${plan.estado}|${planes.length}`}</p>
  ),
}));

import { getAdminServerSession } from "@/lib/admin-session-server";
import { obtenerCuentaAdmin } from "@/lib/api/admin-cuenta-detalle";
import { obtenerPlanDeCuenta } from "@/lib/api/admin-plan-de-cuenta";
import { listarPlanesAdmin } from "@/lib/api/admin-planes";
import AdminCuentaPage from "./page";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const params = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  cleanup();
  redirect.mockClear();
  notFound.mockClear();
  vi.mocked(getAdminServerSession).mockReset();
  vi.mocked(obtenerCuentaAdmin).mockReset();
  vi.mocked(obtenerPlanDeCuenta).mockReset();
  vi.mocked(listarPlanesAdmin).mockReset();
});

const sesion = (access: string | null = "jwt-admin") => vi.mocked(getAdminServerSession).mockResolvedValue({ access } as never);

/** La ficha de una cuenta en el backoffice (#181) con su plan (#191): el plan se pide aparte y, si falla, la ficha se ve igual. */
describe("AdminCuentaPage — el plan", () => {
  it("pide la cuenta, su plan y los planes con el JWT, y muestra el plan antes del detalle", async () => {
    sesion();
    vi.mocked(obtenerCuentaAdmin).mockResolvedValue({ nombre: "Mi negocio" } as never);
    vi.mocked(obtenerPlanDeCuenta).mockResolvedValue({ estado: "VIGENTE" } as never);
    vi.mocked(listarPlanesAdmin).mockResolvedValue([{}, {}, {}] as never);

    render(await AdminCuentaPage(params()));

    expect(obtenerPlanDeCuenta).toHaveBeenCalledWith("jwt-admin", ID);
    expect(listarPlanesAdmin).toHaveBeenCalledWith("jwt-admin");
    expect(screen.getByTestId("plan").textContent).toBe(`${ID}|Mi negocio|VIGENTE|3`);
    expect(screen.getByTestId("detalle").textContent).toBe("Mi negocio");
    expect(screen.getByTestId("plan").compareDocumentPosition(screen.getByTestId("detalle")) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  /** Si el plan no carga, la ficha de la cuenta se ve igual y el plan dice que falló, con un enlace para reintentar la misma página. */
  it("si el plan no carga muestra el error con «Reintentar» y la ficha de la cuenta se ve igual", async () => {
    sesion();
    vi.mocked(obtenerCuentaAdmin).mockResolvedValue({ nombre: "Mi negocio" } as never);
    vi.mocked(obtenerPlanDeCuenta).mockRejectedValue(new ApiError(502, null, "caído", null));
    vi.mocked(listarPlanesAdmin).mockResolvedValue([] as never);

    render(await AdminCuentaPage(params()));

    expect(screen.getByRole("alert").textContent).toContain("No se pudo cargar el plan de la cuenta.");
    expect(screen.getByRole("link", { name: "Reintentar" }).getAttribute("href")).toBe(`/admin/cuentas/${ID}`);
    expect(screen.queryByTestId("plan")).toBeNull();
    expect(screen.getByTestId("detalle").textContent).toBe("Mi negocio");
  });

  it("si no cargan los planes ofrecibles tampoco se muestra el plan a medias", async () => {
    sesion();
    vi.mocked(obtenerCuentaAdmin).mockResolvedValue({ nombre: "Mi negocio" } as never);
    vi.mocked(obtenerPlanDeCuenta).mockResolvedValue({ estado: "VIGENTE" } as never);
    vi.mocked(listarPlanesAdmin).mockRejectedValue(new Error("caído"));

    render(await AdminCuentaPage(params()));

    expect(screen.getByRole("alert").textContent).toContain("No se pudo cargar el plan de la cuenta.");
    expect(screen.queryByTestId("plan")).toBeNull();
    expect(screen.getByTestId("detalle")).toBeTruthy();
  });

  it("una cuenta que no existe es 404 y no se pide su plan", async () => {
    sesion();
    vi.mocked(obtenerCuentaAdmin).mockRejectedValue(new ApiError(404, "NO_ENCONTRADO", "no existe", null));

    await expect(AdminCuentaPage(params())).rejects.toThrow("NOT_FOUND");

    expect(obtenerPlanDeCuenta).not.toHaveBeenCalled();
  });

  it("sin sesión va al login y un id que no es un UUID es 404, sin llamar al backend", async () => {
    sesion(null);
    await expect(AdminCuentaPage(params())).rejects.toThrow("REDIRECT:/admin/login");
    await expect(AdminCuentaPage(params("no-es-un-uuid"))).rejects.toThrow("NOT_FOUND");

    expect(obtenerCuentaAdmin).not.toHaveBeenCalled();
    expect(obtenerPlanDeCuenta).not.toHaveBeenCalled();
  });
});
