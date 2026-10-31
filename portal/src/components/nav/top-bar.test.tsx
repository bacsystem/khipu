import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const ruta = vi.hoisted(() => ({ actual: "/comprobantes" }));
vi.mock("next/navigation", () => ({ usePathname: () => ruta.actual }));
// Solo importa qué acción ofrece cada página: los diálogos se reemplazan por su botón.
vi.mock("./mobile-nav", () => ({ MobileNav: () => null }));
vi.mock("@/components/comprobantes/nuevo-comprobante-dialog", () => ({ NuevoComprobanteDialog: () => <button>Nuevo comprobante</button> }));
vi.mock("@/components/establecimientos/establecimiento-dialog", () => ({ EstablecimientoDialog: () => <button>Nuevo establecimiento</button> }));
vi.mock("@/components/guias/nueva-guia-enlace", () => ({ NuevaGuiaEnlace: () => <button>Nueva guía</button> }));
vi.mock("@/components/series/nueva-serie-dialog", () => ({ NuevaSerieDialog: () => <button>Nueva serie</button> }));
vi.mock("@/components/series/referencia-series", () => ({ ReferenciaSeriesDialog: () => null }));
vi.mock("@/components/api-keys/nueva-api-key-dialog", () => ({ NuevaApiKeyDialog: () => <button>Crear API key</button> }));
vi.mock("@/components/api-keys/referencia-api-keys", () => ({ ReferenciaApiKeysDialog: () => null }));
vi.mock("@/components/api-keys/ejemplo-integracion", () => ({ EjemploIntegracionDialog: () => null }));
vi.mock("@/components/empresa/nueva-empresa-dialog", () => ({ NuevaEmpresaDialog: () => <button>Nueva empresa</button> }));
vi.mock("@/components/empresa/referencia-empresa", () => ({ ReferenciaEmpresaDialog: () => null }));

import { TopBar } from "./top-bar";

function renderizar(pathname: string) {
  ruta.actual = pathname;
  render(
    <TopBar
      entorno="BETA"
      usuario={{ id: "u-1", email: "demo@example.com", nombre: "Demo" } as never}
      cuenta={null}
      empresas={[]}
      apiBaseUrl="http://localhost:8001"
    />,
  );
  return screen.queryAllByRole("button").map((b) => b.textContent);
}

/** #277: «Nuevo comprobante» es la acción de la sección de comprobantes, no de cualquier página sin acción propia. */
describe("TopBar: acción principal según la página", () => {
  afterEach(cleanup);

  it.each(["/comprobantes", "/comprobantes/f-123", "/comprobantes/f-123/nota"])("en %s ofrece «Nuevo comprobante»", (pathname) => {
    expect(renderizar(pathname)).toEqual(["Nuevo comprobante"]);
  });

  /** #24: el panel de inicio es adonde se llega al entrar, y emitir es lo que más se hace desde ahí (y lo que su estado vacío invita a hacer). */
  it("en el panel de inicio también ofrece «Nuevo comprobante», con su miga", () => {
    expect(renderizar("/inicio")).toEqual(["Nuevo comprobante"]);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe("Inicio");
  });

  it("en Establecimientos ofrece «Nuevo establecimiento», y solo esa", () => {
    expect(renderizar("/establecimientos")).toEqual(["Nuevo establecimiento"]);
  });

  /** #39: «Nueva guía» va en la barra superior como las demás acciones de sección (no suelta en el contenido), y no en el propio formulario. */
  it.each(["/guias", "/guias/g-123"])("en %s ofrece «Nueva guía»", (pathname) => {
    expect(renderizar(pathname)).toEqual(["Nueva guía"]);
  });

  it("en el formulario de «Nueva guía» no se repite", () => {
    expect(renderizar("/guias/nueva")).toEqual([]);
  });

  it.each(["/cuenta/plan", "/cuenta/accesos-de-soporte", "/developers"])("en %s no hay acción principal", (pathname) => {
    expect(renderizar(pathname)).toEqual([]);
  });

  it("series, API keys y empresa mantienen la suya", () => {
    expect(renderizar("/series")).toEqual(["Nueva serie"]);
    cleanup();
    expect(renderizar("/api-keys")).toEqual(["Crear API key"]);
    cleanup();
    expect(renderizar("/empresa")).toEqual(["Nueva empresa"]);
  });
});
