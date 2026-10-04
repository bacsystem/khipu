import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiEnvelope } from "@/lib/api/types";
import { CambiarEntorno, ProbarConexion, RevocarApiKey } from "./acciones-de-empresa";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const EMPRESA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const KEY = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

describe("CambiarEntorno (#187)", () => {
  it("una empresa en BETA ofrece pasar a producción: dice que emite de verdad, qué credenciales hacen falta y que no toca lo emitido", () => {
    render(<CambiarEntorno empresaId={EMPRESA} razonSocial="COMERCIAL ANDINA SAC" entorno="BETA" />);
    fireEvent.click(screen.getByTestId("cambiar-entorno"));

    const texto = screen.getByTestId("cambiar-entorno-dialogo").textContent ?? "";
    expect(texto).toContain("COMERCIAL ANDINA SAC pasa de BETA a PRODUCCIÓN");
    expect(texto).toContain("los comprobantes que emita valen de verdad");
    expect(texto).toContain("credenciales SOL y el certificado de producción");
    expect(texto).toContain("No toca los comprobantes ya emitidos");
    expect(texto).toContain("envíos pendientes");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("una empresa en PRODUCCION ofrece volver a beta: dice que lo que emita no tiene validez", () => {
    render(<CambiarEntorno empresaId={EMPRESA} razonSocial="COMERCIAL ANDINA SAC" entorno="PRODUCCION" />);
    fireEvent.click(screen.getByTestId("cambiar-entorno"));

    const texto = screen.getByTestId("cambiar-entorno-dialogo").textContent ?? "";
    expect(texto).toContain("pasa de PRODUCCIÓN a BETA");
    expect(texto).toContain("no tienen validez");
  });

  it.each([
    ["BETA", "PRODUCCION"],
    ["PRODUCCION", "BETA"],
  ] as const)("desde %s confirma pasando a %s: POST a la ruta de la empresa y recarga", async (desde, hacia) => {
    apiRequest.mockResolvedValue(exito({ empresa_id: EMPRESA, desde, hacia }));
    render(<CambiarEntorno empresaId={EMPRESA} razonSocial="X SAC" entorno={desde} />);
    fireEvent.click(screen.getByTestId("cambiar-entorno"));

    fireEvent.click(screen.getByTestId("cambiar-entorno-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/empresas/${EMPRESA}/entorno`, { method: "POST", body: { entorno: hacia } });
  });

  it("pasar a producción pesa más que volver a beta: el botón de confirmar es el destructivo solo en el primer caso", () => {
    const { unmount } = render(<CambiarEntorno empresaId={EMPRESA} razonSocial="X SAC" entorno="BETA" />);
    fireEvent.click(screen.getByTestId("cambiar-entorno"));
    const aProduccion = screen.getByTestId("cambiar-entorno-confirmar").className;
    unmount();
    cleanup();
    render(<CambiarEntorno empresaId={EMPRESA} razonSocial="X SAC" entorno="PRODUCCION" />);
    fireEvent.click(screen.getByTestId("cambiar-entorno"));

    expect(screen.getByTestId("cambiar-entorno-confirmar").className).not.toBe(aProduccion);
  });

  it("un rechazo del backend (envíos pendientes) se muestra en el diálogo, que sigue abierto, y no recarga", async () => {
    apiRequest.mockResolvedValue(error("EMPRESA_CON_ENVIOS_PENDIENTES", "La empresa tiene envíos pendientes a SUNAT"));
    render(<CambiarEntorno empresaId={EMPRESA} razonSocial="X SAC" entorno="BETA" />);
    fireEvent.click(screen.getByTestId("cambiar-entorno"));

    fireEvent.click(screen.getByTestId("cambiar-entorno-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("La empresa tiene envíos pendientes a SUNAT");
    expect(screen.getByTestId("cambiar-entorno-dialogo")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("si otro administrador ya la cambió, lo dice y recarga para mostrar el entorno real", async () => {
    apiRequest.mockResolvedValue(error("ENTORNO_SIN_CAMBIOS", "La empresa ya está en PRODUCCION"));
    render(<CambiarEntorno empresaId={EMPRESA} razonSocial="X SAC" entorno="BETA" />);
    fireEvent.click(screen.getByTestId("cambiar-entorno"));

    fireEvent.click(screen.getByTestId("cambiar-entorno-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("La empresa ya está en PRODUCCION");
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});

describe("RevocarApiKey (#187)", () => {
  function abrir() {
    render(<RevocarApiKey empresaId={EMPRESA} apiKeyId={KEY} prefijo="fk_sol0002" razonSocial="PANADERIA SOL SAC" />);
    fireEvent.click(screen.getByTestId(`revocar-api-key-${KEY}`));
  }

  it("dice cuál key, que deja de autenticar de inmediato, que las demás siguen y que no se deshace", () => {
    abrir();

    const texto = screen.getByTestId(`revocar-api-key-${KEY}-dialogo`).textContent ?? "";
    expect(texto).toContain("fk_sol0002… de PANADERIA SOL SAC");
    expect(texto).toContain("Deja de autenticar de inmediato");
    expect(texto).toContain("Las demás keys de la empresa siguen sirviendo");
    expect(texto).toContain("No se puede deshacer");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("confirmar hace POST a la ruta de esa key, sin cuerpo, y recarga", async () => {
    apiRequest.mockResolvedValue(exito({ api_key_id: KEY, revocada_en: "2026-10-03T09:00:00Z" }));
    abrir();

    fireEvent.click(screen.getByTestId(`revocar-api-key-${KEY}-confirmar`));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/empresas/${EMPRESA}/api-keys/${KEY}/revocar`, { method: "POST", body: undefined });
  });

  it("si ya estaba revocada lo dice y recarga para que la fila muestre su estado real", async () => {
    apiRequest.mockResolvedValue(error("API_KEY_YA_REVOCADA", "La API key ya estaba revocada"));
    abrir();

    fireEvent.click(screen.getByTestId(`revocar-api-key-${KEY}-confirmar`));

    expect((await screen.findByRole("alert")).textContent).toBe("La API key ya estaba revocada");
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});

describe("ProbarConexion (#187)", () => {
  const probar = () => fireEvent.click(screen.getByTestId("probar-conexion"));

  it("sin credenciales SOL el botón está deshabilitado y lo dice, sin llamar a nadie", () => {
    render(<ProbarConexion empresaId={EMPRESA} tieneSol={false} />);

    expect((screen.getByTestId("probar-conexion") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("Sin credenciales SOL no hay con qué probar.")).toBeTruthy();
    probar();
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("explica qué hace la prueba y que no diagnostica sola, antes de pulsarla", () => {
    render(<ProbarConexion empresaId={EMPRESA} tieneSol />);

    const nota = screen.getByText(/La prueba consulta a SUNAT un ticket que no existe/).textContent ?? "";
    expect(nota).toContain("es de solo lectura y no envía nada");
    expect(nota).toContain("un error de ticket es esperable");
    expect(screen.queryByTestId("resultado-conexion")).toBeNull();
  });

  it("SUNAT que contesta con normalidad: lo dice, con el entorno probado y sin código", async () => {
    apiRequest.mockResolvedValue(exito({ resultado: "CONECTADO", entorno: "BETA" }));
    render(<ProbarConexion empresaId={EMPRESA} tieneSol />);

    probar();

    const r = await screen.findByTestId("resultado-conexion");
    expect(r.getAttribute("data-resultado")).toBe("CONECTADO");
    expect(r.textContent).toContain("SUNAT contestó con normalidad");
    expect(r.textContent).toContain("Entorno probado: Beta");
    expect(r.textContent).not.toContain("Código");
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/empresas/${EMPRESA}/prueba-de-conexion`, { method: "POST" });
  });

  it("un error definitivo de SUNAT se muestra con su código y su mensaje, tal cual", async () => {
    apiRequest.mockResolvedValue(exito({ resultado: "RECHAZADO", entorno: "PRODUCCION", codigo: "1033", mensaje: "El ticket no existe" }));
    render(<ProbarConexion empresaId={EMPRESA} tieneSol />);

    probar();

    const r = await screen.findByTestId("resultado-conexion");
    expect(r.getAttribute("data-resultado")).toBe("RECHAZADO");
    expect(r.textContent).toContain("SUNAT contestó con un error");
    expect(r.textContent).toContain("1033");
    expect(r.textContent).toContain("El ticket no existe");
    expect(r.textContent).toContain("Entorno probado: Producción");
  });

  it("sin respuesta útil de SUNAT se distingue de un error de SUNAT", async () => {
    apiRequest.mockResolvedValue(exito({ resultado: "SIN_RESPUESTA", entorno: "BETA", codigo: "0109", mensaje: "Tiempo de espera agotado llamando a SUNAT" }));
    render(<ProbarConexion empresaId={EMPRESA} tieneSol />);

    probar();

    const r = await screen.findByTestId("resultado-conexion");
    expect(r.getAttribute("data-resultado")).toBe("SIN_RESPUESTA");
    expect(r.textContent).toContain("No hubo una respuesta útil de SUNAT");
  });

  it("los tres resultados se distinguen por su tono", async () => {
    const tonos: string[] = [];
    for (const resultado of ["CONECTADO", "RECHAZADO", "SIN_RESPUESTA"]) {
      apiRequest.mockResolvedValue(exito({ resultado, entorno: "BETA" }));
      render(<ProbarConexion empresaId={EMPRESA} tieneSol />);
      probar();
      const r = await screen.findByTestId("resultado-conexion");
      tonos.push(r.querySelector("span")?.className ?? "");
      cleanup();
    }

    expect(new Set(tonos).size).toBe(3);
  });

  it("un error del backend (p. ej. sin credenciales) se muestra, y no queda un resultado viejo", async () => {
    apiRequest.mockResolvedValueOnce(exito({ resultado: "CONECTADO", entorno: "BETA" })).mockResolvedValueOnce(error("SOL_NO_CARGADAS", "La empresa no tiene credenciales SOL cargadas"));
    render(<ProbarConexion empresaId={EMPRESA} tieneSol />);

    probar();
    await screen.findByTestId("resultado-conexion");
    probar();

    expect((await screen.findByRole("alert")).textContent).toBe("La empresa no tiene credenciales SOL cargadas");
    expect(screen.queryByTestId("resultado-conexion")).toBeNull();
  });

  /** Dos clics seguidos harían dos consultas a SUNAT y dos filas en la bitácora. */
  it("un doble clic hace una sola consulta, y mientras prueba el botón lo dice y no se puede volver a pulsar", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    render(<ProbarConexion empresaId={EMPRESA} tieneSol />);

    const boton = screen.getByTestId("probar-conexion");
    act(() => {
      fireEvent.click(boton);
      fireEvent.click(boton);
    });

    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(boton.textContent).toContain("Probando…");
    expect((boton as HTMLButtonElement).disabled).toBe(true);
    await act(async () => resolver(exito({ resultado: "CONECTADO", entorno: "BETA" })));
  });
});
