import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { EmpresaAdmin } from "@/lib/api/admin-empresas";
import { EmpresasTabla } from "./empresas-tabla";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockClear();
});

const ANDINA: EmpresaAdmin = {
  id: "e1",
  ruc: "20100066603",
  razon_social: "COMERCIAL ANDINA SAC",
  cuenta_id: "00000000-0000-4000-8000-000000000001",
  cuenta_nombre: "Mi negocio",
  entorno: "PRODUCCION",
  certificado: "POR_VENCER",
  certificado_vigente_hasta: "2026-10-13",
  certificado_dias_restantes: 10,
  tiene_credenciales_sol: true,
  series: 2,
  comprobantes_del_mes: 14,
  ultima_emision: "2026-10-02",
};

describe("EmpresasTabla (#185)", () => {
  /**
   * Cambiar un filtro cambia el conjunto: seguir en la página 3 de otro conjunto es una página que ya no existe. La página lo corrige
   * redirigiendo a la última, pero eso solo tapa el error cuando el resultado cabe en una sola página; con un conjunto que sigue teniendo
   * varias, el administrador se quedaría en una página que no pidió. Por eso el selector manda siempre a la 1.
   */
  it("elegir un entorno desde la página 3 navega a la primera, con el tamaño de página que tenía", () => {
    render(<EmpresasTabla datos={[ANDINA]} total={120} params={{ pagina: 3, porPagina: 20 }} />);

    fireEvent.change(screen.getByLabelText("Entorno"), { target: { value: "PRODUCCION" } });

    expect(push).toHaveBeenCalledWith("/admin/empresas?entorno=PRODUCCION&por_pagina=20");
  });

  it("elegir un estado de certificado desde la página 3 navega a la primera y conserva el entorno elegido", () => {
    render(<EmpresasTabla datos={[ANDINA]} total={120} params={{ entorno: "BETA", pagina: 3, porPagina: 10 }} />);

    fireEvent.change(screen.getByLabelText("Certificado"), { target: { value: "VENCIDO" } });

    expect(push).toHaveBeenCalledWith("/admin/empresas?entorno=BETA&certificado=VENCIDO");
  });

  it("volver a «Todos» quita ese filtro de la URL y deja el otro", () => {
    render(<EmpresasTabla datos={[ANDINA]} total={5} params={{ entorno: "BETA", certificado: "VENCIDO", pagina: 1, porPagina: 10 }} />);

    fireEvent.change(screen.getByLabelText("Entorno"), { target: { value: "" } });

    expect(push).toHaveBeenCalledWith("/admin/empresas?certificado=VENCIDO");
  });

  it("la paginación conserva los dos filtros y el tamaño de página en sus enlaces", () => {
    render(<EmpresasTabla datos={[ANDINA]} total={45} params={{ entorno: "PRODUCCION", certificado: "VENCIDO", pagina: 1, porPagina: 20 }} />);

    expect(screen.getByRole("link", { name: /Siguiente/ }).getAttribute("href")).toBe(
      "/admin/empresas?entorno=PRODUCCION&certificado=VENCIDO&pagina=2&por_pagina=20",
    );
  });

  it("sin resultados con filtros: lo dice y ofrece quitarlos, conservando el tamaño de página", () => {
    render(<EmpresasTabla datos={[]} total={0} params={{ entorno: "BETA", pagina: 1, porPagina: 20 }} />);

    expect(screen.getByText("No hay empresas con esos filtros.")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Quitar filtros" }).getAttribute("href")).toBe("/admin/empresas?por_pagina=20");
  });

  it("sin filtros y sin empresas dice que todavía no hay ninguna, sin ofrecer quitar nada", () => {
    render(<EmpresasTabla datos={[]} total={0} params={{ pagina: 1, porPagina: 10 }} />);

    expect(screen.getByText("Todavía no hay empresas.")).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Quitar filtros" })).toBeNull();
  });

  it("los selectores muestran los filtros de la URL", () => {
    render(<EmpresasTabla datos={[ANDINA]} total={1} params={{ entorno: "PRODUCCION", certificado: "POR_VENCER", pagina: 1, porPagina: 10 }} />);

    expect((screen.getByLabelText("Entorno") as HTMLSelectElement).value).toBe("PRODUCCION");
    expect((screen.getByLabelText("Certificado") as HTMLSelectElement).value).toBe("POR_VENCER");
  });

  it("la fila trae el estado del certificado y los días que le quedan", () => {
    render(<EmpresasTabla datos={[ANDINA]} total={1} params={{ pagina: 1, porPagina: 10 }} />);

    expect(screen.getByText(/Vence el .* \(10 días\)/)).toBeTruthy();
    expect(screen.getByText("COMERCIAL ANDINA SAC")).toBeTruthy();
    expect(screen.getByText("20100066603")).toBeTruthy();
  });
});
