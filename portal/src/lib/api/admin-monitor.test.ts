import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import { obtenerMonitor, SERVICIOS_DE_SUNAT } from "./admin-monitor";

afterEach(() => vi.unstubAllGlobals());

const sobre = (datos: unknown, status = 200) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status });

describe("SERVICIOS_DE_SUNAT", () => {
  it("son los cuatro que sondea el backend", () => {
    expect([...SERVICIOS_DE_SUNAT]).toEqual(["ENVIO_PRODUCCION", "ENVIO_BETA", "CONSULTA_DE_CDR", "CONSULTA_DE_VALIDEZ"]);
  });
});

describe("obtenerMonitor", () => {
  const MONITOR = { generado_en: "2026-10-15T15:20:00Z", horas: [], hoy: { desde: "2026-10-15T05:00:00Z" }, outbox: { pendientes: 0, vencidos: 0, alerta: false }, sunat: [] };

  it("pide un GET al monitor con el JWT del administrador y devuelve la lectura", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre(MONITOR));
    vi.stubGlobal("fetch", fetchMock);

    const r = await obtenerMonitor("jwt-admin");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/monitor`);
    expect(init.method ?? "GET").toBe("GET");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual(MONITOR);
  });

  it("un rechazo del backend sube con su status y su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "no", codigo: "NO_AUTORIZADO", errores: null }), { status: 401 })));

    await expect(obtenerMonitor("jwt-vencido")).rejects.toMatchObject({ status: 401, codigo: "NO_AUTORIZADO" });
  });
});
