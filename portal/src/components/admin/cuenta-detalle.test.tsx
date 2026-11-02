import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CuentaDetalleAdmin } from "@/lib/api/admin-cuenta-detalle";
import { CuentaDetalle } from "./cuenta-detalle";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

afterEach(cleanup);

const CUENTA: CuentaDetalleAdmin = {
  id: "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11",
  nombre: "Panadería Sol",
  email: "ana@sol.pe",
  creada_en: "2026-09-01T15:00:00Z",
  estado: "SUSPENDIDA",
  suspendida_en: "2026-09-03T14:00:00Z",
  usuarios: [{ id: "u1", email: "ana@sol.pe", rol: "ADMIN", activo: true }],
  empresas: [],
  comprobantes: [],
  eventos: [
    { accion: "REGISTRAR_PAGO", actor: "ADMINISTRADOR", administrador: "beto@khipu.pe", ocurrido_en: "2026-09-05T15:00:00Z", detalle: "pago=abc medio=TRANSFERENCIA vence=sin_cambio" },
    { accion: "CREAR_TENANT", actor: "CLAVE_PLATAFORMA", ocurrido_en: "2026-09-02T10:00:00Z" },
    { accion: "CREAR_CUENTA", actor: "ADMINISTRADOR", ocurrido_en: "2026-09-01T15:00:00Z" },
  ],
  historial_estado: [
    { accion: "SUSPENDER_CUENTA", actor: "ADMINISTRADOR", administrador: "ana@khipu.pe", ocurrido_en: "2026-09-03T14:00:00Z", detalle: "motivo=Factura de agosto sin pagar" },
    { accion: "REACTIVAR_CUENTA", actor: "ADMINISTRADOR", administrador: "beto@khipu.pe", ocurrido_en: "2026-08-20T14:00:00Z" },
    { accion: "SUSPENDER_CUENTA", actor: "ADMINISTRADOR", administrador: "ana@khipu.pe", ocurrido_en: "2026-08-10T14:00:00Z", detalle: "motivo=Julio sin pagar" },
  ],
};

function pestana(nombre: RegExp) {
  fireEvent.click(screen.getByRole("tab", { name: nombre }));
}

describe("CuentaDetalle", () => {
  it("H19: lo de la cuenta va en pestañas, empezando por el plan y los pagos, y solo se monta la activa", () => {
    render(<CuentaDetalle cuenta={CUENTA} hoy="2026-10-08" plan={<p data-testid="plan">plan</p>} />);

    expect(screen.getAllByRole("tab").map((t) => t.textContent?.replace(/\d+$/, ""))).toEqual([
      "Plan y pagos",
      "Historial del estado",
      "Usuarios",
      "Empresas",
      "Comprobantes",
      "Bitácora",
    ]);
    expect(screen.getByTestId("plan")).toBeTruthy();
    expect(screen.queryByTestId("historial-estado")).toBeNull();

    pestana(/Usuarios/);
    expect(screen.queryByTestId("plan")).toBeNull();
    expect(screen.getByText("ADMIN")).toBeTruthy();
  });

  it("H15: el historial del estado lista cada cambio con cuándo, quién y el motivo", () => {
    render(<CuentaDetalle cuenta={CUENTA} hoy="2026-10-08" plan={<p>plan</p>} />);
    pestana(/Historial del estado/);

    const filas = within(screen.getByTestId("historial-estado")).getAllByRole("row");
    expect(filas.map((f) => within(f).getAllByRole("cell").map((c) => c.textContent))).toEqual([
      ["3 Set 2026, 09:00", "Suspendió la cuenta", "ana@khipu.pe", "Factura de agosto sin pagar"],
      ["20 Ago 2026, 09:00", "Reactivó la cuenta", "beto@khipu.pe", "Sin motivo"],
      ["10 Ago 2026, 09:00", "Suspendió la cuenta", "ana@khipu.pe", "Julio sin pagar"],
    ]);
  });

  it("H14: junto al estado se ve el motivo de la suspensión vigente, no el de una anterior", () => {
    render(<CuentaDetalle cuenta={CUENTA} hoy="2026-10-08" />);
    expect(screen.getByTestId("motivo-suspension").textContent).toBe("Motivo: Factura de agosto sin pagar");
  });

  it("H11 y H12: la bitácora dice qué administrador actuó y el detalle se lee sin códigos ni ids", () => {
    render(<CuentaDetalle cuenta={CUENTA} hoy="2026-10-08" />);
    pestana(/Bitácora/);

    const pago = screen.getByText("Registro de un pago").closest("tr")!;
    expect(within(pago).getByText("beto@khipu.pe")).toBeTruthy();
    expect(within(pago).getByText("Medio: Transferencia · Vence: sin cambio")).toBeTruthy();
    expect(screen.getByText("Clave de plataforma")).toBeTruthy();
    // Sin correo (el administrador ya no existe) se dice «Administrador» en vez de dejar la celda vacía.
    expect(within(screen.getByText("Alta de la cuenta").closest("tr")!).getByText("Administrador")).toBeTruthy();
  });

  it("H16: una cuenta de baja que no está suspendida se puede suspender, que es lo que corta el servicio", () => {
    render(<CuentaDetalle cuenta={{ ...CUENTA, estado: "BAJA", suspendida_en: undefined, baja_en: "2026-09-10T10:00:00Z", historial_estado: [] }} hoy="2026-10-08" />);
    expect(screen.getByTestId("suspender-cuenta")).toBeTruthy();
    expect(screen.getByTestId("reponer-cuenta")).toBeTruthy();
  });

  it("una cuenta de baja y suspendida ofrece reactivar", () => {
    render(<CuentaDetalle cuenta={{ ...CUENTA, estado: "BAJA", baja_en: "2026-09-10T10:00:00Z" }} hoy="2026-10-08" />);
    expect(screen.getByTestId("reactivar-cuenta")).toBeTruthy();
    expect(screen.queryByTestId("suspender-cuenta")).toBeNull();
  });
});
