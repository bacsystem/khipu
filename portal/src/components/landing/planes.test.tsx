import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import es from "@/messages/es.json";
import { Planes } from "./planes";
import { planesDeRespaldo } from "./planes-datos";

afterEach(cleanup);

const tarjeta = (nombre: string) => screen.getByRole("heading", { level: 3, name: nombre }).closest("[data-slot=card]") as HTMLElement;

/** #580: el precio de la portada, con el IGV en su línea, y lo que cada plan dice de Producción y Beta. */
describe("Planes de la portada", () => {
  it("el monto y su sufijo no se parten, y el IGV va en su propia línea solo en los planes de pago", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto contacto={null} />);

    const emprende = tarjeta("Emprende");
    const precio = within(emprende).getByTestId("precio-plan");
    expect(precio.textContent?.trim()).toBe("S/ 29");
    expect(precio.className).toContain("whitespace-nowrap");
    expect(within(emprende).getByText("/mes").className).toContain("whitespace-nowrap");
    expect(within(emprende).getByText("IGV incluido")).toBeTruthy();

    expect(within(tarjeta("Gratis")).queryByText("IGV incluido")).toBeNull();
    expect(within(tarjeta("Gratis")).getByText("para siempre")).toBeTruthy();
  });

  it("al año dice el total una sola vez con el IGV, sin repetirlo", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto contacto={null} />);
    fireEvent.click(screen.getByRole("button", { name: /Anual/ }));

    const negocio = tarjeta("Negocio");
    expect(within(negocio).getByText("S/ 690 al año · 2 meses libres")).toBeTruthy();
    expect(within(negocio).getAllByText(/IGV/)).toHaveLength(1);
  });

  it("Gratis emite en Producción y prueba en Beta sin límite; nadie vende «Producción además de pruebas»", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto contacto={null} />);

    const gratis = tarjeta("Gratis");
    expect(within(gratis).getByText("10 documentos/mes")).toBeTruthy();
    expect(within(gratis).getByText("Comprobantes reales en Producción")).toBeTruthy();
    expect(within(gratis).getByText("Pruebas en Beta sin límite")).toBeTruthy();
    expect(within(tarjeta("Pro")).getByText("10 000 documentos/mes")).toBeTruthy();
    expect(screen.queryByText(/Producción además de pruebas|Entorno de pruebas de SUNAT/)).toBeNull();
    expect(screen.getByText(/las pruebas en Beta, los rechazos y los reintentos no consumen tu plan/)).toBeTruthy();
  });
});
