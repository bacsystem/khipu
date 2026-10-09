import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { InformeDeIntegridad, ProblemaDeIntegridad } from "@/lib/api/admin-integridad";
import type { ApiEnvelope } from "@/lib/api/types";
import { VerificarIntegridad } from "./verificar-integridad";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

afterEach(() => {
  cleanup();
  apiRequest.mockReset();
});

const HOY = "2026-10-15";
const EMPRESA_A = "00000000-0000-4000-9000-000000000001";
const EMPRESA_B = "00000000-0000-4000-9000-000000000002";

const exito = (datos: InformeDeIntegridad): ApiEnvelope<InformeDeIntegridad> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const fallo = (codigo: string | null, mensaje: string | null): ApiEnvelope<InformeDeIntegridad> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });
const informe = (verificados: number, problemas: ProblemaDeIntegridad[] = [], desde = "2026-10-09", hasta = HOY): InformeDeIntegridad => ({ desde, hasta, verificados, problemas });
const problema = (n: number, tipo: ProblemaDeIntegridad["tipo"], tenant = EMPRESA_A, detalle: string | undefined = `detalle ${n}`): ProblemaDeIntegridad => ({
  comprobante_id: `00000000-0000-4000-c000-${String(n).padStart(12, "0")}`,
  tenant_id: tenant,
  nombre_archivo: `20100066603-01-F001-${n}`,
  tipo,
  detalle,
});

const desde = () => screen.getByLabelText("Desde") as HTMLInputElement;
const hasta = () => screen.getByLabelText("Hasta (inclusive)") as HTMLInputElement;
const boton = () => screen.getByTestId("integridad-verificar") as HTMLButtonElement;
const escribir = (el: HTMLElement, valor: string) => fireEvent.change(el, { target: { value: valor } });

