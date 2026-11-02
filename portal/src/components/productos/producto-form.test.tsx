import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProductoForm } from "./producto-form";

const sobre = (datos: unknown, status = 200) => new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: status < 400 ? null : "x", codigo: status < 400 ? null : "ITEM_INVALIDO", errores: null }), { status });

const CATALOGO_03 = { id: "03", nombre: "Unidades", columnas: ["Código", "Descripción", "Más usada"], entradas: [{ codigo: "NIU", descripcion: "Unidad (bienes)", extra: { "Más usada": "Sí" } }, { codigo: "ZZ", descripcion: "Unidad (servicios)", extra: { "Más usada": "Sí" } }] };

function stub(respuestaDelGuardado: Response = sobre({ id: "p-1" }, 201)) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => Promise.resolve(String(url).includes("/catalogos/03") ? sobre(CATALOGO_03) : respuestaDelGuardado));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const guardados = (fetch: ReturnType<typeof stub>) => fetch.mock.calls.filter(([url]) => String(url).includes("/productos")).map(([url, init]) => ({ url: String(url), metodo: init?.method, cuerpo: JSON.parse(String(init?.body)) }));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("ProductoForm (#320)", () => {
  it("sin descripción ni precio no manda nada y dice qué falta", async () => {
    const fetch = stub();
    render(<ProductoForm />);

    fireEvent.click(screen.getByRole("button", { name: "Guardar producto" }));

    expect(await screen.findByText("Escribe la descripción")).toBeInTheDocument();
    expect(screen.getByText("El precio debe ser mayor que cero")).toBeInTheDocument();
    expect(guardados(fetch)).toHaveLength(0);
  });

  it("un código SUNAT que no son 8 dígitos se rechaza antes de mandarlo", async () => {
    const fetch = stub();
    render(<ProductoForm />);
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Servicio" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "118" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));
    fireEvent.change(screen.getByLabelText(/Código de producto SUNAT/), { target: { value: "1234" } });

    fireEvent.click(screen.getByRole("button", { name: "Guardar producto" }));

    expect(await screen.findByText(/Son 8 dígitos/)).toBeInTheDocument();
    expect(guardados(fetch)).toHaveLength(0);
  });

  it("guarda con la unidad, la afectación y el precio elegidos, y lo opcional vacío no viaja", async () => {
    const fetch = stub();
    const alGuardar = vi.fn();
    render(<ProductoForm onGuardado={alGuardar} />);
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "  Consultoría  " } });
    await waitFor(() => expect(screen.getByLabelText("Unidad de medida").querySelectorAll("option")).toHaveLength(2));
    fireEvent.change(screen.getByLabelText("Unidad de medida"), { target: { value: "ZZ" } });
    fireEvent.change(screen.getByLabelText("Afectación del IGV"), { target: { value: "20" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "118.5" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));

    fireEvent.click(screen.getByRole("button", { name: "Guardar producto" }));

    await waitFor(() => expect(alGuardar).toHaveBeenCalled());
    expect(guardados(fetch)).toEqual([{ url: "/api/proxy/productos", metodo: "POST", cuerpo: { descripcion: "Consultoría", unidad: "ZZ", tipo_afectacion_igv: "20", precio_unitario: 118.5 } }]);
  });

  it("un precio de más de dos decimales se conserva al editar y al guardar, y no lleva símbolo de moneda", async () => {
    const fetch = stub(sobre({ id: "p-9" }));
    render(<ProductoForm existente={{ id: "p-9", descripcion: "Tornillo", unidad: "NIU", tipo_afectacion_igv: "10", precio_unitario: 0.0123, creado_en: "x", actualizado_en: "x" }} />);

    expect((screen.getByLabelText(/Precio unit\./) as HTMLInputElement).value).toBe("0.0123");
    expect(screen.queryByText("S/")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Tornillo largo" } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));

    await waitFor(() => expect(guardados(fetch)).toHaveLength(1));
    expect(guardados(fetch)[0].cuerpo).toMatchObject({ descripcion: "Tornillo largo", precio_unitario: 0.0123 });
  });

  it("un precio con más de 10 decimales o 12 enteros se rechaza antes de mandarlo", async () => {
    const fetch = stub();
    render(<ProductoForm />);
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Servicio" } });

    for (const precio of ["1.12345678901", "1234567890123"]) {
      fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: precio } });
      fireEvent.click(screen.getByRole("button", { name: "Guardar producto" }));
      expect(await screen.findByText("Hasta 12 enteros y 10 decimales")).toBeInTheDocument();
    }
    expect(guardados(fetch)).toHaveLength(0);
  });

  it("el motivo del backend se ve en el formulario y no se cierra", async () => {
    stub(sobre(null, 422));
    const alGuardar = vi.fn();
    render(<ProductoForm onGuardado={alGuardar} />);
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Servicio" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "10" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));

    fireEvent.click(screen.getByRole("button", { name: "Guardar producto" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("x");
    expect(alGuardar).not.toHaveBeenCalled();
  });

  it("al editar manda un PUT al producto con sus datos", async () => {
    const fetch = stub(sobre({ id: "p-9" }));
    render(<ProductoForm existente={{ id: "p-9", codigo: "SKU-1", descripcion: "Servicio", unidad: "ZZ", tipo_afectacion_igv: "10", precio_unitario: 118, creado_en: "x", actualizado_en: "x" }} />);

    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Servicio plus" } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));

    await waitFor(() => expect(guardados(fetch)).toHaveLength(1));
    expect(guardados(fetch)[0]).toEqual({ url: "/api/proxy/productos/p-9", metodo: "PUT", cuerpo: { codigo: "SKU-1", descripcion: "Servicio plus", unidad: "ZZ", tipo_afectacion_igv: "10", precio_unitario: 118 } });
  });
});
