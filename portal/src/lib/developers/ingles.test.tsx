import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import EntornosPage from "@/app/developers/entornos/page";
import ErroresPage from "@/app/developers/errores/page";
import GuiaPage from "@/app/developers/guia/page";
import { DevelopersView } from "@/components/developers/developers-view";
import { fijarLocaleDePrueba, mensajesDePrueba } from "@/i18n/prueba";
import { entradas } from "./entradas";
import { buscarEnLaDocumentacion, indexar } from "./indice";

vi.mock("@/components/developers/api-reference", () => ({ ApiReference: () => null }));
vi.mock("@/lib/solo-lectura", () => ({ useSinPermisoDeConfigurar: () => null }));

// El índice lateral observa las secciones con IntersectionObserver, que jsdom no trae.
beforeAll(() => {
  vi.stubGlobal("IntersectionObserver", class { observe() {} unobserve() {} disconnect() {} takeRecords() { return []; } });
});
afterEach(cleanup);

/** #25: la documentación para developers en inglés, con las mismas anclas y el código técnico sin traducir. */
describe("developers en inglés (#25)", () => {
  it("la referencia abre con su título y las otras secciones en inglés", () => {
    fijarLocaleDePrueba("en");
    render(<DevelopersView spec={{}} baseServerURL="http://api" mostrarGenerarKey />);

    expect(screen.getByRole("heading", { level: 1, name: "API reference" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Integration guide" })).toHaveAttribute("href", "/developers/guia");
    expect(screen.getByRole("button", { name: "Generate test API key" })).toBeInTheDocument();
    expect(screen.getByText("X-Api-Key")).toBeInTheDocument();
  });

  it("la guía muestra los casos en inglés con su request intacto", () => {
    fijarLocaleDePrueba("en");
    const { container } = render(<GuiaPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Guide to issuing invoices and receipts" })).toBeInTheDocument();
    const base = container.querySelector("#gravada-contado");
    expect(base?.querySelector("h3")).toHaveTextContent("Taxable cash sale (the base case)");
    expect(base).toHaveTextContent('"serie": "F001"');
    expect(screen.getAllByText("When:").length).toBeGreaterThan(1);
  });

  it("las tablas de errores y de entornos traducen la prosa y conservan los códigos", () => {
    fijarLocaleDePrueba("en");
    render(<ErroresPage />);
    expect(screen.getByRole("heading", { level: 1, name: "Errors and statuses" })).toBeInTheDocument();
    expect(screen.getByRole("row", { name: /IDEMPOTENCIA_INVALIDA 422/ })).toHaveTextContent("Use a new key (a UUID) per invoice or note");
    cleanup();

    render(<EntornosPage />);
    expect(screen.getByRole("heading", { level: 2, name: "Limits and quotas" })).toBeInTheDocument();
    expect(screen.getByRole("row", { name: /Items per document/ })).toHaveTextContent("422 ITEM_INVALIDO.");
  });

  it("el buscador encuentra en inglés y lleva a las mismas anclas", () => {
    fijarLocaleDePrueba("en");
    const indice = indexar(entradas(mensajesDePrueba()));

    expect(buscarEnLaDocumentacion("ERROR_ENVIO", indice)[0]).toMatchObject({ seccion: "Document status", href: "/developers/errores#estados" });
    expect(buscarEnLaDocumentacion("webhook signature", indice)[0].href).toBe("/developers/guia#webhooks");
    expect(buscarEnLaDocumentacion("IDEMPOTENCIA_INVALIDA", indice)[0].seccion).toBe("Error code · HTTP 422");
  });
});
