import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { EmpresaDetalleAdmin } from "@/lib/api/admin-empresa-detalle";
import { EmpresaDetalle } from "./empresa-detalle";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));

afterEach(cleanup);

const BASE: EmpresaDetalleAdmin = {
  id: "e1",
  ruc: "20100066603",
  razon_social: "COMERCIAL ANDINA SAC",
  entorno: "BETA",
  creada_en: "2026-09-01T15:00:00Z",
  certificado: "SIN_CERTIFICADO",
  tiene_credenciales_sol: false,
  padron_tasa_especial_igv: false,
  pdf: { plantilla: "CLASICO", color_primario: "#1E1E24", tiene_logo: false },
  series: [],
  establecimientos: [],
  api_keys: [],
  comprobantes: [],
  eventos: [],
  outbox: { total: 0, proximas: [] },
};

describe("EmpresaDetalle (#186)", () => {
  it("el domicilio se arma con la dirección, la urbanización y el distrito, la provincia y el departamento que haya", () => {
    render(
      <EmpresaDetalle
        empresa={{ ...BASE, domicilio: { ubigeo: "150122", direccion: "AV. LARCO 345", urbanizacion: "URB. SOL", distrito: "MIRAFLORES", provincia: "LIMA", departamento: "LIMA", codigo_establecimiento: "0000" } }}
      />,
    );

    expect(screen.getByTestId("domicilio").textContent).toBe("AV. LARCO 345 · URB. SOL · MIRAFLORES, LIMA, LIMA");
  });

  it("un domicilio con solo la dirección no deja separadores colgando", () => {
    render(<EmpresaDetalle empresa={{ ...BASE, domicilio: { ubigeo: "150122", direccion: "AV. LARCO 345" } }} />);

    expect(screen.getByTestId("domicilio").textContent).toBe("AV. LARCO 345");
  });

  it("sin domicilio lo dice, en vez de un hueco", () => {
    render(<EmpresaDetalle empresa={BASE} />);

    expect(screen.getByText("Todavía no declaró su domicilio fiscal.")).toBeTruthy();
    expect(screen.queryByTestId("domicilio")).toBeNull();
  });

  it("una sola tarea pendiente se dice en singular; varias, con el total aunque la lista traiga menos", () => {
    const tarea = { agregado: "DOCUMENTO", agregado_id: "d1", accion: "ENVIAR", intentos: 1, siguiente_intento: "2026-10-01T10:00:00Z" };
    const { rerender } = render(<EmpresaDetalle empresa={{ ...BASE, outbox: { total: 1, proximas: [tarea] } }} />);
    expect(screen.getByTestId("outbox-total").textContent).toBe("1 tarea pendiente");

    rerender(<EmpresaDetalle empresa={{ ...BASE, outbox: { total: 57, proximas: [tarea] } }} />);
    expect(screen.getByTestId("outbox-total").textContent).toBe("57 tareas pendientes");
  });

  it("sin tareas pendientes no hay línea de total", () => {
    render(<EmpresaDetalle empresa={BASE} />);

    expect(screen.queryByTestId("outbox-total")).toBeNull();
    expect(screen.getByText("No tiene tareas pendientes.")).toBeTruthy();
  });

  it("el primer cambio de un comprobante dice «Inicio», no un estado anterior vacío", () => {
    render(<EmpresaDetalle empresa={{ ...BASE, eventos: [{ comprobante: "F001-00000001", estado_nuevo: "RECIBIDO", ocurrido_en: "2026-10-01T10:00:00Z" }] }} />);

    expect(screen.getByText(/Inicio →/)).toBeTruthy();
  });

  it("un estado que el portal no conoce se muestra con su código, no se esconde", () => {
    render(
      <EmpresaDetalle
        empresa={{ ...BASE, eventos: [{ comprobante: "F001-00000001", estado_anterior: "FIRMADO", estado_nuevo: "ESTADO_NUEVO", ocurrido_en: "2026-10-01T10:00:00Z" }] }}
      />,
    );

    expect(screen.getByText("ESTADO_NUEVO")).toBeTruthy();
  });

  it("un CDR con observaciones las lista; sin ellas lo dice; sin CDR dice que SUNAT no respondió", () => {
    const comprobante = { id: "c1", tipo: "01", serie: "F001", fecha_emision: "2026-09-30", moneda: "PEN", total: 118, intentos: 1 } as const;
    render(
      <EmpresaDetalle
        empresa={{
          ...BASE,
          comprobantes: [
            { ...comprobante, id: "c1", numero: 1, estado: "ACEPTADO_CON_OBS", cdr: { codigo: "0", descripcion: "Aceptada", observaciones: ["4287 - Una observación"] } },
            { ...comprobante, id: "c2", numero: 2, estado: "ACEPTADO", cdr: { codigo: "0", descripcion: "Aceptada", observaciones: [] } },
            { ...comprobante, id: "c3", numero: 3, estado: "FIRMADO" },
          ],
        }}
      />,
    );

    const filas = within(screen.getByRole("region", { name: "Comprobantes recientes" })).getAllByRole("row").slice(1);
    expect(within(filas[0]).getByText("4287 - Una observación")).toBeTruthy();
    expect(within(filas[1]).getByText("Sin observaciones")).toBeTruthy();
    expect(within(filas[2]).getByText("Sin respuesta de SUNAT")).toBeTruthy();
  });

  it("el prefijo de una API key lleva puntos suspensivos: no es la key entera", () => {
    render(<EmpresaDetalle empresa={{ ...BASE, api_keys: [{ id: "k1", prefijo: "fk_demo001", activa: true, creada_en: "2026-09-01T15:00:00Z" }] }} />);

    expect(screen.getByText("fk_demo001…")).toBeTruthy();
  });

  /** Las acciones del administrador sobre una empresa (#187) son tres y solo esas: cambiar el entorno, revocar una key vigente y probar la conexión. */
  it("ofrece cambiar el entorno y probar la conexión, y revocar solo las keys vigentes", () => {
    const { unmount } = render(<EmpresaDetalle empresa={BASE} />);
    expect(screen.getAllByRole("button").map((b) => b.getAttribute("data-testid"))).toEqual(["cambiar-entorno", "probar-conexion"]);
    unmount();

    render(
      <EmpresaDetalle
        empresa={{
          ...BASE,
          api_keys: [
            { id: "k1", prefijo: "fk_vigente", activa: true, creada_en: "2026-09-02T15:00:00Z" },
            { id: "k2", prefijo: "fk_vieja01", activa: false, creada_en: "2026-09-01T15:00:00Z", revocada_en: "2026-09-09T12:00:00Z" },
          ],
        }}
      />,
    );
    expect(screen.getAllByRole("button").map((b) => b.getAttribute("data-testid"))).toEqual(["cambiar-entorno", "probar-conexion", "revocar-api-key-k1"]);
  });

  it("la prueba de conexión está deshabilitada sin credenciales SOL y habilitada con ellas", () => {
    const { unmount } = render(<EmpresaDetalle empresa={BASE} />);
    expect((screen.getByTestId("probar-conexion") as HTMLButtonElement).disabled).toBe(true);
    unmount();

    render(<EmpresaDetalle empresa={{ ...BASE, tiene_credenciales_sol: true }} />);
    expect((screen.getByTestId("probar-conexion") as HTMLButtonElement).disabled).toBe(false);
  });

});
