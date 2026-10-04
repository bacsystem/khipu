import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiEnvelope } from "@/lib/api/types";
import { AccionesDeUsuario } from "./acciones-de-usuario";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const USUARIO = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (datos: unknown): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

function mostrar(init: { activo?: boolean; verificado?: boolean } = {}) {
  render(<AccionesDeUsuario cuentaId={CUENTA} usuarioId={USUARIO} correo="beto@sol.pe" activo={init.activo ?? true} verificado={init.verificado ?? false} />);
}

function abrir(tipo: "restablecer" | "verificar") {
  mostrar();
  fireEvent.click(screen.getByTestId(`${tipo}-usuario`));
}

describe("AccionesDeUsuario (#183)", () => {
  it("un usuario activo sin verificar ofrece restablecer, reenviar la verificación y entrar como él", () => {
    mostrar();

    expect(screen.getByTestId("restablecer-usuario")).toBeTruthy();
    expect(screen.getByTestId("verificar-usuario")).toBeTruthy();
    expect(screen.getByTestId("impersonar-usuario")).toBeTruthy();
  });

  it("uno ya verificado ofrece restablecer y entrar como él, pero no reenviar la verificación", () => {
    mostrar({ verificado: true });

    expect(screen.getByTestId("restablecer-usuario")).toBeTruthy();
    expect(screen.getByTestId("impersonar-usuario")).toBeTruthy();
    expect(screen.queryByTestId("verificar-usuario")).toBeNull();
  });

  it("uno desactivado no ofrece nada, aunque no esté verificado", () => {
    mostrar({ activo: false, verificado: false });

    expect(screen.queryByRole("button")).toBeNull();
  });

  it("abrir el modal no envía nada y dice a quién le llega el correo", () => {
    abrir("restablecer");

    expect(apiRequest).not.toHaveBeenCalled();
    expect(screen.getByTestId("acceso-confirmacion").textContent).toContain("beto@sol.pe");
  });

  it.each([
    ["restablecer", "restablecimiento"],
    ["verificar", "verificacion"],
  ] as const)("confirmar %s hace POST a la ruta de %s del usuario, sin cuerpo", async (tipo, ruta) => {
    apiRequest.mockResolvedValue(exito({ usuario_id: USUARIO, correo: "beto@sol.pe" }));
    abrir(tipo);

    fireEvent.click(screen.getByTestId("acceso-confirmar"));

    await screen.findByTestId("acceso-hecho");
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${CUENTA}/usuarios/${USUARIO}/${ruta}`, { method: "POST" });
  });

  it("tras enviarlo dice a quién se mandó, recarga la página para que la bitácora lo muestre y ya no ofrece enviar otro", async () => {
    apiRequest.mockResolvedValue(exito({ usuario_id: USUARIO, correo: "beto@sol.pe" }));
    abrir("restablecer");

    fireEvent.click(screen.getByTestId("acceso-confirmar"));

    expect((await screen.findByTestId("acceso-hecho")).textContent).toBe("Correo de restablecimiento enviado a beto@sol.pe.");
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId("acceso-confirmar")).toBeNull();
  });

  /** Dos clics seguidos mandarían dos correos con dos enlaces distintos: el segundo no debe salir. */
  it("un doble clic en confirmar manda un solo correo", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir("restablecer");

    // Los dos clics dentro de un mismo `act`: el segundo manejador ve el `enviando` viejo y el botón todavía no está deshabilitado, así
    // que lo único que frena el segundo pedido es la guardia del ref.
    const boton = screen.getByTestId("acceso-confirmar");
    act(() => {
      fireEvent.click(boton);
      fireEvent.click(boton);
    });
    await act(async () => resolver(exito({ usuario_id: USUARIO, correo: "beto@sol.pe" })));

    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("mientras se envía el botón dice «Enviando…» y no se puede pulsar ni cancelar", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir("restablecer");

    fireEvent.click(screen.getByTestId("acceso-confirmar"));

    expect(screen.getByTestId("acceso-confirmar").textContent).toContain("Enviando…");
    expect((screen.getByTestId("acceso-confirmar") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Cancelar" }) as HTMLButtonElement).disabled).toBe(true);
    await act(async () => resolver(exito({ usuario_id: USUARIO, correo: "beto@sol.pe" })));
  });

  /** El botón «Cancelar» ya está deshabilitado; Escape o el clic fuera del modal no pasan por él y también deben esperar el resultado. */
  it("Escape mientras se envía no cierra el modal: el administrador debe ver el resultado", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
    abrir("restablecer");
    fireEvent.click(screen.getByTestId("acceso-confirmar"));

    fireEvent.keyDown(screen.getByTestId("acceso-confirmacion"), { key: "Escape" });

    expect(screen.getByTestId("acceso-confirmacion")).toBeTruthy();
    await act(async () => resolver(exito({ usuario_id: USUARIO, correo: "beto@sol.pe" })));
    expect(screen.getByTestId("acceso-hecho")).toBeTruthy();
  });

  it("al cerrar y reabrir tras un envío, el modal vuelve a pedir confirmación, sin el resultado anterior", async () => {
    apiRequest.mockResolvedValue(exito({ usuario_id: USUARIO, correo: "beto@sol.pe" }));
    abrir("restablecer");
    fireEvent.click(screen.getByTestId("acceso-confirmar"));
    await screen.findByTestId("acceso-hecho");

    // Hay dos «Cerrar»: el del pie del modal y la X de la esquina; cualquiera de los dos cierra.
    fireEvent.click(screen.getAllByRole("button", { name: "Cerrar" })[0]);
    await waitFor(() => expect(screen.queryByTestId("acceso-confirmacion")).toBeNull());
    fireEvent.click(screen.getByTestId("restablecer-usuario"));

    expect(screen.queryByTestId("acceso-hecho")).toBeNull();
    expect(screen.getByTestId("acceso-confirmar")).toBeTruthy();
  });

  it("un error del backend se muestra en el modal, que sigue abierto, sin decir que se envió ni recargar", async () => {
    apiRequest.mockResolvedValue(error("CORREO_NO_CONFIGURADO", "El envío de correos no está habilitado en el servidor: no se mandó nada"));
    abrir("restablecer");

    fireEvent.click(screen.getByTestId("acceso-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("El envío de correos no está habilitado en el servidor: no se mandó nada");
    expect(screen.queryByTestId("acceso-hecho")).toBeNull();
    expect(screen.getByTestId("acceso-confirmacion")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("tras un error se puede volver a intentar", async () => {
    apiRequest.mockResolvedValueOnce(error("CORREO_NO_ENVIADO", "No se pudo enviar el correo")).mockResolvedValueOnce(exito({ usuario_id: USUARIO, correo: "beto@sol.pe" }));
    abrir("verificar");

    fireEvent.click(screen.getByTestId("acceso-confirmar"));
    await screen.findByRole("alert");
    fireEvent.click(screen.getByTestId("acceso-confirmar"));

    await screen.findByTestId("acceso-hecho");
    expect(apiRequest).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  /** Si no se sabe si el correo salió, reintentar a ciegas lo mandaría dos veces: se manda al administrador a mirar la bitácora. */
  it("un corte de red avisa que no se sabe si salió y manda a revisar la bitácora", async () => {
    apiRequest.mockResolvedValue(error("RED", "No se pudo conectar con el servidor"));
    abrir("restablecer");

    fireEvent.click(screen.getByTestId("acceso-confirmar"));

    const texto = (await screen.findByRole("alert")).textContent ?? "";
    expect(texto).toContain("No se pudo conectar con el servidor");
    expect(texto).toContain("Revisa la bitácora");
    expect(screen.queryByTestId("acceso-hecho")).toBeNull();
  });

  it("cancelar cierra el modal sin enviar nada, y al abrirlo otra vez no queda el resultado anterior", async () => {
    apiRequest.mockResolvedValue(error("CORREO_NO_CONFIGURADO", "sin correo"));
    abrir("restablecer");
    fireEvent.click(screen.getByTestId("acceso-confirmar"));
    await screen.findByRole("alert");

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByTestId("acceso-confirmacion")).toBeNull());
    fireEvent.click(screen.getByTestId("restablecer-usuario"));

    expect(screen.queryByRole("alert")).toBeNull();
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  // --- #184: entrar como el usuario ------------------------------------------------------------------------------------------------

  describe("entrar como el usuario", () => {
    function abrirImpersonar(irA = vi.fn()) {
      render(<AccionesDeUsuario cuentaId={CUENTA} usuarioId={USUARIO} correo="beto@sol.pe" activo verificado={false} irA={irA} />);
      fireEvent.click(screen.getByTestId("impersonar-usuario"));
      return irA;
    }

    /** La función más sensible del backoffice: el diálogo dice qué se puede y qué no, antes de entrar. */
    it("el diálogo dice que dura 15 minutos, que solo se mira, que queda registrado y que reemplaza la sesión del navegador", () => {
      abrirImpersonar();

      const texto = screen.getByTestId("impersonar-usuario-dialogo").textContent ?? "";
      expect(texto).toContain("Vas a ver el portal tal como lo ve beto@sol.pe");
      expect(texto).toContain("15 minutos y no se puede renovar");
      expect(texto).toContain("Solo puedes mirar: no puedes cambiar nada");
      expect(texto).toContain("ni la contraseña, ni las credenciales SOL, ni las API keys, ni emitir");
      expect(texto).toContain("Queda en la bitácora a tu nombre, y el cliente lo ve en su historial");
      expect(texto).toContain("Reemplaza la sesión de cliente que tengas abierta en este navegador");
      expect(apiRequest).not.toHaveBeenCalled();
    });

    it("confirmar hace POST a la ruta de impersonar de ese usuario, sin cuerpo, y navega al portal del cliente", async () => {
      apiRequest.mockResolvedValue(exito({ expira_en: "2026-10-04T17:15:00Z", usuario: { email: "beto@sol.pe" } }));
      const irA = abrirImpersonar();

      fireEvent.click(screen.getByTestId("impersonar-usuario-confirmar"));

      await waitFor(() => expect(irA).toHaveBeenCalledWith("/comprobantes"));
      expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${CUENTA}/usuarios/${USUARIO}/impersonar`, { method: "POST", body: undefined });
    });

    it("si el backend se niega muestra el motivo, no navega y el diálogo sigue abierto", async () => {
      apiRequest.mockResolvedValue(error("REQUIERE_ADMINISTRADOR", "Impersonar a un usuario requiere la sesión de un administrador"));
      const irA = abrirImpersonar();

      fireEvent.click(screen.getByTestId("impersonar-usuario-confirmar"));

      expect((await screen.findByRole("alert")).textContent).toContain("requiere la sesión de un administrador");
      expect(irA).not.toHaveBeenCalled();
      expect(screen.getByTestId("impersonar-usuario-dialogo")).toBeTruthy();
    });

    it("un doble clic en confirmar abre una sola sesión", async () => {
      let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
      apiRequest.mockReturnValue(new Promise<ApiEnvelope<unknown>>((r) => (resolver = r)));
      abrirImpersonar();

      const boton = screen.getByTestId("impersonar-usuario-confirmar");
      act(() => {
        fireEvent.click(boton);
        fireEvent.click(boton);
      });
      await act(async () => resolver(exito({})));

      expect(apiRequest).toHaveBeenCalledTimes(1);
    });

    it("cancelar no abre ninguna sesión", () => {
      const irA = abrirImpersonar();

      fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

      expect(apiRequest).not.toHaveBeenCalled();
      expect(irA).not.toHaveBeenCalled();
    });
  });
});
