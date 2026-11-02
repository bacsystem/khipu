import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import type { ApiEnvelope } from "@/lib/api/types";
import { CredencialesApiSunatForm } from "./credenciales-api-sunat-form";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const RUTA = "/api/proxy/empresa/credenciales-api-sunat";
const exito = (): ApiEnvelope<unknown> => ({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

function llenar(id: string, secreto: string) {
  fireEvent.change(screen.getByLabelText("Id (client_id)"), { target: { value: id } });
  fireEvent.change(screen.getByLabelText("Clave (client_secret)"), { target: { value: secreto } });
}

describe("CredencialesApiSunatForm (#318)", () => {
  it("guarda el client_id sin espacios al borde y el secreto tal cual, limpia los campos y refresca", async () => {
    apiRequest.mockResolvedValue(exito());
    render(<CredencialesApiSunatForm configuradas={false} />);
    llenar("  d20b421e-4ace-46d8-ac8c-4fbb44cfde17 ", " HhWw/xEzw== ");
    fireEvent.click(screen.getByRole("button", { name: /Guardar credenciales/ }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith(RUTA, { method: "PUT", body: { client_id: "d20b421e-4ace-46d8-ac8c-4fbb44cfde17", client_secret: " HhWw/xEzw== " } });
    expect(screen.getByText("Credenciales API SUNAT actualizadas.")).toBeTruthy();
    expect((screen.getByLabelText("Clave (client_secret)") as HTMLInputElement).value).toBe("");
  });

  it("el secreto va oculto y nunca se muestra lo guardado", () => {
    render(<CredencialesApiSunatForm configuradas />);
    const secreto = screen.getByLabelText("Clave (client_secret)") as HTMLInputElement;
    expect(secreto.type).toBe("password");
    expect(secreto.value).toBe("");
    expect(secreto.placeholder).toContain("no se muestra");
    expect(screen.getByRole("button", { name: /Reemplazar credenciales/ })).toBeTruthy();
  });

  it("muestra el error del backend", async () => {
    apiRequest.mockResolvedValue({ estado: "error", datos: null, mensaje: "El client_id de la API SUNAT no admite espacios", codigo: "CREDENCIALES_API_SUNAT_INVALIDAS", errores: null });
    render(<CredencialesApiSunatForm configuradas={false} />);
    llenar("a b", "x");
    fireEvent.click(screen.getByRole("button", { name: /Guardar credenciales/ }));

    expect(await screen.findByText("El client_id de la API SUNAT no admite espacios")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("solo con credenciales guardadas ofrece quitarlas, y quitarlas es un DELETE", async () => {
    const { unmount } = render(<CredencialesApiSunatForm configuradas={false} />);
    expect(screen.queryByRole("button", { name: /Quitar credenciales/ })).toBeNull();
    unmount();

    apiRequest.mockResolvedValue(exito());
    render(<CredencialesApiSunatForm configuradas />);
    fireEvent.click(screen.getByRole("button", { name: /Quitar credenciales/ }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith(RUTA, { method: "DELETE" });
    expect(screen.getByText(/la validez se consulta con la clave SOL/)).toBeTruthy();
  });
});

describe("CredencialesApiSunatForm en inglés (#25)", () => {
  it("muestra etiquetas, botones y el aviso de éxito en inglés", async () => {
    fijarLocaleDePrueba("en");
    apiRequest.mockResolvedValue(exito());
    render(<CredencialesApiSunatForm configuradas />);
    expect(screen.getByLabelText("Key (client_secret)")).toHaveAttribute("placeholder", "••••••••  (saved, not shown)");
    fireEvent.click(screen.getByRole("button", { name: "Remove credentials" }));
    expect(await screen.findByText("SUNAT API credentials removed: validity is checked with the SOL password.")).toBeInTheDocument();
  });
});
