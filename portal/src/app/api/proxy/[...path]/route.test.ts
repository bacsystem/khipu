import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ACCESS, COOKIE_EMPRESA, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({
  refrescar: vi.fn(),
}));

import { refrescar } from "@/lib/api/auth";
import { GET, POST } from "./route";

function ctx(path: string[]) {
  return { params: Promise.resolve({ path }) };
}

type SessionInit = {
  method?: string;
  body?: string;
  headers?: Record<string, string>;
  access?: string;
  refresh?: string;
  empresa?: string;
};

function requestWithSession(url: string, init: SessionInit = {}) {
  const { access, refresh, empresa, method, body, headers } = init;
  const cookie = [
    access !== undefined ? `${COOKIE_ACCESS}=${access}` : null,
    refresh !== undefined ? `${COOKIE_REFRESH}=${refresh}` : null,
    empresa !== undefined ? `${COOKIE_EMPRESA}=${empresa}` : null,
  ]
    .filter(Boolean)
    .join("; ");
  return new NextRequest(url, { method, body, headers: { ...headers, cookie } });
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.mocked(refrescar).mockReset();
});

describe("proxy /api/proxy/[...path]", () => {
  it("rechaza un segmento .. sin llegar a hacer fetch", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const req = requestWithSession("http://localhost/api/proxy/empresas", {
      method: "GET",
      access: "a1",
    });

    const res = await GET(req, ctx(["empresas", "..", "admin", "tenants"]));

    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("reenvía con Authorization y X-Empresa al backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const req = requestWithSession("http://localhost/api/proxy/empresas", {
      method: "GET",
      access: "a1",
      refresh: "r1",
      empresa: "e1",
    });

    const res = await GET(req, ctx(["empresas"]));

    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8001/v1/empresas");
    expect(init.headers.get("Authorization")).toBe("Bearer a1");
    expect(init.headers.get("X-Empresa")).toBe("e1");
  });

  it("ante un 401 renueva con el refresh y reintenta una vez", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(new Response('{"estado":"exito","datos":[]}', { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    vi.mocked(refrescar).mockResolvedValue({
      access: "a2",
      refresh: "r2",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin" },
    });

    const req = requestWithSession("http://localhost/api/proxy/empresas", {
      method: "GET",
      access: "a1",
      refresh: "r1",
    });

    const res = await GET(req, ctx(["empresas"]));

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][1].headers.get("Authorization")).toBe("Bearer a2");
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a2");
    expect(res.status).toBe(200);
  });

  it("si el refresh también falla, limpia la sesión y deja pasar el 401", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);
    vi.mocked(refrescar).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Refresh inválido"));

    const req = requestWithSession("http://localhost/api/proxy/empresas", {
      method: "GET",
      access: "a1",
      refresh: "r1",
    });

    const res = await GET(req, ctx(["empresas"]));

    expect(res.status).toBe(401);
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("");
  });

  it("ante 401 concurrentes con el mismo refresh, solo llama a refrescar una vez", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(new Response('{"estado":"exito","datos":[]}', { status: 200 }))
      .mockResolvedValueOnce(new Response('{"estado":"exito","datos":[]}', { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    vi.mocked(refrescar).mockResolvedValue({
      access: "a2",
      refresh: "r2",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin" },
    });

    const req1 = requestWithSession("http://localhost/api/proxy/empresas", { method: "GET", access: "a1", refresh: "r1" });
    const req2 = requestWithSession("http://localhost/api/proxy/facturas", { method: "GET", access: "a1", refresh: "r1" });

    const [res1, res2] = await Promise.all([GET(req1, ctx(["empresas"])), GET(req2, ctx(["facturas"]))]);

    expect(refrescar).toHaveBeenCalledTimes(1);
    expect(res1.status).toBe(200);
    expect(res2.status).toBe(200);
  });

  // El módulo es único para todo el proceso: si el refresco en curso se compartiera sin mirar de
  // quién es, al segundo usuario se le escribirían las cookies del primero y quedaría dentro de la
  // sesión ajena. Los dos refrescos se solapan a propósito (el mock tarda), que es cuando pasa.
  it("ante 401 concurrentes de dos sesiones distintas, cada una recibe sus propios tokens", async () => {
    const fetchMock = vi.fn(async (_url: string, init: RequestInit) => {
      const auth = new Headers(init.headers as HeadersInit).get("Authorization");
      return auth?.startsWith("Bearer nuevo-")
        ? new Response('{"estado":"exito"}', { status: 200 })
        : new Response("{}", { status: 401 });
    });
    vi.stubGlobal("fetch", fetchMock);
    vi.mocked(refrescar).mockImplementation(async (refresh: string) => {
      await new Promise((resolve) => setTimeout(resolve, 5));
      return {
        access: `nuevo-${refresh}`,
        refresh: `rotado-${refresh}`,
        usuario: { id: refresh, cuenta_id: `c-${refresh}`, email: `${refresh}@b.com`, rol: "admin" },
      };
    });

    const ana = requestWithSession("http://localhost/api/proxy/empresas", {
      method: "GET",
      access: "viejo-ana",
      refresh: "r-ana",
    });
    const beto = requestWithSession("http://localhost/api/proxy/empresas", {
      method: "GET",
      access: "viejo-beto",
      refresh: "r-beto",
    });

    const [resAna, resBeto] = await Promise.all([GET(ana, ctx(["empresas"])), GET(beto, ctx(["empresas"]))]);

    expect(refrescar).toHaveBeenCalledTimes(2);
    expect(resAna.cookies.get(COOKIE_ACCESS)?.value).toBe("nuevo-r-ana");
    expect(resAna.cookies.get(COOKIE_REFRESH)?.value).toBe("rotado-r-ana");
    expect(resBeto.cookies.get(COOKIE_ACCESS)?.value).toBe("nuevo-r-beto");
    expect(resBeto.cookies.get(COOKIE_REFRESH)?.value).toBe("rotado-r-beto");
  });

  it("reenvía el body en POST", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);

    const req = requestWithSession("http://localhost/api/proxy/empresas", {
      method: "POST",
      access: "a1",
      body: JSON.stringify({ ruc: "12345678901" }),
      headers: { "content-type": "application/json" },
    });

    await POST(req, ctx(["empresas"]));

    const [, init] = fetchMock.mock.calls[0];
    expect(new TextDecoder().decode(init.body)).toBe(JSON.stringify({ ruc: "12345678901" }));
  });
});
