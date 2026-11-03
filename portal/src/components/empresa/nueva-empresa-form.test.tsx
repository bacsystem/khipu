import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DE_BAJA, TIENDAS, consultasAlPadron, digitar, estadoDeLaConsulta, exito, stubPadron } from "@/test/padron-de-prueba";
import { NuevaEmpresaForm } from "./nueva-empresa-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const ruc = () => screen.getByLabelText("RUC") as HTMLInputElement;
const razon = () => screen.getByLabelText("Razón social") as HTMLInputElement;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

/** #575: registrar otra empresa en la cuenta consulta su RUC en el padrón de SUNAT. */
describe("NuevaEmpresaForm — el RUC en el padrón de SUNAT", () => {
  it("rellena la razón social y la manda tal cual al registrar", async () => {
    const fetch = stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) }, (url) => (url === "/api/proxy/empresas" ? exito({ id: "e-2" }) : undefined));
    render(<NuevaEmpresaForm />);

    digitar(ruc(), TIENDAS.ruc);
    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    expect(consultasAlPadron(fetch)).toEqual([`/api/proxy/consultas/ruc/${TIENDAS.ruc}`]);

    fireEvent.submit(ruc().closest("form")!);
    await waitFor(() => expect(fetch.mock.calls.some(([url]) => url === "/api/proxy/empresas")).toBe(true));
    const [, init] = fetch.mock.calls.find(([url]) => url === "/api/proxy/empresas")!;
    expect(JSON.parse(String(init?.body))).toEqual({ ruc: TIENDAS.ruc, razon_social: "TIENDAS DEL SUR S.A.C.", entorno: "BETA" });
  });

  it("avisa como emisor si el RUC está de baja, sin las observaciones de un comprobante", async () => {
    stubPadron({ [DE_BAJA.ruc]: exito(DE_BAJA) });
    render(<NuevaEmpresaForm />);

    digitar(ruc(), DE_BAJA.ruc);

    await waitFor(() => expect(estadoDeLaConsulta()).toBe("encontrado"));
    expect(screen.getByTestId("aviso-ruc-emisor-0")).toHaveTextContent("BAJA DE OFICIO");
    expect(screen.queryByTestId("aviso-ruc-4013")).toBeNull();
  });
});
