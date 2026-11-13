import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE } from "@/lib/sesion/inactividad";
import { avisarSesionTerminada, leerSesionTerminada } from "@/lib/sesion/sesion-terminada";
import { DialogoSesionTerminada } from "./dialogo-sesion-terminada";

const asignar = vi.fn();

function estarEn(pathname: string, search = "") {
  vi.stubGlobal("location", { assign: asignar, pathname, search });
}

const dialogo = () => screen.queryByTestId("dialogo-sesion-terminada");
const abrir = () => act(() => avisarSesionTerminada());

beforeEach(() => {
  estarEn("/clientes");
  window.localStorage.clear();
});

afterEach(() => {
  cleanup();
  asignar.mockClear();
  vi.unstubAllGlobals();
});

/** #654: «Tu sesión terminó», el diálogo que explica una sesión muerta en vez de saltar al login. */
describe("DialogoSesionTerminada", () => {
  it("cerrado mientras la sesión vive: no pinta nada", () => {
    render(<DialogoSesionTerminada />);

    expect(dialogo()).toBeNull();
  });

  it("al terminar la sesión se abre, con su título, su texto y un único botón «Volver a ingresar»", async () => {
    render(<DialogoSesionTerminada />);

    await abrir();

    expect(dialogo()).not.toBeNull();
    expect(screen.getByText("Tu sesión terminó")).toBeInTheDocument();
    expect(screen.getByText("Por seguridad tuvimos que cerrarla. Vuelve a ingresar para seguir donde estabas.")).toBeInTheDocument();
    expect(screen.getAllByRole("button")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Volver a ingresar" })).toBeInTheDocument();
    expect(asignar).not.toHaveBeenCalled(); // con el diálogo montado no se navega solo
  });

  it("y en inglés con el locale en inglés", async () => {
    fijarLocaleDePrueba("en");
    render(<DialogoSesionTerminada />);

    await abrir();

    expect(screen.getByText("Your session has ended")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign in again" })).toBeInTheDocument();
  });

  /** El DS no tiene DOM de pruebas: que `descartable={false}` ignore Escape se comprueba aquí, con jsdom, sobre el elemento que de verdad tiene el foco. */
  it("no se puede cerrar con Escape", async () => {
    render(<DialogoSesionTerminada />);
    await abrir();
    await vi.waitFor(() => expect(screen.getByRole("button", { name: "Volver a ingresar" })).toHaveFocus());

    await act(async () => void fireEvent.keyDown(document.activeElement!, { key: "Escape" }));
    await act(async () => void fireEvent.keyDown(dialogo()!, { key: "Escape" }));
    await act(async () => void fireEvent.keyDown(document.body, { key: "Escape" }));

    expect(dialogo()).not.toBeNull();
    expect(leerSesionTerminada()).toBe(true);
  });

  it("se anuncia como diálogo con el título como nombre y el texto como descripción", async () => {
    render(<DialogoSesionTerminada />);

    await abrir();

    const aviso = screen.getByRole("dialog", { name: "Tu sesión terminó", description: "Por seguridad tuvimos que cerrarla. Vuelve a ingresar para seguir donde estabas." });
    expect(aviso).toBeInTheDocument();
  });

  it("no se puede cerrar con un clic afuera", async () => {
    render(<DialogoSesionTerminada />);
    await abrir();
    const fondo = document.querySelector('[data-slot="dialog-overlay"]')!;

    fireEvent.pointerDown(fondo);
    fireEvent.mouseDown(fondo);
    fireEvent.click(fondo);

    expect(dialogo()).not.toBeNull();
  });

  it("no tiene botón de cerrar", async () => {
    render(<DialogoSesionTerminada />);
    await abrir();

    expect(screen.queryByRole("button", { name: /cerrar/i })).toBeNull();
  });

  it("varios 401 seguidos abren un solo diálogo", async () => {
    render(<DialogoSesionTerminada />);

    await abrir();
    await abrir();
    await abrir();

    expect(screen.getAllByRole("dialog")).toHaveLength(1);
  });

  it("«Volver a ingresar» hace una navegación completa al login con la ruta actual, con su consulta, codificada en next", async () => {
    estarEn("/comprobantes", "?estado=ACEPTADO");
    render(<DialogoSesionTerminada />);
    await abrir();

    fireEvent.click(screen.getByRole("button", { name: "Volver a ingresar" }));

    expect(asignar).toHaveBeenCalledExactlyOnceWith("/login?next=%2Fcomprobantes%3Festado%3DACEPTADO");
  });

  it("las marcas de inactividad se borran al abrirse, no al pulsar el botón", async () => {
    window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, "1");
    window.localStorage.setItem(CLAVE_ULTIMO_KEEP_ALIVE, "1");
    render(<DialogoSesionTerminada />);
    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBe("1");

    await abrir();

    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBeNull();
    expect(window.localStorage.getItem(CLAVE_ULTIMO_KEEP_ALIVE)).toBeNull();
    expect(asignar).not.toHaveBeenCalled();
  });

  it("el foco queda en «Volver a ingresar»", async () => {
    render(<DialogoSesionTerminada />);

    await abrir();

    await vi.waitFor(() => expect(screen.getByRole("button", { name: "Volver a ingresar" })).toHaveFocus());
  });

  /** El diálogo se monta antes que cualquier `fetch` de la página, pero si el aviso llegara antes de montarse, igual debe verse. */
  it("si la sesión ya terminó cuando se monta, se abre de entrada", async () => {
    render(<DialogoSesionTerminada />);
    await abrir();
    cleanup();

    render(<DialogoSesionTerminada />);

    expect(dialogo()).not.toBeNull();
  });
});
