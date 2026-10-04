import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CuentaAdmin } from "@/lib/api/admin-cuentas";
import { CuentasTabla } from "./cuentas-tabla";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockClear();
});

const ANA: CuentaAdmin = {
  id: "c1",
  nombre: "Panadería Sol",
  email: "ana@sol.pe",
  telefono: "987654321",
  creada_en: "2026-09-02T15:00:00Z",
  empresas: 2,
  ultimo_acceso: "2026-10-01T14:30:00Z",
  estado: "ACTIVA",
};
const NUEVA: CuentaAdmin = { id: "c2", nombre: "Ferretería Luna", email: "luis@luna.pe", creada_en: "2026-09-03T15:00:00Z", empresas: 0, estado: "ACTIVA" };
const SUSPENDIDA: CuentaAdmin = { ...ANA, id: "c3", nombre: "Bodega Sur", email: "sur@bodega.pe", estado: "SUSPENDIDA", suspendida_en: "2026-10-02T15:00:00Z" };

const SIN_FILTROS = { pagina: 1, porPagina: 10 };

describe("CuentasTabla estado (#182)", () => {
  it("cada cuenta dice si está activa o suspendida", () => {
    render(<CuentasTabla datos={[ANA, SUSPENDIDA]} total={2} params={SIN_FILTROS} />);

    expect(screen.getByText("Activa")).toBeTruthy();
    expect(screen.getByText("Suspendida")).toBeTruthy();
    expect(screen.getByRole("columnheader", { name: "Estado" })).toBeTruthy();
  });

  /** El color es lo que el administrador lee de un vistazo: suspendida en rojo, activa en verde, y nunca al revés. */
  it("la etiqueta de suspendida va en rojo y la de activa en verde", () => {
    render(<CuentasTabla datos={[ANA, SUSPENDIDA]} total={2} params={SIN_FILTROS} />);

    expect(screen.getByText("Suspendida").className).toContain("text-destructive");
    expect(screen.getByText("Suspendida").className).not.toContain("text-success");
    expect(screen.getByText("Activa").className).toContain("text-success");
    expect(screen.getByText("Activa").className).not.toContain("text-destructive");
  });

  it("la fila de una cuenta suspendida se distingue, y la de una activa no", () => {
    render(<CuentasTabla datos={[ANA, SUSPENDIDA]} total={2} params={SIN_FILTROS} />);

    const filas = screen.getAllByRole("row").slice(1);
    expect(filas[0].getAttribute("data-estado-cuenta")).toBe("ACTIVA");
    expect(filas[0].className).not.toContain("bg-destructive");
    expect(filas[1].getAttribute("data-estado-cuenta")).toBe("SUSPENDIDA");
    expect(filas[1].className).toContain("bg-destructive");
  });

  it("una cuenta suspendida sigue en el listado y enlazada a su detalle, para poder reactivarla", () => {
    render(<CuentasTabla datos={[SUSPENDIDA]} total={1} params={SIN_FILTROS} />);

    expect(screen.getByRole("link", { name: "Bodega Sur" }).getAttribute("href")).toBe("/admin/cuentas/c3");
  });
});

describe("CuentasTabla", () => {
  it("muestra la cuenta con su correo, teléfono, empresas y fechas en hora de Lima", () => {
    render(<CuentasTabla datos={[ANA]} total={1} params={SIN_FILTROS} />);

    expect(screen.getByText("Panadería Sol")).toBeTruthy();
    expect(screen.getByText("ana@sol.pe")).toBeTruthy();
    expect(screen.getByText("987654321")).toBeTruthy();
    expect(screen.getByText("2 Set 2026, 10:00")).toBeTruthy();
    expect(screen.getByText("1 Oct 2026, 09:30")).toBeTruthy();
    expect(screen.getByText("2")).toBeTruthy();
  });

  it("una cuenta sin teléfono ni sesiones muestra un guion y «Nunca», sin inventar valores", () => {
    render(<CuentasTabla datos={[NUEVA]} total={1} params={SIN_FILTROS} />);

    expect(screen.getByText("Nunca")).toBeTruthy();
    expect(screen.getByText("—")).toBeTruthy();
  });

  it("rotula el último acceso como inicio de sesión: no mide el uso por API key", () => {
    render(<CuentasTabla datos={[ANA]} total={1} params={SIN_FILTROS} />);

    expect(screen.getByText("Último inicio de sesión")).toBeTruthy();
  });

  it("no muestra una columna de plan: llega con #189", () => {
    render(<CuentasTabla datos={[ANA]} total={1} params={SIN_FILTROS} />);

    expect(screen.queryByText(/plan/i)).toBeNull();
  });

  it("el pie dice qué filas se ven y el total", () => {
    render(<CuentasTabla datos={[ANA, NUEVA]} total={12} params={SIN_FILTROS} />);

    expect(document.body.textContent?.replace(/\s+/g, " ")).toMatch(/Mostrando 1–2 de 12/);
  });

  it("la paginación conserva la búsqueda y el tamaño de página en sus enlaces", () => {
    render(<CuentasTabla datos={[ANA]} total={25} params={{ q: "luna", pagina: 1, porPagina: 10 }} />);

    expect(screen.getByRole("link", { name: /Siguiente/ }).getAttribute("href")).toBe("/admin/cuentas?q=luna&pagina=2");
  });

  it("sin cuentas y sin búsqueda: invita a esperar a la primera", () => {
    render(<CuentasTabla datos={[]} total={0} params={SIN_FILTROS} />);

    expect(screen.getByText("Todavía no hay cuentas.")).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Quitar filtros" })).toBeNull();
  });

  it("sin resultados con una búsqueda: lo dice y ofrece quitarla", () => {
    render(<CuentasTabla datos={[]} total={0} params={{ q: "nadie", pagina: 1, porPagina: 10 }} />);

    expect(screen.getByText("No hay cuentas con esos filtros.")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Quitar filtros" }).getAttribute("href")).toBe("/admin/cuentas");
  });

  it("buscar manda a la primera página con la búsqueda recortada y conserva el tamaño de página", () => {
    render(<CuentasTabla datos={[ANA]} total={30} params={{ pagina: 3, porPagina: 20 }} />);

    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "  ana " } });
    fireEvent.submit(screen.getByRole("search"));

    expect(push).toHaveBeenCalledWith("/admin/cuentas?q=ana&por_pagina=20");
  });

  it("el buscador arranca con la búsqueda de la URL", () => {
    render(<CuentasTabla datos={[ANA]} total={1} params={{ q: "sol", pagina: 1, porPagina: 10 }} />);

    expect((screen.getByRole("searchbox") as HTMLInputElement).value).toBe("sol");
  });
});
