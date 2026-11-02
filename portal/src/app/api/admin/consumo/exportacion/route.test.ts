import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-consumo", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/api/admin-consumo")>()), exportarConsumoDeCuentas: vi.fn() }));

import { exportarConsumoDeCuentas } from "@/lib/api/admin-consumo";
import { GET } from "./route";

function peticion(init: { sesion?: boolean; query?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  return new NextRequest(`http://localhost/api/admin/consumo/exportacion${init.query ?? ""}`, { headers });
}

const bytes = (texto: string) => new TextEncoder().encode(texto).buffer as ArrayBuffer;

afterEach(() => vi.mocked(exportarConsumoDeCuentas).mockReset());

/** BFF de la exportación del consumo (#193): el CSV se descarga con la sesión del administrador, que sale de su cookie `httpOnly`. */
describe("GET /api/admin/consumo/exportacion", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await GET(peticion({ sesion: false }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(exportarConsumoDeCuentas).not.toHaveBeenCalled();
  });

  it("un mes, filtro u orden mal escritos responden 400 sin llamar al backend: no se exporta otra cosa que lo pedido", async () => {
    for (const query of ["?mes=2026-13", "?mes=octubre", "?mes=", "?filtro=TODOS", "?filtro=", "?orden=NOMBRE", "?orden=documentos"]) {
      const res = await GET(peticion({ query }));
      expect(res.status, query).toBe(400);
      expect((await res.json()).codigo, query).toBe("PARAMETRO_INVALIDO");
    }
    expect(exportarConsumoDeCuentas).not.toHaveBeenCalled();
  });

  it("pide la exportación con el JWT, el mes, el filtro y el orden; sin ellos, lo que se ve por defecto", async () => {
    vi.mocked(exportarConsumoDeCuentas).mockResolvedValue({ cuerpo: bytes("x"), disposicion: 'attachment; filename="consumo-2026-08.csv"' });

    await GET(peticion({ query: "?mes=2026-08&filtro=PLAN_VENCIDO&orden=DOCUMENTOS&pagina=4&por_pagina=50" }));
    await GET(peticion());

    expect(exportarConsumoDeCuentas).toHaveBeenNthCalledWith(1, "jwt-admin", { mes: "2026-08", filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: 1, porPagina: 10 });
    expect(exportarConsumoDeCuentas).toHaveBeenNthCalledWith(2, "jwt-admin", { mes: undefined, filtro: "TODAS", orden: "PORCENTAJE", pagina: 1, porPagina: 10 });
  });

  /** La marca de orden de bytes del principio es lo que hace que Excel lea las tildes: pasa tal cual, sin decodificar y recodificar. */
  it("entrega los bytes del CSV intactos, con su nombre de archivo, como descarga y sin caché", async () => {
    vi.mocked(exportarConsumoDeCuentas).mockResolvedValue({ cuerpo: bytes("﻿cuenta_id,cuenta\r\nc1,Pérez\r\n"), disposicion: 'attachment; filename="consumo-2026-08.csv"' });

    const res = await GET(peticion({ query: "?mes=2026-08" }));

    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("text/csv; charset=utf-8");
    expect(res.headers.get("content-disposition")).toBe('attachment; filename="consumo-2026-08.csv"');
    expect(res.headers.get("cache-control")).toContain("no-store");
    expect(new Uint8Array(await res.arrayBuffer()).slice(0, 3)).toEqual(new Uint8Array([0xef, 0xbb, 0xbf]));
  });

  it("si el backend no manda nombre de archivo, usa uno genérico", async () => {
    vi.mocked(exportarConsumoDeCuentas).mockResolvedValue({ cuerpo: bytes("x"), disposicion: null });

    const res = await GET(peticion());

    expect(res.headers.get("content-disposition")).toBe('attachment; filename="consumo.csv"');
  });

  it("propaga el status y el código del backend", async () => {
    vi.mocked(exportarConsumoDeCuentas).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Sesión vencida"));

    const res = await GET(peticion());

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
  });
});
