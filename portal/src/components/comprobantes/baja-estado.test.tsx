import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Baja } from "@/lib/api/facturas";
import { BajaEstado } from "./baja-estado";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

const BASE: Baja = {
  id: "b-1", identificador: "RA-20260918-1", comprobante: "F001-125", tipo_comprobante: "01", fecha_generacion: "2026-09-18", motivo: "Error en el RUC",
  estado: "ACEPTADA", ticket: "1789768174685", cdr: { codigo: "0", descripcion: "aceptada", observaciones: [] }, intentos: 1, ultimo_error: null,
};

afterEach(cleanup);

describe("BajaEstado", () => {
  it("una baja aceptada dice que el comprobante quedó anulado", () => {
    render(<BajaEstado baja={BASE} />);
    expect(screen.getByTestId("baja")).toHaveTextContent("Comunicación de baja RA-20260918-1");
    expect(screen.getByRole("status")).toHaveTextContent("Aceptada: comprobante anulado");
  });

  /** 274-H1: el resumen diario también informa una boleta que pasó el envío individual. Aceptado, la boleta quedó informada, no anulada. */
  it("un resumen de alta aceptado dice que la boleta quedó informada, no anulada", () => {
    render(<BajaEstado baja={{ ...BASE, identificador: "RC-20260918-2", comprobante: "B001-7", tipo_comprobante: "03", condicion: "ALTA", motivo: "Pasó el envío individual" }} />);
    expect(screen.getByTestId("baja")).toHaveTextContent("Resumen diario RC-20260918-2 (alta)");
    expect(screen.getByRole("status")).toHaveTextContent("Aceptado: boleta informada a SUNAT");
    expect(screen.getByTestId("baja")).not.toHaveTextContent("anulado");
  });

  /** #294: la nota sobre una boleta sigue su régimen: se anula y se informa en el resumen diario (RC), no en una comunicación de baja (RA). */
  it("la baja de una nota sobre boleta es un resumen diario, aunque el comprobante sea 07", () => {
    render(<BajaEstado baja={{ ...BASE, identificador: "RC-20260918-3", comprobante: "BC01-2", tipo_comprobante: "07", condicion: "BAJA" }} />);
    expect(screen.getByTestId("baja")).toHaveTextContent("Resumen diario RC-20260918-3");
    expect(screen.getByRole("status")).toHaveTextContent("Aceptada: comprobante anulado");
  });

  /** #331: pasado el 7.º día la boleta se informa igual; el resumen avisa que salió fuera de plazo y por qué SUNAT la observa (4443). */
  it("un resumen de alta fuera de plazo avisa la observación 4443", () => {
    const alta: Baja = { ...BASE, identificador: "RC-20261008-1", comprobante: "B001-7", tipo_comprobante: "03", condicion: "ALTA", fuera_de_plazo: true };
    render(<BajaEstado baja={alta} />);
    expect(screen.getByTestId("aviso-fuera-de-plazo")).toHaveTextContent("Informada fuera de plazo");
    expect(screen.getByTestId("aviso-fuera-de-plazo")).toHaveTextContent("4443");
    cleanup();
    render(<BajaEstado baja={{ ...alta, fuera_de_plazo: false }} />);
    expect(screen.queryByTestId("aviso-fuera-de-plazo")).toBeNull();
  });

  /** #338: un RC con el código 05 de las gratuitas que caducó sin enviarse queda descartado: no se reconsulta y dice qué resumen lo reemplazó. */
  it("un resumen descartado dice que lo reemplazó otro y no ofrece reconsultar", () => {
    const ultimo = "Reemplazado por RC-20270101-1: generado con el código 05 de las gratuitas, que SUNAT desactivó el 2027-01-01, y todavía sin enviar";
    render(<BajaEstado baja={{ ...BASE, identificador: "RC-20261231-1", comprobante: "B001-7", tipo_comprobante: "03", condicion: "BAJA", estado: "DESCARTADA", ticket: null, cdr: null, ultimo_error: ultimo }} />);
    expect(screen.getByRole("status")).toHaveTextContent("Reemplazada por un resumen nuevo");
    expect(screen.getByTestId("baja")).toHaveTextContent("RC-20270101-1");
    expect(screen.queryByRole("button", { name: /actualizar estado/i })).toBeNull();
  });

  /**
   * #353-H4: una baja cuyo RC caducó (#338) cuando ya habían pasado los 7 días no se envía: khipu la cierra RECHAZADA con 2957. No la rechazó SUNAT ni hay CDR:
   * decirlo así contradecía el detalle («no se envió»).
   */
  it("una baja vencida por plazo no dice que la rechazó SUNAT ni muestra un CDR", () => {
    const cdr = { codigo: "2957", descripcion: "El plazo para dar de baja venció: el comprobante se emitió el 2026-12-24 (…) RC-20261231-1 no se envió", observaciones: [] };
    render(<BajaEstado baja={{ ...BASE, identificador: "RC-20261231-1", comprobante: "B001-7", tipo_comprobante: "03", condicion: "BAJA", estado: "RECHAZADA", ticket: null, cdr }} />);
    expect(screen.getByRole("status")).toHaveTextContent("Vencida: pasaron los 7 días para anularla");
    expect(screen.getByTestId("baja")).not.toHaveTextContent("Rechazada por SUNAT");
    expect(screen.getByTestId("baja")).not.toHaveTextContent("CDR");
    expect(screen.getByTestId("baja")).toHaveTextContent("no se envió");
  });

  it("el alta de una nota sobre boleta dice que la nota quedó informada, no la boleta", () => {
    render(<BajaEstado baja={{ ...BASE, identificador: "RC-20260918-4", comprobante: "BC01-2", tipo_comprobante: "07", condicion: "ALTA", estado: "ENVIADA", cdr: null }} />);
    expect(screen.getByTestId("baja")).toHaveTextContent("Resumen diario RC-20260918-4 (alta)");
    expect(screen.getByTestId("baja")).toHaveTextContent("Mientras no responda, la nota queda enviada");
  });
});
