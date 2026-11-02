import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiEnvelope } from "@/lib/api/types";
import { AccionesDeBaja } from "./acciones-de-baja";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (datos: unknown): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

function abrir(estado: "ACTIVA" | "SUSPENDIDA" | "BAJA" = "ACTIVA") {
  render(<AccionesDeBaja id={ID} nombre="Panadería Sol" estado={estado} />);
  fireEvent.click(screen.getByTestId(estado === "BAJA" ? "reponer-cuenta" : "dar-de-baja-cuenta"));
}

describe("AccionesDeBaja (#201)", () => {
  it("una cuenta en servicio ofrece dar de baja, y una de baja, reponer: nunca las dos", () => {
    const { unmount } = render(<AccionesDeBaja id={ID} nombre="Panadería Sol" estado="ACTIVA" />);
    expect(screen.getByTestId("dar-de-baja-cuenta")).toBeTruthy();
    expect(screen.queryByTestId("reponer-cuenta")).toBeNull();
    unmount();

    render(<AccionesDeBaja id={ID} nombre="Panadería Sol" estado="BAJA" />);
    expect(screen.getByTestId("reponer-cuenta")).toBeTruthy();
    expect(screen.queryByTestId("dar-de-baja-cuenta")).toBeNull();
  });

  /** La suspensión es otra cosa: una cuenta suspendida también se puede dar de baja. */
  it("una cuenta suspendida también ofrece dar de baja", () => {
    render(<AccionesDeBaja id={ID} nombre="Panadería Sol" estado="SUSPENDIDA" />);

    expect(screen.getByTestId("dar-de-baja-cuenta")).toBeTruthy();
  });

  it("no envía nada hasta que se confirma: abrir el diálogo solo lo muestra", () => {
    abrir();

    expect(screen.getByTestId("baja-confirmacion")).toBeTruthy();
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("el diálogo dice qué se conserva, que el RUC sigue ocupado y que NO corta el acceso", () => {
    abrir();

    expect(screen.getByText(/Panadería Sol/)).toBeTruthy();
    const texto = screen.getByTestId("baja-confirmacion").textContent ?? "";
    expect(texto).toContain("Los comprobantes, XML y CDR se conservan");
    expect(texto).toContain("su RUC sigue ocupado");
    expect(texto).toContain("No corta el acceso");
    expect(texto).toContain("suspende la cuenta");
    expect(texto).toContain("Reponer la cuenta la devuelve a los listados");
  });

  it("el diálogo de reponer dice que no toca la suspensión", () => {
    abrir("BAJA");

    const texto = screen.getByTestId("baja-confirmacion").textContent ?? "";
    expect(texto).toContain("Panadería Sol vuelve a los listados y al cobro");
    expect(texto).toContain("si estaba suspendida, sigue suspendida");
  });

  it("dar de baja envía el motivo recortado a la ruta de baja y recarga la página", async () => {
    apiRequest.mockResolvedValue(exito({ cuenta_id: ID, baja_en: "2026-10-03T09:00:00Z" }));
    abrir();

    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "  cerró su negocio  " } });
    fireEvent.click(screen.getByTestId("baja-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${ID}/baja`, { method: "POST", body: { motivo: "cerró su negocio" } });
    expect(screen.queryByTestId("baja-confirmacion")).toBeNull();
  });

  it("el motivo es opcional: sin escribirlo se envía vacío", async () => {
    apiRequest.mockResolvedValue(exito({ cuenta_id: ID }));
    abrir();

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${ID}/baja`, { method: "POST", body: { motivo: "" } });
  });

  it("el motivo no admite más de 200 caracteres", () => {
    abrir();

    expect((screen.getByLabelText(/Motivo/) as HTMLInputElement).maxLength).toBe(200);
  });

  it("reponer no pide motivo y envía el pedido sin cuerpo a la ruta de reponer", async () => {
    apiRequest.mockResolvedValue(exito({ cuenta_id: ID }));
    abrir("BAJA");
    expect(screen.queryByLabelText(/Motivo/)).toBeNull();

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${ID}/reponer`, { method: "POST", body: undefined });
  });

  it("cancelar cierra el diálogo sin enviar nada, y al reabrirlo el motivo no queda escrito", () => {
    abrir();
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "algo" } });

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByTestId("baja-confirmacion")).toBeNull();
    expect(apiRequest).not.toHaveBeenCalled();
    expect(refresh).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId("dar-de-baja-cuenta"));
    expect((screen.getByLabelText(/Motivo/) as HTMLInputElement).value).toBe("");
  });

  /** Dos clics seguidos en «Dar de baja» son un solo pedido: el segundo llegaría como 409 y asustaría al administrador. */
  it("un doble clic en confirmar envía un solo pedido", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir();

    // Los dos clics dentro de un mismo `act`: el segundo manejador ve el `enviando` viejo y el botón todavía no está deshabilitado, así que lo
    // único que frena el segundo pedido es la guardia del ref.
    const boton = screen.getByTestId("baja-confirmar");
    act(() => {
      fireEvent.click(boton);
      fireEvent.click(boton);
    });
    await act(async () => resolver(exito({ cuenta_id: ID })));

    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("mientras se envía el botón dice «Dando de baja…», no se puede volver a pulsar y Escape no cierra el diálogo", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir();

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    expect(screen.getByTestId("baja-confirmar").textContent).toContain("Dando de baja…");
    expect((screen.getByTestId("baja-confirmar") as HTMLButtonElement).disabled).toBe(true);
    fireEvent.keyDown(screen.getByTestId("baja-confirmacion"), { key: "Escape" });
    expect(screen.getByTestId("baja-confirmacion")).toBeTruthy();
    await act(async () => resolver(exito({})));
  });

  it("al reponer el botón dice «Reponiendo…»", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir("BAJA");

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    expect(screen.getByTestId("baja-confirmar").textContent).toContain("Reponiendo…");
    await act(async () => resolver(exito({})));
  });

  it("un error del backend se muestra en el diálogo, que sigue abierto, y no recarga", async () => {
    apiRequest.mockResolvedValue(error("MOTIVO_INVALIDO", "El motivo no puede pasar de 200 caracteres"));
    abrir();

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("El motivo no puede pasar de 200 caracteres");
    expect(screen.getByTestId("baja-confirmacion")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  /** Si otro administrador ya la dio de baja (o la repuso), el botón de esta página quedó viejo: se dice y se recarga para mostrar el estado real. */
  it.each([
    ["CUENTA_YA_DE_BAJA", "La cuenta ya está dada de baja", "ACTIVA"],
    ["CUENTA_NO_DE_BAJA", "La cuenta no está dada de baja", "BAJA"],
  ] as const)("un 409 %s lo dice y recarga la página", async (codigo, mensaje, estado) => {
    apiRequest.mockResolvedValue(error(codigo, mensaje));
    abrir(estado);

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe(mensaje);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("un corte de red no se reintenta a ciegas: avisa que recargue para ver el estado real", async () => {
    apiRequest.mockResolvedValue(error("RED", "No se pudo conectar con el servidor."));
    abrir();

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página para ver el estado real de la cuenta");
    expect(refresh).not.toHaveBeenCalled();
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("una respuesta ilegible tampoco se da por hecha ni se reintenta", async () => {
    apiRequest.mockResolvedValue(error("RESPUESTA_INVALIDA", "Respuesta inválida del servidor."));
    abrir();

    fireEvent.click(screen.getByTestId("baja-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página");
    expect(refresh).not.toHaveBeenCalled();
  });
});
