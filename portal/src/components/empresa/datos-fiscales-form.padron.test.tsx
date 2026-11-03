import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import es from "@/messages/es.json";
import { TIENDAS, consultasAlPadron, exito, stubPadron } from "@/test/padron-de-prueba";
import { DatosFiscalesForm } from "./datos-fiscales-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

/** El catálogo 13 con el distrito del padrón de ejemplo (150101) y otro de otra provincia. */
const CATALOGO_13 = exito({
  entradas: [
    { codigo: "150101", descripcion: "LIMA", extra: { Departamento: "LIMA", Provincia: "LIMA", Distrito: "LIMA" } },
    { codigo: "040101", descripcion: "AREQUIPA", extra: { Departamento: "AREQUIPA", Provincia: "AREQUIPA", Distrito: "AREQUIPA" } },
  ],
});
const catalogo = (url: string) => (url === "/api/proxy/catalogos/13" ? CATALOGO_13 : undefined);
const formulario = (domicilio: Parameters<typeof DatosFiscalesForm>[0]["domicilio"] = null) => (
  <DatosFiscalesForm ruc={TIENDAS.ruc} domicilio={domicilio} cuentaDetracciones={null} nombreComercial={null} padronTasaEspecialIgv={false} />
);

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

/** #575: una empresa sin domicilio fiscal ve el que tiene en el padrón de SUNAT y lo usa con un clic. */
describe("DatosFiscalesForm — el domicilio del padrón de SUNAT", () => {
  it("sin domicilio sugiere el del padrón y, al usarlo, llena la dirección y el ubigeo con su departamento y provincia", async () => {
    const fetch = stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) }, catalogo);
    render(formulario());

    const sugerencia = await screen.findByTestId("domicilio-sunat");
    expect(sugerencia).toHaveTextContent(TIENDAS.direccion);
    expect(consultasAlPadron(fetch)).toEqual([`/api/proxy/consultas/ruc/${TIENDAS.ruc}`]);
    expect((screen.getByLabelText("Dirección") as HTMLInputElement).value).toBe("");

    fireEvent.click(screen.getByText(es.empresa.datosFiscales.usarDomicilioSunat));

    expect((screen.getByLabelText("Dirección") as HTMLInputElement).value).toBe(TIENDAS.direccion);
    await waitFor(() => expect(screen.getByText("Ubigeo 150101 (catálogo 13)")).toBeTruthy());
    expect(screen.getByLabelText("Departamento").textContent).toContain("LIMA");
    expect(screen.getByLabelText("Provincia").textContent).toContain("LIMA");
    expect(screen.queryByTestId("domicilio-sunat")).toBeNull();
  });

  it("con un domicilio guardado no consulta el padrón", async () => {
    const fetch = stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) }, catalogo);
    render(formulario({ ubigeo: "040101", direccion: "Calle Mercaderes 100", departamento: "AREQUIPA", provincia: "AREQUIPA", distrito: "AREQUIPA", urbanizacion: null, codigo_establecimiento: "0000" }));

    await new Promise((r) => setTimeout(r, 20));
    expect(consultasAlPadron(fetch)).toEqual([]);
    expect(screen.queryByTestId("domicilio-sunat")).toBeNull();
  });

  it("un RUC sin domicilio en el padrón (casi todos los RUC 10) no sugiere nada", async () => {
    const sinDomicilio: Partial<typeof TIENDAS> = { ...TIENDAS };
    delete sinDomicilio.direccion;
    delete sinDomicilio.ubigeo;
    const fetch = stubPadron({ [TIENDAS.ruc]: exito(sinDomicilio) }, catalogo);
    render(formulario());

    await waitFor(() => expect(consultasAlPadron(fetch)).toHaveLength(1));
    await new Promise((r) => setTimeout(r, 20));
    expect(screen.queryByTestId("domicilio-sunat")).toBeNull();
  });
});
