import type { ReactElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/session-server", () => ({ getServerSession: vi.fn(async () => ({ access: null, empresaId: null })) }));
vi.mock("@/lib/api/client", () => ({
  apiBaseUrl: () => "http://khipu-backend.railway.internal:8001",
  apiPublicUrl: () => "https://api.ejemplo.pe",
}));
vi.mock("@/components/developers/developers-view", () => ({ DevelopersView: () => null }));

import DevelopersPage from "./page";

afterEach(() => {
  vi.unstubAllGlobals();
});

/** #678: el spec se lee por la URL interna; lo que se entrega al navegador tiene que traer solo la pública. */
describe("DevelopersPage", () => {
  it("entrega a la referencia el spec con la URL pública en servers, sin la interna", async () => {
    const spec = { openapi: "3.0.1", servers: [{ url: "http://khipu-backend.railway.internal:8001" }], paths: {} };
    const fetchSpec = vi.fn(async () => ({ ok: true, json: async () => spec }));
    vi.stubGlobal("fetch", fetchSpec);

    const pagina = (await DevelopersPage()) as ReactElement<{ spec: Record<string, unknown>; baseServerURL: string }>;

    expect(fetchSpec).toHaveBeenCalledWith("http://khipu-backend.railway.internal:8001/openapi.json", { cache: "no-store" });
    expect(pagina.props.spec.servers).toEqual([{ url: "https://api.ejemplo.pe" }]);
    expect(JSON.stringify(pagina.props.spec)).not.toContain("railway.internal");
    expect(pagina.props.baseServerURL).toBe("https://api.ejemplo.pe");
  });
});
