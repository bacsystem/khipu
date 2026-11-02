import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { PasoGuia } from "@/lib/empresa/guia-emision";
import { GuiaParaEmitir } from "./guia-para-emitir";

const paso = (id: PasoGuia["id"], listo: boolean, extra: Partial<PasoGuia> = {}): PasoGuia => ({
  id,
  titulo: `Paso ${id}`,
  detalle: `detalle ${id}`,
  listo,
  recomendado: false,
  enlace: `/ir/${id}`,
  accion: `Hacer ${id}`,
  ...extra,
});

/** #375: la tarjeta aparece mientras falte algo, con un enlace por paso pendiente, y desaparece cuando todo está listo. */
describe("GuiaParaEmitir", () => {
  afterEach(cleanup);

  it("lista los pasos, cuántos están listos y un enlace solo en los pendientes", () => {
    render(<GuiaParaEmitir pasos={[paso("certificado", true), paso("sol", false), paso("series", false)]} />);
    expect(screen.getByText("Para empezar a emitir")).toBeInTheDocument();
    expect(screen.getByTestId("guia-emitir-avance")).toHaveTextContent("1 de 3 listos");
    expect(screen.getByRole("link", { name: "Hacer sol" })).toHaveAttribute("href", "/ir/sol");
    expect(screen.getByRole("link", { name: "Hacer series" })).toHaveAttribute("href", "/ir/series");
    expect(screen.queryByRole("link", { name: "Hacer certificado" })).not.toBeInTheDocument();
  });

  it("anuncia el estado de cada paso a un lector de pantalla", () => {
    render(<GuiaParaEmitir pasos={[paso("certificado", true), paso("sol", false)]} />);
    const items = screen.getAllByRole("listitem");
    expect(within(items[0]).getByText(", listo")).toBeInTheDocument();
    expect(within(items[1]).getByText(", pendiente")).toBeInTheDocument();
  });

  it("marca como recomendado el paso que no bloquea, solo mientras está pendiente", () => {
    const { rerender } = render(<GuiaParaEmitir pasos={[paso("sol", false), paso("domicilio", false, { recomendado: true })]} />);
    expect(screen.getByText("recomendado")).toBeInTheDocument();
    rerender(<GuiaParaEmitir pasos={[paso("sol", false), paso("domicilio", true, { recomendado: true })]} />);
    expect(screen.queryByText("recomendado")).not.toBeInTheDocument();
  });

  it("con todo listo no muestra nada", () => {
    const { container } = render(<GuiaParaEmitir pasos={[paso("certificado", true), paso("sol", true)]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
