import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { BannerDeMantenimiento } from "./banner-de-mantenimiento";

afterEach(cleanup);

describe("BannerDeMantenimiento (#199)", () => {
  it("dice el texto y hasta cuándo, en hora de Lima", () => {
    render(<BannerDeMantenimiento texto="Mantenimiento esta noche de 22:00 a 23:00" hasta="2026-10-16T04:00:00Z" />);

    expect(screen.getByTestId("banner-de-mantenimiento-texto").textContent).toBe("Mantenimiento esta noche de 22:00 a 23:00");
    expect(screen.getByTestId("banner-de-mantenimiento").textContent).toContain("Hasta 15 Oct 2026, 23:00");
  });

  it("es un aviso de estado, anunciado a quien usa lector de pantalla", () => {
    render(<BannerDeMantenimiento texto="x" hasta="2026-10-16T04:00:00Z" />);

    expect(screen.getByRole("status")).toBe(screen.getByTestId("banner-de-mantenimiento"));
  });

  /** El texto lo escribe un administrador y lo ve todo cliente: se muestra como texto, nunca se interpreta como HTML. */
  it("un texto con etiquetas se muestra tal cual y no se interpreta", () => {
    const { container } = render(<BannerDeMantenimiento texto={'<b>urgente</b> <img src=x onerror="alert(1)">'} hasta="2026-10-16T04:00:00Z" />);

    expect(screen.getByTestId("banner-de-mantenimiento-texto").textContent).toBe('<b>urgente</b> <img src=x onerror="alert(1)">');
    expect(container.querySelector("b")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
  });

  it("usa el tono de aviso, no el de error", () => {
    render(<BannerDeMantenimiento texto="x" hasta="2026-10-16T04:00:00Z" />);

    const clases = screen.getByTestId("banner-de-mantenimiento").className;
    expect(clases).toContain("bg-warning");
    expect(clases).not.toContain("destructive");
  });
});
