import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { NuevaApiKeyDialog } from "./nueva-api-key-dialog";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
const apiRequest = vi.fn();
vi.mock("@/lib/api/browser", () => ({ apiRequest: (...a: unknown[]) => apiRequest(...a) }));

afterEach(cleanup);
beforeEach(() => apiRequest.mockReset());

const DEL_SERVIDOR = "Tu plan Gratis incluye 1 API key y ya tienes 1 API key activa. Revoca una que ya no uses, o pide un cambio de plan";

async function generar() {
  fireEvent.click(screen.getByRole("button", { name: "Crear API key" }));
  fireEvent.click(await screen.findByRole("button", { name: "Generar llave" }));
}

describe("NuevaApiKeyDialog (#498)", () => {
  it("si el plan ya no deja más API keys muestra lo que dice el backend: qué incluye el plan y cómo liberar un lugar", async () => {
    apiRequest.mockResolvedValue({ estado: "error", codigo: "CUPO_DEL_PLAN", datos: null, mensaje: DEL_SERVIDOR, errores: null });
    render(<NuevaApiKeyDialog />);

    await generar();

    expect(await screen.findByText(DEL_SERVIDOR)).toBeTruthy();
    expect(screen.queryByText("Listo, la guardé")).toBeNull();
  });

  it("en inglés muestra el texto genérico del catálogo, no el del servidor en español", async () => {
    fijarLocaleDePrueba("en");
    apiRequest.mockResolvedValue({ estado: "error", codigo: "CUPO_DEL_PLAN", datos: null, mensaje: DEL_SERVIDOR, errores: null });
    render(<NuevaApiKeyDialog />);

    fireEvent.click(screen.getByRole("button", { name: "Create API key" }));
    fireEvent.click(await screen.findByRole("button", { name: /Generate key/i }));

    await waitFor(() => expect(screen.getByText("Your plan does not include more of this. Free up a spot or ask for a plan change.")).toBeTruthy());
    expect(screen.queryByText(DEL_SERVIDOR)).toBeNull();
  });
});
