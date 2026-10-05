import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlantillaDeCorreo } from "@/lib/api/admin-configuracion";
import type { ApiEnvelope } from "@/lib/api/types";
import { EditorDePlantilla } from "./editor-de-plantilla";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const RUTA = "/api/admin/configuracion/plantillas/RECUPERACION_CLAVE";

const DE_FABRICA: PlantillaDeCorreo = {
  tipo: "RECUPERACION_CLAVE",
  etiqueta: "Restablecer la contraseña",
  cuando_se_manda: "Se manda cuando alguien olvidó su contraseña.",
  vigente: { asunto: "Restablecer contraseña", cuerpo: "Para restablecer tu contraseña abre este enlace (válido {validez}):\n{enlace}" },
  defecto: { asunto: "Restablecer contraseña", cuerpo: "Para restablecer tu contraseña abre este enlace (válido {validez}):\n{enlace}" },
  personalizada: false,
  variables: [
    { nombre: "enlace", descripcion: "El enlace de un solo uso.", ejemplo: "https://app.khipu.pe/restablecer/0a1b2c3d", indispensable: true },
    { nombre: "validez", descripcion: "Cuánto dura el enlace.", ejemplo: "1 hora", indispensable: false },
  ],
};
const EDITADA: PlantillaDeCorreo = { ...DE_FABRICA, vigente: { asunto: "Mi asunto", cuerpo: "Mi cuerpo {enlace}" }, personalizada: true, actualizada_en: "2026-10-15T20:00:00Z" };

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string | null, mensaje: string | null): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const asunto = () => screen.getByTestId("plantilla-asunto") as HTMLInputElement;
const cuerpo = () => screen.getByTestId("plantilla-cuerpo") as HTMLTextAreaElement;
const escribir = (el: HTMLElement, value: string) => fireEvent.change(el, { target: { value } });

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

