import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import { listarPagosDeCuenta, MEDIOS_DE_PAGO, registrarPago } from "./admin-pagos";

afterEach(() => vi.unstubAllGlobals());

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const sobre = (datos: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status, headers });

describe("MEDIOS_DE_PAGO", () => {
  it("son los siete que conoce el backend, en el orden en que se ofrecen", () => {
    expect([...MEDIOS_DE_PAGO]).toEqual(["TRANSFERENCIA", "DEPOSITO", "YAPE", "PLIN", "TARJETA", "EFECTIVO", "OTRO"]);
  });
});

describe("listarPagosDeCuenta", () => {
  it("pide la primera página con el JWT y devuelve los pagos con el total de la cabecera", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre([{ id: "p1" }, { id: "p2" }], 200, { "X-Total-Count": "37" }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await listarPagosDeCuenta("jwt-admin", CUENTA);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/cuentas/${CUENTA}/pagos?pagina=1&por_pagina=10`);
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual({ datos: [{ id: "p1" }, { id: "p2" }], total: 37 });
  });

  it("acepta otro tamaño de página", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre([]));
    vi.stubGlobal("fetch", fetchMock);

    await listarPagosDeCuenta("jwt-admin", CUENTA, 25);

    expect(fetchMock.mock.calls[0][0]).toContain("por_pagina=25");
  });

  it("sin la cabecera del total cuenta los pagos que llegaron", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sobre([{ id: "p1" }])));

    expect((await listarPagosDeCuenta("jwt-admin", CUENTA)).total).toBe(1);
  });

  it("si el backend falla, el error sube para que la página lo muestre", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "NO_ENCONTRADO", errores: null }), { status: 404 })));

    await expect(listarPagosDeCuenta("jwt-admin", CUENTA)).rejects.toMatchObject({ status: 404, codigo: "NO_ENCONTRADO" });
  });
});

describe("registrarPago", () => {
  const CUERPO = { periodo_desde: "2026-10-01", periodo_hasta: "2026-10-31", monto: 29, medio: "YAPE", fecha_de_pago: "2026-10-14", extender_vencimiento: false } as const;

  it("manda un POST con el cuerpo, el JWT y la IP de origen ya resuelta, y devuelve el pago", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre({ id: "p1" }, 201));
    vi.stubGlobal("fetch", fetchMock);

    const p = await registrarPago("jwt-admin", CUENTA, CUERPO, { "X-Forwarded-For": "203.0.113.7" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/cuentas/${CUENTA}/pagos`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual(CUERPO);
    const h = new Headers(init.headers);
    expect(h.get("Authorization")).toBe("Bearer jwt-admin");
    expect(h.get("X-Forwarded-For")).toBe("203.0.113.7");
    expect(h.get("Content-Type")).toBe("application/json");
    expect(p).toEqual({ id: "p1" });
  });

  it("sin IP de origen no manda cabecera de IP", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre({}, 201));
    vi.stubGlobal("fetch", fetchMock);

    await registrarPago("jwt-admin", CUENTA, CUERPO);

    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("X-Forwarded-For")).toBeNull();
  });

  it("un rechazo del backend sube con su status y su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "ya existe", codigo: "PAGO_DUPLICADO", errores: null }), { status: 409 })));

    await expect(registrarPago("jwt-admin", CUENTA, CUERPO)).rejects.toMatchObject({ status: 409, codigo: "PAGO_DUPLICADO" });
  });
});
