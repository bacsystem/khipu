import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import es from "@/messages/es.json";
import { DE_BAJA, TIENDAS, consultasAlPadron, digitar, estadoDeLaConsulta, exito, falla, stubPadron } from "@/test/padron-de-prueba";
import { OnboardingEmpresaForm } from "./onboarding-empresa-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }) }));

/** Sin empresas todavía: el formulario se muestra (con una, entraría al portal). */
const sinEmpresas = (url: string) => (url === "/api/proxy/empresas" ? exito([]) : undefined);

const ruc = () => screen.getByLabelText("RUC") as HTMLInputElement;
const razon = () => screen.getByLabelText("Razón social") as HTMLInputElement;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

/** #575: el onboarding consulta el padrón con la sesión, todavía sin empresa, y rellena la razón social. */
describe("OnboardingEmpresaForm — el RUC en el padrón de SUNAT", () => {
  it("con el RUC completo rellena la razón social, con un solo pedido por el proxy del portal", async () => {
    const fetch = stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) }, sinEmpresas);
    render(<OnboardingEmpresaForm />);
    await screen.findByLabelText("RUC");

    digitar(ruc(), TIENDAS.ruc);

    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    expect(consultasAlPadron(fetch)).toEqual([`/api/proxy/consultas/ruc/${TIENDAS.ruc}`]);
    expect(estadoDeLaConsulta()).toBe("encontrado");
  });

  it("no pisa la razón social que el usuario ya escribió: le ofrece la de SUNAT", async () => {
    stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) }, sinEmpresas);
    render(<OnboardingEmpresaForm />);
    await screen.findByLabelText("RUC");
    fireEvent.change(razon(), { target: { value: "Tiendas del Sur" } });

    digitar(ruc(), TIENDAS.ruc);

    await screen.findByText(es.consultaRuc.usarNombre);
    expect(razon().value).toBe("Tiendas del Sur");
    fireEvent.click(screen.getByText(es.consultaRuc.usarNombre));
    expect(razon().value).toBe("TIENDAS DEL SUR S.A.C.");
  });

  it("al cambiar el RUC suelta la razón social que puso el padrón", async () => {
    stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) }, sinEmpresas);
    render(<OnboardingEmpresaForm />);
    await screen.findByLabelText("RUC");
    digitar(ruc(), TIENDAS.ruc);
    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));

    fireEvent.change(ruc(), { target: { value: TIENDAS.ruc.slice(0, 10) } });

    await waitFor(() => expect(razon().value).toBe(""));
  });

  it("avisa como emisor si el RUC no está activo ni habido, sin bloquear", async () => {
    stubPadron({ [DE_BAJA.ruc]: exito(DE_BAJA) }, sinEmpresas);
    render(<OnboardingEmpresaForm />);
    await screen.findByLabelText("RUC");

    digitar(ruc(), DE_BAJA.ruc);

    await waitFor(() => expect(razon().value).toBe("QUISPE MAMANI JOSE"));
    expect(screen.getByTestId("aviso-ruc-emisor-0")).toHaveTextContent("BAJA DE OFICIO");
    expect(screen.getByTestId("aviso-ruc-emisor-1")).toHaveTextContent(es.consultaRuc.emisor.noHabido);
    // Las observaciones del comprobante (4013/4014) son de un receptor, no de quien emite.
    expect(screen.queryByTestId("aviso-ruc-4013")).toBeNull();
  });

  it("un RUC que el padrón no tiene se digita a mano, con el texto que no habla de comprobantes", async () => {
    stubPadron({ [TIENDAS.ruc]: falla(404, "NO_ENCONTRADO") }, sinEmpresas);
    render(<OnboardingEmpresaForm />);
    await screen.findByLabelText("RUC");

    digitar(ruc(), TIENDAS.ruc);

    await waitFor(() => expect(estadoDeLaConsulta()).toBe("no-encontrado"));
    expect(screen.getByTestId("consulta-ruc")).toHaveTextContent(es.consultaRuc.noEncontradoGeneral);
    expect(razon().value).toBe("");
  });
});
