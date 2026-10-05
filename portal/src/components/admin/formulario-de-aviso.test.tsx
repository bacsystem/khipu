import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BannerConfigurado } from "@/lib/api/admin-configuracion";
import type { ApiEnvelope } from "@/lib/api/types";
import { FormularioDeAviso } from "./formulario-de-aviso";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

/** Mediodía en Lima del 15 de octubre. */
const AHORA = "2026-10-15T17:00:00.000Z";

function banner(p: Partial<BannerConfigurado> = {}): BannerConfigurado {
  return { texto: "Mantenimiento esta noche", desde: "2026-10-15T16:00:00Z", hasta: "2026-10-16T04:00:00Z", actualizado_en: "2026-10-15T15:00:00Z", vigente_ahora: true, ...p };
}

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string | null, mensaje: string | null): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const texto = () => screen.getByTestId("aviso-texto") as HTMLInputElement;
const desde = () => screen.getByTestId("aviso-desde") as HTMLInputElement;
const hasta = () => screen.getByTestId("aviso-hasta") as HTMLInputElement;
const escribir = (el: HTMLElement, value: string) => fireEvent.change(el, { target: { value } });
const alResultado = vi.fn();

afterEach(() => {
  cleanup();
  refresh.mockClear();
  alResultado.mockReset();
  apiRequest.mockReset();
});

