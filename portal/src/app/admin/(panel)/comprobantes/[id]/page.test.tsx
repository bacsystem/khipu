import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";

const redirect = vi.hoisted(() => vi.fn((url: string) => { throw new Error(`REDIRECT:${url}`); }));
const notFound = vi.hoisted(() => vi.fn(() => { throw new Error("NOT_FOUND"); }));
vi.mock("next/navigation", () => ({ redirect, notFound }));
vi.mock("@/lib/admin-session-server", () => ({ getAdminServerSession: vi.fn() }));
vi.mock("@/lib/api/admin-comprobante", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/api/admin-comprobante")>()), obtenerComprobanteAdmin: vi.fn() }));

import { getAdminServerSession } from "@/lib/admin-session-server";
import { obtenerComprobanteAdmin, type BajaAdmin, type ComprobanteAdmin } from "@/lib/api/admin-comprobante";
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
  bajas: [],
};

const RC_BAJA: BajaAdmin = {
  id: "b-1",
  identificador: "RC-20261013-2",
  condicion: "BAJA",
  estado: "ACEPTADA",
  ticket: "1789768174685",
  motivo: "Se cobró dos veces",
  fecha_generacion: "2026-10-13",
  fecha_referencia: "2026-10-12",
  fuera_de_plazo: false,
  cdr: { codigo: "0", descripcion: "El Resumen diario RC-20261013-2, ha sido aceptado" },
  intentos: 0,
};
const BOLETA = { ...EN_ERROR, tipo: "03", serie: "B001", nombre_archivo: "20100066603-03-B001-7", estado: "ANULADO" as const };

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

  /** #362 H9: sin baja no hay sección: no se ocupa la ficha con un «no hay» en cada comprobante. */
  it("sin bajas no muestra la sección de bajas", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue(EN_ERROR);

    render(await AdminComprobantePage(params()));

    expect(screen.queryByTestId("comprobante-bajas")).toBeNull();
  });

  it("la baja de una boleta muestra su resumen diario: identificador, estado, ticket, motivo, fechas y respuesta de SUNAT", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue({ ...BOLETA, bajas: [RC_BAJA] });

    render(await AdminComprobantePage(params()));

    const baja = screen.getByTestId("comprobante-baja-RC-20261013-2");
    expect(screen.getByTestId("comprobante-bajas")).toContainElement(baja);
    expect(baja.textContent).toContain("Resumen diario RC-20261013-2");
    expect(within(baja).getByTestId("baja-estado").textContent).toBe("Aceptada: comprobante anulado");
    expect(within(baja).getByTestId("baja-ticket").textContent).toBe("1789768174685");
    expect(within(baja).getByTestId("baja-motivo").textContent).toContain("Se cobró dos veces");
    expect(within(baja).getByTestId("baja-motivo").textContent).toContain("Queda solo en khipu: el resumen diario no lo lleva");
    expect(within(baja).getByTestId("baja-generada").textContent).toBe("13 Oct 2026");
    expect(within(baja).getByTestId("baja-emision").textContent).toBe("12 Oct 2026");
    expect(within(baja).getByTestId("baja-respuesta").textContent).toBe("0 — El Resumen diario RC-20261013-2, ha sido aceptado");
    expect(within(baja).getByText("Respuesta de SUNAT")).toBeInTheDocument();
  });

  it("la baja de una factura es una comunicación de baja (RA) y su motivo sí viaja a SUNAT", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue({ ...EN_ERROR, estado: "ANULADO", bajas: [{ ...RC_BAJA, identificador: "RA-20261013-1", motivo: "Error en el RUC" }] });

    render(await AdminComprobantePage(params()));

    const baja = screen.getByTestId("comprobante-baja-RA-20261013-1");
    expect(baja.textContent).toContain("Comunicación de baja RA-20261013-1");
    expect(within(baja).getByTestId("baja-motivo").textContent).toBe("Error en el RUC");
  });

  it("un trámite en curso dice el último intento, no una respuesta; sin ticket muestra un guion", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue({
      ...BOLETA,
      estado: "ACEPTADO",
      bajas: [{ ...RC_BAJA, estado: "ERROR_ENVIO", ticket: undefined, cdr: undefined, intentos: 3, ultimo_error: "SUNAT respondió HTTP 503" }],
    });

    render(await AdminComprobantePage(params()));

    const baja = screen.getByTestId("comprobante-baja-RC-20261013-2");
    expect(within(baja).getByTestId("baja-estado").textContent).toBe("Error de envío: se reintentará");
    expect(within(baja).getByTestId("baja-ticket").textContent).toBe("—");
    expect(within(baja).getByTestId("baja-respuesta").textContent).toBe("SUNAT respondió HTTP 503 (3 intentos)");
    expect(within(baja).getByText("Último intento")).toBeInTheDocument();
    expect(within(baja).queryByText("Respuesta de SUNAT")).toBeNull();
  });

  /** El alta informa la boleta en vez de anularla; fuera de plazo SUNAT puede observarla con 4443 pero no la rechaza (en e-beta no la observó). */
  it("un alta fuera de plazo se rotula «alta», dice «informada» y avisa que la 4443 es posible, no segura", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue({
      ...BOLETA,
      estado: "ACEPTADO",
      bajas: [{ ...RC_BAJA, identificador: "RC-20261020-1", condicion: "ALTA", fuera_de_plazo: true, motivo: "Pasó el 7.º día: se informa en el resumen diario fuera de plazo" }],
    });

    render(await AdminComprobantePage(params()));

    const baja = screen.getByTestId("comprobante-baja-RC-20261020-1");
    expect(baja.textContent).toContain("(alta)");
    expect(within(baja).getByTestId("baja-estado").textContent).toBe("Aceptado: boleta informada a SUNAT");
    const aviso = within(baja).getByTestId("baja-fuera-de-plazo").textContent ?? "";
    expect(aviso).toContain("fuera de plazo");
    expect(aviso).toContain("4443");
    expect(aviso).toContain("puede observarla");
    expect(within(baja).getByTestId("baja-motivo").textContent).not.toContain("Queda solo en khipu"); // el motivo de un alta lo escribe khipu
  });

  it("un alta en plazo no avisa de fuera de plazo, y una baja vencida (2957) se rotula «Vencida»", async () => {
    sesion();
    vi.mocked(obtenerComprobanteAdmin).mockResolvedValue({
      ...BOLETA,
      estado: "ACEPTADO",
      bajas: [
        { ...RC_BAJA, identificador: "RC-20261020-1", condicion: "ALTA", fuera_de_plazo: false },
        { ...RC_BAJA, identificador: "RC-20261019-1", estado: "RECHAZADA", cdr: { codigo: "2957", descripcion: "El plazo para dar de baja venció" } },
      ],
    });

    render(await AdminComprobantePage(params()));

    expect(within(screen.getByTestId("comprobante-baja-RC-20261020-1")).queryByTestId("baja-fuera-de-plazo")).toBeNull();
    expect(within(screen.getByTestId("comprobante-baja-RC-20261019-1")).getByTestId("baja-estado").textContent).toBe("Vencida: pasaron los 7 días para anularla");
    expect(within(screen.getByTestId("comprobante-baja-RC-20261019-1")).getByText("Detalle")).toBeInTheDocument();
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