describe("EditorDePlantilla (#199)", () => {
  it("dice qué correo es, cuándo se manda y si sale de fábrica o editado", () => {
    const { unmount } = render(<EditorDePlantilla plantilla={DE_FABRICA} />);
    expect(screen.getByRole("heading", { name: "Restablecer la contraseña" })).toBeTruthy();
    expect(screen.getByText("Se manda cuando alguien olvidó su contraseña.")).toBeTruthy();
    expect(screen.getByTestId("plantilla-origen").textContent).toBe("Sale con el texto de fábrica.");
    unmount();

    render(<EditorDePlantilla plantilla={EDITADA} />);
    expect(screen.getByTestId("plantilla-origen").textContent).toBe("Editado el 15 Oct 2026, 15:00.");
  });

  it("el asunto y el cuerpo arrancan con el texto vigente", () => {
    render(<EditorDePlantilla plantilla={EDITADA} />);

    expect(asunto().value).toBe("Mi asunto");
    expect(cuerpo().value).toBe("Mi cuerpo {enlace}");
  });

  it("los límites de los campos son los del backend", () => {
    render(<EditorDePlantilla plantilla={DE_FABRICA} />);

    expect(asunto().maxLength).toBe(150);
    expect(cuerpo().maxLength).toBe(5000);
  });

  describe("las variables", () => {
    it("lista cada variable con su descripción y un ejemplo, y marca la indispensable", () => {
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);

      const enlace = screen.getByTestId("plantilla-variable-enlace").closest("li")!;
      expect(enlace.textContent).toContain("{enlace}");
      expect(enlace.textContent).toContain("El enlace de un solo uso.");
      expect(enlace.textContent).toContain("(indispensable)");
      expect(enlace.textContent).toContain("Ejemplo: https://app.khipu.pe/restablecer/0a1b2c3d");
      const validez = screen.getByTestId("plantilla-variable-validez").closest("li")!;
      expect(validez.textContent).not.toContain("indispensable");
    });

    it("un clic inserta la variable en el cuerpo, donde está el cursor, y deja el cursor después", async () => {
      render(<EditorDePlantilla plantilla={{ ...DE_FABRICA, vigente: { asunto: "Hola", cuerpo: "Abre  ya" } }} />);
      act(() => cuerpo().focus());
      cuerpo().setSelectionRange(5, 5);

      fireEvent.click(screen.getByTestId("plantilla-variable-enlace"));

      expect(cuerpo().value).toBe("Abre {enlace} ya");
      await waitFor(() => expect(cuerpo().selectionStart).toBe(13));
      expect(asunto().value).toBe("Hola");
    });

    it("reemplaza lo seleccionado", () => {
      render(<EditorDePlantilla plantilla={{ ...DE_FABRICA, vigente: { asunto: "Hola", cuerpo: "Abre ESTO ya" } }} />);
      act(() => cuerpo().focus());
      cuerpo().setSelectionRange(5, 9);

      fireEvent.click(screen.getByTestId("plantilla-variable-validez"));

      expect(cuerpo().value).toBe("Abre {validez} ya");
    });

    it("si el último campo tocado fue el asunto, la variable va al asunto", () => {
      render(<EditorDePlantilla plantilla={{ ...DE_FABRICA, vigente: { asunto: "Aviso ", cuerpo: "Cuerpo" } }} />);
      act(() => asunto().focus());
      asunto().setSelectionRange(6, 6);

      fireEvent.click(screen.getByTestId("plantilla-variable-validez"));

      expect(asunto().value).toBe("Aviso {validez}");
      expect(cuerpo().value).toBe("Cuerpo");
    });

    it("sin haber tocado nada, la variable va al cuerpo", () => {
      render(<EditorDePlantilla plantilla={{ ...DE_FABRICA, vigente: { asunto: "Aviso", cuerpo: "Cuerpo" } }} />);

      fireEvent.click(screen.getByTestId("plantilla-variable-validez"));

      expect(asunto().value).toBe("Aviso");
      expect(cuerpo().value).toContain("{validez}");
    });

    it("insertar cuenta como un cambio: aparece «Hay cambios sin guardar»", () => {
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);
      expect(screen.getByTestId("plantilla-estado").textContent).toBe("Sin cambios sin guardar.");

      fireEvent.click(screen.getByTestId("plantilla-variable-validez"));

      expect(screen.getByTestId("plantilla-estado").textContent).toBe("Hay cambios sin guardar.");
    });
  });

  describe("el estado", () => {
    it("dice si hay cambios sin guardar, y vuelve a decir que no si se deshacen", () => {
      render(<EditorDePlantilla plantilla={EDITADA} />);
      expect(screen.getByTestId("plantilla-estado").textContent).toBe("Sin cambios sin guardar.");

      escribir(asunto(), "Otro");
      expect(screen.getByTestId("plantilla-estado").textContent).toBe("Hay cambios sin guardar.");

      escribir(asunto(), "Mi asunto");
      expect(screen.getByTestId("plantilla-estado").textContent).toBe("Sin cambios sin guardar.");
    });

    it("un cambio solo en el cuerpo también cuenta", () => {
      render(<EditorDePlantilla plantilla={EDITADA} />);

      escribir(cuerpo(), "Mi cuerpo {enlace} y más");

      expect(screen.getByTestId("plantilla-estado").textContent).toBe("Hay cambios sin guardar.");
    });
  });

  describe("la vista previa", () => {
    it("manda el texto a la ruta de ese correo y muestra cómo se vería", async () => {
      apiRequest.mockResolvedValue(exito({ asunto: "Mi asunto", cuerpo: "Mi cuerpo https://app.khipu.pe/restablecer/0a1b2c3d" }));
      render(<EditorDePlantilla plantilla={EDITADA} />);

      fireEvent.click(screen.getByTestId("plantilla-ver-vista-previa"));

      await waitFor(() => expect(screen.getByTestId("plantilla-vista-previa-asunto").textContent).toBe("Mi asunto"));
      expect(screen.getByTestId("plantilla-vista-previa-cuerpo").textContent).toBe("Mi cuerpo https://app.khipu.pe/restablecer/0a1b2c3d");
      expect(apiRequest).toHaveBeenCalledExactlyOnceWith(`${RUTA}/vista-previa`, { method: "POST", body: { asunto: "Mi asunto", cuerpo: "Mi cuerpo {enlace}" } });
      expect(refresh).not.toHaveBeenCalled();
    });

    it("manda lo que está escrito, no lo guardado", async () => {
      apiRequest.mockResolvedValue(exito({ asunto: "x", cuerpo: "y" }));
      render(<EditorDePlantilla plantilla={EDITADA} />);
      escribir(asunto(), "Sin guardar");
      escribir(cuerpo(), "Tampoco {enlace}");

      fireEvent.click(screen.getByTestId("plantilla-ver-vista-previa"));

      await screen.findByTestId("plantilla-vista-previa");
      expect(apiRequest.mock.calls[0][1].body).toEqual({ asunto: "Sin guardar", cuerpo: "Tampoco {enlace}" });
    });

    it("lo que el backend rechaza se muestra tal cual y no deja una vista previa vieja", async () => {
      apiRequest.mockResolvedValueOnce(exito({ asunto: "a", cuerpo: "b" })).mockResolvedValueOnce(error("PLANTILLA_INVALIDA", "El cuerpo tiene que incluir {enlace}"));
      render(<EditorDePlantilla plantilla={EDITADA} />);
      fireEvent.click(screen.getByTestId("plantilla-ver-vista-previa"));
      await screen.findByTestId("plantilla-vista-previa");

      fireEvent.click(screen.getByTestId("plantilla-ver-vista-previa"));

      expect((await screen.findByRole("alert")).textContent).toBe("El cuerpo tiene que incluir {enlace}");
      expect(screen.queryByTestId("plantilla-vista-previa")).toBeNull();
    });

    it("al cambiar el texto la vista previa desaparece: ya no diría lo que se va a guardar", async () => {
      apiRequest.mockResolvedValue(exito({ asunto: "a", cuerpo: "b" }));
      render(<EditorDePlantilla plantilla={EDITADA} />);
      fireEvent.click(screen.getByTestId("plantilla-ver-vista-previa"));
      await screen.findByTestId("plantilla-vista-previa");

      escribir(cuerpo(), "Otro {enlace}");

      expect(screen.queryByTestId("plantilla-vista-previa")).toBeNull();
    });

    it("mientras se calcula lo dice y no se pide dos veces", async () => {
      let responder: (v: ApiEnvelope<unknown>) => void = () => {};
      apiRequest.mockReturnValue(new Promise((r) => (responder = r)));
      render(<EditorDePlantilla plantilla={EDITADA} />);
      const boton = screen.getByTestId("plantilla-ver-vista-previa") as HTMLButtonElement;

      act(() => {
        fireEvent.click(boton);
        fireEvent.click(boton);
      });

      expect(apiRequest).toHaveBeenCalledTimes(1);
      expect(boton.disabled).toBe(true);
      expect(boton.textContent).toContain("Calculando…");
      await act(async () => responder(exito({ asunto: "a", cuerpo: "b" })));
    });

    it("el cuerpo de la vista previa se muestra como texto, nunca como HTML", async () => {
      apiRequest.mockResolvedValue(exito({ asunto: "<b>a</b>", cuerpo: "<img src=x onerror=alert(1)>" }));
      const { container } = render(<EditorDePlantilla plantilla={EDITADA} />);

      fireEvent.click(screen.getByTestId("plantilla-ver-vista-previa"));

      await screen.findByTestId("plantilla-vista-previa");
      expect(container.querySelector("img")).toBeNull();
      expect(screen.getByTestId("plantilla-vista-previa-cuerpo").textContent).toBe("<img src=x onerror=alert(1)>");
    });

    it("un corte de red dice que algo falló y no deja vista previa", async () => {
      apiRequest.mockResolvedValue(error("RED", "No se pudo conectar"));
      render(<EditorDePlantilla plantilla={EDITADA} />);

      fireEvent.click(screen.getByTestId("plantilla-ver-vista-previa"));

      expect((await screen.findByRole("alert")).textContent).toBe("No se pudo conectar Recarga la página para ver el estado real.");
    });
  });

  describe("guardar", () => {
    it("manda un PUT al correo con asunto y cuerpo tal cual, dice que quedó guardado y recarga", async () => {
      apiRequest.mockResolvedValue(exito(EDITADA));
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);
      escribir(asunto(), "  Mi asunto ");
      escribir(cuerpo(), "Mi cuerpo {enlace}\n\n");

      fireEvent.click(screen.getByTestId("plantilla-guardar"));

      await waitFor(() => expect(screen.getByTestId("plantilla-resultado").textContent).toBe("Texto guardado: vale desde el siguiente correo."));
      expect(apiRequest).toHaveBeenCalledExactlyOnceWith(RUTA, { method: "PUT", body: { asunto: "  Mi asunto ", cuerpo: "Mi cuerpo {enlace}\n\n" } });
      expect(refresh).toHaveBeenCalledTimes(1);
      expect(screen.getByTestId("plantilla-resultado").getAttribute("role")).toBe("status");
    });

    it("lo que el backend rechaza se muestra tal cual, sin decir que se guardó ni recargar", async () => {
      apiRequest.mockResolvedValue(error("PLANTILLA_INVALIDA", "Este correo no tiene la variable {ruc}"));
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);

      fireEvent.click(screen.getByTestId("plantilla-guardar"));

      expect((await screen.findByRole("alert")).textContent).toBe("Este correo no tiene la variable {ruc}");
      expect(screen.queryByTestId("plantilla-resultado")).toBeNull();
      expect(refresh).not.toHaveBeenCalled();
    });

    it("un corte de red no se reintenta a ciegas", async () => {
      apiRequest.mockResolvedValue(error("RED", "No se pudo conectar"));
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);

      fireEvent.click(screen.getByTestId("plantilla-guardar"));

      expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página para ver el estado real.");
      expect(apiRequest).toHaveBeenCalledTimes(1);
    });

    it("dos clics en el mismo instante mandan un solo pedido", async () => {
      let responder: (v: ApiEnvelope<unknown>) => void = () => {};
      apiRequest.mockReturnValue(new Promise((r) => (responder = r)));
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);
      const boton = screen.getByTestId("plantilla-guardar") as HTMLButtonElement;

      act(() => {
        fireEvent.click(boton);
        fireEvent.click(boton);
      });

      expect(apiRequest).toHaveBeenCalledTimes(1);
      expect(boton.disabled).toBe(true);
      expect(boton.textContent).toContain("Guardando…");
      await act(async () => responder(exito(EDITADA)));
    });

    it("al volver a escribir deja de decir que se guardó", async () => {
      apiRequest.mockResolvedValue(exito(EDITADA));
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);
      fireEvent.click(screen.getByTestId("plantilla-guardar"));
      await screen.findByTestId("plantilla-resultado");

      escribir(asunto(), "Otro");

      expect(screen.queryByTestId("plantilla-resultado")).toBeNull();
    });

    it("un error anterior se borra al escribir de nuevo", async () => {
      apiRequest.mockResolvedValue(error("PLANTILLA_INVALIDA", "Mal"));
      render(<EditorDePlantilla plantilla={DE_FABRICA} />);
      fireEvent.click(screen.getByTestId("plantilla-guardar"));
      await screen.findByRole("alert");

      escribir(asunto(), "Otro");

      expect(screen.queryByRole("alert")).toBeNull();
    });
  });

  describe("volver al texto de fábrica", () => {
    it("solo se ofrece si el texto fue editado", () => {
      const { unmount } = render(<EditorDePlantilla plantilla={DE_FABRICA} />);
      expect(screen.queryByTestId("plantilla-restaurar")).toBeNull();
      unmount();

      render(<EditorDePlantilla plantilla={EDITADA} />);
      expect(screen.getByTestId("plantilla-restaurar")).toBeTruthy();
    });

    it("el diálogo dice de qué correo se trata y que queda en la bitácora, y no pide nada hasta confirmar", () => {
      render(<EditorDePlantilla plantilla={EDITADA} />);

      fireEvent.click(screen.getByTestId("plantilla-restaurar"));

      const texto = screen.getByTestId("plantilla-restaurar-dialogo").textContent ?? "";
      expect(texto).toContain("«Restablecer la contraseña» vuelve a salir con su texto de fábrica");
      expect(texto).toContain("Queda en la bitácora (sin el texto)");
      expect(apiRequest).not.toHaveBeenCalled();
    });

    it("confirmar manda un DELETE a la ruta de ese correo y recarga", async () => {
      apiRequest.mockResolvedValue(exito(DE_FABRICA));
      render(<EditorDePlantilla plantilla={EDITADA} />);
      fireEvent.click(screen.getByTestId("plantilla-restaurar"));

      fireEvent.click(screen.getByTestId("plantilla-restaurar-confirmar"));

      await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
      expect(apiRequest).toHaveBeenCalledWith(RUTA, { method: "DELETE", body: undefined });
    });
  });
});
