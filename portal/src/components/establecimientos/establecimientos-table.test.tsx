import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import type { Establecimiento } from "@/lib/api/establecimientos";
import { SoloLectura } from "@/lib/solo-lectura";
import { EstablecimientosTable } from "./establecimientos-table";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

afterEach(cleanup);

const anexo: Establecimiento = {
  codigo: "0001",
  nombre: "Tienda Centro",
  domicilio: { ubigeo: "150101", direccion: "Jr. de la Unión 100", urbanizacion: null } as Establecimiento["domicilio"],
  activo: true,
  principal: false,
};

/** H4 (#21): dar de baja un establecimiento es configuración, como editarlo: sin ese permiso se ve deshabilitado. */
describe("EstablecimientosTable según el rol", () => {
  for (const [rol, deshabilitado] of [["ADMIN", false], ["EMISOR", true], ["LECTURA", true]] as const) {
    it(`${rol}: «Dar de baja» ${deshabilitado ? "deshabilitado" : "habilitado"}`, () => {
      render(
        <SoloLectura activa={false} rol={rol}>
          <EstablecimientosTable establecimientos={[anexo]} series={[]} />
        </SoloLectura>,
      );
      const baja = screen.getByTestId("baja-establecimiento-0001") as HTMLButtonElement;
      expect(baja.disabled).toBe(deshabilitado);
      if (deshabilitado) expect(baja.title).toBe("Solo un administrador de la cuenta puede cambiar la configuración.");
    });
  }

  it("en inglés (#25) la tabla y sus acciones salen traducidas", () => {
    fijarLocaleDePrueba("en");
    render(
      <SoloLectura activa={false} rol="ADMIN">
        <EstablecimientosTable establecimientos={[anexo]} series={[]} />
      </SoloLectura>,
    );
    expect(screen.getByRole("heading", { name: "Issuing points" })).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByTestId("baja-establecimiento-0001")).toHaveTextContent("Deactivate");
  });

  it("en modo soporte también", () => {
    render(
      <SoloLectura activa>
        <EstablecimientosTable establecimientos={[anexo]} series={[]} />
      </SoloLectura>,
    );
    expect((screen.getByTestId("baja-establecimiento-0001") as HTMLButtonElement).disabled).toBe(true);
  });
});
