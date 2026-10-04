import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { MotivoDeAviso, TipoDeAviso } from "@/lib/api/admin-avisos";
import type { ApiEnvelope } from "@/lib/api/types";
import { AvisarAlCliente } from "./avisar-al-cliente";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const ID = "00000000-0000-4000-9000-000000000001";
const RAZON = "PANADERIA SOL SAC";
const CORREO = "panaderia@sol.pe";

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

function montar(p: { tipo?: TipoDeAviso; motivo?: MotivoDeAviso; alResultado?: (m: string) => void } = {}) {
  const alResultado = p.alResultado ?? vi.fn();
  render(<AvisarAlCliente empresaId={ID} razonSocial={RAZON} correo={CORREO} tipo={p.tipo ?? "CERTIFICADO"} motivo={p.motivo ?? "CERTIFICADO_POR_VENCER"} alResultado={alResultado} />);
  fireEvent.click(screen.getByTestId("avisos-avisar"));
  return alResultado;
}

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

describe("AvisarAlCliente (#197)", () => {
  it("el diálogo dice a quién le llega el correo, qué le dice, que no se repite en una semana y que queda en la bitácora, y no pide nada hasta confirmar", () => {
    montar();

    const texto = screen.getByTestId("avisos-avisar-dialogo").textContent ?? "";
    expect(texto).toContain(`Le manda a ${RAZON} un correo de aviso`);
    expect(texto).toContain(`Le manda un correo a ${CORREO}: que su certificado digital está por vencer.`);
    expect(texto).toContain("no se repite en una semana");
    expect(texto).toContain("en la bitácora, sin el correo del cliente");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it.each([
    ["CERTIFICADO_VENCIDO", "que su certificado digital venció y no puede emitir"],
    ["CREDENCIALES_SOL_INVALIDAS", "que SUNAT no acepta sus credenciales SOL"],
  ] as const)("para %s el diálogo dice lo que de verdad se le va a decir", (motivo, frase) => {
    montar({ motivo });

    expect(screen.getByTestId("avisos-avisar-dialogo").textContent).toContain(`${CORREO}: ${frase}.`);
  });

  it.each(["CERTIFICADO", "CREDENCIALES_SOL"] as const)("confirmar hace un POST a los avisos de esa empresa con el tipo %s, recarga y cuenta a quién se le avisó", async (tipo) => {
    apiRequest.mockResolvedValue(exito({ empresa_id: ID, motivo: "CERTIFICADO_POR_VENCER", destinatario: CORREO, enviado_en: "x", avisar_desde: "y" }));
    const alResultado = montar({ tipo });

    fireEvent.click(screen.getByTestId("avisos-avisar-confirmar"));

    await waitFor(() => expect(alResultado).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/empresas/${ID}/avisos`, { method: "POST", body: { tipo } });
    expect(alResultado).toHaveBeenCalledWith(`Se le avisó a ${CORREO} (${RAZON}).`);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it.each(["AVISO_RECIENTE", "AVISO_SIN_MOTIVO", "NO_ENCONTRADO"])("un %s dice que el estado cambió, deja el diálogo abierto y recarga con el estado real", async (codigo) => {
    apiRequest.mockResolvedValue(error(codigo, "Ya se avisó lo mismo"));
    const alResultado = montar();

    fireEvent.click(screen.getByTestId("avisos-avisar-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("Ya se avisó lo mismo");
    expect(refresh).toHaveBeenCalledTimes(1);
    // La recarga puede sacar la fila y con ella el diálogo: el mensaje también va donde sobrevive.
    expect(alResultado).toHaveBeenCalledExactlyOnceWith(`${RAZON}: Ya se avisó lo mismo`);
  });

  it.each(["CORREO_NO_ENVIADO", "CORREO_NO_CONFIGURADO", "EMPRESA_SIN_CUENTA"])("un %s se muestra pero no recarga ni da el aviso por hecho", async (codigo) => {
    apiRequest.mockResolvedValue(error(codigo, "No salió"));
    const alResultado = montar();

    fireEvent.click(screen.getByTestId("avisos-avisar-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("No salió");
    expect(refresh).not.toHaveBeenCalled();
    expect(alResultado).not.toHaveBeenCalled();
  });
});
