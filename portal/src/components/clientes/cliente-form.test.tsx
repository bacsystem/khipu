import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COMERCIAL, DE_BAJA, TIENDAS, consultasAlPadron, digitar, estadoDeLaConsulta, exito, stubPadron } from "@/test/padron-de-prueba";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { ClienteForm } from "./cliente-form";

const guardado = (c: Pick<ClienteGuardado, "tipo_doc" | "num_doc" | "razon_social">): ClienteGuardado => ({
  id: "cl-1", ...c, creado_en: "2026-10-01T10:00:00Z", actualizado_en: "2026-10-01T10:00:00Z",
});

const numero = () => screen.getByLabelText("Número") as HTMLInputElement;
const razon = () => screen.getByLabelText("Razón social") as HTMLInputElement;
const direccion = () => screen.getByLabelText(/Dirección/) as HTMLInputElement;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

/** #575: el RUC de un cliente se consulta en el padrón de SUNAT, como en el modal de emisión (#379). */
describe("ClienteForm — el RUC en el padrón de SUNAT", () => {
  it("rellena la razón social y la dirección vacías", async () => {
    const fetch = stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) });
    render(<ClienteForm />);

    digitar(numero(), TIENDAS.ruc);

    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    expect(direccion().value).toBe("AV. REPUBLICA DE PANAMA NRO. 2457");
    expect(consultasAlPadron(fetch)).toEqual([`/api/proxy/consultas/ruc/${TIENDAS.ruc}`]);
  });

  it("al cambiar a otro RUC suelta lo rellenado y trae lo del nuevo; lo escrito a mano se queda", async () => {
    stubPadron({ [TIENDAS.ruc]: exito(TIENDAS), [COMERCIAL.ruc]: exito(COMERCIAL) });
    render(<ClienteForm />);
    digitar(numero(), TIENDAS.ruc);
    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    fireEvent.change(direccion(), { target: { value: "Jr. Puno 300" } });

    fireEvent.change(numero(), { target: { value: COMERCIAL.ruc } });

    await waitFor(() => expect(razon().value).toBe("COMERCIAL VERDE E.I.R.L."));
    expect(direccion().value).toBe("Jr. Puno 300");
  });

  it("un RUC de baja trae las observaciones que SUNAT pondrá a sus comprobantes", async () => {
    stubPadron({ [DE_BAJA.ruc]: exito(DE_BAJA) });
    render(<ClienteForm />);

    digitar(numero(), DE_BAJA.ruc);

    await waitFor(() => expect(estadoDeLaConsulta()).toBe("encontrado"));
    expect(screen.getByTestId("aviso-ruc-4013")).toBeTruthy();
    expect(screen.getByTestId("aviso-ruc-4014")).toBeTruthy();
  });

  it("al editar un cliente guardado no consulta mientras no cambie el RUC", async () => {
    const fetch = stubPadron({ [TIENDAS.ruc]: exito(TIENDAS) });
    render(<ClienteForm existente={guardado({ tipo_doc: "6", num_doc: TIENDAS.ruc, razon_social: "Tiendas" })} />);

    await new Promise((r) => setTimeout(r, 20));
    expect(consultasAlPadron(fetch)).toEqual([]);
    expect(screen.queryByTestId("consulta-ruc")).toBeNull();
  });

  it("con DNI no consulta el padrón", async () => {
    const fetch = stubPadron({});
    render(<ClienteForm existente={guardado({ tipo_doc: "1", num_doc: "", razon_social: "" })} />);

    digitar(numero(), "12345678");

    await new Promise((r) => setTimeout(r, 20));
    expect(consultasAlPadron(fetch)).toEqual([]);
  });
});
