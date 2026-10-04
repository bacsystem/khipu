import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PagoAdmin } from "@/lib/api/admin-pagos";
import { PagosDeCuenta } from "./pagos-de-cuenta";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/components/admin/registrar-pago", () => ({
  RegistrarPago: ({ cuentaId, cuentaNombre, plan, hoy }: { cuentaId: string; cuentaNombre: string; plan: { estado: string } | null; hoy: string }) => (
    <button data-testid="registrar-pago">{`${cuentaId}|${cuentaNombre}|${plan?.estado ?? "sin plan"}|${hoy}`}</button>
  ),
}));

afterEach(cleanup);

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";

const SIMPLE: PagoAdmin = {
  id: "p1",
  cuenta_id: CUENTA,
  periodo_desde: "2026-10-01",
  periodo_hasta: "2026-10-31",
  monto: 29,
  medio: "YAPE",
  fecha_de_pago: "2026-10-14",
  registrado_en: "2026-10-15T15:00:00Z",
};
const COMPLETO: PagoAdmin = { ...SIMPLE, id: "p2", monto: 1234.5, medio: "TRANSFERENCIA", referencia: "OP-777", nota: "Pagó por el BCP", extendio_hasta: "2026-11-01T05:00:00Z", fecha_de_pago: "2026-09-02" };

function mostrar(pagos: PagoAdmin[], total = pagos.length, plan: { estado: string } | null = { estado: "VIGENTE" }) {
  render(<PagosDeCuenta cuentaId={CUENTA} cuentaNombre="Mi negocio" pagos={{ datos: pagos, total }} plan={plan as never} hoy="2026-10-15" />);
}

describe("PagosDeCuenta (#194)", () => {
  it("tiene su título, su explicación y el botón para registrar un pago con lo que necesita", () => {
    mostrar([SIMPLE]);

    expect(screen.getByRole("heading", { name: "Pagos" })).toBeTruthy();
    expect(screen.getByText(/No hay cobro automático/)).toBeTruthy();
    expect(screen.getByTestId("registrar-pago").textContent).toBe(`${CUENTA}|Mi negocio|VIGENTE|2026-10-15`);
  });

  it("cada pago dice su fecha, el periodo, el monto en soles y el medio", () => {
    mostrar([SIMPLE]);

    const fila = within(screen.getByTestId("pago-fila"));
    expect(fila.getByText("14 Oct 2026")).toBeTruthy();
    expect(fila.getByText("1 Oct 2026 – 31 Oct 2026")).toBeTruthy();
    expect(fila.getByText("S/ 29.00")).toBeTruthy();
    expect(fila.getByText("Yape")).toBeTruthy();
  });

  it("un pago con todo muestra la referencia, la nota y hasta qué día dejó pagada la cuenta", () => {
    mostrar([COMPLETO]);

    const fila = within(screen.getByTestId("pago-fila"));
    expect(fila.getByText("OP-777")).toBeTruthy();
    expect(fila.getByText("Pagó por el BCP")).toBeTruthy();
    expect(fila.getByText("Transferencia")).toBeTruthy();
    expect(fila.getByText("S/ 1,234.50")).toBeTruthy();
    expect(fila.getByText("Pagada hasta el 31 Oct 2026")).toBeTruthy();
    expect(screen.getByTestId("pago-fila").getAttribute("data-extendio")).toBe("true");
  });

  /** Lo omitido nunca se lee como vacío: sin referencia hay un guion, y sin extensión se dice que el vencimiento no cambió. */
  it("un pago sin referencia ni extensión lo dice, y no inventa una nota", () => {
    mostrar([SIMPLE]);

    const fila = within(screen.getByTestId("pago-fila"));
    expect(fila.getByText("—").hasAttribute("hidden")).toBe(false);
    expect(fila.getByText("Sin cambio")).toBeTruthy();
    expect(screen.getByTestId("pago-fila").getAttribute("data-extendio")).toBe("false");
    expect(screen.getByTestId("pago-fila").textContent).not.toContain("undefined");
  });

  it("los pagos salen en el orden en que llegan (el backend ya los manda del más reciente al más antiguo)", () => {
    mostrar([COMPLETO, SIMPLE]);

    const filas = screen.getAllByTestId("pago-fila");
    expect(filas[0].textContent).toContain("2 Set 2026");
    expect(filas[1].textContent).toContain("14 Oct 2026");
  });

  it("sin pagos lo dice y no avisa de ninguna lista recortada", () => {
    mostrar([]);

    expect(screen.getByText("Todavía no se registró ningún pago.")).toBeTruthy();
    expect(screen.queryByTestId("pago-fila")).toBeNull();
    expect(screen.queryByTestId("pagos-recortados")).toBeNull();
  });

  it("con más pagos que los mostrados avisa cuántos son y cuántos se ven", () => {
    mostrar([SIMPLE, COMPLETO], 37);

    expect(screen.getByTestId("pagos-recortados").textContent).toBe("Se muestran los 2 más recientes de 37.");
  });

  it("si se ven todos no avisa nada", () => {
    mostrar([SIMPLE, COMPLETO], 2);

    expect(screen.queryByTestId("pagos-recortados")).toBeNull();
  });

  it("sin el plan cargado igual se puede registrar un pago", () => {
    mostrar([SIMPLE], 1, null);

    expect(screen.getByTestId("registrar-pago").textContent).toContain("sin plan");
  });
});
