import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AJUSTE_VACIO } from "@/lib/comprobantes/ajustes";
import type { Plantilla } from "@/lib/comprobantes/duplicar";
import { SoloLectura } from "@/lib/solo-lectura";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

const preferencia = vi.hoisted(() => ({ avanzadas: false }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
vi.mock("@/lib/use-preferencias-de-emision", () => ({ usePreferenciasDeEmision: () => ({ opcionesAvanzadas: preferencia.avanzadas, cargando: false }) }));

const SERIES = [
  { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" },
];

/** Los «+» de la fila «Agregar:», por su texto. */
const extras = () => within(screen.getByTestId("extras")).getAllByRole("button").map((b) => b.textContent?.trim());

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null })))));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("NuevoComprobanteForm — «Mostrar opciones avanzadas» (#560)", () => {
  it("apagada, la factura ofrece solo crédito, detracción y correo, y dice dónde activar las demás", () => {
    preferencia.avanzadas = false;
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    expect(extras()).toEqual(["Crédito", "Detracción", "Correo"]);
    const enlace = screen.getByRole("link", { name: "Más opciones en Empresa → Emisión" });
    expect(enlace).toHaveAttribute("href", "/empresa?seccion=emision");
    // En otra pestaña: seguir el enlace aquí cerraría el modal con lo escrito.
    expect(enlace).toHaveAttribute("target", "_blank");
  });

  it("apagada, la boleta ofrece detracción y correo", () => {
    preferencia.avanzadas = false;
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    expect(extras()).toEqual(["Detracción", "Correo"]);
  });

  it("encendida, ofrece todos los «+» y no muestra la nota", () => {
    preferencia.avanzadas = true;
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    expect(extras()).toEqual(["Crédito", "Detracción", "Percepción", "Retención", "Tipo de operación", "Más opciones", "Anticipos", "Referencias", "Correo"]);
    expect(screen.queryByTestId("extras-ocultos")).toBeNull();
  });

  it("apagada, una copia que trae una exportación y un descuento global los muestra igual, con su «+» activo", () => {
    preferencia.avanzadas = false;
    const plantilla: Plantilla = {
      tipo: "factura",
      moneda: "USD",
      operacion: "0200",
      cliente: { tipoDoc: "0", numDoc: "EXT123", razonSocial: "ACME INC", direccion: "", pais: "US" },
      lineas: [],
      descuentoGlobal: { ...AJUSTE_VACIO, valor: "10" },
      cargoGlobal: AJUSTE_VACIO,
      redondeo: "",
      omitidos: [],
    };
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} plantilla={plantilla} />);

    expect(extras()).toEqual(["Crédito", "Tipo de operación", "Más opciones", "Correo"]);
    expect(screen.getByRole("button", { name: "Tipo de operación" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("extra-operacion")).toBeInTheDocument();
    expect(screen.getByTestId("extra-masOpciones")).toBeInTheDocument();
  });

  it("la nota es solo para quien puede cambiar el ajuste: un emisor no la ve", () => {
    preferencia.avanzadas = false;
    render(
      <SoloLectura activa={false} rol="EMISOR">
        <NuevoComprobanteForm series={SERIES} tasaIgv={18} />
      </SoloLectura>,
    );

    expect(extras()).toEqual(["Crédito", "Detracción", "Correo"]);
    expect(screen.queryByTestId("extras-ocultos")).toBeNull();
  });

  it("un administrador sí la ve", () => {
    preferencia.avanzadas = false;
    render(
      <SoloLectura activa={false} rol="ADMIN">
        <NuevoComprobanteForm series={SERIES} tasaIgv={18} />
      </SoloLectura>,
    );

    expect(screen.getByTestId("extras-ocultos")).toBeInTheDocument();
  });
});
