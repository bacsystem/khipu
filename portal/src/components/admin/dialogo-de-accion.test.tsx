import { KeyRoundIcon } from "lucide-react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiEnvelope } from "@/lib/api/types";
import { DialogoDeAccion, type DialogoDeAccionProps } from "./dialogo-de-accion";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

function montar(extra: Partial<DialogoDeAccionProps> = {}) {
  render(
    <DialogoDeAccion
      testId="accion"
      boton="Hacer algo"
      icono={KeyRoundIcon}
      titulo="Hacer algo importante"
      descripcion="Esto hace algo."
      efectos={["Hace una cosa.", "No hace la otra."]}
      confirmar="Confirmar"
      enviando="Enviando…"
      cancelar="Cancelar"
      ruta="/api/admin/x"
      {...extra}
    />,
  );
}

function abrir(extra: Partial<DialogoDeAccionProps> = {}) {
  montar(extra);
  fireEvent.click(screen.getByTestId("accion"));
}

describe("DialogoDeAccion", () => {
  it("abrirlo solo lo muestra: no envía nada, y dice qué hace y qué NO hace", () => {
    abrir();

    const dialogo = screen.getByTestId("accion-dialogo");
    expect(dialogo.textContent).toContain("Hacer algo importante");
    expect(dialogo.textContent).toContain("Hace una cosa.");
    expect(dialogo.textContent).toContain("No hace la otra.");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("confirmar hace POST a la ruta con el cuerpo, cierra y recarga la página", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir({ cuerpo: () => ({ motivo: "x" }) });

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/x", { method: "POST", body: { motivo: "x" } });
    expect(screen.queryByTestId("accion-dialogo")).toBeNull();
  });

  it("sin cuerpo envía el pedido sin cuerpo", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir();

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/x", { method: "POST", body: undefined });
  });

  it("el cuerpo se lee al confirmar, no al montar: refleja lo que el administrador escribió en los campos", async () => {
    apiRequest.mockResolvedValue(exito());
    let valor = "antes";
    abrir({ cuerpo: () => ({ valor }) });
    valor = "después";

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/x", { method: "POST", body: { valor: "después" } });
  });

  it("lo que haya en la respuesta llega a quien lo pidió", async () => {
    apiRequest.mockResolvedValue(exito({ hacia: "PRODUCCION" }));
    const alExito = vi.fn();
    abrir({ alExito });

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    await waitFor(() => expect(alExito).toHaveBeenCalledWith({ hacia: "PRODUCCION" }));
  });

  it("muestra los campos propios de la acción dentro del diálogo", () => {
    abrir({ children: <input aria-label="Motivo" /> });

    expect(screen.getByLabelText("Motivo")).toBeTruthy();
  });

  it("cancelar cierra sin enviar nada y avisa para limpiar los campos", () => {
    const alCerrar = vi.fn();
    abrir({ alCerrar });

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByTestId("accion-dialogo")).toBeNull();
    expect(apiRequest).not.toHaveBeenCalled();
    expect(refresh).not.toHaveBeenCalled();
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });

  /** Dos clics seguidos son un solo pedido: el segundo llegaría como 409 y asustaría al administrador. */
  it("un doble clic en confirmar envía un solo pedido", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir();

    // Los dos clics dentro de un mismo `act`: el segundo manejador ve el `enviando` viejo y el botón todavía no está deshabilitado, así que lo único que
    // frena el segundo pedido es la guardia del ref.
    const boton = screen.getByTestId("accion-confirmar");
    act(() => {
      fireEvent.click(boton);
      fireEvent.click(boton);
    });
    await act(async () => resolver(exito()));

    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("mientras envía el botón dice «Enviando…», no se puede pulsar ni cancelar y Escape no cierra el diálogo", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir();

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    expect(screen.getByTestId("accion-confirmar").textContent).toContain("Enviando…");
    expect((screen.getByTestId("accion-confirmar") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Cancelar" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.keyDown(screen.getByTestId("accion-dialogo"), { key: "Escape" });
    expect(screen.getByTestId("accion-dialogo")).toBeTruthy();
    await act(async () => resolver(exito()));
  });

  it("un error del backend se muestra en el diálogo, que sigue abierto, y no recarga", async () => {
    apiRequest.mockResolvedValue(error("ENTORNO_SIN_CAMBIOS", "La empresa ya está en BETA"));
    abrir();

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("La empresa ya está en BETA");
    expect(screen.getByTestId("accion-dialogo")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("tras un error se puede volver a intentar, y el error anterior desaparece", async () => {
    apiRequest.mockResolvedValueOnce(error("X", "falló")).mockResolvedValueOnce(exito());
    abrir();

    fireEvent.click(screen.getByTestId("accion-confirmar"));
    await screen.findByRole("alert");
    fireEvent.click(screen.getByTestId("accion-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledTimes(2);
  });

  /** Si otro administrador ya hizo lo mismo, el botón de esta página quedó viejo: se dice y se recarga para mostrar el estado real. */
  it("un error que dice que la página quedó vieja lo muestra y recarga; uno cualquiera no recarga", async () => {
    apiRequest.mockResolvedValue(error("API_KEY_YA_REVOCADA", "La API key ya estaba revocada"));
    abrir({ estadoViejo: ["API_KEY_YA_REVOCADA"] });

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("La API key ya estaba revocada");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("un corte de red no se reintenta a ciegas: avisa que recargue para ver el estado real", async () => {
    apiRequest.mockResolvedValue(error("RED", "No se pudo conectar con el servidor."));
    abrir();

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página para ver el estado real");
    expect(refresh).not.toHaveBeenCalled();
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("una respuesta ilegible tampoco se da por hecha", async () => {
    apiRequest.mockResolvedValue(error("RESPUESTA_INVALIDA", "Respuesta inválida del servidor."));
    abrir();

    fireEvent.click(screen.getByTestId("accion-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página");
    expect(refresh).not.toHaveBeenCalled();
  });

  it("al cerrar y reabrir tras un error, el error anterior no queda", async () => {
    apiRequest.mockResolvedValue(error("X", "falló"));
    abrir();
    fireEvent.click(screen.getByTestId("accion-confirmar"));
    await screen.findByRole("alert");

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByTestId("accion-dialogo")).toBeNull());
    fireEvent.click(screen.getByTestId("accion"));

    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("con tono de peligro el botón de confirmar es el destructivo, y sin él el primario", () => {
    const { unmount } = render(
      <DialogoDeAccion testId="a" boton="b" icono={KeyRoundIcon} titulo="t" descripcion="d" efectos={["e"]} confirmar="c" enviando="e" cancelar="x" ruta="/r" tono="peligro" />,
    );
    fireEvent.click(screen.getByTestId("a"));
    const peligro = screen.getByTestId("a-confirmar").className;
    unmount();
    cleanup();
    render(<DialogoDeAccion testId="a" boton="b" icono={KeyRoundIcon} titulo="t" descripcion="d" efectos={["e"]} confirmar="c" enviando="e" cancelar="x" ruta="/r" />);
    fireEvent.click(screen.getByTestId("a"));

    expect(screen.getByTestId("a-confirmar").className).not.toBe(peligro);
  });
});