describe("VerificarIntegridad (#198)", () => {
  it("el rango arranca en los últimos siete días, de hace seis días a hoy, y no pide nada hasta que se lo piden", () => {
    render(<VerificarIntegridad hoy={HOY} />);

    expect(desde().value).toBe("2026-10-09");
    expect(hasta().value).toBe(HOY);
    expect(screen.getByText("Por fecha de emisión, hasta 92 días por vez.")).toBeTruthy();
    expect(apiRequest).not.toHaveBeenCalled();
    expect(screen.queryByTestId("integridad-resultado")).toBeNull();
  });

  it("verificar manda el rango por el BFF y muestra cuántos se verificaron", async () => {
    apiRequest.mockResolvedValue(exito(informe(120)));
    render(<VerificarIntegridad hoy={HOY} />);
    escribir(desde(), "2026-09-01");
    escribir(hasta(), "2026-09-30");

    fireEvent.click(boton());

    await screen.findByTestId("integridad-resultado");
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/integridad", { method: "POST", body: { desde: "2026-09-01", hasta: "2026-09-30" } });
  });

  // --- el resumen -------------------------------------------------------------------------------------------------------------------------

  it("un barrido limpio lo dice con las fechas del informe y sin tabla", async () => {
    apiRequest.mockResolvedValue(exito(informe(120, [], "2026-09-01", "2026-09-30")));
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    expect((await screen.findByTestId("integridad-verificados")).textContent).toBe("Se verificaron 120 comprobantes entre el 1 Set 2026 y el 30 Set 2026.");
    expect(screen.getByTestId("integridad-hallazgo").textContent).toBe("Todo en orden: no se encontró ningún problema.");
    expect(screen.getByTestId("integridad-resultado").getAttribute("data-limpio")).toBe("true");
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByTestId("integridad-leyenda")).toBeNull();
  });

  it("uno solo y ninguno se dicen en singular y con claridad", async () => {
    apiRequest.mockResolvedValueOnce(exito(informe(1)));
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());
    expect((await screen.findByTestId("integridad-verificados")).textContent).toBe("Se verificó 1 comprobante entre el 9 Oct 2026 y el 15 Oct 2026.");

    apiRequest.mockResolvedValueOnce(exito(informe(0)));
    fireEvent.click(boton());
    await waitFor(() => expect(screen.getByTestId("integridad-verificados").textContent).toBe("No había comprobantes firmados entre el 9 Oct 2026 y el 15 Oct 2026."));
    expect(screen.getByTestId("integridad-hallazgo").textContent).toBe("Todo en orden: no se encontró ningún problema.");
  });

  it("con problemas dice cuántos y en cuántos comprobantes, en singular y en plural", async () => {
    render(<VerificarIntegridad hoy={HOY} />);

    apiRequest.mockResolvedValueOnce(exito(informe(50, [problema(1, "XML_FALTANTE")])));
    fireEvent.click(boton());
    await waitFor(() => expect(screen.getByTestId("integridad-hallazgo").textContent).toBe("Se encontró 1 problema en 1 comprobante."));

    // Dos problemas del mismo comprobante son un solo comprobante.
    apiRequest.mockResolvedValueOnce(exito(informe(50, [problema(1, "XML_CORRUPTO"), problema(1, "CDR_FALTANTE")])));
    fireEvent.click(boton());
    await waitFor(() => expect(screen.getByTestId("integridad-hallazgo").textContent).toBe("Se encontraron 2 problemas en 1 comprobante."));

    apiRequest.mockResolvedValueOnce(exito(informe(50, [problema(1, "XML_FALTANTE"), problema(2, "XML_FALTANTE"), problema(2, "CDR_FALTANTE")])));
    fireEvent.click(boton());
    await waitFor(() => expect(screen.getByTestId("integridad-hallazgo").textContent).toBe("Se encontraron 3 problemas en 2 comprobantes."));
    expect(screen.getByTestId("integridad-resultado").getAttribute("data-limpio")).toBe("false");
  });

  // --- la tabla ---------------------------------------------------------------------------------------------------------------------------

  it("cada problema dice su tipo, el comprobante (con enlace a su ficha, #251), el detalle y lleva un enlace a su empresa", async () => {
    apiRequest.mockResolvedValue(exito(informe(50, [problema(1, "XML_CORRUPTO", EMPRESA_A, "el DigestValue no está en k/x.xml"), problema(2, "CDR_FALTANTE", EMPRESA_B, "k/R-x.zip")])));
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    const filas = await screen.findAllByTestId("integridad-problema");
    expect(filas).toHaveLength(2);
    const primera = within(filas[0]);
    expect(filas[0].getAttribute("data-tipo")).toBe("XML_CORRUPTO");
    expect(primera.getByText("XML corrupto")).toBeTruthy();
    expect(primera.getByRole("link", { name: "20100066603-01-F001-1" }).getAttribute("href")).toBe("/admin/comprobantes/00000000-0000-4000-c000-000000000001");
    expect(primera.getByText("el DigestValue no está en k/x.xml")).toBeTruthy();
    expect(primera.getByRole("link", { name: "Ver empresa" }).getAttribute("href")).toBe(`/admin/empresas/${EMPRESA_A}`);
    expect(within(filas[1]).getByRole("link", { name: "Ver empresa" }).getAttribute("href")).toBe(`/admin/empresas/${EMPRESA_B}`);
    expect(within(filas[1]).getByText("CDR faltante")).toBeTruthy();
  });

  it("un problema sin detalle muestra un guion y no «undefined»", async () => {
    apiRequest.mockResolvedValue(exito(informe(5, [{ ...problema(1, "XML_FALTANTE"), detalle: undefined }])));
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    const fila = await screen.findByTestId("integridad-problema");
    expect(fila.textContent).toContain("—");
    expect(fila.textContent).not.toContain("undefined");
  });

  /** Perder un objeto o alterarlo es grave; no poder leer el almacenamiento puede ser pasajero y pide repetir: se distinguen a la vista. */
  it("los tipos graves salen en rojo y el almacenamiento inaccesible, como aviso", async () => {
    apiRequest.mockResolvedValue(exito(informe(9, [problema(1, "XML_FALTANTE"), problema(2, "XML_CORRUPTO"), problema(3, "CDR_FALTANTE"), problema(4, "STORAGE_INACCESIBLE")])));
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    await screen.findAllByTestId("integridad-problema");
    const clase = (texto: string) => screen.getByText(texto).className;
    expect(clase("XML faltante")).toContain("text-destructive");
    expect(clase("XML corrupto")).toContain("text-destructive");
    expect(clase("CDR faltante")).toContain("text-destructive");
    expect(clase("Almacenamiento inaccesible")).toContain("bg-warning");
  });

  it("la leyenda explica solo los tipos que aparecen, una vez cada uno", async () => {
    apiRequest.mockResolvedValue(exito(informe(9, [problema(1, "XML_FALTANTE"), problema(2, "XML_FALTANTE"), problema(3, "STORAGE_INACCESIBLE")])));
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    const leyenda = within(await screen.findByTestId("integridad-leyenda"));
    expect(leyenda.getAllByRole("term")).toHaveLength(2);
    expect(leyenda.getByText("El comprobante está firmado pero su XML ya no está en el almacenamiento.")).toBeTruthy();
    expect(leyenda.getByText(/puede ser un fallo de conexión y no un objeto perdido/)).toBeTruthy();
    expect(leyenda.queryByText(/DigestValue/)).toBeNull();
  });

  // --- validación y estados ---------------------------------------------------------------------------------------------------------------

  it("un rango inválido muestra el error y no pide nada; al cambiar una fecha el error se quita", () => {
    render(<VerificarIntegridad hoy={HOY} />);
    escribir(desde(), "2026-10-31");
    escribir(hasta(), "2026-10-01");

    fireEvent.click(boton());

    expect(apiRequest).not.toHaveBeenCalled();
    expect(screen.getByText("El rango no puede terminar antes de empezar.")).toBeTruthy();
    escribir(hasta(), "2026-11-30");
    expect(screen.queryByText("El rango no puede terminar antes de empezar.")).toBeNull();
  });

  it("un rango de más de 92 días y las fechas vacías se rechazan antes de enviar", () => {
    render(<VerificarIntegridad hoy={HOY} />);
    escribir(desde(), "2026-01-01");
    escribir(hasta(), "2026-12-31");
    fireEvent.click(boton());
    expect(screen.getByText("El rango no puede pasar de 92 días.")).toBeTruthy();

    escribir(desde(), "");
    escribir(hasta(), "");
    fireEvent.click(boton());
    expect(screen.getByText("Indica desde cuándo verificar.")).toBeTruthy();
    expect(screen.getByText("Indica hasta cuándo verificar.")).toBeTruthy();
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("mientras verifica dice que puede tardar, bloquea el botón y no muestra un resultado viejo", async () => {
    let terminar!: (r: ApiEnvelope<InformeDeIntegridad>) => void;
    apiRequest.mockResolvedValueOnce(exito(informe(3, [problema(1, "XML_FALTANTE")])));
    render(<VerificarIntegridad hoy={HOY} />);
    fireEvent.click(boton());
    await screen.findByTestId("integridad-resultado");

    apiRequest.mockReturnValueOnce(new Promise((resolve) => (terminar = resolve)));
    await act(async () => fireEvent.click(boton()));

    expect(boton().disabled).toBe(true);
    expect(boton().textContent).toContain("Verificando…");
    expect(screen.getByTestId("integridad-aviso").textContent).toContain("Puede tardar si hay muchos");
    expect(screen.queryByTestId("integridad-resultado")).toBeNull();
    await act(async () => terminar(exito(informe(7))));
    expect(boton().disabled).toBe(false);
    expect(screen.getByTestId("integridad-verificados").textContent).toContain("7 comprobantes");
  });

  /** Un doble clic en el mismo tick no puede lanzar dos barridos sobre el almacenamiento: la guarda es un ref, no el estado. */
  it("dos clics a la vez lanzan un solo barrido", async () => {
    let terminar!: (r: ApiEnvelope<InformeDeIntegridad>) => void;
    apiRequest.mockReturnValue(new Promise((resolve) => (terminar = resolve)));
    render(<VerificarIntegridad hoy={HOY} />);

    await act(async () => {
      fireEvent.click(boton());
      fireEvent.click(boton());
    });

    expect(apiRequest).toHaveBeenCalledTimes(1);
    await act(async () => terminar(exito(informe(1))));
  });

  it("un fallo se muestra con su motivo, sin resultado ni tabla, y se puede volver a intentar", async () => {
    apiRequest.mockResolvedValueOnce(fallo("RANGO_INVALIDO", "El rango de fechas es obligatorio y desde ≤ hasta"));
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    expect((await screen.findByTestId("integridad-error")).textContent).toBe("No se pudo verificar la integridad. El rango de fechas es obligatorio y desde ≤ hasta");
    expect(screen.queryByTestId("integridad-resultado")).toBeNull();
    apiRequest.mockResolvedValueOnce(exito(informe(4)));
    fireEvent.click(boton());
    await screen.findByTestId("integridad-resultado");
    expect(screen.queryByTestId("integridad-error")).toBeNull();
  });

  it("un fallo sin mensaje usa el texto del código y uno de red no se confunde con un resultado limpio", async () => {
    apiRequest.mockResolvedValue({ estado: "error", datos: null, mensaje: null, codigo: "RED", errores: null });
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    const error = await screen.findByTestId("integridad-error");
    expect(error.textContent).toMatch(/^No se pudo verificar la integridad\. \S/);
    expect(screen.queryByText("Todo en orden: no se encontró ningún problema.")).toBeNull();
  });

  /** H22: con un rango inválido seguía a la vista «Todo en orden» del barrido anterior, que ya no corresponde a lo que dice el formulario. */
  it("cambiar el rango después de un barrido quita su resultado, y un rango inválido no lo deja a la vista", async () => {
    apiRequest.mockResolvedValue(exito(informe(120)));
    render(<VerificarIntegridad hoy={HOY} />);
    fireEvent.click(boton());
    await screen.findByTestId("integridad-resultado");

    escribir(desde(), "2026-01-01");
    expect(screen.queryByTestId("integridad-resultado")).toBeNull();

    fireEvent.click(boton());
    expect(screen.getByText(/92 días/)).toBeTruthy();
    expect(screen.queryByTestId("integridad-resultado")).toBeNull();
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("un éxito sin datos no se muestra como un barrido limpio", async () => {
    apiRequest.mockResolvedValue({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
    render(<VerificarIntegridad hoy={HOY} />);

    fireEvent.click(boton());

    await screen.findByTestId("integridad-error");
    expect(screen.queryByTestId("integridad-resultado")).toBeNull();
  });
});
