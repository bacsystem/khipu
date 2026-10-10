import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [
  { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" },
];

function stubEmision() {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() =>
    Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: { id: "c-1" }, mensaje: null, codigo: null, errores: null }), { status: 201 })),
  );
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function emisiones(fetch: ReturnType<typeof stubEmision>) {
  return fetch.mock.calls.filter(([url]) => String(url).includes("/facturas")).map(([, init]) => JSON.parse(String(init?.body)));
}

function linea(descripcion: string, precio: string) {
  fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: descripcion } });
  fireEvent.change(screen.getByLabelText("Precio unit. (con IGV)"), { target: { value: precio } });
  fireEvent.blur(screen.getByLabelText("Precio unit. (con IGV)"));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("NuevoComprobanteForm — boleta (#20)", () => {
  it("sin documento manda «-» como tipo y número, con el nombre por defecto", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
    expect(screen.queryByLabelText("Número de documento")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Nombre del comprador")).toHaveValue("CLIENTES VARIOS");
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ serie: "B001", cliente: { tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS" } });
  });

  it("sin documento por más de S/ 700 avisa y no emite: SUNAT exige identificar al comprador", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
    linea("Televisor", "700.01");

    expect(screen.getByTestId("aviso-sin-documento")).toHaveTextContent("S/ 700.00");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/identifica al comprador/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("con DNI manda el tipo 1 y exige 8 dígitos", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    const numero = screen.getByLabelText("Número de documento");
    expect(numero).toHaveAttribute("pattern", "[0-9]{8}");
    fireEvent.change(numero, { target: { value: "12345678" } });
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "Juan Pérez" } });
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].cliente).toMatchObject({ tipo_doc: "1", num_doc: "12345678", razon_social: "Juan Pérez" });
  });

  /**
   * 328-H2: el diálogo avisa del tope con lo aceptado, pero el backend también cuenta lo que está en camino. Si la emisión llega igual al tope (429
   * LIMITE_PLAN), el error lleva a Plan y consumo en vez de dejar solo el texto.
   */
  it("si el backend responde LIMITE_PLAN muestra su mensaje y el enlace a Plan y consumo", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({ estado: "error", datos: null, codigo: "LIMITE_PLAN", mensaje: "Llegaste al tope de 30 documentos de octubre de 2026 de tu plan Gratis", errores: null }),
            { status: 429 },
          ),
        ),
      ),
    );
    const cancelar = vi.fn();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} onCancelar={cancelar} />);

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Llegaste al tope de 30 documentos/));
    const enlace = screen.getByRole("link", { name: "Ver plan y consumo" });
    expect(enlace).toHaveAttribute("href", "/cuenta/plan");
    fireEvent.click(enlace);
    expect(cancelar).toHaveBeenCalled();
  });

  it("la factura sigue pidiendo RUC y usa solo series F", () => {
    stubEmision();
    render(<NuevoComprobanteForm tipo="factura" series={SERIES} tasaIgv={18} />);
    expect(screen.getByLabelText("RUC")).toHaveAttribute("pattern", "[0-9]{11}");
    expect(screen.queryByRole("option", { name: /B001/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Emitir factura" })).toBeInTheDocument();
  });
});
