import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({ redirect: vi.fn((url: string) => { throw new Error(`REDIRECT:${url}`); }) }));
vi.mock("@/lib/admin-session-server", () => ({ getAdminServerSession: vi.fn(async () => ({ access: "jwt-admin" })) }));
vi.mock("@/lib/api/admin-avisos", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/admin-avisos")>()),
  listarCredencialesSolFallando: vi.fn(async () => ({ datos: [], total: 0 })),
  listarCertificadosEnRiesgo: vi.fn(async () => ({ datos: [], total: 0 })),
}));
vi.mock("@/components/admin/avisos-tabla", () => ({ AvisosTabla: () => <p data-testid="tabla" /> }));

import AdminAvisosPage from "./page";

afterEach(cleanup);

describe("AdminAvisosPage", () => {
  /** H21: una empresa que nunca cargó certificado no tiene nada que vencer y no sale en los avisos, aunque tampoco puede emitir. */
  it("lleva a las empresas sin certificado, que no salen en estas listas", async () => {
    render(await AdminAvisosPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("link", { name: "Ver las empresas sin certificado" }).getAttribute("href")).toBe("/admin/empresas?certificado=SIN_CERTIFICADO");
  });
});
