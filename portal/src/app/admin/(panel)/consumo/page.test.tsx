import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect }));
vi.mock("@/lib/admin-session-server", () => ({ getAdminServerSession: vi.fn() }));
vi.mock("@/lib/api/admin-consumo", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/api/admin-consumo")>()), listarConsumoDeCuentas: vi.fn() }));
vi.mock("@/components/admin/consumo-tabla", () => ({
  ConsumoTabla: ({ datos, total, params }: { datos: { cuentas: { nombre: string }[] }; total: number; params: unknown }) => (
    <p data-testid="tabla" data-total={total} data-params={JSON.stringify(params)}>
      {datos.cuentas.map((c) => c.nombre).join(",")}
    </p>
  ),
}));

import { getAdminServerSession } from "@/lib/admin-session-server";
import { listarConsumoDeCuentas } from "@/lib/api/admin-consumo";
import AdminConsumoPage from "./page";

afterEach(() => {
  cleanup();
  redirect.mockClear();
  vi.mocked(getAdminServerSession).mockReset();
  vi.mocked(listarConsumoDeCuentas).mockReset();
});

const sesion = (access: string | null = "jwt-admin") => vi.mocked(getAdminServerSession).mockResolvedValue({ access } as never);
const pagina = (cuentas: { nombre: string }[], total = cuentas.length) => ({ datos: { mes: "2026-10", umbral_de_alerta: 80, cuentas }, total });
const buscar = (q: Record<string, string> = {}) => ({ searchParams: Promise.resolve(q) });

/** La página de consumo (#193) corre en el servidor con el JWT del administrador, que el navegador nunca ve. */
describe("AdminConsumoPage", () => {
  it("sin sesión de administrador va al login y no llama al backend", async () => {
    sesion(null);

    await expect(AdminConsumoPage(buscar())).rejects.toThrow("REDIRECT:/admin/login");

    expect(listarConsumoDeCuentas).not.toHaveBeenCalled();
  });

  it("pide el consumo con el JWT y los parámetros saneados de la URL y lo pone en la tabla, bajo el título y la explicación", async () => {
    sesion();
    vi.mocked(listarConsumoDeCuentas).mockResolvedValue(pagina([{ nombre: "Ana" }, { nombre: "Luis" }], 57) as never);

    render(await AdminConsumoPage(buscar({ mes: "2026-09", filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: "2", por_pagina: "20" })));

    expect(listarConsumoDeCuentas).toHaveBeenCalledWith("jwt-admin", { mes: "2026-09", filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: 2, porPagina: 20 });
    expect(screen.getByRole("heading", { name: "Consumo" })).toBeTruthy();
    expect(screen.getByText(/Cuenta lo que SUNAT aceptó, aunque después se anule/)).toBeTruthy();
    const tabla = screen.getByTestId("tabla");
    expect(tabla.textContent).toBe("Ana,Luis");
    expect(tabla.getAttribute("data-total")).toBe("57");
    expect(JSON.parse(tabla.getAttribute("data-params") as string)).toEqual({ mes: "2026-09", filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: 2, porPagina: 20 });
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("un mes, filtro u orden raros en la URL son los valores por defecto: no llegan al backend", async () => {
    sesion();
    vi.mocked(listarConsumoDeCuentas).mockResolvedValue(pagina([]) as never);

    render(await AdminConsumoPage(buscar({ mes: "octubre", filtro: "x", orden: "y", pagina: "-4", por_pagina: "999" })));

    expect(listarConsumoDeCuentas).toHaveBeenCalledWith("jwt-admin", { mes: undefined, filtro: "TODAS", orden: "PORCENTAJE", pagina: 1, porPagina: 10 });
  });

  /** Una página pasada de la última (marcador viejo) lleva a la última, no a una tabla vacía que dice «no hay cuentas». */
  it("una página fuera de rango redirige a la última, conservando mes, filtro y orden", async () => {
    sesion();
    vi.mocked(listarConsumoDeCuentas).mockResolvedValue(pagina([], 12) as never);

    await expect(AdminConsumoPage(buscar({ mes: "2026-09", filtro: "CERCA_DEL_LIMITE", pagina: "99" }))).rejects.toThrow("REDIRECT:/admin/consumo?mes=2026-09&filtro=CERCA_DEL_LIMITE&pagina=2");
  });

  /** Si el backend no responde, la página lo dice y deja reintentar la misma consulta; no muestra una tabla vacía como si no hubiera cuentas. */
  it("si no se puede cargar muestra el error con un enlace para reintentar la misma consulta y ninguna tabla", async () => {
    sesion();
    vi.mocked(listarConsumoDeCuentas).mockRejectedValue(new Error("backend caído"));

    render(await AdminConsumoPage(buscar({ mes: "2026-09", filtro: "PLAN_VENCIDO" })));

    expect(screen.getByRole("alert").textContent).toContain("No se pudo cargar el consumo de las cuentas.");
    expect(screen.getByRole("link", { name: "Reintentar" }).getAttribute("href")).toBe("/admin/consumo?mes=2026-09&filtro=PLAN_VENCIDO");
    expect(screen.queryByTestId("tabla")).toBeNull();
  });
});
