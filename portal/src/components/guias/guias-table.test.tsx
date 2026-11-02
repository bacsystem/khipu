import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Guia } from "@/lib/api/guias";
import { GuiasTable } from "./guias-table";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }) }));

function guia(parcial: Partial<Guia> & Pick<Guia, "id" | "numero" | "estado">): Guia {
  return {
    tipo: "09",
    serie: "T001",
    nombre_archivo: "x",
    fecha_emision: "2026-10-20",
    destinatario: { tipo_doc: "6", num_doc: "20601234565", nombre: "PESQUERA PACÍFICO S.A.C." },
    traslado: {
      fecha_inicio: "2026-10-21",
      peso_bruto: 25.5,
      unidad_peso: "KGM",
      partida: { ubigeo: "150101", direccion: "Av. Argentina 1" },
      llegada: { ubigeo: "150131", direccion: "Calle Los Pinos 1" },
      vehiculos: [],
      conductores: [],
      indicadores: [],
    },
    items: [{ descripcion: "Laptop", unidad: "NIU", cantidad: 1 }],
    documentos_relacionados: [],
    intentos: 0,
    consultas_ticket: 0,
    ...parcial,
  };
}

beforeEach(() => push.mockReset());
afterEach(cleanup);

describe("GuiasTable", () => {
  it("lista cada guía con su número de ocho dígitos, su tipo, su destinatario, su peso y su estado", () => {
    render(<GuiasTable datos={[guia({ id: "g-1", numero: 8, estado: "ACEPTADO" }), guia({ id: "g-2", numero: 1, serie: "V001", tipo: "31", estado: "FIRMADO" })]} total={2} pagina={1} porPagina={10} filtros={{}} />);

    const fila = screen.getByRole("row", { name: /T001-00000008/ });
    expect(within(fila).getByText("Remitente (09)")).toBeInTheDocument();
    expect(within(fila).getByText("PESQUERA PACÍFICO S.A.C.")).toBeInTheDocument();
    expect(within(fila).getByText("25.50 KGM")).toBeInTheDocument();
    expect(within(fila).getByText("Aceptada")).toBeInTheDocument();
    expect(within(screen.getByRole("row", { name: /V001-00000001/ })).getByText("Firmada")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver la guía T001-00000008" })).toHaveAttribute("href", "/guias/g-1");
  });

  it("sin guías ni filtros dice que todavía no hay; con filtros, que ninguna coincide, y ofrece limpiar", () => {
    const { rerender } = render(<GuiasTable datos={[]} total={0} pagina={1} porPagina={10} filtros={{}} />);
    expect(screen.getByText("Todavía no hay guías de remisión.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Limpiar" })).toBeNull();

    rerender(<GuiasTable datos={[]} total={0} pagina={1} porPagina={10} filtros={{ estado: "RECHAZADO" }} />);
    expect(screen.getByText("Ninguna guía coincide con los filtros.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Limpiar" })).toHaveAttribute("href", "/guias");
  });

  /** #531: la barra de filtros del design system aplica cada filtro al cambiarlo (sin botón «Filtrar»), en mayúsculas la serie, y vuelve a la primera página. */
  it("buscar una serie lleva a la URL con el filtro y vuelve a la primera página", async () => {
    vi.useFakeTimers();
    try {
      render(<GuiasTable datos={[]} total={0} pagina={3} porPagina={10} filtros={{ estado: "ERROR_ENVIO" }} />);
      fireEvent.change(screen.getByLabelText("Serie"), { target: { value: " t001 " } });
      await vi.advanceTimersByTimeAsync(1000);
      expect(push).toHaveBeenCalledWith("/guias?estado=ERROR_ENVIO&serie=T001");
    } finally {
      vi.useRealTimers();
    }
  });

  it("una fecha se quita con su ✕ y el filtro sale de la URL", () => {
    render(<GuiasTable datos={[]} total={0} pagina={2} porPagina={10} filtros={{ desde: "2026-10-01", hasta: "2026-10-31" }} />);
    fireEvent.click(screen.getByRole("button", { name: "Quitar la fecha desde" }));
    expect(push).toHaveBeenCalledWith("/guias?hasta=2026-10-31");
  });

  it("los filtros de la URL se ven en la barra", () => {
    render(<GuiasTable datos={[]} total={0} pagina={1} porPagina={10} filtros={{ estado: "ACEPTADO", tipo: "31", serie: "V001", desde: "2026-10-01", hasta: "2026-10-31" }} />);

    expect(screen.getByRole("combobox", { name: "Estado" })).toHaveTextContent("Aceptada");
    expect(screen.getByRole("combobox", { name: "Tipo" })).toHaveTextContent("Transportista");
    expect(screen.getByLabelText("Serie")).toHaveValue("V001");
    expect(screen.getByRole("button", { name: "Quitar la fecha desde" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Quitar la fecha hasta" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Filtrar" })).toBeNull();
  });

  it("el pie pagina con enlaces que conservan los filtros", () => {
    render(<GuiasTable datos={[guia({ id: "g-1", numero: 8, estado: "ACEPTADO" })]} total={25} pagina={1} porPagina={10} filtros={{ estado: "ACEPTADO" }} />);

    expect(document.body.textContent).toContain("de 25 guías");
    const enlaces = screen.getAllByRole("link").map((a) => a.getAttribute("href"));
    expect(enlaces).toContain("/guias?estado=ACEPTADO&pagina=2");
  });
});
