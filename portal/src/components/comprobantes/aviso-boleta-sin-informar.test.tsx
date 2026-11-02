import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { AvisoDePlazo } from "@/lib/comprobantes/plazo";
import { AvisoBoletaSinInformar } from "./aviso-boleta-sin-informar";

afterEach(cleanup);

const aviso = (fase: AvisoDePlazo["fase"], diasRestantes: number): AvisoDePlazo => ({ fase, limite: "2026-10-17", diasRestantes });

/**
 * #356: la ficha de una boleta (o nota B) que sigue sin informarse a SUNAT lo dice con lo que le queda, para que el cliente la envíe antes de que cierre el resumen diario. Cada
 * fase tiene su texto: el último día del envío individual, solo el resumen, el último día del resumen, y ya fuera de plazo.
 */
describe("AvisoBoletaSinInformar", () => {
  it("el último día del envío individual avisa que desde mañana solo va en el resumen diario", () => {
    render(<AvisoBoletaSinInformar aviso={aviso("ultimo-dia-individual", 2)} que="boleta" />);

    const a = screen.getByTestId("aviso-boleta-sin-informar");
    expect(a).toHaveAttribute("data-fase", "ultimo-dia-individual");
    expect(a).toHaveTextContent("Esta boleta todavía no se informó a SUNAT");
    expect(a).toHaveTextContent("Hoy es el último día para enviarla sola");
    expect(a).toHaveTextContent("hasta el 17 Oct 2026");
  });

  it("después solo va en el resumen diario y dice cuántos días quedan", () => {
    render(<AvisoBoletaSinInformar aviso={aviso("solo-resumen", 1)} que="boleta" />);

    expect(screen.getByTestId("aviso-boleta-sin-informar")).toHaveTextContent("Ya no se puede enviar sola");
    expect(screen.getByTestId("aviso-boleta-sin-informar")).toHaveTextContent("hasta el 17 Oct 2026 (queda 1 día)");
    cleanup();
    render(<AvisoBoletaSinInformar aviso={aviso("solo-resumen", 3)} que="boleta" />);
    expect(screen.getByTestId("aviso-boleta-sin-informar")).toHaveTextContent("(quedan 3 días)");
  });

  it("el último día del resumen es urgente y avisa qué pasa después", () => {
    render(<AvisoBoletaSinInformar aviso={aviso("ultimo-dia-resumen", 0)} que="boleta" />);

    const a = screen.getByTestId("aviso-boleta-sin-informar");
    expect(a).toHaveAttribute("data-urgente", "true");
    expect(a).toHaveTextContent("Hoy vence el plazo");
    expect(a).toHaveTextContent("fuera de plazo");
  });

  it("pasado el plazo dice que se informa fuera de plazo y que SUNAT puede observarla", () => {
    render(<AvisoBoletaSinInformar aviso={aviso("fuera-de-plazo", -3)} que="boleta" />);

    const a = screen.getByTestId("aviso-boleta-sin-informar");
    expect(a).toHaveAttribute("data-urgente", "true");
    expect(a).toHaveTextContent("El plazo venció el 17 Oct 2026");
    expect(a).toHaveTextContent("SUNAT puede observarla");
  });

  it("una nota dice «nota», no «boleta»", () => {
    render(<AvisoBoletaSinInformar aviso={aviso("solo-resumen", 1)} que="nota" />);

    expect(screen.getByTestId("aviso-boleta-sin-informar")).toHaveTextContent("Esta nota todavía no se informó a SUNAT");
    expect(screen.getByTestId("aviso-boleta-sin-informar")).toHaveTextContent("Ya no se puede enviar sola");
  });

  it("es un aviso accesible", () => {
    render(<AvisoBoletaSinInformar aviso={aviso("solo-resumen", 1)} que="boleta" />);

    expect(screen.getByRole("status")).toBe(screen.getByTestId("aviso-boleta-sin-informar"));
  });
});
