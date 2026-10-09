import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";

const redirect = vi.hoisted(() => vi.fn((url: string) => { throw new Error(`REDIRECT:${url}`); }));
const notFound = vi.hoisted(() => vi.fn(() => { throw new Error("NOT_FOUND"); }));
vi.mock("next/navigation", () => ({ redirect, notFound }));
vi.mock("@/lib/admin-session-server", () => ({ getAdminServerSession: vi.fn() }));
vi.mock("@/lib/api/admin-comprobante", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/api/admin-comprobante")>()), obtenerComprobanteAdmin: vi.fn() }));

import { getAdminServerSession } from "@/lib/admin-session-server";
import { obtenerComprobanteAdmin, type ComprobanteAdmin } from "@/lib/api/admin-comprobante";
import AdminComprobantePage from "./page";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const EMPRESA = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f";
const params = (id = ID) => ({ params: Promise.resolve({ id }) });
const sesion = (access: string | null = "jwt-admin") => vi.mocked(getAdminServerSession).mockResolvedValue({ access } as never);

const EN_ERROR: ComprobanteAdmin = {
  id: ID,
  empresa_id: EMPRESA,
  ruc: "20100066603",
  razon_social: "COMERCIAL ANDINA SAC",
  nombre_archivo: "20100066603-01-F001-7",
  tipo: "01",
  serie: "F001",
  numero: 7,
  fecha_emision: "2026-10-12",
  estado: "ERROR_ENVIO",
  intentos: 3,
  ultimo_error: "0109 - El sistema no puede responder",
  tiene_xml: true,
  tiene_cdr: false,
};

afterEach(() => {
  cleanup();
  redirect.mockClear();
  notFound.mockClear();
  vi.mocked(getAdminServerSession).mockReset();
  vi.mocked(obtenerComprobanteAdmin).mockReset();
});

describe("AdminComprobantePage (#251)", () => {
  it("pide la ficha con el JWT y muestra estado, intentos, último error, archivos y el enlace a la empresa", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue(EN_ERROR);

    render(await AdminComprobantePage(params()));

    expect(obtenerComprobanteAdmin).toHaveBeenCalledWith("jwt-admin", ID);
    expect(screen.getByText("20100066603-01-F001-7")).toBeInTheDocument();
    expect(screen.getByTestId("comprobante-intentos").textContent).toBe("3");
    expect(screen.getByTestId("comprobante-ultimo-error").textContent).toBe("0109 - El sistema no puede responder");
    expect(screen.getByTestId("comprobante-respuesta").textContent).toBe("Todavía no respondió");
    expect(screen.getByTestId("comprobante-xml").textContent).toBe("XML firmado: Guardado");
    expect(screen.getByTestId("comprobante-cdr").textContent).toBe("CDR de SUNAT: No está");
    expect(screen.getByRole("link", { name: "Ver la empresa" }).getAttribute("href")).toBe(`/admin/empresas/${EMPRESA}`);
  });

  it("con respuesta de SUNAT la muestra con su código", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue({
      ...EN_ERROR,
      estado: "ACEPTADO",
      intentos: 0,
      ultimo_error: undefined,
      respuesta_sunat: { codigo: "0", descripcion: "La Factura numero F001-7, ha sido aceptada" },
      tiene_cdr: true,
    });

    render(await AdminComprobantePage(params()));

    expect(screen.getByTestId("comprobante-respuesta").textContent).toBe("0 — La Factura numero F001-7, ha sido aceptada");
    expect(screen.getByTestId("comprobante-ultimo-error").textContent).toBe("Ninguno");
    expect(screen.getByTestId("comprobante-cdr").textContent).toBe("CDR de SUNAT: Guardado");
  });

  /** 273-H2: fuera de plazo (o descartado) ya no va a llegar a SUNAT: no es «todavía no respondió», y el 2108 está en el último error. */
  it("fuera de plazo dice que no llegó a SUNAT y muestra el motivo como último error", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue({ ...EN_ERROR, estado: "FUERA_DE_PLAZO", ultimo_error: "2108 - Presentación fuera de fecha" });

    render(await AdminComprobantePage(params()));

    expect(screen.getByTestId("comprobante-respuesta").textContent).toBe("No llegó a SUNAT");
    expect(screen.getByTestId("comprobante-ultimo-error").textContent).toBe("2108 - Presentación fuera de fecha");
  });

  it("si el backend falla muestra el error con «Reintentar» a la misma página", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockRejectedValue(new ApiError(502, null, "caído", null));

    render(await AdminComprobantePage(params()));

    expect(screen.getByRole("alert").textContent).toContain("No se pudo cargar el comprobante.");
    expect(screen.getByRole("link", { name: "Reintentar" }).getAttribute("href")).toBe(`/admin/comprobantes/${ID}`);
  });

  it("un comprobante que no existe es 404", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockRejectedValue(new ApiError(404, "NO_ENCONTRADO", "no existe", null));
    await expect(AdminComprobantePage(params())).rejects.toThrow("NOT_FOUND");
  });

  it("sin sesión va al login y un id que no es un UUID es 404, sin llamar al backend", async () => {
    sesion(null);
    await expect(AdminComprobantePage(params())).rejects.toThrow("REDIRECT:/admin/login");
    await expect(AdminComprobantePage(params("no-es-un-uuid"))).rejects.toThrow("NOT_FOUND");
    expect(obtenerComprobanteAdmin).not.toHaveBeenCalled();
  });
});
