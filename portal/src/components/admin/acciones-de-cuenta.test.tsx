import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiEnvelope } from "@/lib/api/types";
import { AccionesDeCuenta } from "./acciones-de-cuenta";

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

function abrir(estado: "ACTIVA" | "SUSPENDIDA" = "ACTIVA", empresas = 2) {
  render(<AccionesDeCuenta id={ID} nombre="Panadería Sol" estado={estado} empresas={empresas} />);
  fireEvent.click(screen.getByTestId(estado === "ACTIVA" ? "suspender-cuenta" : "reactivar-cuenta"));
}

describe("AccionesDeCuenta (#182)", () => {
  it("una cuenta activa ofrece suspender, y una suspendida, reactivar: nunca las dos", () => {
    const { unmount } = render(<AccionesDeCuenta id={ID} nombre="Panadería Sol" estado="ACTIVA" empresas={1} />);
    expect(screen.getByTestId("suspender-cuenta")).toBeTruthy();
    expect(screen.queryByTestId("reactivar-cuenta")).toBeNull();
    unmount();

    render(<AccionesDeCuenta id={ID} nombre="Panadería Sol" estado="SUSPENDIDA" empresas={1} />);
    expect(screen.getByTestId("reactivar-cuenta")).toBeTruthy();
    expect(screen.queryByTestId("suspender-cuenta")).toBeNull();
  });

  it("no envía nada hasta que se confirma: abrir el diálogo solo lo muestra", () => {
    abrir();

    expect(screen.getByTestId("suspension-confirmacion")).toBeTruthy();
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("el diálogo dice a quién alcanza, qué corta y qué NO hace", () => {
    abrir("ACTIVA", 3);

    expect(screen.getByText(/Panadería Sol/)).toBeTruthy();
    expect(screen.getByTestId("suspension-alcance").textContent).toBe("3 empresas");
    const texto = screen.getByTestId("suspension-confirmacion").textContent ?? "";
    expect(texto).toContain("ni siquiera con una sesión ya abierta");
    expect(texto).toContain("seguirán enviándose a SUNAT");
    expect(texto).toContain("lo devuelve todo a como estaba");
  });

  it("el alcance se dice bien con una empresa y con ninguna", () => {
    const { unmount } = render(<AccionesDeCuenta id={ID} nombre="A" estado="ACTIVA" empresas={1} />);
    fireEvent.click(screen.getByTestId("suspender-cuenta"));
    expect(screen.getByTestId("suspension-alcance").textContent).toBe("1 empresa");
    unmount();
    cleanup();

    render(<AccionesDeCuenta id={ID} nombre="A" estado="ACTIVA" empresas={0} />);
    fireEvent.click(screen.getByTestId("suspender-cuenta"));
    expect(screen.getByTestId("suspension-alcance").textContent).toBe("Sin empresas todavía");
  });

  it("suspender envía el motivo recortado a la ruta de suspender y recarga la página", async () => {
    apiRequest.mockResolvedValue(exito({ cuenta_id: ID, estado: "SUSPENDIDA" }));
    abrir();

    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "  no pagó septiembre  " } });
    fireEvent.click(screen.getByTestId("suspension-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${ID}/suspender`, { method: "POST", body: { motivo: "no pagó septiembre" } });
    expect(screen.queryByTestId("suspension-confirmacion")).toBeNull();
  });

  it("el motivo es opcional: sin escribirlo se envía vacío", async () => {
    apiRequest.mockResolvedValue(exito({ cuenta_id: ID, estado: "SUSPENDIDA" }));
    abrir();

    fireEvent.click(screen.getByTestId("suspension-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${ID}/suspender`, { method: "POST", body: { motivo: "" } });
  });

  it("el motivo no admite más de 200 caracteres", () => {
    abrir();

    expect((screen.getByLabelText(/Motivo/) as HTMLInputElement).maxLength).toBe(200);
  });

  it("reactivar no pide motivo y envía el pedido sin cuerpo a la ruta de reactivar", async () => {
    apiRequest.mockResolvedValue(exito({ cuenta_id: ID, estado: "ACTIVA" }));
    abrir("SUSPENDIDA");
    expect(screen.queryByLabelText(/Motivo/)).toBeNull();

    fireEvent.click(screen.getByTestId("suspension-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${ID}/reactivar`, { method: "POST", body: undefined });
  });

  it("cancelar cierra el diálogo sin enviar nada", () => {
    abrir();

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByTestId("suspension-confirmacion")).toBeNull();
    expect(apiRequest).not.toHaveBeenCalled();
    expect(refresh).not.toHaveBeenCalled();
  });

  /** Dos clics seguidos en «Suspender» son un solo pedido: el segundo llegaría como 409 y asustaría al administrador. */
  it("un doble clic en confirmar envía un solo pedido", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir();

    // Los dos clics dentro de un mismo `act`: React no aplica el estado entre uno y otro, así que el segundo manejador ve el `enviando` viejo
    // y el botón todavía no está deshabilitado. Es lo único que frena el segundo pedido en ese caso: la guardia del ref, no el `disabled`.
    const boton = screen.getByTestId("suspension-confirmar");
    act(() => {
      fireEvent.click(boton);
      fireEvent.click(boton);
    });
    await act(async () => resolver(exito({ cuenta_id: ID, estado: "SUSPENDIDA" })));

    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("mientras se envía el botón dice «Suspendiendo…» y no se puede volver a pulsar", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir();

    fireEvent.click(screen.getByTestId("suspension-confirmar"));

    expect(screen.getByTestId("suspension-confirmar").textContent).toContain("Suspendiendo…");
    expect((screen.getByTestId("suspension-confirmar") as HTMLButtonElement).disabled).toBe(true);
    await act(async () => resolver(exito({})));
  });

  it("un error del backend se muestra en el diálogo, que sigue abierto, y no recarga", async () => {
    apiRequest.mockResolvedValue(error("MOTIVO_INVALIDO", "El motivo no puede pasar de 200 caracteres"));
    abrir();

    fireEvent.click(screen.getByTestId("suspension-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("El motivo no puede pasar de 200 caracteres");
    expect(screen.getByTestId("suspension-confirmacion")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  /** Si otro administrador ya la suspendió, el botón de esta página quedó viejo: se dice y se recarga para mostrar el estado real. */
  it("un 409 porque la cuenta ya estaba en ese estado lo dice y recarga la página", async () => {
    apiRequest.mockResolvedValue(error("CUENTA_YA_SUSPENDIDA", "La cuenta ya está suspendida"));
    abrir();

    fireEvent.click(screen.getByTestId("suspension-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("La cuenta ya está suspendida");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("un corte de red no se reintenta a ciegas: avisa que recargue para ver el estado real", async () => {
    apiRequest.mockResolvedValue(error("RED", "No se pudo conectar con el servidor."));
    abrir();

    fireEvent.click(screen.getByTestId("suspension-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página para ver el estado real de la cuenta");
    expect(refresh).not.toHaveBeenCalled();
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });
});