describe("FormularioDeAviso (#199)", () => {
  describe("sin aviso publicado", () => {
    it("dice que no hay ninguno y ofrece publicar, empezando ahora en hora de Lima", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);

      expect(screen.getByTestId("aviso-ninguno").textContent).toBe("No hay ningún aviso publicado.");
      expect(screen.getByTestId("aviso-publicar").textContent).toContain("Publicar aviso");
      expect(texto().value).toBe("");
      expect(desde().value).toBe("2026-10-15T12:00");
      expect(hasta().value).toBe("");
      expect(screen.queryByTestId("aviso-retirar")).toBeNull();
      expect(screen.queryByTestId("aviso-estado")).toBeNull();
    });

    it("el texto tiene el tope del backend y las dos fechas son de fecha y hora", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);

      expect(texto().maxLength).toBe(300);
      expect(desde().type).toBe("datetime-local");
      expect(hasta().type).toBe("datetime-local");
    });

    it("explica los límites: una línea, hasta 300 caracteres, fin obligatorio y a lo sumo 90 días", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);

      expect(screen.getByText("Una sola línea, hasta 300 caracteres. Se muestra como texto plano.")).toBeTruthy();
      expect(screen.getByText("Obligatorio, y a lo sumo 90 días después de empezar.")).toBeTruthy();
    });
  });

  describe("la vista previa", () => {
    it("no aparece hasta que hay texto y un fin válido", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      expect(screen.queryByTestId("aviso-vista-previa")).toBeNull();

      escribir(texto(), "Mantenimiento");
      expect(screen.queryByTestId("aviso-vista-previa")).toBeNull();

      escribir(hasta(), "2026-10-15T23:00");
      expect(screen.getByTestId("aviso-vista-previa")).toBeTruthy();
    });

    it("muestra el banner tal como lo verán los clientes, con el fin en hora de Lima", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      escribir(texto(), "  Mantenimiento esta noche  ");
      escribir(hasta(), "2026-10-15T23:00");

      const previa = screen.getByTestId("aviso-vista-previa");

      expect(previa.querySelector('[data-testid="banner-de-mantenimiento-texto"]')?.textContent).toBe("Mantenimiento esta noche");
      expect(previa.textContent).toContain("Hasta 15 Oct 2026, 23:00");
    });

    it("un texto en blanco no genera vista previa", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      escribir(texto(), "   ");
      escribir(hasta(), "2026-10-15T23:00");

      expect(screen.queryByTestId("aviso-vista-previa")).toBeNull();
    });
  });

  describe("publicar", () => {
    function llenar() {
      escribir(texto(), "Mantenimiento esta noche");
      escribir(desde(), "2026-10-15T22:00");
      escribir(hasta(), "2026-10-15T23:30");
    }

    it("manda un PUT con el texto y las fechas de Lima convertidas a instantes, dice que se publicó y recarga", async () => {
      apiRequest.mockResolvedValue(exito(banner()));
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      llenar();

      fireEvent.click(screen.getByTestId("aviso-publicar"));

      await waitFor(() => expect(alResultado).toHaveBeenLastCalledWith("Aviso publicado."));
      expect(apiRequest).toHaveBeenCalledExactlyOnceWith("/api/admin/configuracion/banner", {
        method: "PUT",
        body: { texto: "Mantenimiento esta noche", desde: "2026-10-16T03:00:00.000Z", hasta: "2026-10-16T04:30:00.000Z" },
      });
      expect(refresh).toHaveBeenCalledTimes(1);
    });

    it("sin texto o sin fechas lo dice bajo cada campo y no manda nada", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      escribir(desde(), "");

      fireEvent.click(screen.getByTestId("aviso-publicar"));

      expect(screen.getByText("Escribe el texto del aviso.")).toBeTruthy();
      expect(screen.getByText("Indica desde cuándo se muestra.")).toBeTruthy();
      expect(screen.getByText("Indica hasta cuándo se muestra.")).toBeTruthy();
      expect(texto().getAttribute("aria-invalid")).toBe("true");
      expect(desde().getAttribute("aria-invalid")).toBe("true");
      expect(hasta().getAttribute("aria-invalid")).toBe("true");
      expect(apiRequest).not.toHaveBeenCalled();
    });

    it("escribir en un campo borra su error, y solo el suyo", () => {
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      fireEvent.click(screen.getByTestId("aviso-publicar"));
      expect(screen.getByText("Escribe el texto del aviso.")).toBeTruthy();
      expect(screen.getByText("Indica hasta cuándo se muestra.")).toBeTruthy();

      escribir(texto(), "Mantenimiento");

      expect(screen.queryByText("Escribe el texto del aviso.")).toBeNull();
      expect(screen.getByText("Indica hasta cuándo se muestra.")).toBeTruthy();
    });

    it("lo que el backend rechaza (el orden, el límite de 90 días) se muestra tal cual, sin decir que se publicó ni recargar", async () => {
      apiRequest.mockResolvedValue(error("BANNER_INVALIDO", "Un aviso puede durar hasta 90 días"));
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      llenar();

      fireEvent.click(screen.getByTestId("aviso-publicar"));

      expect((await screen.findByRole("alert")).textContent).toBe("Un aviso puede durar hasta 90 días");
      expect(alResultado).not.toHaveBeenCalledWith("Aviso publicado.");
      expect(refresh).not.toHaveBeenCalled();
    });

    it("un corte de red no se reintenta a ciegas", async () => {
      apiRequest.mockResolvedValue(error("RED", "No se pudo conectar"));
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      llenar();

      fireEvent.click(screen.getByTestId("aviso-publicar"));

      expect((await screen.findByRole("alert")).textContent).toBe("No se pudo conectar Recarga la página para ver el estado real.");
      expect(apiRequest).toHaveBeenCalledTimes(1);
    });

    it("dos clics en el mismo instante mandan un solo pedido", async () => {
      let responder: (v: ApiEnvelope<unknown>) => void = () => {};
      apiRequest.mockReturnValue(new Promise((r) => (responder = r)));
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      llenar();
      const boton = screen.getByTestId("aviso-publicar") as HTMLButtonElement;

      act(() => {
        fireEvent.click(boton);
        fireEvent.click(boton);
      });

      expect(apiRequest).toHaveBeenCalledTimes(1);
      expect(boton.disabled).toBe(true);
      expect(boton.textContent).toContain("Publicando…");
      await act(async () => responder(exito(banner())));
    });

    it("al volver a escribir pide que se deje de decir que se publicó", async () => {
      apiRequest.mockResolvedValue(exito(banner()));
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      llenar();
      fireEvent.click(screen.getByTestId("aviso-publicar"));
      await waitFor(() => expect(alResultado).toHaveBeenLastCalledWith("Aviso publicado."));

      escribir(texto(), "Otro");

      expect(alResultado).toHaveBeenLastCalledWith(null);
    });

    it("al empezar a publicar borra el resultado anterior, antes de saber cómo termina", async () => {
      let responder: (v: ApiEnvelope<unknown>) => void = () => {};
      apiRequest.mockReturnValue(new Promise((r) => (responder = r)));
      render(<FormularioDeAviso banner={null} ahora={AHORA} alResultado={alResultado} />);
      llenar();
      alResultado.mockClear();   // escribir ya había pedido borrar el resultado: acá importa lo que hace publicar

      fireEvent.click(screen.getByTestId("aviso-publicar"));

      expect(alResultado).toHaveBeenLastCalledWith(null);
      await act(async () => responder(exito(banner())));
    });
  });

  describe("con un aviso publicado", () => {
    it("dice cuál es, su vigencia en hora de Lima y que se está mostrando", () => {
      render(<FormularioDeAviso banner={banner()} ahora={AHORA} alResultado={alResultado} />);

      expect(screen.getByTestId("aviso-estado").textContent).toBe("Se está mostrando ahora.");
      expect(screen.getByTestId("aviso-estado").getAttribute("data-estado")).toBe("vigente");
      expect(screen.getByTestId("aviso-publicado-texto").textContent).toBe("Mantenimiento esta noche");
      expect(screen.getByTestId("aviso-actual").textContent).toContain("Del 15 Oct 2026, 11:00 al 15 Oct 2026, 23:00.");
      expect(screen.queryByTestId("aviso-ninguno")).toBeNull();
    });

    it("uno programado para después dice que todavía no se muestra", () => {
      render(<FormularioDeAviso banner={banner({ desde: "2026-10-16T01:00:00Z", vigente_ahora: false })} ahora={AHORA} alResultado={alResultado} />);

      expect(screen.getByTestId("aviso-estado").textContent).toBe("Programado: todavía no se muestra.");
      expect(screen.getByTestId("aviso-estado").getAttribute("data-estado")).toBe("programado");
    });

    it("uno que ya terminó dice que venció", () => {
      render(<FormularioDeAviso banner={banner({ desde: "2026-10-14T10:00:00Z", hasta: "2026-10-14T14:00:00Z", vigente_ahora: false })} ahora={AHORA} alResultado={alResultado} />);

      expect(screen.getByTestId("aviso-estado").textContent).toBe("Ya venció: no se muestra.");
      expect(screen.getByTestId("aviso-estado").getAttribute("data-estado")).toBe("vencido");
    });

    it("el formulario arranca con sus datos, en hora de Lima, y el botón dice «Reemplazar aviso»", () => {
      render(<FormularioDeAviso banner={banner()} ahora={AHORA} alResultado={alResultado} />);

      expect(texto().value).toBe("Mantenimiento esta noche");
      expect(desde().value).toBe("2026-10-15T11:00");
      expect(hasta().value).toBe("2026-10-15T23:00");
      expect(screen.getByTestId("aviso-publicar").textContent).toContain("Reemplazar aviso");
    });

    it("ofrece retirarlo, y el diálogo dice qué pasa y que queda en la bitácora", () => {
      render(<FormularioDeAviso banner={banner()} ahora={AHORA} alResultado={alResultado} />);

      fireEvent.click(screen.getByTestId("aviso-retirar"));

      const dialogo = screen.getByTestId("aviso-retirar-dialogo").textContent ?? "";
      expect(dialogo).toContain("Los clientes dejan de ver el aviso apenas recarguen su portal");
      expect(dialogo).toContain("Queda en la bitácora");
      expect(apiRequest).not.toHaveBeenCalled();
    });

    it("confirmar manda un DELETE y recarga", async () => {
      apiRequest.mockResolvedValue(exito(null));
      render(<FormularioDeAviso banner={banner()} ahora={AHORA} alResultado={alResultado} />);
      fireEvent.click(screen.getByTestId("aviso-retirar"));

      fireEvent.click(screen.getByTestId("aviso-retirar-confirmar"));

      await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
      expect(apiRequest).toHaveBeenCalledWith("/api/admin/configuracion/banner", { method: "DELETE", body: undefined });
    });

    it("si otro administrador ya lo retiró, lo dice y recarga para mostrar el estado real", async () => {
      apiRequest.mockResolvedValue(error("NO_ENCONTRADO", "No hay un aviso publicado"));
      render(<FormularioDeAviso banner={banner()} ahora={AHORA} alResultado={alResultado} />);
      fireEvent.click(screen.getByTestId("aviso-retirar"));

      fireEvent.click(screen.getByTestId("aviso-retirar-confirmar"));

      expect((await screen.findByRole("alert")).textContent).toBe("No hay un aviso publicado");
      expect(refresh).toHaveBeenCalledTimes(1);
    });
  });
});
