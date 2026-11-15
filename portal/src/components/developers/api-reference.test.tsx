import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const capturado = vi.hoisted(() => ({ configuraciones: [] as Record<string, unknown>[] }));

vi.mock("@scalar/api-reference-react", () => ({
  ApiReferenceReact: ({ configuration }: { configuration: Record<string, unknown> }) => {
    capturado.configuraciones.push(configuration);
    return null;
  },
}));
vi.mock("@scalar/api-reference-react/style.css", () => ({}));

import { ApiReference } from "./api-reference";

describe("ApiReference", () => {
  beforeEach(() => {
    capturado.configuraciones.length = 0;
  });

  // DEPS-01: con `proxyUrl` indefinido Scalar manda cada «Test Request» por https://proxy.scalar.com (con la API key real
  // y el cuerpo del ejemplo); `withDefaultFonts` carga tipografías desde fonts.scalar.com y `telemetry` viene activada.
  // Cualquier valor definido en `proxyUrl` anula el default: la cadena vacía es «sin proxy».
  it("no manda las peticiones de prueba, las fuentes ni telemetría a scalar.com", () => {
    render(<ApiReference spec={{}} baseServerURL="http://localhost:8001" apiKey="fk_prueba" />);

    const configuracion = capturado.configuraciones.at(-1);
    expect(configuracion).toBeDefined();
    expect(configuracion?.proxyUrl).toBe("");
    expect(configuracion?.withDefaultFonts).toBe(false);
    expect(configuracion?.telemetry).toBe(false);
  });

  it("limpia light-mode/dark-mode de document.body al desmontar", () => {
    document.body.classList.add("dark-mode");

    const { unmount } = render(<ApiReference spec={{}} baseServerURL="http://localhost:8001" />);
    expect(document.body.classList.contains("dark-mode")).toBe(true);

    unmount();

    expect(document.body.classList.contains("dark-mode")).toBe(false);
    expect(document.body.classList.contains("light-mode")).toBe(false);
  });
});
