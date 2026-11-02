import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/browser";
import type { EstadoDeGuia, Guia } from "@/lib/api/guias";
import { SoloLectura } from "@/lib/solo-lectura";
import { GuiaAcciones } from "./guia-acciones";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

function guia(estado: EstadoDeGuia): Guia {
  return {
    id: "g-1",
    tipo: "09",
    serie: "T001",
    numero: 3,
    nombre_archivo: "20100047226-09-T001-3",
    fecha_emision: "2026-10-20",
    estado,
    destinatario: { tipo_doc: "6", num_doc: "20601234565", nombre: "PESQUERA PACÍFICO S.A.C." },
    traslado: {
      fecha_inicio: "2026-10-21",
      peso_bruto: 10,
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
  };
}

const exito = { estado: "exito" as const, datos: null, mensaje: null, codigo: null, errores: null };
const fallo = (codigo: string, mensaje: string | null = null) => ({ estado: "error" as const, datos: null, mensaje, codigo, errores: null });

beforeEach(() => {
  vi.mocked(apiRequest).mockReset();
  refresh.mockReset();
});
afterEach(cleanup);

describe("GuiaAcciones", () => {
  it("una guía firmada ofrece enviar; una con error de envío, reenviar", () => {
    render(<GuiaAcciones guia={guia("FIRMADO")} />);
    expect(screen.getByRole("button", { name: "Enviar a SUNAT" })).toBeEnabled();
    cleanup();
    render(<GuiaAcciones guia={guia("ERROR_ENVIO")} />);
    expect(screen.getByRole("button", { name: "Reenviar a SUNAT" })).toBeEnabled();
  });

  it("una guía que ya tiene la respuesta de SUNAT no ofrece nada", () => {
    for (const estado of ["ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO"] as const) {
      const { container } = render(<GuiaAcciones guia={guia(estado)} />);
      expect(container).toBeEmptyDOMElement();
      cleanup();
    }
  });

  it("enviar llama al endpoint de la guía y refresca la ficha", async () => {
    vi.mocked(apiRequest).mockResolvedValue(exito);
    render(<GuiaAcciones guia={guia("FIRMADO")} />);

    fireEvent.click(screen.getByRole("button", { name: "Enviar a SUNAT" }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith("/api/proxy/guias-remision/g-1/enviar", { method: "POST" });
  });

  it("consultar el ticket llama a su endpoint", async () => {
    vi.mocked(apiRequest).mockResolvedValue(exito);
    render(<GuiaAcciones guia={guia("ENVIADO_PENDIENTE_TICKET")} />);

    fireEvent.click(screen.getByRole("button", { name: "Consultar el ticket" }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith("/api/proxy/guias-remision/g-1/ticket", { method: "POST" });
  });

  it("dos clics seguidos mandan un solo pedido", async () => {
    let resolver: (v: typeof exito) => void = () => {};
    vi.mocked(apiRequest).mockReturnValue(new Promise((r) => (resolver = r)));
    render(<GuiaAcciones guia={guia("FIRMADO")} />);
    const boton = screen.getByRole("button", { name: "Enviar a SUNAT" });

    fireEvent.click(boton);
    fireEvent.click(boton);

    expect(apiRequest).toHaveBeenCalledTimes(1);
    resolver(exito);
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it("un rechazo del backend se muestra con su mensaje y no refresca", async () => {
    vi.mocked(apiRequest).mockResolvedValue(fallo("GRE_NO_DISPONIBLE", "El envío no está habilitado en este servidor"));
    render(<GuiaAcciones guia={guia("FIRMADO")} />);

    fireEvent.click(screen.getByRole("button", { name: "Enviar a SUNAT" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("El envío no está habilitado en este servidor");
    expect(refresh).not.toHaveBeenCalled();
  });

  it("sin mensaje del backend muestra el texto del código en el idioma de la sesión", async () => {
    vi.mocked(apiRequest).mockResolvedValue(fallo("ESTADO_NO_ENVIABLE"));
    render(<GuiaAcciones guia={guia("FIRMADO")} />);

    fireEvent.click(screen.getByRole("button", { name: "Enviar a SUNAT" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("El documento no está en un estado que se pueda enviar a SUNAT.");
  });

  it("un corte de red no dice que falló: avisa que no se sabe si llegó y manda a recargar", async () => {
    vi.mocked(apiRequest).mockResolvedValue(fallo("RED_CORTADA"));
    render(<GuiaAcciones guia={guia("FIRMADO")} />);

    fireEvent.click(screen.getByRole("button", { name: "Enviar a SUNAT" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("no sabemos si el pedido llegó");
    expect(refresh).not.toHaveBeenCalled();
  });

  it("un envío en curso solo se retoma tras marcar que se revisó en SOL, y manda forzar=true", async () => {
    vi.mocked(apiRequest).mockResolvedValue(exito);
    render(<GuiaAcciones guia={guia("ENVIANDO")} />);
    const retomar = screen.getByRole("button", { name: "Retomar el envío" });
    expect(retomar).toBeDisabled();

    fireEvent.click(screen.getByRole("checkbox", { name: "Revisé en SOL que la guía no llegó" }));
    expect(retomar).toBeEnabled();
    fireEvent.click(retomar);

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(apiRequest).toHaveBeenCalledWith("/api/proxy/guias-remision/g-1/enviar?forzar=true", { method: "POST" });
  });

  it("avisa que un envío en curso solo se retoma pasados 10 minutos, que es cuando el backend lo permite (H1)", () => {
    render(<GuiaAcciones guia={guia("ENVIANDO")} />);

    expect(screen.getByText(/10 minutos/)).toBeInTheDocument();
  });

  it("con el rol de solo lectura las acciones se ven apagadas, con el motivo", () => {
    render(
      <SoloLectura activa={false} rol="LECTURA">
        <GuiaAcciones guia={guia("FIRMADO")} />
      </SoloLectura>,
    );
    const boton = screen.getByRole("button", { name: "Enviar a SUNAT" });
    expect(boton).toBeDisabled();
    expect(boton).toHaveAttribute("title");
  });
});
