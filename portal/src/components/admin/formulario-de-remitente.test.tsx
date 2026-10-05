import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { RemitenteConfigurado } from "@/lib/api/admin-configuracion";
import type { ApiEnvelope } from "@/lib/api/types";
import { direccionEnPalabras, FormularioDeRemitente } from "./formulario-de-remitente";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const DEL_SERVIDOR: RemitenteConfigurado = { vigente: { email: "no-responder@khipu.pe" }, personalizado: false, predeterminado: { email: "no-responder@khipu.pe" } };
const PERSONALIZADO: RemitenteConfigurado = {
  vigente: { nombre: "khipu", email: "avisos@khipu.pe", responder_a: "soporte@khipu.pe" },
  personalizado: true,
  actualizado_en: "2026-10-15T20:00:00Z",
  predeterminado: { email: "no-responder@khipu.pe" },
};

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string | null, mensaje: string | null): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const campo = (id: string) => screen.getByTestId(id) as HTMLInputElement;
const alResultado = vi.fn();

afterEach(() => {
  cleanup();
  refresh.mockClear();
  alResultado.mockReset();
  apiRequest.mockReset();
});

describe("direccionEnPalabras", () => {
  it("con nombre dice «nombre <correo>» y sin nombre solo el correo", () => {
    expect(direccionEnPalabras({ nombre: "khipu", email: "avisos@khipu.pe" })).toBe("khipu <avisos@khipu.pe>");
    expect(direccionEnPalabras({ email: "avisos@khipu.pe" })).toBe("avisos@khipu.pe");
  });
});

