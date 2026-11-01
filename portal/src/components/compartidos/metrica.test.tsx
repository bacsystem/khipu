import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Metrica } from "./metrica";

describe("Metrica (#523)", () => {
  afterEach(cleanup);

  it("un valor numérico va en el tamaño de métrica", () => {
    render(<Metrica etiqueta="Emitidos" ayuda="este mes">12</Metrica>);
    expect(screen.getByText("12").className).toContain("text-metrica tabular-nums");
  });

  /** Una palabra («Sin certificado», «AddressTypeCode») a 22 px dominaba la tarjeta: con `texto` baja a 18 px. */
  it("con texto, el valor va en text-metrica-texto", () => {
    render(<Metrica etiqueta="Estado del certificado" ayuda="Carga tu .p12" texto>Sin certificado</Metrica>);
    const valor = screen.getByText("Sin certificado");
    expect(valor.className).toContain("text-metrica-texto");
    expect(valor.className).not.toContain("tabular-nums");
  });
});
