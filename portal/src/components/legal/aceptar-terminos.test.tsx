import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const refresh = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));

import { AceptarTerminos } from "./aceptar-terminos";
import { debeAceptarTerminos } from "./pantalla-de-terminos";

afterEach(() => {
  cleanup();
  refresh.mockClear();
  vi.unstubAllGlobals();
});

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ datos: null, mensaje: null, codigo: null, errores: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

const casilla = () => screen.getByRole("checkbox", { name: /Acepto los términos del servicio y la política de privacidad/ });
const aceptar = () => fireEvent.click(screen.getByRole("button", { name: "Aceptar y continuar" }));

describe("AceptarTerminos (#342)", () => {
  it("enlaza los dos textos, en otra pestaña", () => {
    render(<AceptarTerminos />);

    for (const [nombre, href] of [["términos del servicio", "/terminos"], ["política de privacidad", "/privacidad"]]) {
      const enlace = screen.getByRole("link", { name: nombre });
      expect(enlace.getAttribute("href")).toBe(href);
      expect(enlace.getAttribute("target")).toBe("_blank");
    }
  });

  it("sin marcar la casilla no envía nada y dice por qué", () => {
    const fetch = stubFetch(200, { estado: "exito" });
    render(<AceptarTerminos />);

    aceptar();

    expect(fetch).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toContain("Marca la casilla para aceptar los términos");
    expect(casilla().getAttribute("aria-invalid")).toBe("true");
    expect(casilla().getAttribute("aria-describedby")).toBe("acepta-terminos-error");
  });

  it("marcada, acepta en el BFF y vuelve a pintar la página, que ya no lo detiene", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: { version: "2026-10-20", aceptada_en: "2026-10-20T15:00:00Z" } });
    render(<AceptarTerminos />);

    fireEvent.click(casilla());
    aceptar();

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/auth/terminos");
    expect(init.method).toBe("POST");
  });

  it("si el backend lo rechaza lo dice y deja reintentar", async () => {
    stubFetch(403, { estado: "error", codigo: "SOPORTE_SOLO_LECTURA", mensaje: "solo se puede mirar" });
    render(<AceptarTerminos />);

    fireEvent.click(casilla());
    aceptar();

    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy());
    expect(refresh).not.toHaveBeenCalled();
    expect((screen.getByRole("button", { name: "Aceptar y continuar" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("puede cerrar la sesión en vez de aceptar", async () => {
    const fetch = stubFetch(200, { estado: "exito" });
    const assign = vi.fn();
    const original = window.location;
    Object.defineProperty(window, "location", { configurable: true, value: { ...original, assign } });
    try {
      render(<AceptarTerminos />);

      fireEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));

      await waitFor(() => expect(assign).toHaveBeenCalledWith("/login"));
      expect((fetch.mock.calls[0] as unknown as [string])[0]).toBe("/api/auth/logout");
    } finally {
      Object.defineProperty(window, "location", { configurable: true, value: original });
    }
  });
});

describe("debeAceptarTerminos (#342)", () => {
  it("solo con terminos_pendientes en una sesión que no es de soporte", () => {
    expect(debeAceptarTerminos({ terminos_pendientes: true })).toBe(true);
    expect(debeAceptarTerminos({ terminos_pendientes: false })).toBe(false);
    expect(debeAceptarTerminos({})).toBe(false);
    expect(debeAceptarTerminos({ terminos_pendientes: true, soporte_hasta: "2026-10-20T15:15:00Z" })).toBe(false);
  });
});
