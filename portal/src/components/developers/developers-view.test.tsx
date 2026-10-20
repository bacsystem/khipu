import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DevelopersView } from "./developers-view";

// Scalar es pesado y ya tiene su prueba: aquí solo importa la cabecera propia de la página (#13).
vi.mock("./api-reference", () => ({ ApiReference: ({ apiKey }: { apiKey?: string }) => <div data-testid="referencia">{apiKey ?? "sin key"}</div> }));
vi.mock("@/lib/solo-lectura", () => ({ useSoloLectura: () => null }));

afterEach(cleanup);

describe("DevelopersView (#13)", () => {
  it("abre con el título, una intro que dice cómo se autentica y las otras secciones", () => {
    render(<DevelopersView spec={{}} baseServerURL="http://api" mostrarGenerarKey={false} />);

    expect(screen.getByRole("heading", { level: 1, name: "Referencia de la API" })).toBeInTheDocument();
    expect(screen.getByText("X-Api-Key")).toBeInTheDocument();
    const nav = screen.getByRole("navigation", { name: "Más documentación" });
    expect(nav.querySelectorAll("a")).toHaveLength(4);
    expect(screen.getByRole("link", { name: "Guía de integración" })).toHaveAttribute("href", "/developers/guia");
    expect(screen.getByRole("link", { name: "Catálogos SUNAT" })).toHaveAttribute("href", "/developers/catalogos");
    expect(screen.getByRole("link", { name: "Errores y estados" })).toHaveAttribute("href", "/developers/errores");
    expect(screen.getByRole("link", { name: "Entornos y límites" })).toHaveAttribute("href", "/developers/entornos");
    expect(screen.getByTestId("referencia")).toHaveTextContent("sin key");
  });

  it("el botón de la key de prueba solo está con sesión", () => {
    const { rerender } = render(<DevelopersView spec={{}} baseServerURL="http://api" mostrarGenerarKey={false} />);
    expect(screen.queryByRole("button", { name: "Generar API key de prueba" })).not.toBeInTheDocument();

    rerender(<DevelopersView spec={{}} baseServerURL="http://api" mostrarGenerarKey />);
    expect(screen.getByRole("button", { name: "Generar API key de prueba" })).toBeInTheDocument();
  });
});
