import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { InvitacionAlEquipo, MiembroDelEquipo } from "@/lib/api/equipo";
import { SoloLectura } from "@/lib/solo-lectura";
import { EquipoView } from "./equipo-view";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.fn();
vi.mock("@/lib/api/browser", () => ({ apiRequest: (...a: unknown[]) => apiRequest(...a) }));

afterEach(cleanup);
beforeEach(() => {
  apiRequest.mockReset();
  refresh.mockReset();
});

const ana: MiembroDelEquipo = { id: "u1", email: "ana@torres.pe", rol: "ADMIN", activo: true, correo_verificado: true, es_usted: true };
const beto: MiembroDelEquipo = { id: "u2", email: "beto@torres.pe", rol: "EMISOR", activo: true, correo_verificado: true, es_usted: false };
const caro: InvitacionAlEquipo = { id: "i1", email: "caro@estudio.pe", rol: "LECTURA", expira_en: "2026-10-27T12:00:00Z", vencida: false, envios: 1, creada_en: "2026-10-20T12:00:00Z" };

describe("EquipoView (#21)", () => {
  it("lista usuarios con su rol e invitaciones pendientes", () => {
    render(<EquipoView miembros={[ana, beto]} invitaciones={[caro]} />);
    expect((screen.getByLabelText("Rol de beto@torres.pe") as HTMLSelectElement).value).toBe("EMISOR");
    expect(within(screen.getByTestId("miembro-ana@torres.pe")).getByText("Tú")).toBeTruthy();
    expect(within(screen.getByTestId("invitacion-caro@estudio.pe")).getByText("Solo lectura")).toBeTruthy();
  });

  it("cambiar el rol llama a la API con el rol elegido", async () => {
    apiRequest.mockResolvedValue({ estado: "exito", datos: { ...beto, rol: "LECTURA" } });
    render(<EquipoView miembros={[ana, beto]} invitaciones={[]} />);

    fireEvent.change(screen.getByLabelText("Rol de beto@torres.pe"), { target: { value: "LECTURA" } });

    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith("/api/proxy/cuenta/usuarios/u2/rol", { method: "PUT", body: { rol: "LECTURA" } }));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it("muestra el error del backend (último administrador)", async () => {
    apiRequest.mockResolvedValue({ estado: "error", codigo: "ULTIMO_ADMINISTRADOR", datos: null, mensaje: "x", errores: null });
    render(<EquipoView miembros={[ana]} invitaciones={[]} />);

    fireEvent.change(screen.getByLabelText("Rol de ana@torres.pe"), { target: { value: "EMISOR" } });
    fireEvent.click(await screen.findByRole("button", { name: "Sí, dejar de ser administrador" }));

    expect((await screen.findAllByRole("alert")).map((a) => a.textContent).join(" ")).toContain("al menos un administrador");
  });

  /** H9: bajarse a uno mismo de administrador pide confirmación; cancelar no llama a la API. */
  it("degradarse a sí mismo pide confirmación en un diálogo", async () => {
    apiRequest.mockResolvedValue({ estado: "exito", datos: { ...ana, rol: "LECTURA" } });
    render(<EquipoView miembros={[ana, beto]} invitaciones={[]} />);

    fireEvent.change(screen.getByLabelText("Rol de ana@torres.pe"), { target: { value: "LECTURA" } });
    const dialogo = await screen.findByRole("dialog");
    expect(dialogo.textContent).toContain("perderás el acceso de administrador");
    expect(dialogo.textContent).toContain("Solo lectura");
    expect(apiRequest).not.toHaveBeenCalled();

    fireEvent.click(within(dialogo).getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(apiRequest).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Rol de ana@torres.pe"), { target: { value: "LECTURA" } });
    fireEvent.click(await screen.findByRole("button", { name: "Sí, dejar de ser administrador" }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith("/api/proxy/cuenta/usuarios/u1/rol", { method: "PUT", body: { rol: "LECTURA" } }));
  });

  it("cambiar el rol de otro no pide confirmación", async () => {
    apiRequest.mockResolvedValue({ estado: "exito", datos: { ...beto, rol: "ADMIN" } });
    render(<EquipoView miembros={[ana, beto]} invitaciones={[]} />);
    fireEvent.change(screen.getByLabelText("Rol de beto@torres.pe"), { target: { value: "ADMIN" } });
    expect(screen.queryByRole("dialog")).toBeNull();
    await waitFor(() => expect(apiRequest).toHaveBeenCalled());
  });

  it("al desactivar a un administrador avisa que sus API keys siguen vigentes, con el enlace", () => {
    const dani: MiembroDelEquipo = { ...beto, id: "u3", email: "dani@torres.pe", rol: "ADMIN" };
    render(<EquipoView miembros={[ana, dani, beto]} invitaciones={[]} />);

    fireEvent.click(within(screen.getByTestId("miembro-dani@torres.pe")).getByRole("button", { name: /Desactivar/ }));
    const aviso = screen.getByTestId("aviso-api-keys");
    expect(aviso.textContent).toContain("siguen vigentes");
    expect(within(aviso).getByRole("link", { name: "API keys" }).getAttribute("href")).toBe("/api-keys");

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    fireEvent.click(within(screen.getByTestId("miembro-beto@torres.pe")).getByRole("button", { name: /Desactivar/ }));
    expect(screen.queryByTestId("aviso-api-keys")).toBeNull();
  });

  it("desactivar pide confirmación y no se ofrece para uno mismo", async () => {
    apiRequest.mockResolvedValue({ estado: "exito", datos: { ...beto, activo: false } });
    render(<EquipoView miembros={[ana, beto]} invitaciones={[]} />);
    expect(within(screen.getByTestId("miembro-ana@torres.pe")).queryByRole("button", { name: /Desactivar/ })).toBeNull();

    fireEvent.click(within(screen.getByTestId("miembro-beto@torres.pe")).getByRole("button", { name: /Desactivar/ }));
    expect(apiRequest).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: /Sí, desactivar/ }));

    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith("/api/proxy/cuenta/usuarios/u2/desactivar", { method: "POST" }));
  });

  it("reenviar y revocar una invitación", async () => {
    apiRequest.mockResolvedValue({ estado: "exito", datos: { invitacion: caro, correo_enviado: true } });
    render(<EquipoView miembros={[ana]} invitaciones={[caro]} />);

    fireEvent.click(screen.getByRole("button", { name: /Reenviar/ }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith("/api/proxy/cuenta/invitaciones/i1/reenviar", { method: "POST" }));
    expect(await screen.findByText(/Enviamos un enlace nuevo a caro@estudio.pe/)).toBeTruthy();

    await waitFor(() => expect((screen.getByRole("button", { name: /^Revocar$/ }) as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(screen.getByRole("button", { name: /^Revocar$/ }));
    fireEvent.click(await screen.findByRole("button", { name: /Sí, revocar/ }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith("/api/proxy/cuenta/invitaciones/i1", { method: "DELETE" }));
  });

  it("en modo soporte todo queda deshabilitado", () => {
    render(
      <SoloLectura activa>
        <EquipoView miembros={[ana, beto]} invitaciones={[caro]} />
      </SoloLectura>,
    );
    expect((screen.getByLabelText("Rol de beto@torres.pe") as HTMLSelectElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: /Reenviar/ }) as HTMLButtonElement).disabled).toBe(true);
  });

  /** #498: reactivar a alguien ocupa un lugar del plan; si no hay, el administrador ve qué plan tiene y cómo liberar uno, no un «error genérico». */
  it("si el plan no tiene lugar para reactivar, muestra lo que dice el backend", async () => {
    const mensaje = "Tu plan Gratis incluye 1 usuario y ya tienes 1 usuario (activos o con invitación pendiente). Desactiva a alguien o revoca una invitación pendiente, o pide un cambio de plan";
    apiRequest.mockResolvedValue({ estado: "error", codigo: "CUPO_DEL_PLAN", datos: null, mensaje, errores: null });
    render(<EquipoView miembros={[ana, { ...beto, activo: false }]} invitaciones={[]} />);

    fireEvent.click(within(screen.getByTestId("miembro-beto@torres.pe")).getByRole("button", { name: "Reactivar" }));

    expect((await screen.findAllByRole("alert")).map((a) => a.textContent).join(" ")).toContain(mensaje);
    expect(apiRequest).toHaveBeenCalledWith("/api/proxy/cuenta/usuarios/u2/reactivar", { method: "POST" });
  });
});