describe("FormularioDeRemitente (#199)", () => {
  it("dice con qué remitente salen hoy los correos y de dónde viene", () => {
    render(<FormularioDeRemitente remitente={PERSONALIZADO} alResultado={alResultado} />);

    expect(screen.getByTestId("correo-vigente").textContent).toBe("Hoy los correos salen de: khipu <avisos@khipu.pe>");
    expect(screen.getByTestId("correo-origen").textContent).toBe("Lo fijó un administrador el 15 Oct 2026, 15:00.");
  });

  it("sin remitente propio dice que es el del servidor", () => {
    render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

    expect(screen.getByTestId("correo-vigente").textContent).toBe("Hoy los correos salen de: no-responder@khipu.pe");
    expect(screen.getByTestId("correo-origen").textContent).toBe("Es el de la configuración del servidor: nadie lo ha cambiado desde acá.");
  });

  it("los campos arrancan con el remitente vigente", () => {
    render(<FormularioDeRemitente remitente={PERSONALIZADO} alResultado={alResultado} />);

    expect(campo("correo-nombre").value).toBe("khipu");
    expect(campo("correo-email").value).toBe("avisos@khipu.pe");
    expect(campo("correo-responder-a").value).toBe("soporte@khipu.pe");
  });

  it("sin nombre ni respuestas los campos opcionales arrancan vacíos", () => {
    render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

    expect(campo("correo-nombre").value).toBe("");
    expect(campo("correo-responder-a").value).toBe("");
  });

  it("avisa que el servidor de correo tiene que aceptar esa dirección", () => {
    render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

    expect(screen.getByText(/tiene que aceptar mandar desde esa dirección/)).toBeTruthy();
  });

  it("los límites de los campos son los del backend", () => {
    render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

    expect(campo("correo-nombre").maxLength).toBe(100);
    expect(campo("correo-email").type).toBe("email");
    expect(campo("correo-responder-a").type).toBe("email");
  });

  describe("guardar", () => {
    it("manda un PUT con los tres campos tal cual y dice que quedó guardado, recargando la página", async () => {
      apiRequest.mockResolvedValue(exito(PERSONALIZADO));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);
      fireEvent.change(campo("correo-nombre"), { target: { value: "  khipu " } });
      fireEvent.change(campo("correo-email"), { target: { value: "avisos@khipu.pe" } });
      fireEvent.change(campo("correo-responder-a"), { target: { value: "soporte@khipu.pe" } });

      fireEvent.click(screen.getByTestId("correo-guardar"));

      await waitFor(() => expect(alResultado).toHaveBeenLastCalledWith("Remitente guardado: vale desde el siguiente correo."));
      expect(apiRequest).toHaveBeenCalledExactlyOnceWith("/api/admin/configuracion/correo", { method: "PUT", body: { nombre: "  khipu ", email: "avisos@khipu.pe", responder_a: "soporte@khipu.pe" } });
      expect(refresh).toHaveBeenCalledTimes(1);
    });

    it("lo que el backend rechaza se muestra tal cual, sin decir que se guardó ni recargar", async () => {
      apiRequest.mockResolvedValue(error("REMITENTE_INVALIDO", "El correo del remitente no es una dirección de correo válida"));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

      fireEvent.click(screen.getByTestId("correo-guardar"));

      expect((await screen.findByRole("alert")).textContent).toBe("El correo del remitente no es una dirección de correo válida");
      expect(alResultado).not.toHaveBeenCalledWith(expect.stringContaining("guardado"));
      expect(refresh).not.toHaveBeenCalled();
    });

    it("un corte de red no se reintenta a ciegas: dice que se recargue para ver el estado real", async () => {
      apiRequest.mockResolvedValue(error("RED", "No se pudo conectar"));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

      fireEvent.click(screen.getByTestId("correo-guardar"));

      expect((await screen.findByRole("alert")).textContent).toBe("No se pudo conectar Recarga la página para ver el estado real.");
      expect(apiRequest).toHaveBeenCalledTimes(1);
    });

    it("una respuesta ilegible tampoco se reintenta", async () => {
      apiRequest.mockResolvedValue(error("RESPUESTA_INVALIDA", null));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

      fireEvent.click(screen.getByTestId("correo-guardar"));

      expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página para ver el estado real.");
    });

    it("dos clics en el mismo instante mandan un solo pedido", async () => {
      let responder: (v: ApiEnvelope<unknown>) => void = () => {};
      apiRequest.mockReturnValue(new Promise((r) => (responder = r)));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);
      const boton = screen.getByTestId("correo-guardar");

      act(() => {
        fireEvent.click(boton);
        fireEvent.click(boton);
      });

      expect(apiRequest).toHaveBeenCalledTimes(1);
      expect((boton as HTMLButtonElement).disabled).toBe(true);
      expect(boton.textContent).toContain("Guardando…");
      await act(async () => responder(exito(PERSONALIZADO)));
    });

    it("al volver a escribir pide que se deje de decir que se guardó: ya no es lo guardado", async () => {
      apiRequest.mockResolvedValue(exito(PERSONALIZADO));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);
      fireEvent.click(screen.getByTestId("correo-guardar"));
      await waitFor(() => expect(alResultado).toHaveBeenLastCalledWith("Remitente guardado: vale desde el siguiente correo."));

      fireEvent.change(campo("correo-nombre"), { target: { value: "otro" } });

      expect(alResultado).toHaveBeenLastCalledWith(null);
    });

    it("al empezar a guardar borra el resultado anterior, antes de saber cómo termina", async () => {
      let responder: (v: ApiEnvelope<unknown>) => void = () => {};
      apiRequest.mockReturnValue(new Promise((r) => (responder = r)));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);

      fireEvent.click(screen.getByTestId("correo-guardar"));

      expect(alResultado).toHaveBeenLastCalledWith(null);
      await act(async () => responder(exito(PERSONALIZADO)));
    });

    it("un error anterior se borra al volver a guardar", async () => {
      apiRequest.mockResolvedValueOnce(error("REMITENTE_INVALIDO", "Mal")).mockResolvedValueOnce(exito(PERSONALIZADO));
      render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);
      fireEvent.click(screen.getByTestId("correo-guardar"));
      await screen.findByRole("alert");

      fireEvent.click(screen.getByTestId("correo-guardar"));

      await waitFor(() => expect(alResultado).toHaveBeenLastCalledWith("Remitente guardado: vale desde el siguiente correo."));
      expect(screen.queryByRole("alert")).toBeNull();
    });
  });

  describe("volver al del servidor", () => {
    it("solo se ofrece si hay un remitente propio", () => {
      const { unmount } = render(<FormularioDeRemitente remitente={DEL_SERVIDOR} alResultado={alResultado} />);
      expect(screen.queryByTestId("correo-restablecer")).toBeNull();
      unmount();

      render(<FormularioDeRemitente remitente={PERSONALIZADO} alResultado={alResultado} />);
      expect(screen.getByTestId("correo-restablecer")).toBeTruthy();
    });

    it("el diálogo dice a cuál se vuelve y que queda en la bitácora, y no pide nada hasta confirmar", () => {
      render(<FormularioDeRemitente remitente={PERSONALIZADO} alResultado={alResultado} />);

      fireEvent.click(screen.getByTestId("correo-restablecer"));

      const texto = screen.getByTestId("correo-restablecer-dialogo").textContent ?? "";
      expect(texto).toContain("vuelven a salir de no-responder@khipu.pe");
      expect(texto).toContain("No cambia los textos de los correos");
      expect(texto).toContain("Queda en la bitácora");
      expect(apiRequest).not.toHaveBeenCalled();
    });

    it("confirmar manda un DELETE y recarga", async () => {
      apiRequest.mockResolvedValue(exito(DEL_SERVIDOR));
      render(<FormularioDeRemitente remitente={PERSONALIZADO} alResultado={alResultado} />);
      fireEvent.click(screen.getByTestId("correo-restablecer"));

      fireEvent.click(screen.getByTestId("correo-restablecer-confirmar"));

      await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
      expect(apiRequest).toHaveBeenCalledWith("/api/admin/configuracion/correo", { method: "DELETE", body: undefined });
    });

    it("el predeterminado con nombre se dice con su nombre", () => {
      render(<FormularioDeRemitente remitente={{ ...PERSONALIZADO, predeterminado: { nombre: "Servidor", email: "s@khipu.pe" } }} alResultado={alResultado} />);
      fireEvent.click(screen.getByTestId("correo-restablecer"));

      expect(screen.getByTestId("correo-restablecer-dialogo").textContent).toContain("vuelven a salir de Servidor <s@khipu.pe>");
    });
  });
});
